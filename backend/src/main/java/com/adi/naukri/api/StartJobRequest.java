package com.adi.naukri.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /api/jobs}.
 *
 * <p>{@link #accounts} is the ordered batch to process; each entry carries the
 * account holder's name (used to locate the local resume file) and the account
 * email. When {@link #resumeFolderPath} is provided, the automator skips the
 * Naukri download and instead globs {@code <resumeFolderPath>/<name>*.pdf} for
 * exactly one file, renames it with today's date (in place), and uploads it.
 * When {@link #resumeFolderPath} is null/empty, the automator falls back to
 * the Naukri download flow.</p>
 *
 * <p>{@link #baseUrlOverride} is a test-only field. Production callers never
 * send it; when absent (null) the orchestrator uses
 * {@code https://www.naukri.com}.</p>
 *
 * <p>{@link #initialDelayMs} is optional. When absent (null) the orchestrator
 * uses the default of 3 000 ms. Pass 0 to disable the warm-up pause.</p>
 *
 * Author: Adikarthik Gupta C B
 */
public record StartJobRequest(
        @NotEmpty List<@Valid AccountInput> accounts,
        @NotBlank String password,
        boolean headless,
        boolean manualLogin,
        @NotBlank String outputFolder,
        String resumeFolderPath,
        String baseUrlOverride,
        Long initialDelayMs
) {}
