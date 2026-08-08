package com.adi.naukri.automation;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Full per-account Naukri automation runner.
 *
 * <p>Steps executed in order (revised 2026-07-17):
 * <ol>
 *   <li>LOGIN — navigate to the Naukri homepage, click the Login link (simulates real user
 *       arrival), fill credentials with human-like keystroke delays (or wait for manual login),
 *       detect dashboard / error / OTP outcome.</li>
 *   <li>HEADLINE_APPEND — navigate to profile, open the resume-headline modal, toggle a
 *       trailing dot on the current text (net-zero across two runs via
 *       {@link #computeToggledHeadline}), save, wait for the shared success toast.</li>
 *   <li>DOWNLOAD_RESUME — click the download icon, capture the file via
 *       {@code Page.waitForResponse} on the resman-aggregator URL (browser-download fallback).</li>
 *   <li>UPLOAD_RESUME — set the file input to the renamed file, click Update resume.</li>
 *   <li>LOGOUT — direct-nav to {@code /nlogin/logout} (drawer fallback), wipe cookies /
 *       localStorage / sessionStorage, then close the browser context.</li>
 * </ol>
 * RENAME_RESUME is performed internally between DOWNLOAD_RESUME and UPLOAD_RESUME
 * and does not produce a separate {@link StepResult} entry. HEADLINE_STRIP was
 * removed because Naukri only shows a success toast on the FIRST profile save
 * per session — a second save silently succeeds but our toast-wait times out.</p>
 *
 * <p>On any post-login step failure a screenshot, serialized DOM (outerHTML), and the
 * current URL are saved under
 * {@code <cfg.downloadsDir>/<runTs>/screenshots/<email>__<step>.png},
 * {@code <cfg.downloadsDir>/<runTs>/dom-dumps/<email>__<step>.html}, and
 * {@code <cfg.downloadsDir>/<runTs>/dom-dumps/<email>__<step>.url.txt}.</p>
 *
 * <p>Stop / abort support: {@link #abort()} closes the running {@link BrowserContext}
 * immediately from any thread. Any blocked Playwright call will receive a
 * {@link PlaywrightException}. If the stopping flag is set in the orchestrator at that
 * point the exception is classified as {@code RUN_STOPPED}, not {@code FAILED}.</p>
 *
 * <p><strong>Password safety:</strong> the password parameter is never written
 * to any log, file, or exception message.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class NaukriAutomator implements Automator {

    private static final Logger log = LoggerFactory.getLogger(NaukriAutomator.class);

    /** Dashboard URL matcher — covers homepage, mnjuser, profile, dashboard variants. */
    private static final Pattern DASHBOARD_URL =
            Pattern.compile("(?i).*(mnjuser|homepage|profile|dashboard).*");

    private static final Random RNG = new Random();

    private final ResumeRenamer renamer;

    /**
     * Reference to the currently-running BrowserContext.
     * Set right after {@code browser.newContext()} inside the {@code run()} call,
     * cleared in a {@code finally} block before the context is closed.
     * Used by {@link #abort()} to force-close the context from any thread.
     */
    private final AtomicReference<BrowserContext> currentContextRef = new AtomicReference<>();

    public NaukriAutomator() {
        this.renamer = new ResumeRenamer();
    }

    public NaukriAutomator(ResumeRenamer renamer) {
        this.renamer = renamer;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Force-closes the currently-running BrowserContext, interrupting any in-flight
     * Playwright call blocked on the automator thread.
     *
     * <p>Idempotent — safe to call when no run is in progress or when called multiple times.</p>
     */
    @Override
    public void abort() {
        BrowserContext ctx = currentContextRef.getAndSet(null);
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignore) {
                // Closing an already-closed context throws; we don't care.
            }
        }
    }

    @Override
    public List<StepResult> run(
            String email,
            String name,
            String password,
            AutomationRunMode mode,
            AutomatorConfig cfg,
            PlaywrightSession session,
            ManualLoginGate gate,
            StepListener listener) {

        List<StepResult> results = new ArrayList<>();
        Page page = session.open(mode);

        // Register the newly-created BrowserContext so abort() can close it.
        BrowserContext ctx = page.context();
        currentContextRef.set(ctx);

        // Apply LOGIN-step timeouts now (postLoginActionMs is applied later after login succeeds).
        ctx.setDefaultNavigationTimeout(cfg.pageLoadMs());
        ctx.setDefaultTimeout(cfg.actionMs());

        try {
            Files.createDirectories(cfg.downloadsDir());
        } catch (IOException e) {
            log.warn("Could not create downloads dir: {}", e.getMessage());
        }

        // Collect dump paths produced during post-login failures.
        List<String> dumpPaths = new ArrayList<>();

        try {
            // ── Step 1: LOGIN ────────────────────────────────────────────────────
            listener.onStepStarted(AutomationStep.LOGIN);
            StepResult loginResult = doLogin(page, email, password, cfg, gate, listener);
            results.add(loginResult);
            listener.onStep(loginResult);
            if (!loginResult.ok()) {
                return results;
            }

            // Switch to post-login timeouts for all remaining steps (real Naukri is slow).
            ctx.setDefaultTimeout(cfg.postLoginActionMs());

            // Step order (revised 2026-07-17 per user observation):
            //   LOGIN → HEADLINE_APPEND → DOWNLOAD_RESUME → UPLOAD_RESUME → LOGOUT
            // Rationale: Naukri only shows the success toast on the FIRST profile
            // save per session; a second save (e.g. HEADLINE_STRIP) silently
            // succeeds but our wait-for-toast times out. So we do exactly one
            // headline edit (append) and rely on `computeToggledHeadline` to
            // alternate the trailing dot across runs — net-zero over 2 runs.
            // Resume refresh runs AFTER headline so the visible headline change
            // (which the user sees as "activity") happens first.

            // ── Step 2: HEADLINE_APPEND (single edit — no strip) ─────────────────
            listener.onStepStarted(AutomationStep.HEADLINE_APPEND);
            StepResult appendResult = doHeadlineAppend(page, email, cfg, listener, dumpPaths);
            results.add(appendResult);
            listener.onStep(appendResult);
            if (!appendResult.ok()) {
                return results;
            }

            // ── Step 3: DOWNLOAD_RESUME (or LOCATE_LOCAL when a folder is set) ───
            // When cfg.resumeFolderPath() is non-null we skip the browser
            // interaction entirely and glob the folder for <name>*.pdf. The
            // step name stays DOWNLOAD_RESUME so the UI + reports remain
            // consistent across modes.
            listener.onStepStarted(AutomationStep.DOWNLOAD_RESUME);
            Path[] resumeFile = new Path[1]; // mutable holder
            StepResult downloadResult;
            if (cfg.resumeFolderPath() != null) {
                downloadResult = doLocateLocalResume(name, cfg, resumeFile, listener);
            } else {
                downloadResult = doDownloadResume(page, email, cfg, resumeFile, listener, dumpPaths);
            }
            results.add(downloadResult);
            listener.onStep(downloadResult);
            if (!downloadResult.ok()) {
                return results;
            }

            // Rename resume internally (not a separate StepResult per spec).
            // Smart-date replace: <prefix> 15.07.2026 <suffix>.pdf → <prefix> <today>.<suffix>.pdf
            Path renamedFile = resumeFile[0];
            try {
                renamedFile = renamer.rename(resumeFile[0], LocalDate.now());
                log.debug("[{}] Resume renamed: {} -> {}", email,
                        resumeFile[0].getFileName(), renamedFile.getFileName());
            } catch (Exception e) {
                log.warn("[{}] Rename failed, using original: {}", email, e.getMessage());
                // Non-fatal: proceed with original file
            }

            // ── Step 4: UPLOAD_RESUME ────────────────────────────────────────────
            listener.onStepStarted(AutomationStep.UPLOAD_RESUME);
            StepResult uploadResult = doUploadResume(page, email, cfg, renamedFile, listener, dumpPaths);
            results.add(uploadResult);
            listener.onStep(uploadResult);
            if (!uploadResult.ok()) {
                return results;
            }

            // ── Step 5: LOGOUT ───────────────────────────────────────────────────
            listener.onStepStarted(AutomationStep.LOGOUT);
            StepResult logoutResult = doLogout(page, email, cfg, listener, dumpPaths);
            results.add(logoutResult);
            listener.onStep(logoutResult);

            return results;

        } finally {
            // Clear the context ref so abort() becomes a no-op after the run finishes.
            currentContextRef.set(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private step implementations
    // ─────────────────────────────────────────────────────────────────────────

    private StepResult doLogin(
            Page page, String email, String password,
            AutomatorConfig cfg, ManualLoginGate gate, StepListener listener) {

        long start = System.currentTimeMillis();

        // Single-attempt LOGIN (2026-07-17: retries disabled per user request).
        // Any PlaywrightException surfaces as FAILED with no fresh browser
        // session, and credential-rejection / OTP outcomes are still returned
        // unchanged by doLoginAttempt.
        try {
            return doLoginAttempt(page, email, password, cfg, gate, listener, start);
        } catch (PlaywrightException e) {
            takeScreenshot(page, email, cfg);
            return StepResult.failure(AutomationStep.LOGIN,
                    "FAILED: " + e.getMessage(), elapsed(start));
        }
    }

    /**
     * One attempt at the LOGIN step. Emits sub-step STEP_STARTED events via
     * {@code listener.onStepStarted} using dot-notation step names such as
     * {@code LOGIN.open-homepage}. Returns a StepResult (ok or failed) without
     * throwing for normal failures; may throw {@link PlaywrightException} for
     * unexpected Playwright errors (the caller's retry loop catches those).
     */
    private StepResult doLoginAttempt(
            Page page, String email, String password,
            AutomatorConfig cfg, ManualLoginGate gate, StepListener listener, long start) {

        // ── E. Arrive via homepage ───────────────────────────────────────────
        emitSubStep(listener, email, "LOGIN.launch-browser");
        emitSubStep(listener, email, "LOGIN.open-homepage");
        page.navigate(cfg.baseUrl());
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        sleepRandom(800, 1400); // let the homepage settle

        // Guard 1: already-logged-in short-circuit.
        // Real Naukri persists session cookies. If we hit naukri.com and the
        // browser still has a valid session, Naukri auto-redirects us to
        // /mnjuser/homepage. In that case LOGIN is a no-op - proceed to the
        // headline / resume flow immediately.
        if (DASHBOARD_URL.matcher(page.url()).matches()) {
            log.info("[{}] Already logged in via persisted session - skipping LOGIN. URL: {}",
                    email, page.url());
            return StepResult.success(AutomationStep.LOGIN, elapsed(start));
        }

        boolean arrivedViaHomepage = false;
        try {
            emitSubStep(listener, email, "LOGIN.click-login-link");
            Locator loginLink = page.locator("a[href*='/nlogin/login']").first();
            if (loginLink.count() > 0) {
                loginLink.click();
                page.waitForURL("**/nlogin/login**",
                        new Page.WaitForURLOptions().setTimeout(cfg.pageLoadMs()));
                arrivedViaHomepage = true;
            } else {
                log.debug("[{}] Login link not found on homepage — using direct navigation", email);
            }
        } catch (Exception homeEx) {
            log.debug("[{}] Homepage-first navigation failed ({}); falling back to direct /nlogin/login",
                    email, homeEx.getMessage());
        }

        if (!arrivedViaHomepage) {
            page.navigate(cfg.baseUrl() + "/nlogin/login");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Guard 2: /nlogin/login-redirected-to-dashboard short-circuit.
            // If session is still valid, Naukri may reject the direct navigation
            // to the login form and bounce us to /mnjuser/homepage instead.
            if (DASHBOARD_URL.matcher(page.url()).matches()) {
                log.info("[{}] /nlogin/login redirected to {} - session still valid, skipping LOGIN.",
                        email, page.url());
                return StepResult.success(AutomationStep.LOGIN, elapsed(start));
            }
        }

        // ── D. Human-like typing (per user 2026-07-17: this IS working) ──────
        // pressSequentially reliably drives Naukri's React onChange because
        // Playwright's per-key events, though synthetic, fire in a way
        // Naukri's stack accepts. User confirmed via UI observation that
        // the email types character-by-character and login succeeds. Do NOT
        // change this to page.fill() - a prior attempt did so based on a
        // misread of a failure screenshot and broke a working flow.
        emitSubStep(listener, email, "LOGIN.wait-for-form");
        page.locator(NaukriSelectors.LOGIN_EMAIL).click();
        sleepRandom(200, 400);

        emitSubStep(listener, email, "LOGIN.type-email");
        page.locator(NaukriSelectors.LOGIN_EMAIL)
                .pressSequentially(email,
                        new Locator.PressSequentiallyOptions().setDelay(randomDelayMs(70, 140)));

        sleepRandom(300, 600);

        page.locator(NaukriSelectors.LOGIN_PASSWORD).click();
        sleepRandom(200, 400);

        emitSubStep(listener, email, "LOGIN.type-password");
        page.locator(NaukriSelectors.LOGIN_PASSWORD)
                .pressSequentially(password,
                        new Locator.PressSequentiallyOptions().setDelay(randomDelayMs(70, 140)));

        if (cfg.manualLogin()) {
            emitSubStep(listener, email, "LOGIN.wait-for-manual");
            boolean reached = gate.waitForResume(
                    email,
                    cfg.manualLoginTimeout(),
                    () -> DASHBOARD_URL.matcher(page.url()).matches()
            );
            if (!reached) {
                takeScreenshot(page, email, cfg);
                return StepResult.failure(AutomationStep.LOGIN,
                        "MANUAL_LOGIN_TIMEOUT", elapsed(start));
            }
        } else {
            emitSubStep(listener, email, "LOGIN.submit");
            page.click(NaukriSelectors.LOGIN_SUBMIT);

            // Wait for URL to change AWAY from /nlogin/login. If it changes
            // to /mnjuser/* or /otp, we know the submit landed. If it stays
            // on /nlogin/login for the full timeout, credentials were
            // rejected server-side (or the button click was a no-op).
            emitSubStep(listener, email, "LOGIN.wait-for-redirect");
            try {
                page.waitForURL(u -> !u.contains("/nlogin/login"),
                        new Page.WaitForURLOptions().setTimeout(cfg.actionMs()));
            } catch (PlaywrightException urlTimeout) {
                // Stayed on /nlogin/login - the outcome-detection code below
                // will report AUTH_FAILED (or the error-banner branch).
                log.debug("[{}] URL did not change from /nlogin/login within {} ms",
                        email, cfg.actionMs());
            }
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        }

        // ── Detect post-login state ───────────────────────────────────────────
        emitSubStep(listener, email, "LOGIN.detect-outcome");
        String url = page.url();

        if (DASHBOARD_URL.matcher(url).matches()) {
            return StepResult.success(AutomationStep.LOGIN, elapsed(start));
        }

        if (url.contains("/otp")) {
            takeScreenshot(page, email, cfg);
            return StepResult.failure(AutomationStep.LOGIN, "REQUIRES_MANUAL", elapsed(start));
        }

        try {
            if (page.locator(NaukriSelectors.LOGIN_ERROR).isVisible()) {
                takeScreenshot(page, email, cfg);
                return StepResult.failure(AutomationStep.LOGIN, "AUTH_FAILED", elapsed(start));
            }
        } catch (Exception bannerEx) {
            log.debug("[{}] Could not check error banner: {}", email, bannerEx.getMessage());
        }

        takeScreenshot(page, email, cfg);
        return StepResult.failure(AutomationStep.LOGIN,
                "FAILED: unexpected URL after login: " + url, elapsed(start));
    }

    /**
     * Open the "Resume headline" edit modal, apply the given text transform to
     * the current textarea value, click Save, wait for the shared success toast,
     * then wait for the toast to disappear so the next save's toast-wait
     * doesn't see a stale one. Handles the maxlength ceiling by toggling a
     * trailing space instead of a dot when appending would overflow.
     */
    private StepResult doHeadlineEdit(
            Page page, String email, AutomatorConfig cfg,
            AutomationStep step, boolean append,
            StepListener listener, List<String> dumpPaths) {
        long start = System.currentTimeMillis();
        try {
            emitSubStep(listener, email, step + ".open-profile");
            if (!page.url().contains("/mnjuser/profile")) {
                page.navigate(cfg.baseUrl() + "/mnjuser/profile");
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }

            // Naukri's profile page is a React SPA — the resume-headline card
            // ships inside a lazy-loaded chunk (#lazyResumeHead). Wait for it
            // to become visible, then give hydration a moment to settle
            // before we start hunting for the edit icon inside it.
            emitSubStep(listener, email, step + ".wait-for-headline-card");
            page.waitForSelector(NaukriSelectors.HEADLINE_LAZY_ANCHOR,
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(cfg.postLoginActionMs()));
            sleepRandom(1500, 2500);

            // Tear down any Naukri promo/upsell overlay that may have popped
            // over the profile page after a previous save. Left in place, its
            // .ltCont/.ltLayer intercepts pointer events and the edit-icon
            // click below spins for the full 25s timeout. Safe to run
            // pre-emptively — if no overlay is present this is a no-op.
            dismissBlockingOverlays(page);
            sleepRandom(400, 800);

            emitSubStep(listener, email, step + ".click-edit-icon");
            // Real Naukri: click .widgetHead .edit.icon inside the widget whose
            // .widgetTitle text is "Resume headline". We scope by that title
            // to avoid catching other widget edit icons on the page.
            Locator widgetTitle = page.locator(NaukriSelectors.HEADLINE_WIDGET_TITLE)
                    .filter(new Locator.FilterOptions()
                            .setHasText(NaukriSelectors.HEADLINE_WIDGET_TITLE_TEXT))
                    .first();
            // Walk up to the surrounding card, then find the edit icon inside it.
            Locator editIcon = widgetTitle.locator("xpath=ancestor::div[contains(@class,'card')][1]")
                    .locator(NaukriSelectors.HEADLINE_EDIT_ICON).first();
            editIcon.click();

            emitSubStep(listener, email, step + ".wait-for-textarea");
            page.waitForSelector(NaukriSelectors.HEADLINE_TEXTAREA,
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(cfg.postLoginActionMs()));

            emitSubStep(listener, email, step + ".compute-next-value");
            String current = page.inputValue(NaukriSelectors.HEADLINE_TEXTAREA);
            int maxLen = readMaxLength(page, NaukriSelectors.HEADLINE_TEXTAREA, 250);
            String next = computeToggledHeadline(current, append, maxLen);
            if (next.equals(current)) {
                // No safe change possible (e.g., already at ceiling and no dot to strip).
                // Cancel the modal cleanly so the profile page returns to its idle state.
                page.locator(NaukriSelectors.HEADLINE_CANCEL).click();
                return StepResult.failure(step,
                        "no-op: headline already at ceiling with no trailing dot to strip",
                        elapsed(start));
            }

            emitSubStep(listener, email, step + ".fill");
            page.fill(NaukriSelectors.HEADLINE_TEXTAREA, next);
            sleepRandom(300, 600);

            emitSubStep(listener, email, step + ".save");
            page.click(NaukriSelectors.HEADLINE_SAVE);

            emitSubStep(listener, email, step + ".wait-for-toast");
            page.waitForSelector(NaukriSelectors.SUCCESS_TOAST,
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(cfg.postLoginActionMs()));

            // Toast appeared = server-confirmed save. Real Naukri leaves the
            // .success-message-container in the DOM forever (fades visually
            // via inner opacity, but never satisfies Playwright's HIDDEN
            // state). Actively JS-remove it so it doesn't linger over the
            // resume card and intercept the download-icon click below.
            emitSubStep(listener, email, step + ".dismiss-toast");
            try {
                page.evaluate("() => document.querySelectorAll('" +
                        NaukriSelectors.SUCCESS_TOAST +
                        "').forEach(el => { try { el.remove(); } catch (e) {} })");
            } catch (Exception ignore) {
                // Page could have navigated mid-cleanup; not fatal.
            }
            sleepRandom(1500, 2500);

            return StepResult.success(step, elapsed(start));
        } catch (Exception e) {
            takeScreenshot(page, email, cfg);
            dumpDom(page, email, step, cfg, dumpPaths);
            return StepResult.failure(step, e.getMessage(), elapsed(start));
        }
    }

    private StepResult doHeadlineAppend(Page page, String email, AutomatorConfig cfg,
            StepListener listener, List<String> dumpPaths) {
        return doHeadlineEdit(page, email, cfg,
                AutomationStep.HEADLINE_APPEND, true, listener, dumpPaths);
    }

    private StepResult doHeadlineStrip(Page page, String email, AutomatorConfig cfg,
            StepListener listener, List<String> dumpPaths) {
        return doHeadlineEdit(page, email, cfg,
                AutomationStep.HEADLINE_STRIP, false, listener, dumpPaths);
    }

    /** Read a numeric attribute (like maxlength) from an input; fall back to {@code defaultValue}. */
    private static int readMaxLength(Page page, String selector, int defaultValue) {
        try {
            String v = page.locator(selector).first().getAttribute("maxlength");
            if (v != null && !v.isBlank()) return Integer.parseInt(v.trim());
        } catch (Exception ignore) {}
        return defaultValue;
    }

    /**
     * Compute the toggled headline value.
     *
     * <p>If {@code append} is true and the current text does NOT end with a dot,
     * append a single {@code .}, provided that keeps us within {@code maxLen}.
     * If appending would overflow, toggle a trailing space instead.
     * If {@code append} is false OR the current text already ends with a dot,
     * strip the trailing dot / space.</p>
     */
    static String computeToggledHeadline(String current, boolean append, int maxLen) {
        if (current == null) current = "";
        boolean endsWithDot = current.matches("(?s).*\\.\\s*$");
        if (endsWithDot) {
            return current.replaceAll("\\.\\s*$", "");
        }
        if (!append) {
            // Strip step called but no dot to strip — return unchanged so caller
            // can decide (the flow expects a real change; this is a no-op case).
            return current;
        }
        String trimmed = current.replaceAll("\\s*$", "");
        if (trimmed.length() + 1 <= maxLen) {
            return trimmed + ".";
        }
        // At the ceiling — toggle trailing space instead so the save still fires.
        if (current.matches("(?s).*\\s$")) {
            return current.replaceAll("\\s+$", "");
        }
        if (current.length() + 1 <= maxLen) {
            return current + " ";
        }
        return current; // truly no room — caller handles as no-op
    }

    private StepResult doDownloadResume(
            Page page, String email, AutomatorConfig cfg, Path[] out,
            StepListener listener, List<String> dumpPaths) {

        long start = System.currentTimeMillis();
        try {
            // Ensure we're on the profile page. After LOGIN's Guard-1
            // short-circuit we may still be on /mnjuser/homepage where the
            // download icon doesn't exist.
            emitSubStep(listener, email, "DOWNLOAD_RESUME.ensure-on-profile");
            if (!page.url().contains("/mnjuser/profile")) {
                page.navigate(cfg.baseUrl() + "/mnjuser/profile");
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }

            emitSubStep(listener, email, "DOWNLOAD_RESUME.locate-icon");
            // Real Naukri: clicking [data-title="download-resume"] fires an XHR
            // to /cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/<hash>/resume
            // AND the browser saves the file. We capture BOTH — waitForResponse
            // gives us the URL + bytes for the rename+re-upload path, waitForDownload
            // captures the file the browser stores locally.

            // Race: whichever appears first tells us what happened. Real Naukri
            // fires waitForResponse; the mock fires waitForDownload via a hidden
            // <a download>. We try both concurrently.
            emitSubStep(listener, email, "DOWNLOAD_RESUME.click-and-capture");

            String serverFilename;
            byte[] resumeBytes;
            try {
                // Preferred path — wait for the resman-aggregator response.
                // Playwright's String overload uses glob matching, not regex, so we
                // pass a compiled Pattern via the Predicate<Response> overload.
                Pattern urlPattern = Pattern.compile(NaukriSelectors.RESUME_DOWNLOAD_URL_REGEX);
                Response response = page.waitForResponse(
                        r -> urlPattern.matcher(r.url()).matches(),
                        new Page.WaitForResponseOptions().setTimeout(cfg.postLoginActionMs()),
                        () -> page.click(NaukriSelectors.RESUME_DOWNLOAD)
                );
                resumeBytes = response.body();
                serverFilename = extractFilenameFromContentDisposition(
                        response.headers().getOrDefault("content-disposition", ""),
                        "resume.pdf");
                log.debug("[{}] Sniffed download URL: {} ({} bytes)", email, response.url(), resumeBytes.length);
            } catch (PlaywrightException sniffMiss) {
                // Fallback path — response sniff timed out; try the browser-download event.
                log.debug("[{}] Response sniff did not match, falling back to waitForDownload: {}",
                        email, sniffMiss.getMessage());
                Download download = page.waitForDownload(() ->
                        page.click(NaukriSelectors.RESUME_DOWNLOAD));
                serverFilename = download.suggestedFilename();
                if (serverFilename == null || serverFilename.isBlank()) serverFilename = "resume.pdf";
                Path tmp = cfg.downloadsDir().resolve("tmp-" + System.currentTimeMillis() + "-" + serverFilename);
                download.saveAs(tmp);
                resumeBytes = Files.readAllBytes(tmp);
                Files.deleteIfExists(tmp);
            }

            emitSubStep(listener, email, "DOWNLOAD_RESUME.save-to-disk");
            String safeEmail = email.replaceAll("[^a-zA-Z0-9._-]", "_");
            String uniqueName = safeEmail + "-" + System.currentTimeMillis() + "-" + serverFilename;
            Path dest = cfg.downloadsDir().resolve(uniqueName);
            Files.write(dest, resumeBytes);
            out[0] = dest;

            log.debug("[{}] Resume downloaded to {}", email, dest);
            return StepResult.success(AutomationStep.DOWNLOAD_RESUME, elapsed(start));
        } catch (Exception e) {
            takeScreenshot(page, email, cfg);
            dumpDom(page, email, AutomationStep.DOWNLOAD_RESUME, cfg, dumpPaths);
            return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                    e.getMessage(), elapsed(start));
        }
    }

    /**
     * Alternative resume-locate flow: instead of downloading from Naukri,
     * glob {@code cfg.resumeFolderPath()} for {@code <name>*.{pdf,doc,docx,rtf}}
     * and return the single match. Errors when zero or more than one file
     * matches — the caller (operator) is expected to keep exactly one resume
     * per name in the folder.
     */
    private StepResult doLocateLocalResume(
            String name, AutomatorConfig cfg, Path[] out, StepListener listener) {

        long start = System.currentTimeMillis();
        try {
            emitSubStep(listener, name == null ? "" : name, "DOWNLOAD_RESUME.locate-local-file");

            Path folder = cfg.resumeFolderPath();
            if (folder == null) {
                return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                        "resumeFolderPath not configured", elapsed(start));
            }
            if (name == null || name.isBlank()) {
                return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                        "account name is required to locate a local resume", elapsed(start));
            }
            if (!Files.isDirectory(folder)) {
                return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                        "resume folder does not exist: " + folder, elapsed(start));
            }

            // Case-insensitive name prefix match with any Naukri-supported extension.
            String prefix = name.trim().toLowerCase(java.util.Locale.ROOT);
            List<Path> matches;
            try (java.util.stream.Stream<Path> stream = Files.list(folder)) {
                matches = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String fn = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                            if (!fn.startsWith(prefix)) return false;
                            return fn.endsWith(".pdf") || fn.endsWith(".doc")
                                    || fn.endsWith(".docx") || fn.endsWith(".rtf");
                        })
                        .toList();
            }

            if (matches.isEmpty()) {
                return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                        "No resume file matching '" + name + "*.{pdf,doc,docx,rtf}' found in " + folder,
                        elapsed(start));
            }
            if (matches.size() > 1) {
                String list = matches.stream()
                        .map(p -> p.getFileName().toString())
                        .collect(java.util.stream.Collectors.joining(", "));
                return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                        "Multiple resume files match '" + name + "*' in " + folder +
                        " — keep only one per name. Matches: " + list,
                        elapsed(start));
            }

            out[0] = matches.get(0);
            log.debug("[{}] Located local resume: {}", name, out[0]);
            return StepResult.success(AutomationStep.DOWNLOAD_RESUME, elapsed(start));
        } catch (Exception e) {
            return StepResult.failure(AutomationStep.DOWNLOAD_RESUME,
                    e.getMessage(), elapsed(start));
        }
    }

    /**
     * Extract a filename from a Content-Disposition header value like
     * {@code attachment; filename="Arpitha S 15.07.2026 yahoo.pdf"}. Returns
     * {@code fallback} if none present.
     */
    static String extractFilenameFromContentDisposition(String cd, String fallback) {
        if (cd == null || cd.isBlank()) return fallback;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("filename\\*?=(?:UTF-8''|\")?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(cd);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty()) return name;
        }
        return fallback;
    }

    private StepResult doUploadResume(
            Page page, String email, AutomatorConfig cfg, Path file,
            StepListener listener, List<String> dumpPaths) {

        long start = System.currentTimeMillis();
        try {
            // Belt-and-braces: ensure we're still on the profile page. The
            // download step may have navigated away in some browser builds.
            emitSubStep(listener, email, "UPLOAD_RESUME.ensure-on-profile");
            if (!page.url().contains("/mnjuser/profile")) {
                page.navigate(cfg.baseUrl() + "/mnjuser/profile");
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }

            emitSubStep(listener, email, "UPLOAD_RESUME.attach-file");
            // Real Naukri: input#attachCV is always in the DOM. setInputFiles
            // triggers the change event Naukri listens for.
            page.setInputFiles(NaukriSelectors.RESUME_UPLOAD_INP, file);

            emitSubStep(listener, email, "UPLOAD_RESUME.click-update-btn");
            // input.dummyUpload is the visible "Update resume" pill. Some Naukri
            // builds auto-submit on file selection; a click is a no-op then.
            try {
                page.click(NaukriSelectors.RESUME_UPDATE_BTN,
                        new Page.ClickOptions().setTimeout(3000));
            } catch (PlaywrightException clickMiss) {
                log.debug("[{}] Update-resume button click skipped/failed ({}); assuming auto-submit",
                        email, clickMiss.getMessage());
            }

            emitSubStep(listener, email, "UPLOAD_RESUME.wait-for-toast");
            // Naukri suppresses the shared .success-message-container toast on
            // the 2nd save per session (HEADLINE_APPEND consumed it earlier).
            // Instead it injects a card-scoped success box inside the resume
            // card: #attachCVMsgBox .msgBox.success. Wait for THAT — if
            // absent within postLoginActionMs, fall through non-fatally
            // (rare Naukri builds may skip it entirely; the "Uploaded on"
            // date update is the ultimate ground truth).
            try {
                page.waitForSelector(NaukriSelectors.RESUME_UPLOAD_MSG_OK,
                        new Page.WaitForSelectorOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(cfg.postLoginActionMs()));
                log.debug("[{}] Resume upload success message detected", email);
            } catch (PlaywrightException noMsg) {
                log.info("[{}] Resume upload success message not detected within {} ms " +
                        "— proceeding (upload may still have succeeded; the Uploaded-on " +
                        "date on the resume card is the ground-truth signal)",
                        email, cfg.postLoginActionMs());
            }

            // Give the server time to process the upload regardless of message state.
            sleepRandom(3500, 4500);

            return StepResult.success(AutomationStep.UPLOAD_RESUME, elapsed(start));
        } catch (Exception e) {
            takeScreenshot(page, email, cfg);
            dumpDom(page, email, AutomationStep.UPLOAD_RESUME, cfg, dumpPaths);
            return StepResult.failure(AutomationStep.UPLOAD_RESUME,
                    e.getMessage(), elapsed(start));
        }
    }

    /**
     * True when the current URL indicates we've successfully signed out.
     * Real Naukri may land on either {@code /nlogin/login} (auto-redirect) or
     * {@code /nlogin/logout} (self-standing "you've been signed out" page);
     * both are equivalent-successful states. Anything else means we're still
     * on the app.
     */
    private static boolean isLoggedOutUrl(String url) {
        if (url == null) return false;
        return url.contains("/nlogin/login") || url.contains("/nlogin/logout");
    }

    private StepResult doLogout(Page page, String email, AutomatorConfig cfg,
            StepListener listener, List<String> dumpPaths) {
        long start = System.currentTimeMillis();
        try {
            // ── Preferred path: direct navigation to /nlogin/logout ─────────
            // Works against real Naukri and the mock. Bypasses the drawer entirely,
            // which is much more reliable than hunting for the top-nav avatar
            // and drawer trigger across Naukri's shifting minified class names.
            emitSubStep(listener, email, "LOGOUT.direct-navigate");
            try {
                page.navigate(cfg.baseUrl() + NaukriSelectors.LOGOUT_URL_PATH);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            } catch (PlaywrightException navMiss) {
                log.debug("[{}] Direct logout navigation failed ({}); falling back to drawer",
                        email, navMiss.getMessage());
            }

            // If we didn't land on a logged-out URL (either /nlogin/login or
            // /nlogin/logout is fine), fall back to the drawer path.
            if (!isLoggedOutUrl(page.url())) {
                emitSubStep(listener, email, "LOGOUT.open-drawer");
                boolean drawerOpened = tryOpenLogoutDrawer(page, cfg);
                if (drawerOpened) {
                    emitSubStep(listener, email, "LOGOUT.click-drawer-logout");
                    Locator logoutLink = page.locator(NaukriSelectors.LOGOUT_LINK_DRAWER).first();
                    if (logoutLink.count() == 0) {
                        logoutLink = page.locator(NaukriSelectors.LOGOUT_LINK_TITLE).first();
                    }
                    if (logoutLink.count() > 0) {
                        logoutLink.click();
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    }
                } else {
                    // Last-ditch: try the mock's plain anchor.
                    Locator legacy = page.locator(NaukriSelectors.LOGOUT_LINK).first();
                    if (legacy.count() > 0) {
                        legacy.click();
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    }
                }
            }

            emitSubStep(listener, email, "LOGOUT.wait-for-login-page");
            if (!isLoggedOutUrl(page.url())) {
                takeScreenshot(page, email, cfg);
                dumpDom(page, email, AutomationStep.LOGOUT, cfg, dumpPaths);
                return StepResult.failure(AutomationStep.LOGOUT,
                        "Expected to land on /nlogin/login or /nlogin/logout, got: "
                                + page.url(), elapsed(start));
            }

            // ── Complete session teardown ─────────────────────────────────────
            emitSubStep(listener, email, "LOGOUT.session-teardown");
            try {
                page.context().clearCookies();
                page.evaluate("() => { try { localStorage.clear(); sessionStorage.clear(); } catch(e) {} }");
                page.context().clearPermissions();
            } catch (Exception ignore) {
                log.debug("[{}] Session teardown clear failed (ignored): {}", email, ignore.getMessage());
            }

            // ── Close the browser context ─────────────────────────────────────
            // The orchestrator's PlaywrightSession will close the context after
            // this method returns, but we close it explicitly here so the
            // browser window is gone by the time we emit the SUCCESS event —
            // matches the user's expectation of "logout then close browser".
            emitSubStep(listener, email, "LOGOUT.close-browser");
            try {
                BrowserContext ctx = currentContextRef.getAndSet(null);
                if (ctx != null) ctx.close();
            } catch (Exception ignore) {
                log.debug("[{}] Browser close failed (ignored): {}", email, ignore.getMessage());
            }

            return StepResult.success(AutomationStep.LOGOUT, elapsed(start));
        } catch (Exception e) {
            if (isLoggedOutUrl(page.url())) {
                // We got logged out despite the exception; still close browser.
                try {
                    BrowserContext ctx = currentContextRef.getAndSet(null);
                    if (ctx != null) ctx.close();
                } catch (Exception ignore) {}
                return StepResult.success(AutomationStep.LOGOUT, elapsed(start));
            }
            takeScreenshot(page, email, cfg);
            dumpDom(page, email, AutomationStep.LOGOUT, cfg, dumpPaths);
            return StepResult.failure(AutomationStep.LOGOUT, e.getMessage(), elapsed(start));
        }
    }

    /**
     * Click any known drawer-trigger candidate until {@code .drawer-wrapper}
     * appears. Returns true if the drawer opened, false otherwise.
     */
    private boolean tryOpenLogoutDrawer(Page page, AutomatorConfig cfg) {
        for (String trigger : NaukriSelectors.LOGOUT_DRAWER_TRIGGERS) {
            try {
                Locator loc = page.locator(trigger).first();
                if (loc.count() == 0) continue;
                loc.click(new Locator.ClickOptions().setTimeout(2000));
                page.waitForSelector(NaukriSelectors.LOGOUT_DRAWER,
                        new Page.WaitForSelectorOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(3000));
                return true;
            } catch (Exception ignore) {
                // try next candidate
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Removes Naukri's post-save "learning tips" / Pro-upsell overlay if present.
     * Only the pointer-intercepting layer ({@code .ltLayer.open}) and the
     * promotional content ({@code .pro-card}) are removed — the surrounding
     * {@code .ltCont} container is left in place because Naukri mounts other
     * modals (including the headline edit modal we're about to open) into it.
     *
     * <p>Idempotent — if no overlay is present, {@code querySelectorAll} yields
     * an empty NodeList and the forEach is a no-op. No Escape keypress: Naukri
     * has global Escape handlers that can close a modal we've just opened.</p>
     */
    private void dismissBlockingOverlays(Page page) {
        try {
            page.evaluate(
                "() => { document.querySelectorAll('" +
                NaukriSelectors.OVERLAY_LTLAYER + ", " +
                NaukriSelectors.OVERLAY_PROCARD + ", " +
                NaukriSelectors.SUCCESS_TOAST +
                "').forEach(el => { try { el.remove(); } catch (e) {} }); }"
            );
        } catch (Exception ignore) {
            // Page could have navigated mid-cleanup; not fatal.
        }
    }

    /**
     * Emits a STEP_STARTED event with a rich sub-step name (e.g. "LOGIN.type-email").
     * Uses {@link StepListener#onSubStepStarted} which defaults to no-op for all existing
     * callers, preserving full backward compatibility.
     */
    private void emitSubStep(StepListener listener, String email, String subStep) {
        listener.onSubStepStarted(subStep);
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * Sleeps for a random duration within [minMs, maxMs].
     * Restores interrupt flag on InterruptedException rather than swallowing it.
     */
    private void sleepRandom(int minMs, int maxMs) {
        int ms = minMs + RNG.nextInt(maxMs - minMs + 1);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns a random double delay in [minMs, maxMs] for use as the per-keystroke delay
     * in {@link Locator.PressSequentiallyOptions#setDelay(double)}.
     */
    private double randomDelayMs(int minMs, int maxMs) {
        return minMs + RNG.nextInt(maxMs - minMs + 1);
    }

    private void takeScreenshot(Page page, String email, AutomatorConfig cfg) {
        try {
            Path screenshotsDir = cfg.downloadsDir().resolve("screenshots");
            Files.createDirectories(screenshotsDir);
            String safeEmail = email.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dest = screenshotsDir.resolve(safeEmail + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(dest));
            log.debug("[{}] Screenshot saved to {}", email, dest);
        } catch (Exception ex) {
            log.warn("[{}] Could not save screenshot: {}", email, ex.getMessage());
        }
    }

    /**
     * Saves a per-step failure DOM dump:
     * <ul>
     *   <li>{@code <downloadsDir>/screenshots/<safeEmail>__<step>.png}</li>
     *   <li>{@code <downloadsDir>/dom-dumps/<safeEmail>__<step>.html} — full hydrated outerHTML</li>
     *   <li>{@code <downloadsDir>/dom-dumps/<safeEmail>__<step>.url.txt} — current page URL</li>
     * </ul>
     * Adds the saved paths to {@code dumpPaths}. All errors are caught so a dump failure
     * never masks the original exception.
     */
    private void dumpDom(Page page, String email, AutomationStep step,
                         AutomatorConfig cfg, List<String> dumpPaths) {
        try {
            String safeEmail = email.replaceAll("[^a-zA-Z0-9._-]", "_");
            String stepName  = step.name();
            String fileBase  = safeEmail + "__" + stepName;

            Path screenshotsDir = cfg.downloadsDir().resolve("screenshots");
            Path domDumpsDir    = cfg.downloadsDir().resolve("dom-dumps");
            Files.createDirectories(screenshotsDir);
            Files.createDirectories(domDumpsDir);

            // ── screenshot ──────────────────────────────────────────────────
            Path ssPath = screenshotsDir.resolve(fileBase + ".png");
            try {
                page.screenshot(new Page.ScreenshotOptions().setPath(ssPath));
                dumpPaths.add(ssPath.toString());
                log.debug("[{}] Dump screenshot -> {}", email, ssPath);
            } catch (Exception ex) {
                log.warn("[{}] Could not save dump screenshot for {}: {}", email, stepName, ex.getMessage());
            }

            // ── DOM HTML ────────────────────────────────────────────────────
            Path htmlPath = domDumpsDir.resolve(fileBase + ".html");
            try {
                String html = page.content();
                Files.writeString(htmlPath, html);
                dumpPaths.add(htmlPath.toString());
                log.debug("[{}] Dump DOM -> {}", email, htmlPath);
            } catch (Exception ex) {
                log.warn("[{}] Could not save dump HTML for {}: {}", email, stepName, ex.getMessage());
            }

            // ── current URL ─────────────────────────────────────────────────
            Path urlPath = domDumpsDir.resolve(fileBase + ".url.txt");
            try {
                Files.writeString(urlPath, page.url());
                dumpPaths.add(urlPath.toString());
                log.debug("[{}] Dump URL -> {}", email, urlPath);
            } catch (Exception ex) {
                log.warn("[{}] Could not save dump URL for {}: {}", email, stepName, ex.getMessage());
            }

        } catch (Exception outer) {
            // A completely unexpected error in the dump machinery — log and carry on.
            log.warn("[{}] dumpDom failed unexpectedly for {}: {}", email, step, outer.getMessage());
        }
    }
}
