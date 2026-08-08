package com.adi.naukri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class NaukriApplication {
    public static void main(String[] args) {
        SpringApplication.run(NaukriApplication.class, args);
    }

    @Component
    static class PortAnnouncer {
        private final Environment env;
        PortAnnouncer(Environment env) { this.env = env; }

        @EventListener(ApplicationReadyEvent.class)
        void announce() {
            String port = env.getProperty("local.server.port");
            System.out.println("NAUKRI_BE_PORT=" + port);
            System.out.flush();
        }
    }
}
