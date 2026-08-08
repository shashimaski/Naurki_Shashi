package com.adi.naukri.automation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Retries were disabled 2026-07-17 per user request: every run gets exactly
 * one attempt regardless of headed / headless mode. Tests below lock that
 * contract in place - if retries are ever re-enabled, these will fail loudly
 * to force a review.
 *
 * Author: Adikarthik Gupta C B
 */
class RetryPolicyTest {
    final RetryPolicy p = new RetryPolicy();

    @Test
    void headed_run_gets_single_attempt() {
        List<RetryAttempt> a = p.attemptsFor(AutomationRunMode.HEADED, 30_000);
        assertEquals(1, a.size(), "retries are disabled; expected exactly 1 attempt");
        assertEquals(AutomationRunMode.HEADED, a.get(0).mode());
        assertEquals(30_000, a.get(0).timeoutMs());
    }

    @Test
    void headless_run_gets_single_attempt() {
        List<RetryAttempt> a = p.attemptsFor(AutomationRunMode.HEADLESS, 30_000);
        assertEquals(1, a.size(), "retries are disabled; expected exactly 1 attempt");
        assertEquals(AutomationRunMode.HEADLESS, a.get(0).mode());
        assertEquals(30_000, a.get(0).timeoutMs());
    }
}
