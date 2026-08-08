package com.adi.naukri.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * One (name, email) tuple in a batch start-job request.
 *
 * <p>{@code name} is used to locate the local resume file when the caller has
 * supplied {@code resumeFolderPath} on the parent request — the automator globs
 * {@code <resumeFolderPath>/<name>*.pdf} and expects exactly one match. If the
 * caller has not enabled the local-folder flow the name is still passed
 * through unchanged for report attribution.</p>
 *
 * Author: Adikarthik Gupta C B
 */
public record AccountInput(
        @NotBlank String name,
        @NotBlank @Email String email
) {}
