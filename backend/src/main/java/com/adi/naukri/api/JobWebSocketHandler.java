package com.adi.naukri.api;

import com.adi.naukri.orchestrator.JobEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler that streams {@link com.adi.naukri.orchestrator.JobEvent}s
 * to connected clients.
 *
 * <p>On connect: extracts {@code jobId} from session attributes (placed there by
 * {@link PathExtractingInterceptor}), subscribes to {@link JobEventBus} for that
 * jobId, and replays any buffered events before delivering live events.</p>
 *
 * <p>On close: unsubscribes from the bus. Silently ignores send errors on already-
 * closed sessions to prevent cascading exceptions.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class JobWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(JobWebSocketHandler.class);

    private final JobEventBus  eventBus;
    private final ObjectMapper mapper;

    /** Tracks active bus registrations so we can cancel them on close. */
    private final Map<String, JobEventBus.Registration> registrations = new ConcurrentHashMap<>();

    public JobWebSocketHandler(JobEventBus eventBus) {
        this.eventBus = eventBus;
        this.mapper   = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String jobId = (String) session.getAttributes().get("jobId");
        if (jobId == null || jobId.isBlank()) {
            log.warn("[WS] afterConnectionEstablished: jobId missing from session attributes {}; " +
                    "closing with BAD_DATA. URI={}", session.getId(), session.getUri());
            closeQuietly(session);
            return;
        }
        log.debug("[WS] Client connected: sessionId={} jobId={}", session.getId(), jobId);

        // Subscribe to the event bus. The bus replays buffered events synchronously
        // before returning, so new events are always delivered after buffered ones.
        JobEventBus.Registration reg = eventBus.subscribe(jobId, event -> {
            if (!session.isOpen()) return;
            try {
                String json = mapper.writeValueAsString(event);
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception e) {
                // Session was closed between isOpen() check and sendMessage — ignore
            }
        });

        registrations.put(session.getId(), reg);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        JobEventBus.Registration reg = registrations.remove(session.getId());
        if (reg != null) {
            reg.close();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.BAD_DATA);
        } catch (Exception ignored) {}
    }
}
