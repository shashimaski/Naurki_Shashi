package com.adi.naukri.orchestrator;

import com.adi.naukri.api.AccountInput;
import com.adi.naukri.automation.*;
import com.adi.naukri.report.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Orchestrates sequential per-account automation runs driven by a
 * {@link JobRequest}.
 *
 * <p>One run at a time. Worker runs on a single-thread executor.
 * Current run state is held in an {@link AtomicReference} so control ops
 * ({@link #stop}, {@link #continueNow}, {@link #skip}) can inspect / mutate
 * it safely from any thread.</p>
 *
 * <p>Password is held in-memory only and never logged.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class JobOrchestrator {

    // ------------------------------------------------------------------
    // Internal run state
    // ------------------------------------------------------------------

    private record JobRun(
            String              jobId,
            List<AccountInput>  pendingAccounts,
            String              currentEmail,
            GateSignal          gateSignal,
            boolean             stopRequested
    ) {
        JobRun withStop() {
            return new JobRun(jobId, pendingAccounts, currentEmail, gateSignal, true);
        }
    }

    /**
     * Shared signal object created per account when manualLogin is active.
     * The automator blocks on {@link #await}; the orchestrator calls
     * {@link #release} to resume or skip.
     */
    private static final class GateSignal {
        private final CountDownLatch latch    = new CountDownLatch(1);
        private volatile boolean     skipping = false;

        /** Blocks the caller until {@link #release} is called (or timeout). */
        boolean await(long timeoutSec) throws InterruptedException {
            return latch.await(timeoutSec, TimeUnit.SECONDS);
        }

        void release(boolean skip) {
            this.skipping = skip;
            latch.countDown();
        }

        boolean isSkipping() { return skipping; }
    }

    // ------------------------------------------------------------------
    // Dependencies
    // ------------------------------------------------------------------

    private final Automator                  automator;
    private final RetryPolicy                retryPolicy;
    private final ReportWriter               reportWriter;
    private final JobEventBus                eventBus;
    private final Supplier<PlaywrightSession> sessionFactory;
    private final RunRegistry                runRegistry;

    private final ExecutorService                 worker  = Executors.newSingleThreadExecutor();
    private final AtomicReference<JobRun>         runRef  = new AtomicReference<>(null);

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public JobOrchestrator(
            Automator automator,
            RetryPolicy retryPolicy,
            ReportWriter reportWriter,
            JobEventBus eventBus,
            Supplier<PlaywrightSession> sessionFactory,
            RunRegistry runRegistry) {
        this.automator      = automator;
        this.retryPolicy    = retryPolicy;
        this.reportWriter   = reportWriter;
        this.eventBus       = eventBus;
        this.sessionFactory = sessionFactory;
        this.runRegistry    = runRegistry;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Starts a new job run.
     *
     * @param req the job request
     * @return handle with jobId + future; future completes when run finishes
     * @throws IllegalStateException if a run is already in progress
     */
    public JobHandle start(JobRequest req) {
        String jobId = UUID.randomUUID().toString();
        List<AccountInput> pending = new ArrayList<>(req.accounts());

        JobRun newRun = new JobRun(jobId, pending, null, null, false);
        if (!runRef.compareAndSet(null, newRun)) {
            throw new IllegalStateException(
                    "A job run is already in progress; stop it before starting a new one.");
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> executeRun(jobId, req),
                worker);

        return new JobHandle(jobId, future);
    }

    /**
     * Requests the current run to stop immediately.
     *
     * <p>Sets the stopping flag so the per-account loop will not start the next account,
     * then calls {@link Automator#abort()} on the injected automator to force-close the
     * running {@link com.microsoft.playwright.BrowserContext}. Any Playwright call that
     * the automator thread is currently blocked on will receive a
     * {@link com.microsoft.playwright.PlaywrightException}, which the automator
     * classifies as stopped (not failed) because the stopping flag is set.</p>
     *
     * <p>Idempotent — safe to call when no run is in progress.</p>
     */
    public void stop(String jobId) {
        updateRun(jobId, run -> run.withStop());
        // Force-close the running browser context so the automator thread unblocks immediately
        // rather than waiting for the current Playwright timeout to expire.
        automator.abort();
    }

    /**
     * Signals the orchestrator to resume from a manual-login gate
     * (marks the login as completed).
     */
    public void continueNow(String jobId) {
        releaseGate(jobId, false);
    }

    /**
     * Signals the orchestrator to skip the current account that is waiting
     * at a manual-login gate.
     */
    public void skip(String jobId) {
        releaseGate(jobId, true);
    }

    /** Shuts down the worker executor. Call in tests / app shutdown. */
    public void shutdown() {
        worker.shutdownNow();
        try { worker.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    // ------------------------------------------------------------------
    // Core run loop
    // ------------------------------------------------------------------

    private void executeRun(String jobId, JobRequest req) {
        List<AccountResult>     results    = new ArrayList<>();
        Instant                 runStart   = Instant.now();
        List<AccountInput>      accounts   = new ArrayList<>(req.accounts());
        int                     total      = accounts.size();
        boolean                 wasStopped = false;   // local flag — not subject to runRef races

        eventBus.publish(new JobEvent.RunStarted(jobId, Instant.now(), total));

        AutomationRunMode initialMode = req.headless()
                ? AutomationRunMode.HEADLESS
                : AutomationRunMode.HEADED;

        // Build the per-run directory up-front so screenshots land inside it (I1 fix).
        String runTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(runStart);
        Path runDir = Path.of(req.outputFolder()).resolve(runTimestamp);

        // Resume folder — null when the caller did not enable the local-folder flow;
        // the automator will fall back to the Naukri download in that case.
        Path resumeFolder = (req.resumeFolderPath() != null && !req.resumeFolderPath().isBlank())
                ? Path.of(req.resumeFolderPath())
                : null;

        for (int i = 0; i < accounts.size(); i++) {
            AccountInput account = accounts.get(i);
            String       email   = account.email();
            String       name    = account.name();

            // Check stop flag (read from AtomicReference each iteration)
            JobRun current = runRef.get();
            if (current != null && current.stopRequested()) {
                wasStopped = true;
                // Emit SKIPPED for all remaining accounts starting at i
                for (int j = i; j < accounts.size(); j++) {
                    String skipped = accounts.get(j).email();
                    eventBus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), skipped, j));
                    AccountResult skippedResult = new AccountResult(
                            skipped, AccountStatus.SKIPPED, null,
                            null, null,
                            Instant.now(), Instant.now(), 0, List.of());
                    results.add(skippedResult);
                    eventBus.publish(new JobEvent.AccountCompleted(
                            jobId, Instant.now(), skipped, AccountStatus.SKIPPED, null, null));
                }
                break;
            }

            // Set current email in run state
            updateEmail(jobId, email);

            eventBus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), email, i));

            AccountResult result = processAccount(jobId, email, name, req.password(), initialMode,
                    req.effectiveBaseUrl(), runDir, resumeFolder,
                    req.manualLogin(), req.initialDelayMs());
            results.add(result);

            eventBus.publish(new JobEvent.AccountCompleted(
                    jobId, Instant.now(), email,
                    result.status(),
                    result.resumeOldName(),
                    result.resumeNewName()));

            // If the account was stopped mid-run (abort() was called), mark wasStopped
            // and skip remaining accounts — they will be handled as SKIPPED below.
            if (result.status() == AccountStatus.STOPPED) {
                wasStopped = true;
                // Mark all remaining accounts as SKIPPED
                for (int j = i + 1; j < accounts.size(); j++) {
                    String skipped = accounts.get(j).email();
                    eventBus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), skipped, j));
                    AccountResult skippedResult = new AccountResult(
                            skipped, AccountStatus.SKIPPED, null,
                            null, null,
                            Instant.now(), Instant.now(), 0, List.of());
                    results.add(skippedResult);
                    eventBus.publish(new JobEvent.AccountCompleted(
                            jobId, Instant.now(), skipped, AccountStatus.SKIPPED, null, null));
                }
                break;
            }
        }

        // Write report — capture the inputs used to start this run (password
        // is never persisted; only whether one was supplied is recorded).
        try {
            RunInputs inputs = RunInputs.from(jobId, runStart, req);
            reportWriter.write(runDir, inputs, results);
            runRegistry.record(jobId, runDir);
        } catch (Exception ex) {
            // Log but do not suppress run completion event
            System.err.println("[JobOrchestrator] ReportWriter failed: " + ex.getMessage());
        }

        if (wasStopped) {
            eventBus.publish(new JobEvent.RunStopped(jobId, Instant.now()));
        } else {
            JobEvent.Summary summary = buildSummary(results);
            eventBus.publish(new JobEvent.RunCompleted(jobId, Instant.now(), summary));
        }

        // Clear run state
        runRef.set(null);
    }

    // ------------------------------------------------------------------
    // Per-account processing
    // ------------------------------------------------------------------

    /**
     * Processes a single account, retrying per the {@link RetryPolicy} on failure.
     *
     * @param jobId          job identifier (for event publishing)
     * @param email          account email
     * @param password       account password (never logged)
     * @param initialMode    headless/headed as requested
     * @param baseUrl        effective base URL of the target site
     * @param runDir         per-run output directory; screenshots land inside runDir/screenshots/
     * @param manualLogin    true if programmatic login is bypassed for manual entry
     * @param initialDelayMs ms to sleep before launching the browser (0 = disabled)
     */
    private AccountResult processAccount(
            String jobId, String email, String name, String password,
            AutomationRunMode initialMode, String baseUrl, Path runDir,
            Path resumeFolder, boolean manualLogin, long initialDelayMs) {

        Instant start = Instant.now();

        // Emit PRE_START warmup step and sleep to let the WS handshake + run-screen render settle.
        if (initialDelayMs > 0) {
            eventBus.publish(new JobEvent.StepStarted(jobId, Instant.now(), email, "PRE_START.warmup"));
            try { Thread.sleep(initialDelayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        // Compute base timeout from RetryPolicy default constant
        final long baseTimeoutMs = 30_000L;
        List<RetryAttempt> attempts = retryPolicy.attemptsFor(initialMode, baseTimeoutMs);

        List<StepResult> lastStepResults = List.of();
        AccountStatus    lastStatus      = AccountStatus.FAILED;
        List<String>     lastDumpPaths   = List.of();

        for (RetryAttempt attempt : attempts) {
            AutomatorConfig cfg = new AutomatorConfig(
                    baseUrl,
                    runDir,                       // I1: screenshots go under runDir/screenshots/
                    resumeFolder,                 // null → download from Naukri; non-null → locate local file
                    attempt.timeoutMs(),          // scale page-load with each retry attempt
                    15_000L,
                    25_000L,                      // postLoginActionMs: 25 s for real Naukri hydration
                    manualLogin,
                    Duration.ofMinutes(5),
                    0L);                          // initialDelayMs handled above, not inside automator

            GateSignal gateSignal = manualLogin ? new GateSignal() : null;
            updateGate(jobId, gateSignal);

            ManualLoginGate gate = (e, timeout, dashboardReached) -> {
                if (gateSignal == null) return true;

                // Single publish of AwaitManualLogin per attempt (gate lambda is the sole emitter;
                // NaukriAutomator no longer calls listener.onManualLoginAwait — I2 fix).
                eventBus.publish(new JobEvent.AwaitManualLogin(
                        jobId, Instant.now(), e, timeout.toSeconds()));

                try {
                    boolean signalled = gateSignal.await(timeout.toSeconds());
                    if (!signalled) return false;
                    if (gateSignal.isSkipping()) return false;
                    return true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            };

            // Build step listener — emits STEP_STARTED before each top-level step,
            // STEP_STARTED for each sub-step (using rich dot-notation step name),
            // STEP_COMPLETED / STEP_FAILED after (no-op for onManualLoginAwait, gate handles it).
            StepListener listener = new StepListener() {
                @Override
                public void onStepStarted(AutomationStep step) {
                    eventBus.publish(new JobEvent.StepStarted(
                            jobId, Instant.now(), email, step.name()));
                }

                @Override
                public void onSubStepStarted(String subStep) {
                    // Publish a STEP_STARTED with the rich sub-step string (e.g. "LOGIN.type-email").
                    // The FE RunTable shows whatever step string arrives — no FE change needed.
                    eventBus.publish(new JobEvent.StepStarted(
                            jobId, Instant.now(), email, subStep));
                }

                @Override
                public void onStep(StepResult r) {
                    String stepName = r.step().name();
                    if (r.ok()) {
                        eventBus.publish(new JobEvent.StepCompleted(
                                jobId, Instant.now(), email, stepName, r.durationMs()));
                    } else {
                        eventBus.publish(new JobEvent.StepFailed(
                                jobId, Instant.now(), email, stepName, r.error()));
                    }
                }

                @Override
                public void onManualLoginAwait(String e) {
                    // No-op: gate lambda publishes AwaitManualLogin (I2 fix — avoids duplicate).
                }
            };

            PlaywrightSession session = sessionFactory.get();

            List<StepResult> stepResults;
            try {
                stepResults = automator.run(email, name, password, attempt.mode(), cfg, session, gate, listener);
            } catch (Exception ex) {
                // If stop was requested and the exception is from aborting the context,
                // surface a STOPPED status immediately instead of retrying.
                JobRun currentRun = runRef.get();
                if (currentRun != null && currentRun.stopRequested()) {
                    lastStepResults = List.of();
                    lastStatus      = AccountStatus.STOPPED;
                    break;
                }
                // Automator threw for another reason — continue to next retry attempt
                lastStepResults = List.of();
                lastStatus      = AccountStatus.FAILED;
                continue;
            }

            lastStepResults = stepResults;

            // If stop was requested and the last step result indicates a Playwright abort,
            // classify the whole account as STOPPED so it does not count as FAILED.
            JobRun currentRun = runRef.get();
            if (currentRun != null && currentRun.stopRequested()) {
                lastStatus    = AccountStatus.STOPPED;
                lastDumpPaths = List.of();
                break;
            }

            lastStatus = deriveStatus(stepResults);

            // Consider the attempt successful if all steps passed (last step ok==true means LOGOUT passed)
            if (lastStatus == AccountStatus.OK) {
                break; // success — no more retries needed
            }

            // Short-circuit statuses that will not improve on retry
            if (lastStatus == AccountStatus.AUTH_FAILED || lastStatus == AccountStatus.REQUIRES_MANUAL) {
                break;
            }
            // Otherwise: FAILED — continue to next retry attempt
        }

        Instant end = Instant.now();

        // Build step timings from the last attempt's results
        List<StepTiming> timings = lastStepResults.stream()
                .map(r -> new StepTiming(r.step().name(), r.durationMs()))
                .toList();

        String resumeOldName = null;
        String resumeNewName = null;

        return new AccountResult(email, lastStatus, firstError(lastStepResults),
                resumeOldName, resumeNewName, start, end, 0, timings, lastDumpPaths);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AccountStatus deriveStatus(List<StepResult> steps) {
        if (steps.isEmpty()) return AccountStatus.FAILED;
        for (StepResult r : steps) {
            if (!r.ok()) {
                String err = r.error() == null ? "" : r.error().toLowerCase();
                if (err.contains("auth") || err.contains("login") || err.contains("credential")) {
                    return AccountStatus.AUTH_FAILED;
                }
                if (err.contains("manual")) {
                    return AccountStatus.REQUIRES_MANUAL;
                }
                return AccountStatus.FAILED;
            }
        }
        return AccountStatus.OK;
    }

    private String firstError(List<StepResult> steps) {
        for (StepResult r : steps) {
            if (!r.ok()) return r.error();
        }
        return null;
    }

    private JobEvent.Summary buildSummary(List<AccountResult> results) {
        int ok = 0, authFailed = 0, requiresManual = 0, failed = 0, skipped = 0;
        for (AccountResult r : results) {
            switch (r.status()) {
                case OK             -> ok++;
                case AUTH_FAILED    -> authFailed++;
                case REQUIRES_MANUAL-> requiresManual++;
                case FAILED         -> failed++;
                case SKIPPED        -> skipped++;
                case STOPPED        -> skipped++; // stopped accounts count against skipped in summary
            }
        }
        return new JobEvent.Summary(ok, authFailed, requiresManual, failed, skipped);
    }

    private void updateRun(String jobId, java.util.function.UnaryOperator<JobRun> fn) {
        runRef.updateAndGet(run -> {
            if (run == null || !run.jobId().equals(jobId)) return run;
            return fn.apply(run);
        });
    }

    private void updateEmail(String jobId, String email) {
        runRef.updateAndGet(run -> {
            if (run == null || !run.jobId().equals(jobId)) return run;
            return new JobRun(run.jobId(), run.pendingAccounts(), email, run.gateSignal(), run.stopRequested());
        });
    }

    private void updateGate(String jobId, GateSignal gate) {
        runRef.updateAndGet(run -> {
            if (run == null || !run.jobId().equals(jobId)) return run;
            return new JobRun(run.jobId(), run.pendingAccounts(), run.currentEmail(), gate, run.stopRequested());
        });
    }

    private void releaseGate(String jobId, boolean skip) {
        JobRun run = runRef.get();
        if (run != null && run.jobId().equals(jobId) && run.gateSignal() != null) {
            run.gateSignal().release(skip);
        }
    }
}
