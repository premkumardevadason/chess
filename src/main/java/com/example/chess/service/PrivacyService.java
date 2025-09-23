package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Privacy service for handling encrypted gaze data and audit logging
 * Implements GDPR compliance requirements
 */
@Service
public class PrivacyService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrivacyService.class);
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private SecretKey encryptionKey;
    private SecureRandom secureRandom;

    @PostConstruct
    public void init() throws Exception {
        // Load or generate encryption key securely
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        encryptionKey = keyGen.generateKey();
        secureRandom = new SecureRandom();
        
        logger.info("PrivacyService initialized with AES-256 encryption");
    }

    public byte[] encryptGazeData(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
        byte[] encryptedData = cipher.doFinal(data);
        
        // Prepend IV to encrypted data
        byte[] result = new byte[GCM_IV_LENGTH + encryptedData.length];
        System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, 0, result, GCM_IV_LENGTH, encryptedData.length);
        
        return result;
    }

    public byte[] decryptGazeData(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        
        // Extract IV from encrypted data
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
        
        byte[] actualEncryptedData = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, GCM_IV_LENGTH, actualEncryptedData, 0, actualEncryptedData.length);
        
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
        return cipher.doFinal(actualEncryptedData);
    }

    public void logAccess(String userId, String action) {
        // Log to secure audit trail
        logger.info("AUDIT: User {} performed action: {}", userId, action);
    }
    
    public void logDataCollection(String sessionId, String dataId, long timestamp) {
        logger.info("AUDIT: DATA_COLLECTION|{}|{}|{}", sessionId, dataId, timestamp);
    }
    
    public void logDataAccess(String userId, String dataId, String purpose) {
        logger.info("AUDIT: DATA_ACCESS|{}|{}|{}", userId, dataId, purpose);
    }
    
    public void logDataDeletion(String userId, String dataId) {
        logger.info("AUDIT: DATA_DELETION|{}|{}", userId, dataId);
    }
    
    public void logConsentChange(String sessionId, String consentType, boolean granted) {
        logger.info("AUDIT: CONSENT_CHANGE|{}|{}|{}", sessionId, consentType, granted);
    }
}
