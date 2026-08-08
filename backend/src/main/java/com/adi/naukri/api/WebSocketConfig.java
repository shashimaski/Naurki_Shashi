package com.adi.naukri.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * Registers the {@link JobWebSocketHandler} at {@code /ws/jobs/{jobId}}.
 *
 * <p>A {@link PathExtractingInterceptor} extracts the {@code jobId} path segment
 * from the upgrade URL and stores it in the WebSocket session attributes under
 * the key {@code "jobId"}, so the handler can subscribe to the correct event stream.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JobWebSocketHandler handler;

    public WebSocketConfig(JobWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/jobs/*")
                .addInterceptors(new PathExtractingInterceptor())
                .setAllowedOriginPatterns("*");
    }
}
