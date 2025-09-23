package com.example.chess.service;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Gaze feature extractor for LSTM input
 * Extracts sequential features and chess context for move prediction
 */
@Component
public class GazeFeatureExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(GazeFeatureExtractor.class);
    
    /**
     * Extract sequential features for LSTM input
     * Shape: [sequenceLength, features] = [30, 20]
     */
    public INDArray extractSequence(GazeSequence gazeSequence) {
        List<GazePoint> points = gazeSequence.getGazePoints();
        int seqLen = Math.min(points.size(), 30); // Last 30 points (1 second)
        
        INDArray sequence = Nd4j.zeros(seqLen, 20);
        
        for (int t = 0; t < seqLen; t++) {
            GazePoint point = points.get(points.size() - seqLen + t);
            double[] features = extractPointFeatures(point, t);
            sequence.putRow(t, Nd4j.create(features));
        }
        
        return sequence;
    }
    
    private double[] extractPointFeatures(GazePoint point, int timeStep) {
        return new double[] {
            // Spatial features (8 dimensions)
            point.x / 1920.0,           // Normalized screen X
            point.y / 1080.0,           // Normalized screen Y
            point.chessSquareX / 8.0,   // Chess board X (0-7)
            point.chessSquareY / 8.0,   // Chess board Y (0-7)
            point.gazeVelocityX,        // Velocity X
            point.gazeVelocityY,        // Velocity Y
            point.fixationDuration,     // How long looking at this point
            point.saccadeAmplitude,     // Jump distance from previous
            
            // Temporal features (6 dimensions)
            timeStep / 30.0,            // Normalized time in sequence
            point.timestamp,            // Absolute timestamp
            point.deltaTime,            // Time since previous point
            point.isFixation ? 1.0 : 0.0, // Fixation vs saccade
            point.blinkDetected ? 1.0 : 0.0, // Blink detection
            point.confidenceScore,      // Eye tracking confidence
            
            // Chess context features (6 dimensions)
            point.pieceType,            // What piece is being looked at
            point.isLegalMoveTarget ? 1.0 : 0.0, // Valid move destination
            point.threatLevel,          // Tactical importance of square
            point.isPlayerPiece ? 1.0 : 0.0,     // Own vs opponent piece
            point.moveNumber / 100.0,   // Game progress
            point.timeRemaining / 600.0 // Normalized time pressure
        };
    }
    
    /**
     * Extract chess context features for dense layer
     * Shape: [40] features
     */
    public INDArray extractChessContext(GazeSequence gazeSequence) {
        double[] context = new double[40];
        int idx = 0;
        
        // Game state features (20 dimensions)
        context[idx++] = gazeSequence.gamePhase;        // Opening/Middle/Endgame
        context[idx++] = gazeSequence.materialBalance;  // Piece advantage
        context[idx++] = gazeSequence.kingSafety;       // King safety score
        context[idx++] = gazeSequence.centerControl;    // Center control
        context[idx++] = gazeSequence.developmentScore; // Piece development
        context[idx++] = gazeSequence.pawnStructure;    // Pawn structure score
        context[idx++] = gazeSequence.pieceActivity;    // Piece mobility
        context[idx++] = gazeSequence.tacticalThreats;  // Immediate threats
        context[idx++] = gazeSequence.positionalAdvantage; // Long-term advantage
        context[idx++] = gazeSequence.timeRemaining;    // Clock pressure
        context[idx++] = gazeSequence.moveNumber;       // Game progress
        context[idx++] = gazeSequence.repetitionRisk;   // Draw risk
        context[idx++] = gazeSequence.complexityScore;  // Position complexity
        context[idx++] = gazeSequence.numberOfLegalMoves; // Move options
        context[idx++] = gazeSequence.isInCheck ? 1.0 : 0.0; // Check status
        context[idx++] = gazeSequence.canCastle ? 1.0 : 0.0;  // Castling rights
        context[idx++] = gazeSequence.enPassantAvailable ? 1.0 : 0.0; // En passant
        context[idx++] = gazeSequence.promotionPossible ? 1.0 : 0.0;  // Promotion
        context[idx++] = gazeSequence.playerSkillLevel; // User skill estimate
        context[idx++] = gazeSequence.historicalAccuracy; // Past prediction accuracy
        
        // Attention pattern features (20 dimensions)
        context[idx++] = gazeSequence.attentionSpread;     // How scattered is gaze
        context[idx++] = gazeSequence.focusIntensity;      // Concentration level
        context[idx++] = gazeSequence.scanPathLength;      // Total gaze distance
        context[idx++] = gazeSequence.backtrackCount;      // Revisiting squares
        context[idx++] = gazeSequence.hesitationTime;      // Decision uncertainty
        context[idx++] = gazeSequence.confirmationLooks;   // Double-checking
        context[idx++] = gazeSequence.alternativeConsiderations; // Options explored
        context[idx++] = gazeSequence.cognitiveLoad;       // Mental effort
        context[idx++] = gazeSequence.gazeStability;       // Steadiness
        context[idx++] = gazeSequence.movementSmoothness;  // Smooth vs jerky
        context[idx++] = gazeSequence.boundaryProximity;   // Edge vs center focus
        context[idx++] = gazeSequence.centerBias;          // Center preference
        context[idx++] = gazeSequence.diagonalPreference;  // Diagonal patterns
        context[idx++] = gazeSequence.horizontalMovement;  // Horizontal scanning
        context[idx++] = gazeSequence.verticalMovement;    // Vertical scanning
        context[idx++] = gazeSequence.knightMovePattern;   // L-shaped patterns
        context[idx++] = gazeSequence.castlingPattern;     // Castling consideration
        context[idx++] = gazeSequence.capturePattern;      // Capture focus
        context[idx++] = gazeSequence.defensivePattern;    // Defensive attention
        context[idx++] = gazeSequence.timeToDecision;      // Decision speed
        
        return Nd4j.create(context);
    }
    
    public static class GazeSequence {
        // Game state features
        public double gamePhase = 0.0;
        public double materialBalance = 0.0;
        public double kingSafety = 0.0;
        public double centerControl = 0.0;
        public double developmentScore = 0.0;
        public double pawnStructure = 0.0;
        public double pieceActivity = 0.0;
        public double tacticalThreats = 0.0;
        public double positionalAdvantage = 0.0;
        public double timeRemaining = 0.0;
        public double moveNumber = 0.0;
        public double repetitionRisk = 0.0;
        public double complexityScore = 0.0;
        public double numberOfLegalMoves = 0.0;
        public boolean isInCheck = false;
        public boolean canCastle = false;
        public boolean enPassantAvailable = false;
        public boolean promotionPossible = false;
        public double playerSkillLevel = 0.0;
        public double historicalAccuracy = 0.0;
        
        // Attention pattern features
        public double attentionSpread = 0.0;
        public double focusIntensity = 0.0;
        public double scanPathLength = 0.0;
        public double backtrackCount = 0.0;
        public double hesitationTime = 0.0;
        public double confirmationLooks = 0.0;
        public double alternativeConsiderations = 0.0;
        public double cognitiveLoad = 0.0;
        public double gazeStability = 0.0;
        public double movementSmoothness = 0.0;
        public double boundaryProximity = 0.0;
        public double centerBias = 0.0;
        public double diagonalPreference = 0.0;
        public double horizontalMovement = 0.0;
        public double verticalMovement = 0.0;
        public double knightMovePattern = 0.0;
        public double castlingPattern = 0.0;
        public double capturePattern = 0.0;
        public double defensivePattern = 0.0;
        public double timeToDecision = 0.0;
        
        public List<GazePoint> getGazePoints() {
            // Return list of gaze points
            return List.of(); // Placeholder
        }
    }
    
    public static class GazePoint {
        public double x;
        public double y;
        public double chessSquareX;
        public double chessSquareY;
        public double gazeVelocityX;
        public double gazeVelocityY;
        public double fixationDuration;
        public double saccadeAmplitude;
        public double timestamp;
        public double deltaTime;
        public boolean isFixation;
        public boolean blinkDetected;
        public double confidenceScore;
        public double pieceType;
        public boolean isLegalMoveTarget;
        public double threatLevel;
        public boolean isPlayerPiece;
        public double moveNumber;
        public double timeRemaining;
        
        public GazePoint() {
            // Default constructor
        }
    }
}
