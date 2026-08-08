package com.adi.naukri.api;

import com.adi.naukri.orchestrator.JobHandle;
import com.adi.naukri.orchestrator.JobOrchestrator;
import com.adi.naukri.orchestrator.JobRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller that exposes job lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/jobs}           — start a new automation run</li>
 *   <li>{@code POST /api/jobs/{id}/stop}     — stop run after current account</li>
 *   <li>{@code POST /api/jobs/{id}/continue} — resume from manual-login gate</li>
 *   <li>{@code POST /api/jobs/{id}/skip}     — skip current account at manual-login gate</li>
 * </ul>
 *
 * Author: Adikarthik Gupta C B
 */
@RestController
@RequestMapping("/api")
public class JobController {

    private final JobOrchestrator orchestrator;

    public JobController(JobOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Starts a new automation run.
     *
     * @param req validated request body
     * @return {@link StartJobResponse} with the new jobId and WebSocket URL
     */
    @PostMapping("/jobs")
    public StartJobResponse startJob(@Valid @RequestBody StartJobRequest req) {
        // initialDelayMs is optional in the request; fall back to the JobRequest default (3 s).
        long initialDelayMs = (req.initialDelayMs() != null) ? req.initialDelayMs() : 3_000L;
        JobRequest jobReq = new JobRequest(
                req.accounts(),
                req.password(),
                req.headless(),
                req.manualLogin(),
                req.outputFolder(),
                req.resumeFolderPath(),
                req.baseUrlOverride(),
                initialDelayMs
        );
        JobHandle handle = orchestrator.start(jobReq);
        return new StartJobResponse(handle.jobId(), "/ws/jobs/" + handle.jobId());
    }

    /**
     * Requests the current run to stop after the current account completes.
     */
    @PostMapping("/jobs/{id}/stop")
    public ResponseEntity<Void> stopJob(@PathVariable String id) {
        orchestrator.stop(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resumes a run that is waiting at a manual-login gate.
     */
    @PostMapping("/jobs/{id}/continue")
    public ResponseEntity<Void> continueJob(@PathVariable String id) {
        orchestrator.continueNow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Skips the account currently waiting at a manual-login gate.
     */
    @PostMapping("/jobs/{id}/skip")
    public ResponseEntity<Void> skipJob(@PathVariable String id) {
        orchestrator.skip(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns 409 Conflict when a job run is already in progress (I3 fix).
     * Spring Boot's default would return 500 with a generic body.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleJobAlreadyRunning(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "job-already-running"));
    }
}
