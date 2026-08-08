package com.adi.naukri.orchestrator;

import com.adi.naukri.report.AccountStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Sealed event hierarchy for the JobOrchestrator event bus.
 *
 * <p>All events carry a {@code jobId} and a {@code timestamp}. The sealed
 * constraint means only the records defined here can implement this interface,
 * giving exhaustive {@code instanceof} pattern-matching in switch expressions.</p>
 *
 * <p>Jackson type info is configured so that JSON serialization includes a
 * {@code "type"} discriminator field (e.g. {@code "RUN_STARTED"}). This
 * enables the WebSocket handler and frontend to identify event types without
 * reflection.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JobEvent.RunStarted.class,       name = "RUN_STARTED"),
    @JsonSubTypes.Type(value = JobEvent.AccountStarted.class,   name = "ACCOUNT_STARTED"),
    @JsonSubTypes.Type(value = JobEvent.StepStarted.class,      name = "STEP_STARTED"),
    @JsonSubTypes.Type(value = JobEvent.StepCompleted.class,    name = "STEP_COMPLETED"),
    @JsonSubTypes.Type(value = JobEvent.StepFailed.class,       name = "STEP_FAILED"),
    @JsonSubTypes.Type(value = JobEvent.AwaitManualLogin.class, name = "AWAIT_MANUAL_LOGIN"),
    @JsonSubTypes.Type(value = JobEvent.AccountCompleted.class, name = "ACCOUNT_COMPLETED"),
    @JsonSubTypes.Type(value = JobEvent.RunCompleted.class,     name = "RUN_COMPLETED"),
    @JsonSubTypes.Type(value = JobEvent.RunStopped.class,       name = "RUN_STOPPED"),
})
public sealed interface JobEvent
        permits JobEvent.RunStarted,
                JobEvent.AccountStarted,
                JobEvent.StepStarted,
                JobEvent.StepCompleted,
                JobEvent.StepFailed,
                JobEvent.AwaitManualLogin,
                JobEvent.AccountCompleted,
                JobEvent.RunCompleted,
                JobEvent.RunStopped {

    String  jobId();
    Instant timestamp();

    // ------------------------------------------------------------------
    // Summary — embedded value type used by RunCompleted
    // ------------------------------------------------------------------
    record Summary(int ok, int authFailed, int requiresManual, int failed, int skipped) {}

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Emitted once when a job run begins. */
    record RunStarted(String jobId, Instant timestamp, int total)
            implements JobEvent {}

    /** Emitted when the orchestrator starts processing an account. */
    record AccountStarted(String jobId, Instant timestamp, String email, int index)
            implements JobEvent {}

    /** Emitted just before an automation step executes. */
    record StepStarted(String jobId, Instant timestamp, String email, String step)
            implements JobEvent {}

    /** Emitted after a step succeeds. */
    record StepCompleted(String jobId, Instant timestamp, String email, String step, long durationMs)
            implements JobEvent {}

    /** Emitted when a step fails. */
    record StepFailed(String jobId, Instant timestamp, String email, String step, String error)
            implements JobEvent {}

    /**
     * Emitted when the automator is waiting for the user to complete manual
     * login.  The orchestrator will not proceed until it receives a
     * {@link JobOrchestrator#continueNow} or {@link JobOrchestrator#skip} signal.
     */
    record AwaitManualLogin(String jobId, Instant timestamp, String email, long timeoutSec)
            implements JobEvent {}

    /** Emitted when an account finishes (success or any failure). */
    record AccountCompleted(
            String jobId, Instant timestamp, String email,
            AccountStatus status, String resumeOldName, String resumeNewName)
            implements JobEvent {}

    /** Emitted at the end of a run that completed normally. */
    record RunCompleted(String jobId, Instant timestamp, Summary summary)
            implements JobEvent {}

    /** Emitted when a run is stopped by {@link JobOrchestrator#stop}. */
    record RunStopped(String jobId, Instant timestamp)
            implements JobEvent {}
}
