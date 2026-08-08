package com.adi.naukri.automation;

/**
 * Thrown by {@link NaukriAutomator} when an unrecoverable error occurs that
 * prevents further execution (e.g. browser crash, unexpected page state).
 *
 * Individual step failures are captured as {@link StepResult} entries with
 * {@code ok=false}; this exception is reserved for infrastructure-level faults.
 *
 * Author: Adikarthik Gupta C B
 */
public class AutomatorException extends RuntimeException {

    public AutomatorException(String message) {
        super(message);
    }

    public AutomatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
