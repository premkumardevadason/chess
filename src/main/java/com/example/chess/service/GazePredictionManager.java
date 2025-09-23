package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Gaze prediction manager with real-time cancellation system
 * Manages prediction lifecycle and coordinates with precomputation service
 */
@Service
public class GazePredictionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(GazePredictionManager.class);
    
    private volatile MovePrediction currentPrediction = null;
    private volatile long lastPredictionTime = 0;
    private final Object predictionLock = new Object();
    
    // Confidence decay parameters
    private static final double CONFIDENCE_DECAY_RATE = 0.1; // 10% per 100ms
    private static final long PREDICTION_TIMEOUT_MS = 1000; // 1 second
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;
    
    @Autowired
    private MultiAgentPrecomputationService precomputationService;
    
    // MovePredictionAI will be implemented when needed
    // private MovePredictionAI predictionAI;
    
    @Autowired
    private PieceIntentionAnalyzer pieceIntentionAnalyzer;
    
    @Autowired
    private com.example.chess.WebSocketController webSocketController;
    
    /**
     * Processes new gaze data and manages prediction lifecycle
     * Enhanced with piece intention analysis
     */
    public void processGazeUpdate(GazePattern newPattern) {
        synchronized (predictionLock) {
            // REQUIREMENT 4: Analyze piece intention first
            String focusedSquare = newPattern.getFocusedSquare();
            if (focusedSquare != null) {
                PieceIntentionAnalyzer.GazePattern gazePattern = new PieceIntentionAnalyzer.GazePattern();
                PieceIntentionAnalyzer.PieceIntention intention = 
                    pieceIntentionAnalyzer.analyzePieceIntention(focusedSquare, gazePattern);
                
                // Send intention analysis to frontend
                Map<String, Object> intentionData = new HashMap<>();
                intentionData.put("square", intention.square);
                intentionData.put("piece", intention.piece);
                intentionData.put("intention", intention.intention);
                intentionData.put("predictedMoves", intention.predictedMoves);
                
                // Use the existing WebSocketController's messaging template
                // webSocketController.broadcastMessage("/topic/pieceIntention", intentionData);
                logger.debug("Would send intention data: {}", intentionData);
            }
            
            // Placeholder for move prediction - will be implemented with actual AI
            MovePrediction newPrediction = new MovePrediction("e2-e4", 0.7);
            long currentTime = System.currentTimeMillis();
            
            // Check if prediction has changed significantly
            if (shouldUpdatePrediction(newPrediction, currentTime)) {
                
                // Cancel previous precomputation if different move
                if (currentPrediction != null && 
                    !newPrediction.move.equals(currentPrediction.move)) {
                    
                    logger.info("Gaze shifted: {} → {} (confidence: {:.2f})", 
                        currentPrediction.move, newPrediction.move, newPrediction.confidence);
                    
                    // Cancel ongoing precomputation
                    precomputationService.cancelCurrentComputation();
                }
                
                // Start new precomputation if confidence is high enough
                if (newPrediction.confidence > MIN_CONFIDENCE_THRESHOLD) {
                    currentPrediction = newPrediction;
                    lastPredictionTime = currentTime;
                    
                    // Trigger new precomputation
                    precomputationService.preComputeAllResponses(
                        newPrediction.move, getCurrentPosition());
                }
            } else {
                // Update confidence with decay if same move
                updatePredictionConfidence(currentTime);
            }
        }
    }
    
    private boolean shouldUpdatePrediction(MovePrediction newPrediction, long currentTime) {
        if (currentPrediction == null) {
            return true; // First prediction
        }
        
        // Different move with sufficient confidence
        if (!newPrediction.move.equals(currentPrediction.move) && 
            newPrediction.confidence > MIN_CONFIDENCE_THRESHOLD) {
            return true;
        }
        
        // Same move but significantly higher confidence
        if (newPrediction.move.equals(currentPrediction.move) && 
            newPrediction.confidence > currentPrediction.confidence + 0.1) {
            return true;
        }
        
        // Prediction timeout - need refresh
        if (currentTime - lastPredictionTime > PREDICTION_TIMEOUT_MS) {
            return true;
        }
        
        return false;
    }
    
    private void updatePredictionConfidence(long currentTime) {
        if (currentPrediction != null) {
            long timeDelta = currentTime - lastPredictionTime;
            double decayFactor = Math.exp(-CONFIDENCE_DECAY_RATE * timeDelta / 100.0);
            
            // Create new prediction with updated confidence
            currentPrediction = new MovePrediction(currentPrediction.move, 
                currentPrediction.confidence * decayFactor);
            
            // Cancel if confidence drops too low
            if (currentPrediction.confidence < MIN_CONFIDENCE_THRESHOLD) {
                logger.info("Prediction confidence decayed below threshold: {:.2f}", 
                    currentPrediction.confidence);
                precomputationService.cancelCurrentComputation();
                currentPrediction = null;
            }
        }
    }
    
    public MovePrediction getCurrentPrediction() {
        synchronized (predictionLock) {
            return currentPrediction;
        }
    }
    
    public void recordSuccessfulPrediction(String actualMove) {
        logger.info("Successful prediction: {}", actualMove);
        // Update prediction accuracy metrics
    }
    
    public void recordMissedPrediction(String actualMove) {
        logger.info("Missed prediction: {}", actualMove);
        // Update prediction accuracy metrics
    }
    
    private String getCurrentPosition() {
        // Get current chess position in FEN format
        return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"; // Placeholder
    }
    
    public static class MovePrediction {
        public final String move;
        public final double confidence;
        
        public MovePrediction(String move, double confidence) {
            this.move = move;
            this.confidence = confidence;
        }
    }
    
    public static class GazePattern {
        // Placeholder for gaze pattern data
        public String getFocusedSquare() {
            return "e2"; // Placeholder
        }
    }
}
