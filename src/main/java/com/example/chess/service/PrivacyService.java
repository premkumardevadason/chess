package com.example.chess.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.nio.file.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PrivacyService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrivacyService.class);
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String KEY_FILE = "encryption.key";
    
    private SecretKey encryptionKey;
    private final Map<String, String> auditLog = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void init() throws Exception {
        try {
            // Try to load existing key
            Path keyFile = Paths.get(KEY_FILE);
            if (Files.exists(keyFile)) {
                byte[] keyBytes = Files.readAllBytes(keyFile);
                encryptionKey = new SecretKeySpec(keyBytes, "AES");
                logger.info("Loaded existing encryption key");
            } else {
                // Generate new key and save it
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                encryptionKey = keyGen.generateKey();
                Files.write(keyFile, encryptionKey.getEncoded(), StandardOpenOption.CREATE);
                logger.info("Generated new encryption key");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize encryption key", e);
            throw e;
        }
    }
    
    public byte[] encryptGazeData(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
        
        byte[] encryptedData = cipher.doFinal(data);
        
        byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
        System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
        
        return encryptedWithIv;
    }
    
    public byte[] decryptGazeData(byte[] encryptedData) throws Exception {
        if (encryptedData.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw new IllegalArgumentException("Encrypted data too short");
        }
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];
        
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
        
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);
        
        return cipher.doFinal(cipherText);
    }
    
    public String anonymizeSessionId(String sessionId) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sessionId.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return "anonymous-" + System.currentTimeMillis();
        }
    }
    
    public void logAccess(String userId, String action) {
        String timestamp = java.time.Instant.now().toString();
        String logEntry = timestamp + " - " + action;
        auditLog.put(userId + "-" + timestamp, logEntry);
        
        logger.debug("[AUDIT] User {} performed: {}", anonymizeSessionId(userId), action);
    }
    
    public void logDataCollection(String sessionId, String dataType, long timestamp) {
        String anonymizedId = anonymizeSessionId(sessionId);
        logAccess(anonymizedId, "DATA_COLLECTION: " + dataType + " at " + timestamp);
    }
    
    public void logDataDeletion(String sessionId, String reason) {
        String anonymizedId = anonymizeSessionId(sessionId);
        logAccess(anonymizedId, "DATA_DELETION: " + reason);
    }
    
    public Map<String, String> getAuditLog() {
        return new ConcurrentHashMap<>(auditLog);
    }
    
    public void clearAuditLog() {
        auditLog.clear();
        logger.info("Audit log cleared");
        logAccess("system", "AUDIT_LOG_CLEARED");
    }
}