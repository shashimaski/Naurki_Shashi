package com.adi.naukri.automation;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for PlaywrightSession.
 * Requires Playwright Chromium to be installed:
 *   mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
 *
 * Author: Adikarthik Gupta C B
 */
@Tag("integration")
class PlaywrightSessionIT {

    @Test
    void opens_and_navigates_to_data_url() {
        try (PlaywrightSession s = new PlaywrightSession()) {
            Page p = s.open(AutomationRunMode.HEADLESS);
            p.navigate("data:text/html,<h1>hi</h1>");
            assertEquals("hi", p.textContent("h1"));
        }
    }
}
