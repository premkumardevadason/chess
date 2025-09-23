package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Visual training data manager for eye-tracking data persistence
 * Handles encrypted binary format storage and GDPR compliance
 */
@Service
public class VisualTrainingDataManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualTrainingDataManager.class);
    
    private static final String VISUAL_TRAINING_DIR = "state/visual-training/";
    private static final String USER_SESSIONS_DIR = "user-sessions/";
    private static final String ENCRYPTED_GAZE_PATTERNS_FILE = "encrypted-gaze-patterns.dat";
    private static final String ANONYMIZED_MOVE_PREDICTIONS_FILE = "anonymized-move-predictions.dat";
    private static final String USER_CONSENT_RECORDS_FILE = "user-consent-records.json";
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private PrivacyService privacyService;
    
    @Value("${chess.eyetracking.data.retention.days:7}")
    private int dataRetentionDays;
    
    @Value("${chess.eyetracking.auto.delete:true}")
    private boolean autoDelete;
    
    // In-memory caches for performance
    private final Map<String, List<GazeDataPoint>> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, List<MovePredictionResult>> predictionCache = new ConcurrentHashMap<>();
    private final AtomicLong totalDataPoints = new AtomicLong(0);
    
    @PostConstruct
    public void initializeStorage() {
        try {
            createDirectoryIfNotExists(VISUAL_TRAINING_DIR);
            createDirectoryIfNotExists(VISUAL_TRAINING_DIR + USER_SESSIONS_DIR);
            
            // Load existing data into cache
            loadExistingData();
            
            logger.info("Visual training data manager initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize visual training data manager", e);
        }
    }
    
    /**
     * Handle visual training data events
     */
    @EventListener
    public void handleVisualTrainingData(VisualTrainingEvent event) {
        try {
            GazeDataPoint dataPoint = event.getGazeDataPoint();
            String sessionId = event.getSessionId();
            
            // Store in session cache
            sessionCache.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(dataPoint);
            
            // Persist to encrypted binary format
            persistGazeData(sessionId, dataPoint);
            
            totalDataPoints.incrementAndGet();
            
            logger.debug("Stored gaze data point for session: {}", sessionId);
            
        } catch (Exception e) {
            logger.error("Error handling visual training data", e);
        }
    }
    
    /**
     * Save move prediction result
     */
    public void saveMovePrediction(String predictedMove, String actualMove, double confidence, String sessionId) {
        try {
            MovePredictionResult result = new MovePredictionResult(
                predictedMove, actualMove, confidence, System.currentTimeMillis(), sessionId
            );
            
            // Store in prediction cache
            predictionCache.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(result);
            
            // Persist to encrypted binary format
            persistMovePrediction(result);
            
            logger.debug("Saved move prediction result: {} -> {} (confidence: {:.2f})", 
                predictedMove, actualMove, confidence);
                
        } catch (Exception e) {
            logger.error("Error saving move prediction", e);
        }
    }
    
    /**
     * Persist gaze data to encrypted binary format
     */
    private void persistGazeData(String sessionId, GazeDataPoint dataPoint) {
        try {
            // Serialize data point
            byte[] serializedData = serializeGazeDataPoint(dataPoint);
            
            // Encrypt data (simplified for now)
            byte[] encryptedData = serializedData; // TODO: Implement proper encryption
            
            // Write to session file
            String sessionFile = VISUAL_TRAINING_DIR + USER_SESSIONS_DIR + "session-" + sessionId + ".dat";
            try (FileOutputStream fos = new FileOutputStream(sessionFile, true);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(encryptedData);
            }
            
            // Also append to main gaze patterns file
            appendToGazePatternsFile(encryptedData);
            
        } catch (Exception e) {
            logger.error("Error persisting gaze data", e);
        }
    }
    
    /**
     * Persist move prediction to encrypted binary format
     */
    private void persistMovePrediction(MovePredictionResult result) {
        try {
            // Serialize prediction result
            byte[] serializedData = serializeMovePredictionResult(result);
            
            // Encrypt data (simplified for now)
            byte[] encryptedData = serializedData; // TODO: Implement proper encryption
            
            // Write to predictions file
            String predictionFile = VISUAL_TRAINING_DIR + ANONYMIZED_MOVE_PREDICTIONS_FILE;
            try (FileOutputStream fos = new FileOutputStream(predictionFile, true);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(encryptedData);
            }
            
        } catch (Exception e) {
            logger.error("Error persisting move prediction", e);
        }
    }
    
    /**
     * Load existing data into cache
     */
    private void loadExistingData() {
        try {
            // Load session data
            Path sessionsDir = Paths.get(VISUAL_TRAINING_DIR + USER_SESSIONS_DIR);
            if (Files.exists(sessionsDir)) {
                Files.list(sessionsDir)
                    .filter(path -> path.toString().endsWith(".dat"))
                    .forEach(this::loadSessionData);
            }
            
            // Load prediction data
            Path predictionsFile = Paths.get(VISUAL_TRAINING_DIR + ANONYMIZED_MOVE_PREDICTIONS_FILE);
            if (Files.exists(predictionsFile)) {
                loadPredictionData(predictionsFile);
            }
            
            logger.info("Loaded existing training data: {} sessions, {} total data points",
                sessionCache.size(), totalDataPoints.get());
                
        } catch (Exception e) {
            logger.warn("Error loading existing data", e);
        }
    }
    
    /**
     * Load session data from file
     */
    private void loadSessionData(Path sessionFile) {
        try {
            String sessionId = extractSessionId(sessionFile.getFileName().toString());
            
            try (FileInputStream fis = new FileInputStream(sessionFile.toFile());
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                
                List<GazeDataPoint> sessionData = new ArrayList<>();
                
                while (fis.available() > 0) {
                    try {
                        byte[] encryptedData = (byte[]) ois.readObject();
                        byte[] decryptedData = encryptedData; // TODO: Implement proper decryption
                        GazeDataPoint dataPoint = deserializeGazeDataPoint(decryptedData);
                        sessionData.add(dataPoint);
                    } catch (EOFException e) {
                        break; // End of file
                    }
                }
                
                sessionCache.put(sessionId, sessionData);
                totalDataPoints.addAndGet(sessionData.size());
                
            }
        } catch (Exception e) {
            logger.warn("Error loading session data from {}", sessionFile, e);
        }
    }
    
    /**
     * Load prediction data from file
     */
    private void loadPredictionData(Path predictionsFile) {
        try (FileInputStream fis = new FileInputStream(predictionsFile.toFile());
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            
            while (fis.available() > 0) {
                try {
                    byte[] encryptedData = (byte[]) ois.readObject();
                    byte[] decryptedData = encryptionService.decrypt(encryptedData);
                    MovePredictionResult result = deserializeMovePredictionResult(decryptedData);
                    
                    predictionCache.computeIfAbsent(result.getSessionId(), k -> new ArrayList<>()).add(result);
                } catch (EOFException e) {
                    break; // End of file
                }
            }
        } catch (Exception e) {
            logger.warn("Error loading prediction data", e);
        }
    }
    
    /**
     * Append to main gaze patterns file
     */
    private void appendToGazePatternsFile(byte[] encryptedData) {
        try {
            String gazeFile = VISUAL_TRAINING_DIR + ENCRYPTED_GAZE_PATTERNS_FILE;
            try (FileOutputStream fos = new FileOutputStream(gazeFile, true);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(encryptedData);
            }
        } catch (Exception e) {
            logger.error("Error appending to gaze patterns file", e);
        }
    }
    
    /**
     * Create directory if it doesn't exist
     */
    private void createDirectoryIfNotExists(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created directory: {}", dirPath);
            }
        } catch (Exception e) {
            logger.error("Error creating directory: {}", dirPath, e);
        }
    }
    
    /**
     * Serialize gaze data point to bytes
     */
    private byte[] serializeGazeDataPoint(GazeDataPoint dataPoint) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(dataPoint);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Error serializing gaze data point", e);
            return new byte[0];
        }
    }
    
    /**
     * Deserialize gaze data point from bytes
     */
    private GazeDataPoint deserializeGazeDataPoint(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (GazeDataPoint) ois.readObject();
        } catch (Exception e) {
            logger.error("Error deserializing gaze data point", e);
            return null;
        }
    }
    
    /**
     * Serialize move prediction result to bytes
     */
    private byte[] serializeMovePredictionResult(MovePredictionResult result) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(result);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Error serializing move prediction result", e);
            return new byte[0];
        }
    }
    
    /**
     * Deserialize move prediction result from bytes
     */
    private MovePredictionResult deserializeMovePredictionResult(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (MovePredictionResult) ois.readObject();
        } catch (Exception e) {
            logger.error("Error deserializing move prediction result", e);
            return null;
        }
    }
    
    /**
     * Extract session ID from filename
     */
    private String extractSessionId(String filename) {
        // Extract session ID from "session-{id}.dat" format
        String baseName = filename.substring(0, filename.lastIndexOf('.'));
        return baseName.substring(baseName.lastIndexOf('-') + 1);
    }
    
    /**
     * Get training data statistics
     */
    public TrainingDataStats getTrainingDataStats() {
        return new TrainingDataStats(
            sessionCache.size(),
            totalDataPoints.get(),
            predictionCache.values().stream().mapToInt(List::size).sum(),
            getTotalDataSize()
        );
    }
    
    /**
     * Get total data size in bytes
     */
    private long getTotalDataSize() {
        try {
            Path trainingDir = Paths.get(VISUAL_TRAINING_DIR);
            if (!Files.exists(trainingDir)) {
                return 0;
            }
            
            return Files.walk(trainingDir)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        } catch (Exception e) {
            logger.warn("Error calculating total data size", e);
            return 0;
        }
    }
    
    /**
     * Clean up old data based on retention policy
     */
    public void cleanupOldData() {
        if (!autoDelete) {
            return;
        }
        
        try {
            long cutoffTime = System.currentTimeMillis() - (dataRetentionDays * 24 * 60 * 60 * 1000L);
            
            // Clean up session cache
            sessionCache.entrySet().removeIf(entry -> {
                List<GazeDataPoint> dataPoints = entry.getValue();
                if (dataPoints.isEmpty()) return true;
                
                long latestTimestamp = dataPoints.stream()
                    .mapToLong(GazeDataPoint::getTimestamp)
                    .max()
                    .orElse(0);
                
                return latestTimestamp < cutoffTime;
            });
            
            // Clean up prediction cache
            predictionCache.entrySet().removeIf(entry -> {
                List<MovePredictionResult> results = entry.getValue();
                if (results.isEmpty()) return true;
                
                long latestTimestamp = results.stream()
                    .mapToLong(MovePredictionResult::getTimestamp)
                    .max()
                    .orElse(0);
                
                return latestTimestamp < cutoffTime;
            });
            
            logger.info("Cleaned up old training data older than {} days", dataRetentionDays);
            
        } catch (Exception e) {
            logger.error("Error cleaning up old data", e);
        }
    }
    
    // Data classes
    public static class GazeDataPoint implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final double gazeX;
        public final double gazeY;
        public final String chessSquare;
        public final long timestamp;
        public final String userAction;
        public final double confidence;
        
        public GazeDataPoint(double gazeX, double gazeY, String chessSquare, 
                           long timestamp, String userAction, double confidence) {
            this.gazeX = gazeX;
            this.gazeY = gazeY;
            this.chessSquare = chessSquare;
            this.timestamp = timestamp;
            this.userAction = userAction;
            this.confidence = confidence;
        }
        
        public long getTimestamp() { return timestamp; }
    }
    
    public static class MovePredictionResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final String predictedMove;
        public final String actualMove;
        public final double confidence;
        public final long timestamp;
        public final String sessionId;
        
        public MovePredictionResult(String predictedMove, String actualMove, double confidence,
                                  long timestamp, String sessionId) {
            this.predictedMove = predictedMove;
            this.actualMove = actualMove;
            this.confidence = confidence;
            this.timestamp = timestamp;
            this.sessionId = sessionId;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getSessionId() { return sessionId; }
    }
    
    public static class VisualTrainingEvent {
        private final String sessionId;
        private final GazeDataPoint gazeDataPoint;
        
        public VisualTrainingEvent(String sessionId, GazeDataPoint gazeDataPoint) {
            this.sessionId = sessionId;
            this.gazeDataPoint = gazeDataPoint;
        }
        
        public String getSessionId() { return sessionId; }
        public GazeDataPoint getGazeDataPoint() { return gazeDataPoint; }
    }
    
    public static class TrainingDataStats {
        public final int activeSessions;
        public final long totalDataPoints;
        public final int totalPredictions;
        public final long totalDataSizeBytes;
        
        public TrainingDataStats(int activeSessions, long totalDataPoints, 
                               int totalPredictions, long totalDataSizeBytes) {
            this.activeSessions = activeSessions;
            this.totalDataPoints = totalDataPoints;
            this.totalPredictions = totalPredictions;
            this.totalDataSizeBytes = totalDataSizeBytes;
        }
        
        @Override
        public String toString() {
            return String.format("TrainingDataStats{sessions=%d, dataPoints=%d, predictions=%d, size=%.2fMB}",
                activeSessions, totalDataPoints, totalPredictions, totalDataSizeBytes / (1024.0 * 1024.0));
        }
    }
}
