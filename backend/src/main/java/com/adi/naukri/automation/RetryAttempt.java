package com.adi.naukri.automation;
public record RetryAttempt(int attempt, AutomationRunMode mode, long timeoutMs) {}
