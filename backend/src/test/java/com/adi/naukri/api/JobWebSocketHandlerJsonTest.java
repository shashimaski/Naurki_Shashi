package com.adi.naukri.api;

import com.adi.naukri.orchestrator.JobEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test that proves the ObjectMapper used inside {@link JobWebSocketHandler}
 * can serialise {@link Instant}-bearing events without throwing
 * {@code InvalidDefinitionException("Java 8 date/time type not supported by default")}.
 *
 * <p>This is the regression guard for the root-cause fix: a hand-constructed
 * {@code new ObjectMapper()} without {@link JavaTimeModule} would throw on any
 * event that carries an {@code Instant} timestamp field, causing the WS session
 * to be closed with an error before the FE receives any events.</p>
 *
 * Author: Adikarthik Gupta C B
 */
class JobWebSocketHandlerJsonTest {

    /**
     * Constructs the same ObjectMapper that {@link JobWebSocketHandler} uses
     * (JavaTimeModule registered + WRITE_DATES_AS_TIMESTAMPS disabled) and
     * serialises every concrete {@link JobEvent} subtype that carries an
     * {@code Instant} field.  Any serialisation exception is a test failure.
     */
    @Test
    void handler_object_mapper_serializes_instant_without_throwing() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Instant now = Instant.now();

        // Test every concrete event type — each carries Instant timestamp
        JobEvent[] events = {
            new JobEvent.RunStarted("job-1", now, 2),
            new JobEvent.AccountStarted("job-1", now, "a@x.com", 0),
            new JobEvent.StepStarted("job-1", now, "a@x.com", "LOGIN"),
            new JobEvent.StepStarted("job-1", now, "a@x.com", "LOGIN.type-email"),
            new JobEvent.StepCompleted("job-1", now, "a@x.com", "LOGIN", 1234L),
            new JobEvent.StepFailed("job-1", now, "a@x.com", "LOGIN", "AUTH_FAILED"),
            new JobEvent.AwaitManualLogin("job-1", now, "a@x.com", 300L),
            new JobEvent.AccountCompleted("job-1", now, "a@x.com",
                    com.adi.naukri.report.AccountStatus.OK, null, null),
            new JobEvent.RunCompleted("job-1", now,
                    new JobEvent.Summary(1, 0, 0, 0, 0)),
            new JobEvent.RunStopped("job-1", now),
        };

        for (JobEvent event : events) {
            // Must not throw
            String json = assertDoesNotThrow(
                    () -> mapper.writeValueAsString(event),
                    "Serialisation threw for event type: " + event.getClass().getSimpleName());

            // timestamp must serialise as an ISO-8601 string, not a number
            JsonNode node = mapper.readTree(json);
            assertTrue(node.has("timestamp"),
                    "Expected 'timestamp' field in: " + json);
            assertTrue(node.get("timestamp").isTextual(),
                    "Expected timestamp to be a string (ISO-8601) but got: "
                    + node.get("timestamp").getNodeType()
                    + " in: " + json);
            // Basic sanity: timestamp string must contain 'T' (ISO-8601 datetime separator)
            String ts = node.get("timestamp").asText();
            assertTrue(ts.contains("T"),
                    "Timestamp does not look like ISO-8601: " + ts);

            // type discriminator must be present
            assertTrue(node.has("type"),
                    "Expected 'type' discriminator field in: " + json);
        }
    }

    /**
     * Contrast test: a bare {@code new ObjectMapper()} WITHOUT JavaTimeModule
     * MUST throw on Instant — confirming the original bug existed.
     */
    @Test
    void bare_object_mapper_throws_on_instant() {
        ObjectMapper bareMapper = new ObjectMapper(); // no JavaTimeModule
        JobEvent event = new JobEvent.RunStarted("job-x", Instant.now(), 1);

        assertThrows(Exception.class, () -> bareMapper.writeValueAsString(event),
                "Expected a bare ObjectMapper to throw on Instant serialisation");
    }
}
