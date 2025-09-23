package com.example.chess.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data retention manager for GDPR compliance
 * Handles automatic cleanup of expired gaze data
 */
@Service
public class DataRetentionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DataRetentionManager.class);
    
    @Value("${chess.eyetracking.data.retention.days:7}")
    private int retentionDays;
    
    private static final String VISUAL_TRAINING_DIR = "state/visual-training/";
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredData() {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L);
        
        try {
            List<String> expiredDataIds = findExpiredData(cutoffTime);
            
            for (String dataId : expiredDataIds) {
                deleteData(dataId);
                logger.info("Deleted expired gaze data: {}", dataId);
            }
            
            if (!expiredDataIds.isEmpty()) {
                logger.info("Cleanup completed: {} expired data files deleted", expiredDataIds.size());
            }
        } catch (Exception e) {
            logger.error("Error during data cleanup", e);
        }
    }
    
    public void anonymizeHistoricalData() {
        // Anonymize data older than 24 hours
        try {
            List<String> dataToAnonymize = findDataOlderThan(24 * 60 * 60 * 1000L);
            
            for (String dataId : dataToAnonymize) {
                anonymizeData(dataId);
            }
            
            if (!dataToAnonymize.isEmpty()) {
                logger.info("Anonymization completed: {} files processed", dataToAnonymize.size());
            }
        } catch (Exception e) {
            logger.error("Error during data anonymization", e);
        }
    }
    
    public String storeWithRetention(byte[] data, String sessionId) {
        try {
            // Create directory if it doesn't exist
            Path dir = Paths.get(VISUAL_TRAINING_DIR + "user-sessions/");
            Files.createDirectories(dir);
            
            // Generate unique data ID
            String dataId = "data-" + System.currentTimeMillis() + "-" + sessionId.hashCode();
            String fileName = dataId + ".dat";
            Path filePath = dir.resolve(fileName);
            
            // Write encrypted data
            Files.write(filePath, data);
            
            logger.debug("Stored gaze data with retention: {}", dataId);
            return dataId;
        } catch (IOException e) {
            logger.error("Failed to store data with retention", e);
            return null;
        }
    }
    
    private List<String> findExpiredData(long cutoffTime) throws IOException {
        Path dir = Paths.get(VISUAL_TRAINING_DIR + "user-sessions/");
        if (!Files.exists(dir)) {
            return List.of();
        }
        
        return Files.list(dir)
            .filter(Files::isRegularFile)
            .filter(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                } catch (IOException e) {
                    logger.warn("Could not check modification time for: {}", path, e);
                    return false;
                }
            })
            .map(path -> path.getFileName().toString())
            .collect(Collectors.toList());
    }
    
    private List<String> findDataOlderThan(long ageMs) throws IOException {
        long cutoffTime = System.currentTimeMillis() - ageMs;
        return findExpiredData(cutoffTime);
    }
    
    private void deleteData(String dataId) {
        try {
            Path filePath = Paths.get(VISUAL_TRAINING_DIR + "user-sessions/" + dataId + ".dat");
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.error("Failed to delete data: {}", dataId, e);
        }
    }
    
    private void anonymizeData(String dataId) {
        try {
            Path filePath = Paths.get(VISUAL_TRAINING_DIR + "user-sessions/" + dataId + ".dat");
            if (Files.exists(filePath)) {
                // Read, anonymize, and write back
                byte[] data = Files.readAllBytes(filePath);
                byte[] anonymizedData = anonymizeGazeData(data);
                Files.write(filePath, anonymizedData);
                
                logger.debug("Anonymized data: {}", dataId);
            }
        } catch (IOException e) {
            logger.error("Failed to anonymize data: {}", dataId, e);
        }
    }
    
    private byte[] anonymizeGazeData(byte[] data) {
        // Simple anonymization by adding noise to coordinates
        // In a real implementation, this would be more sophisticated
        byte[] anonymized = data.clone();
        
        // Add small random noise to make data less identifiable
        for (int i = 0; i < anonymized.length; i += 4) {
            if (i + 3 < anonymized.length) {
                // Add small random value to coordinate data
                int randomOffset = (int) (Math.random() * 10 - 5); // -5 to +5
                anonymized[i] = (byte) ((anonymized[i] + randomOffset) % 256);
            }
        }
        
        return anonymized;
    }
}
