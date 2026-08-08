package com.adi.naukri.orchestrator;

import com.adi.naukri.api.AccountInput;

import java.util.List;

/**
 * Immutable request object passed to {@link JobOrchestrator#start}.
 *
 * @param accounts         ordered list of (name, email) tuples to process.
 *                         {@code name} is used to locate the local resume file
 *                         when {@link #resumeFolderPath} is set.
 * @param password         shared account password — never logged or persisted.
 * @param headless         {@code true} to run browser in headless mode.
 * @param manualLogin      {@code true} to pause for manual login instead of
 *                         scripted login.
 * @param outputFolder     base path under which the run sub-folder will be created.
 * @param resumeFolderPath optional path to a folder containing local resume
 *                         files named {@code <name>*.pdf}. When set, the
 *                         automator skips the Naukri download step and uses
 *                         the local file. When {@code null}/blank, the
 *                         automator falls back to downloading from Naukri.
 * @param baseUrlOverride  optional override for the Naukri base URL;
 *                         {@code null} means use the default.
 * @param initialDelayMs   milliseconds to sleep at the start of each account
 *                         run before opening the browser. 0 disables the
 *                         delay (useful in tests).
 *
 * Author: Adikarthik Gupta C B
 */
public record JobRequest(
        List<AccountInput> accounts,
        String             password,
        boolean            headless,
        boolean            manualLogin,
        String             outputFolder,
        String             resumeFolderPath,
        String             baseUrlOverride,
        long               initialDelayMs
) {
    /** Compact constructor — defaults initialDelayMs to 3 s if not specified. */
    public JobRequest(
            List<AccountInput> accounts,
            String password,
            boolean headless,
            boolean manualLogin,
            String outputFolder,
            String resumeFolderPath,
            String baseUrlOverride) {
        this(accounts, password, headless, manualLogin,
                outputFolder, resumeFolderPath, baseUrlOverride, 3_000L);
    }

    public String effectiveBaseUrl() {
        return (baseUrlOverride != null && !baseUrlOverride.isBlank())
                ? baseUrlOverride
                : "https://www.naukri.com";
    }
}
