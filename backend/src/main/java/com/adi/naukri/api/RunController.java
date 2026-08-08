package com.adi.naukri.api;

import com.adi.naukri.orchestrator.RunRegistry;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * REST controller exposing run-report download endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/runs/{jobId}/report.csv} — streams the CSV report for a completed job</li>
 * </ul>
 *
 * Author: Adikarthik Gupta C B
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunRegistry runRegistry;

    public RunController(RunRegistry runRegistry) {
        this.runRegistry = runRegistry;
    }

    /**
     * Streams the report.csv for a completed job as an attachment.
     *
     * @param jobId the job identifier
     * @return 200 with CSV content, or 404 if jobId is unknown
     */
    @GetMapping(value = "/{jobId}/report.csv", produces = "text/csv")
    public ResponseEntity<Resource> getReport(@PathVariable String jobId) {
        return runRegistry.lookup(jobId)
                .map(runDir -> {
                    Path csvPath = runDir.resolve("report.csv");
                    Resource resource = new PathResource(csvPath);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"report.csv\"")
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .<Resource>body(resource);
                })
                .orElseGet(() -> ResponseEntity.notFound().<Resource>build());
    }
}
