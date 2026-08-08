package com.adi.naukri.integration;

import com.adi.naukri.automation.TestPorts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full pipeline integration test: REST POST → orchestrator → automator →
 * mock Naukri → WebSocket events → report.csv written.
 *
 * <p>Starts the mock-naukri fat jar as a child process. Uses a real Playwright
 * Chromium browser in headless mode. Collects WebSocket events until
 * {@code RUN_COMPLETED} or a 120-second timeout.</p>
 *
 * <p>Tagged {@code integration} so it can be excluded from fast unit-test runs
 * via {@code -Dgroups=!integration}.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullPipelineIT {

    static Process mock;
    static int     mockPort;

    @Autowired TestRestTemplate rest;
    @LocalServerPort int bePort;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @BeforeAll
    static void startMockNaukri() throws Exception {
        mockPort = TestPorts.free();

        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar",
                "../mock-naukri/target/mock-naukri.jar",
                "--server.port=" + mockPort
        );
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        mock = pb.start();

        TestPorts.waitUntilOpen(mockPort, Duration.ofSeconds(30));
    }

    @AfterAll
    static void stopMockNaukri() {
        if (mock != null) mock.destroy();
    }

    // -----------------------------------------------------------------------
    // Test: happy path — 2 accounts, mock Naukri responds to all steps
    // -----------------------------------------------------------------------

    @Test
    void full_pipeline_happy_path() throws Exception {
        Path outDir = Files.createTempDirectory("naukri-pipeline-it");

        @SuppressWarnings("unchecked")
        Map<String, Object> reqBody = Map.of(
                "emails",          List.of("ok@x.com", "ok2@x.com"),
                "password",        "p",
                "headless",        true,
                "manualLogin",     false,
                "outputFolder",    outDir.toString(),
                "baseUrlOverride", "http://127.0.0.1:" + mockPort
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = rest.postForObject("/api/jobs", reqBody, Map.class);
        assertNotNull(resp, "POST /api/jobs returned null");
        String jobId = (String) resp.get("jobId");
        assertNotNull(jobId, "Response missing jobId");

        // Collect WS events until RUN_COMPLETED (or 120s timeout)
        List<Map<String, Object>> events =
                new WsClient("/ws/jobs/" + jobId, bePort)
                        .collectUntil("RUN_COMPLETED", 120);

        // ── Assertions ──────────────────────────────────────────────────────

        // 1. RUN_STARTED received
        assertTrue(
                events.stream().anyMatch(e -> "RUN_STARTED".equals(e.get("type"))),
                "Expected RUN_STARTED in events: " + eventTypes(events));

        // 2. Exactly 2 ACCOUNT_COMPLETED events
        long accountCompletedCount = events.stream()
                .filter(e -> "ACCOUNT_COMPLETED".equals(e.get("type")))
                .count();
        assertEquals(2, accountCompletedCount,
                "Expected 2 ACCOUNT_COMPLETED events, got " + accountCompletedCount
                + "; full event sequence: " + eventTypes(events));

        // 3. Both ACCOUNT_COMPLETED events have status OK
        events.stream()
                .filter(e -> "ACCOUNT_COMPLETED".equals(e.get("type")))
                .forEach(e -> assertEquals("OK", e.get("status"),
                        "Expected ACCOUNT_COMPLETED status=OK but got: " + e.get("status")));

        // 4. report.csv written somewhere under outDir (orchestrator nests it in a timestamped folder)
        boolean reportFound;
        try (var walk = Files.walk(outDir)) {
            reportFound = walk.anyMatch(p -> p.getFileName().toString().equals("report.csv"));
        }
        assertTrue(reportFound,
                "report.csv not found anywhere under " + outDir);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<String> eventTypes(List<Map<String, Object>> events) {
        return events.stream().map(e -> (String) e.get("type")).toList();
    }
}
