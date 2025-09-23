package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Piece intention analyzer for strategic thinking detection
 * Determines which piece user is thinking about and predicts strategic intention
 */
@Component
public class PieceIntentionAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(PieceIntentionAnalyzer.class);
    
    @Autowired
    private com.example.chess.ChessGame chessGame;
    
    /**
     * REQUIREMENT 4: Determine which piece user is thinking about
     * and predict their strategic intention
     */
    public PieceIntention analyzePieceIntention(String focusedSquare, GazePattern pattern) {
        String[][] board = chessGame.getBoard();
        String piece = board[getRow(focusedSquare)][getCol(focusedSquare)];
        
        if (piece == null || piece.isEmpty()) {
            return new PieceIntention(focusedSquare, "empty", "none", new ArrayList<>());
        }
        
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        boolean isUserTurn = chessGame.isWhiteTurn();
        
        if (isWhitePiece && isUserTurn) {
            // User looking at their own white piece - predict possible moves
            return analyzeUserPieceIntention(focusedSquare, piece, pattern);
        } else if (!isWhitePiece && !isUserTurn) {
            // User looking at AI's black piece - predict where AI might move
            return analyzeAIPieceIntention(focusedSquare, piece, pattern);
        }
        
        return new PieceIntention(focusedSquare, piece, "observation", new ArrayList<>());
    }
    
    private PieceIntention analyzeUserPieceIntention(String square, String piece, GazePattern pattern) {
        List<String> possibleMoves = List.of("e2-e4", "e2-e3"); // Placeholder
        
        // Analyze gaze pattern to predict most likely moves
        List<String> predictedMoves = new ArrayList<>();
        
        for (String move : possibleMoves) {
            String targetSquare = extractTargetSquare(move);
            double moveConfidence = calculateMoveConfidence(square, targetSquare, pattern);
            
            if (moveConfidence > 0.6) {
                predictedMoves.add(move);
            }
        }
        
        // Sort by confidence
        predictedMoves.sort((m1, m2) -> {
            double conf1 = calculateMoveConfidence(square, extractTargetSquare(m1), pattern);
            double conf2 = calculateMoveConfidence(square, extractTargetSquare(m2), pattern);
            return Double.compare(conf2, conf1);
        });
        
        String intention = predictedMoves.isEmpty() ? "considering" : "planning_move";
        
        logger.info("User thinking about {} piece at {}: {} (predicted moves: {})", 
            piece, square, intention, predictedMoves.size());
            
        return new PieceIntention(square, piece, intention, predictedMoves);
    }
    
    private PieceIntention analyzeAIPieceIntention(String square, String piece, GazePattern pattern) {
        // User looking at AI piece - predict where AI might move it
        List<String> aiPossibleMoves = List.of("e7-e5", "e7-e6"); // Placeholder
        
        // Use current AI to predict its most likely moves with this piece
        String selectedAI = "Negamax"; // Placeholder
        List<String> aiPredictedMoves = predictAIMoves(selectedAI, square, aiPossibleMoves);
        
        String intention = "anticipating_ai_move";
        
        logger.info("User anticipating AI {} piece at {}: {} possible moves", 
            piece, square, aiPossibleMoves.size());
            
        return new PieceIntention(square, piece, intention, aiPredictedMoves);
    }
    
    private List<String> predictAIMoves(String aiName, String square, List<String> possibleMoves) {
        // Quick evaluation of AI's likely moves with this piece
        List<String> predictedMoves = new ArrayList<>();
        
        for (String move : possibleMoves) {
            double aiMoveScore = evaluateAIMoveScore(aiName, move);
            if (aiMoveScore > 0.7) {
                predictedMoves.add(move);
            }
        }
        
        return predictedMoves.subList(0, Math.min(3, predictedMoves.size()));
    }
    
    private double calculateMoveConfidence(String fromSquare, String toSquare, GazePattern pattern) {
        // Analyze gaze transitions between source and target squares
        double transitionScore = pattern.getTransitionScore(fromSquare, toSquare);
        double fixationScore = pattern.getFixationScore(toSquare);
        double temporalScore = pattern.getTemporalScore();
        
        return (transitionScore * 0.4 + fixationScore * 0.4 + temporalScore * 0.2);
    }
    
    private double evaluateAIMoveScore(String aiName, String move) {
        // Quick heuristic evaluation of how likely AI is to make this move
        // This could be enhanced with actual AI evaluation calls
        return 0.5 + Math.random() * 0.5; // Placeholder
    }
    
    private String extractTargetSquare(String move) {
        // Extract target square from move notation (e.g., "e2-e4" -> "e4")
        if (move.contains("-")) {
            return move.split("-")[1];
        }
        return move.substring(move.length() - 2);
    }
    
    private int getRow(String square) {
        return 8 - Character.getNumericValue(square.charAt(1));
    }
    
    private int getCol(String square) {
        return square.charAt(0) - 'a';
    }
    
    public static class PieceIntention {
        public final String square;
        public final String piece;
        public final String intention; // "planning_move", "considering", "anticipating_ai_move", "observation"
        public final List<String> predictedMoves;
        
        public PieceIntention(String square, String piece, String intention, List<String> predictedMoves) {
            this.square = square;
            this.piece = piece;
            this.intention = intention;
            this.predictedMoves = predictedMoves;
        }
    }
    
    public static class GazePattern {
        // Placeholder for gaze pattern data
        // This would contain the actual gaze pattern information
        
        public double getTransitionScore(String fromSquare, String toSquare) {
            // Calculate transition score between squares
            return Math.random(); // Placeholder
        }
        
        public double getFixationScore(String square) {
            // Calculate fixation score for a square
            return Math.random(); // Placeholder
        }
        
        public double getTemporalScore() {
            // Calculate temporal pattern score
            return Math.random(); // Placeholder
        }
    }
}
