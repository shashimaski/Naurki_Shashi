package com.adi.naukri.orchestrator;

import java.util.concurrent.Future;

/**
 * Handle returned immediately by {@link JobOrchestrator#start}.
 *
 * <p>Callers can use {@link #future()} to await completion or observe failures.</p>
 *
 * @param jobId  unique identifier for this run
 * @param future completes (with {@code null}) when the run finishes or is stopped;
 *               completes exceptionally on unexpected internal errors
 *
 * Author: Adikarthik Gupta C B
 */
public record JobHandle(String jobId, Future<Void> future) {}
