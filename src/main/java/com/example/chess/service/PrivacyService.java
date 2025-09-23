package com.example.chess.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class PrivacyService {
    
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private SecretKey encryptionKey;
    private final Map<String, String> auditLog = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void init() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        encryptionKey = keyGen.generateKey();
        
        System.out.println("Privacy service initialized with AES-256 encryption");
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
        
        System.out.println("[AUDIT] User " + anonymizeSessionId(userId) + " performed: " + action);
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
        logAccess("system", "AUDIT_LOG_CLEARED");
    }
}