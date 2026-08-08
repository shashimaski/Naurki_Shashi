package com.adi.naukri.api;

/**
 * Response body for {@code POST /api/jobs}.
 *
 * @param jobId  unique identifier for the created run
 * @param wsUrl  relative WebSocket URL to stream events for this job
 *               (e.g. {@code /ws/jobs/&lt;jobId&gt;})
 *
 * Author: Adikarthik Gupta C B
 */
public record StartJobResponse(String jobId, String wsUrl) {}
