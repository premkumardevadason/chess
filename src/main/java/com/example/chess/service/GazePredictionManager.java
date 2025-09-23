package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    
    @Autowired
    private MovePredictionAI predictionAI;
    
    @Autowired
    private PieceIntentionAnalyzer pieceIntentionAnalyzer;
    
    @Autowired
    private com.example.chess.WebSocketController webSocketController;
    
    @Autowired
    private ChessGame chessGame;
    
    @Autowired
    private VisualTrainingDataManager trainingDataManager;
    
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
            
            // Convert GazePattern to MovePredictionAI.GazeSequence
            MovePredictionAI.GazeSequence gazeSequence = convertToGazeSequence(newPattern);
            
            // Get prediction from LSTM AI
            MovePredictionAI.MovePrediction aiPrediction = predictionAI.predictMove(gazeSequence);
            MovePrediction newPrediction = new MovePrediction(aiPrediction.move, aiPrediction.confidence);
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
    
    /**
     * Record a successful prediction for training
     */
    public void recordSuccessfulPrediction(String actualMove) {
        if (currentPrediction != null) {
            // Train the LSTM AI on successful prediction
            MovePredictionAI.GazeSequence gazeSequence = getCurrentGazeSequence();
            if (gazeSequence != null) {
                predictionAI.trainOnUserMove(gazeSequence, actualMove);
            }
            
            // Save prediction result for analysis
            trainingDataManager.saveMovePrediction(
                currentPrediction.move, actualMove, currentPrediction.confidence, "current-session"
            );
            
            logger.info("Recorded successful prediction: {} -> {} (confidence: {:.2f})",
                currentPrediction.move, actualMove, currentPrediction.confidence);
        }
    }
    
    /**
     * Record a missed prediction for training
     */
    public void recordMissedPrediction(String actualMove) {
        if (currentPrediction != null) {
            // Save missed prediction for analysis
            trainingDataManager.saveMovePrediction(
                currentPrediction.move, actualMove, currentPrediction.confidence, "current-session"
            );
            
            logger.info("Recorded missed prediction: {} -> {} (confidence: {:.2f})",
                currentPrediction.move, actualMove, currentPrediction.confidence);
        }
    }
    
    /**
     * Convert GazePattern to MovePredictionAI.GazeSequence
     */
    private MovePredictionAI.GazeSequence convertToGazeSequence(GazePattern pattern) {
        MovePredictionAI.GazeSequence sequence = new MovePredictionAI.GazeSequence();
        
        // Convert gaze points
        if (pattern.getGazePoints() != null) {
            for (GazePoint point : pattern.getGazePoints()) {
                MovePredictionAI.GazePoint aiPoint = new MovePredictionAI.GazePoint();
                aiPoint.x = point.getX();
                aiPoint.y = point.getY();
                aiPoint.chessSquareX = point.getChessSquareX();
                aiPoint.chessSquareY = point.getChessSquareY();
                aiPoint.timestamp = point.getTimestamp();
                aiPoint.confidenceScore = point.getConfidence();
                sequence.gazePoints.add(aiPoint);
            }
        }
        
        // Set chess context
        sequence.gamePhase = getGamePhase();
        sequence.materialBalance = getMaterialBalance();
        sequence.kingSafety = getKingSafety();
        sequence.centerControl = getCenterControl();
        sequence.developmentScore = getDevelopmentScore();
        sequence.pawnStructure = getPawnStructure();
        sequence.pieceActivity = getPieceActivity();
        sequence.tacticalThreats = getTacticalThreats();
        sequence.positionalAdvantage = getPositionalAdvantage();
        sequence.timeRemaining = getTimeRemaining();
        sequence.moveNumber = getMoveNumber();
        sequence.isInCheck = chessGame.isInCheck();
        sequence.canCastle = canCastle();
        sequence.numberOfLegalMoves = getNumberOfLegalMoves();
        
        return sequence;
    }
    
    /**
     * Get current gaze sequence for training
     */
    private MovePredictionAI.GazeSequence getCurrentGazeSequence() {
        // This would return the current gaze sequence being tracked
        // For now, return a placeholder
        return new MovePredictionAI.GazeSequence();
    }
    
    /**
     * Get current board position as FEN string
     */
    private String getCurrentPosition() {
        try {
            return chessGame.getFEN();
        } catch (Exception e) {
            logger.warn("Error getting current position", e);
            return "";
        }
    }
    
    // Chess context helper methods
    private double getGamePhase() {
        // Opening: 0-20 moves, Middle: 20-40 moves, Endgame: 40+ moves
        int moveNumber = getMoveNumber();
        if (moveNumber < 20) return 0.0; // Opening
        if (moveNumber < 40) return 0.5; // Middle
        return 1.0; // Endgame
    }
    
    private double getMaterialBalance() {
        // Calculate material balance (positive = white advantage)
        try {
            String[][] board = chessGame.getBoard();
            int whiteMaterial = 0, blackMaterial = 0;
            
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    String piece = board[i][j];
                    if (piece != null && !piece.isEmpty()) {
                        int value = getPieceValue(piece);
                        if (Character.isUpperCase(piece.charAt(0))) {
                            whiteMaterial += value;
                        } else {
                            blackMaterial += value;
                        }
                    }
                }
            }
            
            return (whiteMaterial - blackMaterial) / 100.0; // Normalize
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private int getPieceValue(String piece) {
        if (piece == null || piece.isEmpty()) return 0;
        char p = Character.toLowerCase(piece.charAt(0));
        switch (p) {
            case 'p': return 1;
            case 'n': case 'b': return 3;
            case 'r': return 5;
            case 'q': return 9;
            case 'k': return 100;
            default: return 0;
        }
    }
    
    private double getKingSafety() { return 0.5; } // Placeholder
    private double getCenterControl() { return 0.5; } // Placeholder
    private double getDevelopmentScore() { return 0.5; } // Placeholder
    private double getPawnStructure() { return 0.5; } // Placeholder
    private double getPieceActivity() { return 0.5; } // Placeholder
    private double getTacticalThreats() { return 0.5; } // Placeholder
    private double getPositionalAdvantage() { return 0.5; } // Placeholder
    private double getTimeRemaining() { return 600.0; } // Placeholder
    private int getMoveNumber() { return chessGame.getMoveNumber(); }
    private boolean canCastle() { return true; } // Placeholder
    private int getNumberOfLegalMoves() { return 20; } // Placeholder
    
    
    // Data classes
    public static class GazePattern {
        private List<GazePoint> gazePoints = new ArrayList<>();
        private String focusedSquare;
        
        public List<GazePoint> getGazePoints() { return gazePoints; }
        public String getFocusedSquare() { return focusedSquare; }
        public void setFocusedSquare(String square) { this.focusedSquare = square; }
    }
    
    public static class GazePoint {
        private double x, y;
        private double chessSquareX, chessSquareY;
        private long timestamp;
        private double confidence;
        
        public GazePoint() {}
        
        public GazePoint(double x, double y, long timestamp, double confidence) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
            this.confidence = confidence;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getChessSquareX() { return chessSquareX; }
        public double getChessSquareY() { return chessSquareY; }
        public long getTimestamp() { return timestamp; }
        public double getConfidence() { return confidence; }
    }
    
    public static class MovePrediction {
        public final String move;
        public final double confidence;
        
        public MovePrediction(String move, double confidence) {
            this.move = move;
            this.confidence = confidence;
        }
    }
}
