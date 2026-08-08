package com.adi.naukri.automation;

import java.util.List;

/**
 * Interface for the per-account automation runner.
 *
 * <p>Extracted from {@link NaukriAutomator} so that the orchestrator (M6) and
 * REST-layer tests can inject a fake implementation without launching a real
 * browser.</p>
 *
 * Author: Adikarthik Gupta C B
 */
public interface Automator {

    /**
     * Runs the full Naukri automation flow for a single account.
     *
     * @param email    account email (never logged with the password).
     * @param name     account holder's display name — used to locate the local
     *                 resume file when {@code cfg.resumeFolderPath()} is set.
     *                 Ignored otherwise. Never {@code null} in production;
     *                 tests may pass {@code null} for the download-from-Naukri
     *                 flow.
     * @param password account password — memory-only, never persisted or logged.
     * @param mode     browser run mode (headless / headed).
     * @param cfg      run-level configuration.
     * @param session  open Playwright session to use.
     * @param gate     callback that blocks until manual login is complete
     *                 (only invoked when {@code cfg.manualLogin()} is true).
     * @param listener receives step-level events as they happen.
     * @return ordered list of {@link StepResult}; list is truncated at the
     *         first failure.
     */
    List<StepResult> run(
            String            email,
            String            name,
            String            password,
            AutomationRunMode mode,
            AutomatorConfig   cfg,
            PlaywrightSession session,
            ManualLoginGate   gate,
            StepListener      listener
    );

    /**
     * Force-closes the currently running {@link com.microsoft.playwright.BrowserContext},
     * interrupting any in-flight Playwright call.
     *
     * <p>Must be idempotent — safe to call when no run is in progress, and
     * safe to call multiple times. Implementations that do not manage a real
     * browser may leave this as the default no-op.</p>
     */
    default void abort() {
        // no-op default — fake/test implementations do not need a real browser to close
    }
}
