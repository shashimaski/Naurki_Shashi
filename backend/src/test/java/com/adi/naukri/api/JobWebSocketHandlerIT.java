package com.adi.naukri.api;

import com.adi.naukri.orchestrator.JobEvent;
import com.adi.naukri.orchestrator.JobEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link JobWebSocketHandler}.
 *
 * <p>Starts a real Spring Boot server on a random port, connects a
 * {@link StandardWebSocketClient}, publishes events via {@link JobEventBus},
 * and asserts that text frames arrive with the expected JSON {@code type} fields.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobWebSocketHandlerIT {

    @LocalServerPort int port;
    @Autowired JobEventBus eventBus;

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules(); // registers JavaTimeModule for Instant

    // -----------------------------------------------------------------------
    // Test: client receives all 3 published events (including buffered replay)
    // -----------------------------------------------------------------------

    @Test
    void client_receives_all_three_events_with_correct_type() throws Exception {
        String jobId = "ws-test-" + System.nanoTime();

        // Publish 3 events BEFORE connecting — they should be replayed on connect
        eventBus.publish(new JobEvent.RunStarted(jobId, Instant.now(), 1));
        eventBus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), "a@x.com", 0));
        eventBus.publish(new JobEvent.StepCompleted(jobId, Instant.now(), "a@x.com", "LOGIN", 100L));

        // Collect exactly 3 frames
        List<String> rawFrames = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        wsClient.execute(new AbstractWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                rawFrames.add(message.getPayload());
                latch.countDown();
            }
        }, "ws://127.0.0.1:" + port + "/ws/jobs/" + jobId).get(5, TimeUnit.SECONDS);

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "Did not receive 3 frames within timeout; got: " + rawFrames.size());
        assertEquals(3, rawFrames.size());

        // Parse and verify type field sequence
        List<String> types = rawFrames.stream()
                .map(frame -> {
                    try {
                        return mapper.readTree(frame).path("type").asText();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        assertTrue(types.contains("RUN_STARTED"),       "Expected RUN_STARTED in: " + types);
        assertTrue(types.contains("ACCOUNT_STARTED"),   "Expected ACCOUNT_STARTED in: " + types);
        assertTrue(types.contains("STEP_COMPLETED"),    "Expected STEP_COMPLETED in: " + types);
    }

    // -----------------------------------------------------------------------
    // Test: events published AFTER connect are also delivered
    // -----------------------------------------------------------------------

    @Test
    void client_receives_event_published_after_connect() throws Exception {
        String jobId = "ws-live-" + System.nanoTime();

        CountDownLatch latch = new CountDownLatch(1);
        List<JsonNode> received = new ArrayList<>();

        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        wsClient.execute(new AbstractWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                received.add(mapper.readTree(message.getPayload()));
                latch.countDown();
            }
        }, "ws://127.0.0.1:" + port + "/ws/jobs/" + jobId).get(5, TimeUnit.SECONDS);

        // Small pause to ensure subscription is established
        Thread.sleep(100);
        eventBus.publish(new JobEvent.RunStopped(jobId, Instant.now()));

        boolean got = latch.await(5, TimeUnit.SECONDS);
        assertTrue(got, "Did not receive live event within timeout");
        assertEquals("RUN_STOPPED", received.get(0).path("type").asText());
    }
}
