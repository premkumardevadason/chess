package com.example.chess.mcp.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Encrypts AI training data before it reaches AI systems
 */
@Component
public class EncryptedTrainingDataPipeline {
    
    private static final Logger logger = LogManager.getLogger(EncryptedTrainingDataPipeline.class);
    
    private final SecretKey trainingDataKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public EncryptedTrainingDataPipeline() throws Exception {
        // Initialize training data encryption key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        this.trainingDataKey = keyGen.generateKey();
        logger.info("Initialized encrypted training data pipeline");
    }
    
    /**
     * Encrypts move history before sending to AI training systems
     */
    public String encryptTrainingData(String sessionId, List<String> moveHistory, boolean gameResult) {
        try {
            // Create training data record
            TrainingDataRecord record = new TrainingDataRecord(sessionId, moveHistory, gameResult);
            String recordJson = record.toJson();
            
            // Encrypt training data
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, trainingDataKey, gcmSpec);
            
            byte[] ciphertext = cipher.doFinal(recordJson.getBytes());
            
            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            String encryptedData = Base64.getEncoder().encodeToString(combined);
            
            logger.debug("Encrypted training data for session: {}", sessionId);
            return encryptedData;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt training data for session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Training data encryption failed", e);
        }
    }
    
    /**
     * Decrypts training data for AI systems
     */
    public TrainingDataRecord decryptTrainingData(String encryptedData) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
            
            // Decrypt training data
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, trainingDataKey, gcmSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            String recordJson = new String(plaintext);
            
            return TrainingDataRecord.fromJson(recordJson);
            
        } catch (Exception e) {
            logger.error("Failed to decrypt training data: {}", e.getMessage());
            throw new RuntimeException("Training data decryption failed", e);
        }
    }
    
    /**
     * Training data record structure
     */
    public static class TrainingDataRecord {
        private final String sessionId;
        private final List<String> moveHistory;
        private final boolean gameResult;
        private final long timestamp;
        
        public TrainingDataRecord(String sessionId, List<String> moveHistory, boolean gameResult) {
            this.sessionId = sessionId;
            this.moveHistory = moveHistory;
            this.gameResult = gameResult;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getSessionId() { return sessionId; }
        public List<String> getMoveHistory() { return moveHistory; }
        public boolean getGameResult() { return gameResult; }
        public long getTimestamp() { return timestamp; }
        
        public String toJson() {
            return String.format(
                "{\"sessionId\":\"%s\",\"moveHistory\":%s,\"gameResult\":%b,\"timestamp\":%d}",
                sessionId,
                moveHistory.toString().replace("'", "\""),
                gameResult,
                timestamp
            );
        }
        
        public static TrainingDataRecord fromJson(String json) {
            // Simple JSON parsing - in production use proper JSON library
            try {
                String sessionId = extractJsonValue(json, "sessionId");
                boolean gameResult = Boolean.parseBoolean(extractJsonValue(json, "gameResult"));
                
                // Extract move history array
                String moveHistoryStr = extractJsonArray(json, "moveHistory");
                List<String> moveHistory = java.util.Arrays.asList(
                    moveHistoryStr.replace("[", "").replace("]", "").replace("\"", "").split(",")
                );
                
                return new TrainingDataRecord(sessionId, moveHistory, gameResult);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse training data JSON", e);
            }
        }
        
        private static String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern) + pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        
        private static String extractJsonArray(String json, String key) {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern) + pattern.length();
            int bracketStart = json.indexOf("[", start);
            int bracketEnd = json.indexOf("]", bracketStart) + 1;
            return json.substring(bracketStart, bracketEnd);
        }
    }
}