package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Privacy-compliant data collection service
 * Handles encrypted gaze data collection with GDPR compliance
 */
@Service
public class PrivacyCompliantDataCollectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrivacyCompliantDataCollectionService.class);
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private ConsentManager consentManager;
    
    @Autowired
    private DataRetentionManager retentionManager;
    
    @Autowired
    private PrivacyService privacyService;
    
    public void collectGazeData(String sessionId, Map<String, Object> rawData) {
        // 1. Verify user consent
        if (!consentManager.hasConsent(sessionId, ConsentManager.ConsentType.GAZE_DATA_COLLECTION)) {
            logger.warn("Gaze data collection attempted without consent for session: {}", sessionId);
            return;
        }
        
        // 2. Anonymize data immediately
        AnonymizedGazeData anonymizedData = anonymizeGazeData(rawData);
        
        // 3. Encrypt sensitive data
        PrivacyCompliantDataCollectionService.EncryptedGazeData encryptedData = encryptionService.encrypt(anonymizedData);
        
        // 4. Store with retention policy
        String dataId = retentionManager.storeWithRetention(encryptedData.getData(), sessionId);
        
        // 5. Log data processing
        privacyService.logDataCollection(sessionId, dataId, anonymizedData.getTimestamp());
    }
    
    private AnonymizedGazeData anonymizeGazeData(Map<String, Object> rawData) {
        // Extract and anonymize gaze data
        Double gazeX = (Double) rawData.get("gazeX");
        Double gazeY = (Double) rawData.get("gazeY");
        String square = (String) rawData.get("square");
        Long timestamp = (Long) rawData.get("timestamp");
        String userAction = (String) rawData.get("userAction");
        
        // Anonymize coordinates by adding noise
        double anonymizedX = gazeX + (Math.random() - 0.5) * 10; // ±5 pixel noise
        double anonymizedY = gazeY + (Math.random() - 0.5) * 10; // ±5 pixel noise
        
        return new AnonymizedGazeData(anonymizedX, anonymizedY, square, timestamp, userAction);
    }
    
    public static class AnonymizedGazeData {
        public final double gazeX;
        public final double gazeY;
        public final String square;
        public final long timestamp;
        public final String userAction;
        
        public AnonymizedGazeData(double gazeX, double gazeY, String square, long timestamp, String userAction) {
            this.gazeX = gazeX;
            this.gazeY = gazeY;
            this.square = square;
            this.timestamp = timestamp;
            this.userAction = userAction;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    public static class EncryptedGazeData {
        private final byte[] data;
        
        public EncryptedGazeData(byte[] data) {
            this.data = data;
        }
        
        public byte[] getData() {
            return data;
        }
    }
}
