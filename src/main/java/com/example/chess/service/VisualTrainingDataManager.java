package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.geom.Point2D;
import javax.annotation.PostConstruct;

@Service
public class VisualTrainingDataManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualTrainingDataManager.class);
    // No session-based training - all calibration is global and reusable
    
    @PostConstruct
    public void initializeStorage() {
        logger.info("Visual training data storage initialized - using global calibration only");
    }
    
    // No session-based data collection - all calibration is global
    public void saveGazeData(String sessionId, Point2D gazePoint, String chessSquare, long timestamp) {
        // No-op - using global calibration only
    }
    
    public void saveMovePrediction(String sessionId, String predictedMove, String actualMove, double confidence, String method) {
        // No-op - using global calibration only
    }
    
    public void saveMovePrediction(String sessionId, String predictedMove, double confidence, String method) {
        // No-op - using global calibration only
    }
    
    // Removed session-based data structures - using global calibration only
}