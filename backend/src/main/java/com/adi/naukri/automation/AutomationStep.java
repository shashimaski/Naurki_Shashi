package com.adi.naukri.automation;

/**
 * Ordered steps in a single Naukri account automation run.
 *
 * <p>RENAME_RESUME was removed: it is an internal operation performed between
 * DOWNLOAD_RESUME and UPLOAD_RESUME that does not produce a StepResult event
 * per spec §4.</p>
 *
 * Author: Adikarthik Gupta C B
 */
public enum AutomationStep {
    LOGIN,
    HEADLINE_APPEND,
    HEADLINE_STRIP,
    DOWNLOAD_RESUME,
    UPLOAD_RESUME,
    LOGOUT
}
