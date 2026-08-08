package com.adi.naukri.report;

import com.adi.naukri.api.AccountInput;
import com.adi.naukri.orchestrator.JobRequest;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of the inputs supplied for a run — captured in the report so the
 * operator can reconstruct exactly how the batch was started.
 *
 * <p><strong>Security:</strong> the password is intentionally <em>never</em>
 * captured. {@link #passwordProvided()} records only whether one was supplied.</p>
 *
 * @param jobId            the orchestrator-assigned job id.
 * @param runStartedAt     UTC timestamp when the run began.
 * @param accounts         the (name, email) tuples that were queued to run.
 * @param headless         whether the browser ran headless.
 * @param manualLogin      whether manual-login mode was enabled.
 * @param outputFolder     base output folder (per-run subfolder lives under this).
 * @param resumeFolderPath folder used to locate per-account resume files;
 *                         {@code null} means the Naukri download flow was used.
 * @param baseUrlOverride  test-only override; {@code null} in production runs.
 * @param initialDelayMs   warm-up pause per account before browser launch.
 * @param passwordProvided {@code true} if a non-blank password was supplied
 *                         (never captures the value).
 *
 * Author: Adikarthik Gupta C B
 */
public record RunInputs(
        String             jobId,
        Instant            runStartedAt,
        List<AccountInput> accounts,
        boolean            headless,
        boolean            manualLogin,
        String             outputFolder,
        String             resumeFolderPath,
        String             baseUrlOverride,
        long               initialDelayMs,
        boolean            passwordProvided
) {
    /** Convenience factory that pulls everything (except the password value) from a JobRequest. */
    public static RunInputs from(String jobId, Instant runStartedAt, JobRequest req) {
        boolean passwordProvided = req.password() != null && !req.password().isBlank();
        return new RunInputs(
                jobId,
                runStartedAt,
                req.accounts(),
                req.headless(),
                req.manualLogin(),
                req.outputFolder(),
                req.resumeFolderPath(),
                req.baseUrlOverride(),
                req.initialDelayMs(),
                passwordProvided
        );
    }
}
