package com.example.chess.mcp.security;

import com.example.chess.mcp.session.ChessGameSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypts chess game session state separately from message encryption
 */
@Component
public class EncryptedSessionState {
    
    private static final Logger logger = LogManager.getLogger(EncryptedSessionState.class);
    
    private final ConcurrentHashMap<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypts chess game session state for persistent storage
     */
    public String encryptSessionState(String sessionId, ChessGameSession session) {
        try {
            SecretKey sessionKey = getOrCreateSessionKey(sessionId);
            
            // Serialize session to JSON
            String sessionJson = objectMapper.writeValueAsString(session.getGameState());
            
            // Encrypt session data
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, gcmSpec);
            
            byte[] ciphertext = cipher.doFinal(sessionJson.getBytes());
            
            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            logger.error("Failed to encrypt session state for {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Session encryption failed", e);
        }
    }
    
    /**
     * Decrypts chess game session state from persistent storage
     */
    public String decryptSessionState(String sessionId, String encryptedState) {
        try {
            SecretKey sessionKey = getOrCreateSessionKey(sessionId);
            
            byte[] combined = Base64.getDecoder().decode(encryptedState);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);
            
            // Decrypt session data
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext);
            
        } catch (Exception e) {
            logger.error("Failed to decrypt session state for {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Session decryption failed", e);
        }
    }
    
    /**
     * Removes session encryption key when session ends
     */
    public void removeSessionKey(String sessionId) {
        sessionKeys.remove(sessionId);
        logger.debug("Removed session key for: {}", sessionId);
    }
    
    private SecretKey getOrCreateSessionKey(String sessionId) {
        return sessionKeys.computeIfAbsent(sessionId, id -> {
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                SecretKey key = keyGen.generateKey();
                logger.debug("Created session key for: {}", id);
                return key;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate session key", e);
            }
        });
    }
}