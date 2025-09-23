package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consent management service for GDPR compliance
 * Handles user consent for gaze data collection
 */
@Service
public class ConsentManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsentManager.class);
    
    private final Map<String, UserConsent> userConsents = new ConcurrentHashMap<>();
    private final Map<String, ConsentRequest> pendingConsents = new ConcurrentHashMap<>();
    
    public enum ConsentType {
        GAZE_DATA_COLLECTION,
        DATA_ANALYTICS,
        PERFORMANCE_MONITORING
    }
    
    public boolean requestConsent(String sessionId, ConsentType type) {
        // Display consent UI and wait for user response
        ConsentRequest request = new ConsentRequest(sessionId, type, System.currentTimeMillis());
        
        // Store pending request
        pendingConsents.put(sessionId, request);
        
        logger.info("Consent requested for session {}: {}", sessionId, type);
        return false; // Pending user response
    }
    
    public void recordConsent(String sessionId, ConsentType type, boolean granted) {
        UserConsent consent = userConsents.computeIfAbsent(sessionId, 
            k -> new UserConsent(sessionId));
            
        consent.setConsent(type, granted, System.currentTimeMillis());
        
        if (granted) {
            logger.info("User consent granted for {}: {}", sessionId, type);
        } else {
            logger.info("User consent denied for {}: {}", sessionId, type);
        }
    }
    
    public boolean hasConsent(String sessionId, ConsentType type) {
        UserConsent consent = userConsents.get(sessionId);
        return consent != null && consent.hasConsent(type);
    }
    
    public boolean hasValidConsent(String sessionId, ConsentType type) {
        UserConsent consent = userConsents.get(sessionId);
        if (consent == null) {
            return false;
        }
        
        // Check if consent is still valid (not expired)
        return consent.hasConsent(type) && !consent.isExpired();
    }
    
    public void revokeConsent(String sessionId, ConsentType type) {
        UserConsent consent = userConsents.get(sessionId);
        if (consent != null) {
            consent.revokeConsent(type);
            logger.info("Consent revoked for session {}: {}", sessionId, type);
        }
    }
    
    public void clearConsent(String sessionId) {
        userConsents.remove(sessionId);
        pendingConsents.remove(sessionId);
        logger.info("All consent cleared for session: {}", sessionId);
    }
    
    public static class UserConsent {
        private final String sessionId;
        private final Map<ConsentType, ConsentRecord> consents = new ConcurrentHashMap<>();
        
        public UserConsent(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void setConsent(ConsentType type, boolean granted, long timestamp) {
            consents.put(type, new ConsentRecord(granted, timestamp));
        }
        
        public boolean hasConsent(ConsentType type) {
            ConsentRecord record = consents.get(type);
            return record != null && record.granted;
        }
        
        public void revokeConsent(ConsentType type) {
            consents.remove(type);
        }
        
        public boolean isExpired() {
            // Consent expires after 24 hours
            long expirationTime = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
            long currentTime = System.currentTimeMillis();
            
            return consents.values().stream()
                .anyMatch(record -> currentTime - record.timestamp > expirationTime);
        }
    }
    
    public static class ConsentRecord {
        public final boolean granted;
        public final long timestamp;
        
        public ConsentRecord(boolean granted, long timestamp) {
            this.granted = granted;
            this.timestamp = timestamp;
        }
    }
    
    public static class ConsentRequest {
        public final String sessionId;
        public final ConsentType type;
        public final long timestamp;
        
        public ConsentRequest(String sessionId, ConsentType type, long timestamp) {
            this.sessionId = sessionId;
            this.type = type;
            this.timestamp = timestamp;
        }
    }
}
