package com.example.chess.mcp.validation;

public class ValidationResult {
    private final boolean valid;
    private final String error;
    
    private ValidationResult(boolean valid, String error) {
        this.valid = valid;
        this.error = error;
    }
    
    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }
    
    public static ValidationResult invalid(String error) {
        return new ValidationResult(false, error);
    }
    
    public boolean isValid() { return valid; }
    public String getError() { return error; }
}