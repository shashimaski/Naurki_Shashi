package com.adi.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone mock Naukri server — simulates Naukri.com pages for integration/E2E tests.
 * Built by Adikarthik Gupta C B
 */
@SpringBootApplication
public class MockNaukriApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockNaukriApplication.class, args);
    }
}
