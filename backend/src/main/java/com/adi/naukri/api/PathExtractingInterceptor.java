package com.adi.naukri.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket handshake interceptor that extracts the last path segment of the
 * upgrade URL as the {@code jobId} session attribute.
 *
 * <p>For example, a connection to {@code /ws/jobs/abc-123} will produce
 * {@code attributes.get("jobId") == "abc-123"}.</p>
 *
 * Author: Adikarthik Gupta C B
 */
class PathExtractingInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PathExtractingInterceptor.class);

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String path = request.getURI().getPath();
        // Path is like /ws/jobs/<jobId> — take the last segment
        String[] segments = path.split("/");
        if (segments.length > 0) {
            String jobId = segments[segments.length - 1];
            if (jobId == null || jobId.isBlank()) {
                log.warn("[WS] PathExtractingInterceptor: could not extract jobId from path '{}'; " +
                        "handler will close this session with BAD_DATA.", path);
            } else {
                log.debug("[WS] Handshake: path='{}' jobId='{}'", path, jobId);
            }
            attributes.put("jobId", jobId);
        } else {
            log.warn("[WS] PathExtractingInterceptor: empty path '{}'; no jobId extracted.", path);
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
