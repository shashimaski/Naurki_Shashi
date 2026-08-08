package com.adi.naukri.automation;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Strategy interface that blocks the automator until the user completes a manual login
 * or until the timeout elapses.
 *
 * <p>The production implementation polls the Playwright page URL.
 * Test implementations can return immediately or simulate a delay.</p>
 *
 * @see NaukriAutomator
 *
 * Author: Adikarthik Gupta C B
 */
@FunctionalInterface
public interface ManualLoginGate {

    /**
     * Waits for the user to complete login manually.
     *
     * @param email            the account being processed (for display / logging).
     * @param timeout          maximum time to wait.
     * @param dashboardReached supplier that returns {@code true} when the browser
     *                         has reached the dashboard URL pattern.
     * @return {@code true} if the dashboard was reached within the timeout,
     *         {@code false} if the timeout elapsed without success.
     */
    boolean waitForResume(String email, Duration timeout, Supplier<Boolean> dashboardReached);
}
