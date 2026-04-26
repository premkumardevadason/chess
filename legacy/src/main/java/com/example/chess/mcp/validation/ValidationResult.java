package com.example.chess.mcp.validation;

import java.time.Duration;

public class ValidationResult {
    private final boolean valid;
    private final String error;
    private final boolean rateLimited;
    private final Duration retryAfter;
    private final boolean accessDenied;
    
    private ValidationResult(boolean valid, String error, boolean rateLimited, Duration retryAfter, boolean accessDenied) {
        this.valid = valid;
        this.error = error;
        this.rateLimited = rateLimited;
        this.retryAfter = retryAfter;
        this.accessDenied = accessDenied;
    }
    
    public static ValidationResult valid() {
        return new ValidationResult(true, null, false, null, false);
    }
    
    public static ValidationResult invalid(String error) {
        return new ValidationResult(false, error, false, null, false);
    }
    
    public static ValidationResult rateLimited(String error, Duration retryAfter) {
        return new ValidationResult(false, error, true, retryAfter, false);
    }
    
    public static ValidationResult accessDenied(String error) {
        return new ValidationResult(false, error, false, null, true);
    }
    
    public boolean isValid() { return valid; }
    public String getError() { return error; }
    public boolean isRateLimited() { return rateLimited; }
    public Duration getRetryAfter() { return retryAfter; }
    public boolean isAccessDenied() { return accessDenied; }
}