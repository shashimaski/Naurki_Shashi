package com.adi.naukri.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cross-origin filter — required because the Electron renderer loads the
 * bundled SPA over file:// (Origin: null) while the BE listens on
 * http://127.0.0.1:<random-port>. Without this, every POST from the
 * renderer triggers a preflight OPTIONS that Spring rejects, and fetch
 * reports the generic "Failed to fetch".
 *
 * Allows: null origin (file://), localhost / 127.0.0.1 on any port,
 * all methods, all headers. No credentials.
 *
 * NOTE: /ws/** is deliberately excluded from this filter. WebSocket upgrade
 * requests must not be handled by the servlet-level CorsFilter — Tomcat's WS
 * upgrade pathway processes the response headers directly and a concurrent
 * CorsFilter write corrupts the handshake. Origin checking for WS is handled
 * by WebSocketConfig.setAllowedOriginPatterns("*") instead.
 *
 * Created by: Adikarthik Gupta C B
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOriginPattern("null");
        cfg.addAllowedOriginPattern("file://*");
        cfg.addAllowedOriginPattern("http://127.0.0.1:*");
        cfg.addAllowedOriginPattern("http://localhost:*");
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        // Apply to REST endpoints only; /ws/** has its own origin policy in WebSocketConfig.
        src.registerCorsConfiguration("/api/**", cfg);
        return new CorsFilter(src);
    }
}
