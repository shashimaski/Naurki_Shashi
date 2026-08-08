package com.adi.naukri.report;
import java.time.Instant;
import java.util.List;

/**
 * Immutable result for a single account automation run.
 *
 * @param dumpPaths  paths to DOM/screenshot dump files captured on post-login step failures
 *                   (empty list when the happy-path completes with no failures).
 *
 * Author: Adikarthik Gupta C B
 */
public record AccountResult(
    String email, AccountStatus status, String error,
    String resumeOldName, String resumeNewName,
    Instant startedAt, Instant endedAt, int retries,
    List<StepTiming> steps,
    List<String> dumpPaths) {

    /** Backward-compat constructor: dumpPaths defaults to an empty list. */
    public AccountResult(
            String email, AccountStatus status, String error,
            String resumeOldName, String resumeNewName,
            Instant startedAt, Instant endedAt, int retries,
            List<StepTiming> steps) {
        this(email, status, error, resumeOldName, resumeNewName,
             startedAt, endedAt, retries, steps, List.of());
    }
}
