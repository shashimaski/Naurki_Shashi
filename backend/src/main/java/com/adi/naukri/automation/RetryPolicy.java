package com.adi.naukri.automation;

import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Retry policy for a single account's automation run.
 *
 * <p><strong>2026-07-17 change (user request):</strong> retries are disabled.
 * Every account gets exactly one attempt regardless of headed / headless mode.
 * If the attempt fails, the account is reported failed - no fresh browser
 * session is spawned. Rationale: retries on real Naukri risk triggering
 * abuse-detection flags on the account, and the user prefers to see one
 * clean failure over multiple redundant attempts.</p>
 *
 * <p>To re-enable retries in the future, restore the multi-attempt list here.
 * The rest of the orchestrator already handles arbitrary attempt counts.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class RetryPolicy {
    public List<RetryAttempt> attemptsFor(AutomationRunMode initial, long baseMs) {
        return List.of(new RetryAttempt(1, initial, baseMs));
    }
}
