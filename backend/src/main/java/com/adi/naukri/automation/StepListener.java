package com.adi.naukri.automation;

/**
 * Callback interface for monitoring NaukriAutomator step progress.
 *
 * <p>Implementations must be thread-safe (the automator runs on a background thread).</p>
 *
 * Author: Adikarthik Gupta C B
 */
public interface StepListener {

    /**
     * Called just before a top-level step begins executing.
     *
     * @param step the step that is about to execute.
     */
    void onStepStarted(AutomationStep step);

    /**
     * Called just before a sub-phase within a step begins.
     *
     * <p>The {@code subStep} string uses dot-notation, e.g. {@code "LOGIN.type-email"}.
     * Existing implementations that do not override this method receive a no-op default,
     * preserving full backward compatibility.</p>
     *
     * @param subStep dot-notation label for the sub-phase (e.g. {@code "LOGIN.type-email"}).
     */
    default void onSubStepStarted(String subStep) {
        // no-op by default — existing anonymous listeners are unaffected
    }

    /**
     * Called after each step completes (whether ok or failed).
     *
     * @param result the result of the completed step.
     */
    void onStep(StepResult result);

    /**
     * Called when the automator is waiting for the user to complete manual login.
     *
     * @param email the account email address currently being processed.
     */
    void onManualLoginAwait(String email);
}
