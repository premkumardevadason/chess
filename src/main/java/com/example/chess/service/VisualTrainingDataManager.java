package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.geom.Point2D;
import javax.annotation.PostConstruct;

@Service
public class VisualTrainingDataManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualTrainingDataManager.class);
    private static final String VISUAL_TRAINING_DIR = "state/visual-training/";
    private static final String USER_SESSIONS_DIR = VISUAL_TRAINING_DIR + "user-sessions/";
    
    @Autowired
    private PrivacyService privacyService;
    
    private final Map<String, List<GazeDataPoint>> sessionData = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeStorage() {
        try {
            createDirectoryIfNotExists(VISUAL_TRAINING_DIR);
            createDirectoryIfNotExists(USER_SESSIONS_DIR);
            logger.info("Visual training data storage initialized");
        } catch (Exception e) {
            System.err.println("Error initializing storage: " + e.getMessage());
        }
    }
    
    public void saveGazeData(String sessionId, Point2D gazePoint, String chessSquare, long timestamp) {
        saveGazeDataInternal(sessionId, gazePoint, chessSquare, timestamp);
    }
    
    public void saveMovePrediction(String sessionId, String predictedMove, String actualMove, double confidence, String method) {
        System.out.println("Saving prediction: " + predictedMove + " -> " + actualMove + " (" + confidence + ")");
    }
    
    public void saveMovePrediction(String sessionId, String predictedMove, double confidence, String method) {
        System.out.println("Saving prediction: " + predictedMove + " (" + confidence + ")");
    }
    
    private void saveGazeDataInternal(String sessionId, Point2D gazePoint, String chessSquare, long timestamp) {
        try {
            GazeDataPoint dataPoint = new GazeDataPoint(
                gazePoint, chessSquare, timestamp, System.currentTimeMillis()
            );
            
            sessionData.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(dataPoint);
            saveEncryptedGazeData(sessionId, dataPoint);
            
            if (privacyService != null) {
                privacyService.logDataCollection(sessionId, "GAZE_POINT", timestamp);
            }
            
        } catch (Exception e) {
            System.err.println("Error saving gaze data: " + e.getMessage());
        }
    }
    
    private void saveEncryptedGazeData(String sessionId, GazeDataPoint dataPoint) throws Exception {
        String anonymizedId = privacyService.anonymizeSessionId(sessionId);
        String sessionFile = USER_SESSIONS_DIR + "session-" + anonymizedId + ".dat";
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(dataPoint);
        }
        
        byte[] encryptedData = privacyService.encryptGazeData(baos.toByteArray());
        
        try (FileOutputStream fos = new FileOutputStream(sessionFile, true)) {
            fos.write(intToBytes(encryptedData.length));
            fos.write(encryptedData);
        }
    }
    
    public void deleteSessionData(String sessionId, String reason) {
        try {
            String anonymizedId = privacyService.anonymizeSessionId(sessionId);
            String sessionFile = USER_SESSIONS_DIR + "session-" + anonymizedId + ".dat";
            
            sessionData.remove(sessionId);
            Files.deleteIfExists(Paths.get(sessionFile));
            
            if (privacyService != null) {
                privacyService.logDataDeletion(sessionId, reason);
            }
            
            System.out.println("Deleted session data for: " + anonymizedId + " (" + reason + ")");
            
        } catch (Exception e) {
            System.err.println("Error deleting session data: " + e.getMessage());
        }
    }
    
    private void createDirectoryIfNotExists(String dirPath) throws IOException {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }
    
    private byte[] intToBytes(int value) {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }
    
    public static class GazeDataPoint implements Serializable {
        public final Point2D gazePoint;
        public final String chessSquare;
        public final long gazeTimestamp;
        public final long recordTimestamp;
        
        public GazeDataPoint(Point2D gazePoint, String chessSquare, long gazeTimestamp, long recordTimestamp) {
            this.gazePoint = gazePoint;
            this.chessSquare = chessSquare;
            this.gazeTimestamp = gazeTimestamp;
            this.recordTimestamp = recordTimestamp;
        }
    }
}