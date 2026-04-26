package com.example.chess.exceptions;

/**
 * Custom exception for Chess AI operations
 */
public class ChessAIException extends Exception {
    
    public enum ErrorType {
        MODEL_SAVE_FAILED,
        MODEL_LOAD_FAILED,
        TRAINING_DATA_CORRUPTION,
        SERIALIZATION_ERROR,
        FILE_IO_ERROR,
        NETWORK_ERROR,
        VALIDATION_ERROR
    }
    
    private final ErrorType errorType;
    private final String component;
    
    public ChessAIException(ErrorType errorType, String component, String message) {
        super(String.format("[%s] %s: %s", errorType, component, message));
        this.errorType = errorType;
        this.component = component;
    }
    
    public ChessAIException(ErrorType errorType, String component, String message, Throwable cause) {
        super(String.format("[%s] %s: %s", errorType, component, message), cause);
        this.errorType = errorType;
        this.component = component;
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    public String getComponent() {
        return component;
    }
    
    public boolean isRecoverable() {
        return errorType == ErrorType.FILE_IO_ERROR || 
               errorType == ErrorType.NETWORK_ERROR ||
               errorType == ErrorType.SERIALIZATION_ERROR;
    }
}
