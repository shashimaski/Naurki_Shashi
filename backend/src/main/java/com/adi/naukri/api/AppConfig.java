package com.adi.naukri.api;

import com.adi.naukri.automation.PlaywrightSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/**
 * Application-level Spring bean definitions.
 *
 * <p>Registers the {@link PlaywrightSession} factory as a
 * {@code Supplier<PlaywrightSession>} bean so it can be injected into
 * {@link com.adi.naukri.orchestrator.JobOrchestrator}.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Configuration
public class AppConfig {

    /**
     * Factory that creates a new {@link PlaywrightSession} (and its underlying
     * Playwright / Chromium context) on each call.  The caller is responsible
     * for closing the session.
     */
    @Bean
    public Supplier<PlaywrightSession> playwrightSessionFactory() {
        return PlaywrightSession::new;
    }
}
