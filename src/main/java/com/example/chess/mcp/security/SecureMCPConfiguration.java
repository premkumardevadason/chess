package com.example.chess.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for MCP Double Ratchet encryption
 */
@Configuration
@ConditionalOnProperty(name = "mcp.encryption.enabled", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties(prefix = "mcp.encryption")
public class SecureMCPConfiguration {
    
    private boolean enabled = false;
    private String algorithm = "AES/GCM/NoPadding";
    private int keySize = 256;
    private boolean backwardCompatible = true;
    private int sessionTimeoutMinutes = 30;
    private boolean encryptTrainingData = true;
    private boolean encryptSessionState = true;
    
    // Rate limiting adjustments for encryption overhead
    private int encryptedRequestsPerMinute = 80; // Reduced from 100
    private int encryptedMovesPerMinute = 50;    // Reduced from 60
    private double encryptionOverheadFactor = 1.25;
    
    @Bean
    @ConditionalOnProperty(name = "mcp.encryption.enabled", havingValue = "true", matchIfMissing = false)
    public SecureMCPWebSocketHandler secureMCPWebSocketHandler() {
        return new SecureMCPWebSocketHandler();
    }
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    
    public int getKeySize() { return keySize; }
    public void setKeySize(int keySize) { this.keySize = keySize; }
    
    public boolean isBackwardCompatible() { return backwardCompatible; }
    public void setBackwardCompatible(boolean backwardCompatible) { this.backwardCompatible = backwardCompatible; }
    
    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
    
    public boolean isEncryptTrainingData() { return encryptTrainingData; }
    public void setEncryptTrainingData(boolean encryptTrainingData) { this.encryptTrainingData = encryptTrainingData; }
    
    public boolean isEncryptSessionState() { return encryptSessionState; }
    public void setEncryptSessionState(boolean encryptSessionState) { this.encryptSessionState = encryptSessionState; }
    
    public int getEncryptedRequestsPerMinute() { return encryptedRequestsPerMinute; }
    public void setEncryptedRequestsPerMinute(int encryptedRequestsPerMinute) { this.encryptedRequestsPerMinute = encryptedRequestsPerMinute; }
    
    public int getEncryptedMovesPerMinute() { return encryptedMovesPerMinute; }
    public void setEncryptedMovesPerMinute(int encryptedMovesPerMinute) { this.encryptedMovesPerMinute = encryptedMovesPerMinute; }
    
    public double getEncryptionOverheadFactor() { return encryptionOverheadFactor; }
    public void setEncryptionOverheadFactor(double encryptionOverheadFactor) { this.encryptionOverheadFactor = encryptionOverheadFactor; }
}