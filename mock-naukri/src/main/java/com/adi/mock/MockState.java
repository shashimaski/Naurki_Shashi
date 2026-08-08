package com.adi.mock;

import org.springframework.stereotype.Component;

/**
 * Mutable singleton state shared across mock controller requests.
 * Reset between test runs via POST /_mock/reset.
 */
@Component
public class MockState {

    private String lastHeadline = "Senior Engineer";
    private String uploadedResumeName = null;
    private int headlineSaveCount = 0;
    private boolean loggedOut = false;

    public String getLastHeadline() {
        return lastHeadline;
    }

    public void setLastHeadline(String lastHeadline) {
        this.lastHeadline = lastHeadline;
    }

    public String getUploadedResumeName() {
        return uploadedResumeName;
    }

    public void setUploadedResumeName(String uploadedResumeName) {
        this.uploadedResumeName = uploadedResumeName;
    }

    public int getHeadlineSaveCount() {
        return headlineSaveCount;
    }

    public void incrementHeadlineSaveCount() {
        this.headlineSaveCount++;
    }

    public boolean isLoggedOut() {
        return loggedOut;
    }

    public void setLoggedOut(boolean loggedOut) {
        this.loggedOut = loggedOut;
    }

    /** Reset all state to defaults. */
    public void reset() {
        this.lastHeadline = "Senior Engineer";
        this.uploadedResumeName = null;
        this.headlineSaveCount = 0;
        this.loggedOut = false;
    }
}
