package com.adi.naukri.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal WebSocket test client that connects to a server endpoint, collects
 * decoded JSON events, and blocks until a terminal event type is received.
 *
 * <p>Usage:
 * <pre>
 *   List&lt;Map&lt;String,Object&gt;&gt; events =
 *       new WsClient("/ws/jobs/" + jobId, port).collectUntil("RUN_COMPLETED", 90);
 * </pre>
 * </p>
 *
 * Author: Adikarthik Gupta C B
 */
public class WsClient {

    private final String  wsUrl;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * @param path relative WebSocket path, e.g. {@code /ws/jobs/abc-123}
     * @param port server port
     */
    public WsClient(String path, int port) {
        this.wsUrl = "ws://127.0.0.1:" + port + path;
    }

    /**
     * Connects, accumulates events, and returns once an event with the given
     * {@code terminalType} arrives (or the timeout elapses).
     *
     * @param terminalType  value of the {@code type} JSON field that signals completion
     * @param timeoutSec    maximum seconds to wait
     * @return all events received (including the terminal one)
     * @throws RuntimeException on connection or timeout failure
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> collectUntil(String terminalType, long timeoutSec)
            throws Exception {

        List<Map<String, Object>> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        StandardWebSocketClient client = new StandardWebSocketClient();
        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void handleTextMessage(WebSocketSession session, TextMessage message)
                    throws Exception {
                Map<String, Object> event = mapper.readValue(message.getPayload(), Map.class);
                events.add(event);
                if (terminalType.equals(event.get("type"))) {
                    done.countDown();
                }
            }
        }, wsUrl).get(10, TimeUnit.SECONDS);

        boolean completed = done.await(timeoutSec, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException(
                    "Timed out waiting for " + terminalType + " after " + timeoutSec
                    + "s; events received: " + events.stream()
                            .map(e -> e.get("type"))
                            .toList());
        }
        return events;
    }
}
