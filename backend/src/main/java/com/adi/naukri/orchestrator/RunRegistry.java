package com.adi.naukri.orchestrator;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the runDir path for each completed job, keyed by jobId.
 * Allows {@link com.adi.naukri.api.RunController} to resolve the CSV report.
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class RunRegistry {

    private final Map<String, Path> registry = new ConcurrentHashMap<>();

    /**
     * Records the output directory for a completed job.
     *
     * @param jobId  the job identifier
     * @param runDir the directory that was written by ReportWriter
     */
    public void record(String jobId, Path runDir) {
        registry.put(jobId, runDir);
    }

    /**
     * Looks up the run directory for a job.
     *
     * @param jobId the job identifier
     * @return the runDir, or {@link Optional#empty()} if unknown
     */
    public Optional<Path> lookup(String jobId) {
        return Optional.ofNullable(registry.get(jobId));
    }
}
