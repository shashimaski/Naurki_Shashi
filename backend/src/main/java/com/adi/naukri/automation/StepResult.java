package com.adi.naukri.automation;

/**
 * Result of a single automation step.
 *
 * Author: Adikarthik Gupta C B
 *
 * @param step       the step that was executed
 * @param ok         true if the step succeeded
 * @param error      error message if ok==false, null otherwise
 * @param durationMs wall-clock duration of this step in milliseconds
 */
public record StepResult(AutomationStep step, boolean ok, String error, long durationMs) {

    /** Convenience factory for a successful step. */
    public static StepResult success(AutomationStep step, long durationMs) {
        return new StepResult(step, true, null, durationMs);
    }

    /** Convenience factory for a failed step. */
    public static StepResult failure(AutomationStep step, String error, long durationMs) {
        return new StepResult(step, false, error, durationMs);
    }
}
