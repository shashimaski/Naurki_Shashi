package com.adi.naukri.api;

import com.adi.naukri.orchestrator.RunRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc contract tests for GET /api/runs/{jobId}/report.csv.
 *
 * Created by: Adikarthik Gupta C B
 */
@SpringBootTest
@AutoConfigureMockMvc
class RunControllerTest {

    @Autowired MockMvc mvc;
    @Autowired RunRegistry runRegistry;

    @TempDir
    Path tempDir;

    /** Unknown jobId → 404 */
    @Test
    void report_unknownJobId_returns404() throws Exception {
        mvc.perform(get("/api/runs/no-such-job/report.csv"))
           .andExpect(status().isNotFound());
    }

    /** Known jobId with report.csv → 200, text/csv, attachment disposition */
    @Test
    void report_knownJobId_returns200WithCsv() throws Exception {
        // Arrange: write a tiny CSV file into a temp dir
        Path csvFile = tempDir.resolve("report.csv");
        Files.writeString(csvFile, "email,status\nalice@example.com,OK\n");

        // Register the runDir (tempDir) for a known job
        String jobId = "test-job-" + System.nanoTime();
        runRegistry.record(jobId, tempDir);

        // Act + Assert
        mvc.perform(get("/api/runs/" + jobId + "/report.csv"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/csv"))
           .andExpect(header().string("Content-Disposition",
                   org.hamcrest.Matchers.containsString("attachment")))
           .andExpect(header().string("Content-Disposition",
                   org.hamcrest.Matchers.containsString("report.csv")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("alice@example.com")));
    }
}
