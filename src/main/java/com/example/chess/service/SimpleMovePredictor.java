package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimpleMovePredictor {
    
    private Map<String, List<String>> gazeSequences = new ConcurrentHashMap<>();
    private Map<String, Integer> gazeCounts = new ConcurrentHashMap<>();
    
    @Autowired
    private com.example.chess.ChessGame chessGame;
    
    public MovePrediction predictMove(String sessionId, String focusedSquare) {
        if (focusedSquare == null || chessGame == null) {
            return new MovePrediction(null, 0.0);
        }
        
        // Get current board state
        String[][] board = chessGame.getBoard();
        if (board == null) {
            return new MovePrediction(null, 0.0);
        }
        
        // Record gaze sequence
        recordGazeSequence(sessionId, focusedSquare);
        
        // Simple prediction based on piece type and legal moves
        String predictedMove = predictBasedOnPiece(focusedSquare, board);
        double confidence = calculateConfidence(sessionId, focusedSquare);
        
        return new MovePrediction(predictedMove, confidence);
    }
    
    private void recordGazeSequence(String sessionId, String square) {
        gazeSequences.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(square);
        gazeCounts.merge(square, 1, Integer::sum);
        
        // Keep only last 10 gaze points
        List<String> sequence = gazeSequences.get(sessionId);
        if (sequence.size() > 10) {
            sequence.remove(0);
        }
    }
    
    private String predictBasedOnPiece(String square, String[][] board) {
        try {
            int row = 8 - Character.getNumericValue(square.charAt(1));
            int col = square.charAt(0) - 'a';
            
            if (row < 0 || row >= 8 || col < 0 || col >= 8) {
                return null;
            }
            
            String piece = board[row][col];
            if (piece == null || piece.isEmpty()) {
                return null;
            }
            
            // Simple prediction based on piece type
            List<String> possibleMoves = generateSimpleMoves(square, piece, board);
            if (!possibleMoves.isEmpty()) {
                // Return most common target square
                return possibleMoves.get(0);
            }
            
        } catch (Exception e) {
            System.err.println("Error predicting move: " + e.getMessage());
        }
        
        return null;
    }
    
    private List<String> generateSimpleMoves(String fromSquare, String piece, String[][] board) {
        List<String> moves = new ArrayList<>();
        
        // Simplified move generation based on piece type
        char pieceType = piece.toLowerCase().charAt(0);
        int fromRow = 8 - Character.getNumericValue(fromSquare.charAt(1));
        int fromCol = fromSquare.charAt(0) - 'a';
        
        switch (pieceType) {
            case '♙': // Pawn
                if (fromRow > 0) {
                    moves.add(getSquareName(fromRow - 1, fromCol));
                    if (fromRow == 6) { // Starting position
                        moves.add(getSquareName(fromRow - 2, fromCol));
                    }
                }
                break;
                
            case '♖': // Rook
                // Add horizontal and vertical moves
                for (int i = 0; i < 8; i++) {
                    if (i != fromCol) moves.add(getSquareName(fromRow, i));
                    if (i != fromRow) moves.add(getSquareName(i, fromCol));
                }
                break;
                
            case '♗': // Bishop
                // Add diagonal moves
                for (int i = 1; i < 8; i++) {
                    if (fromRow + i < 8 && fromCol + i < 8) 
                        moves.add(getSquareName(fromRow + i, fromCol + i));
                    if (fromRow - i >= 0 && fromCol - i >= 0) 
                        moves.add(getSquareName(fromRow - i, fromCol - i));
                    if (fromRow + i < 8 && fromCol - i >= 0) 
                        moves.add(getSquareName(fromRow + i, fromCol - i));
                    if (fromRow - i >= 0 && fromCol + i < 8) 
                        moves.add(getSquareName(fromRow - i, fromCol + i));
                }
                break;
                
            case '♘': // Knight
                int[][] knightMoves = {{-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}};
                for (int[] move : knightMoves) {
                    int newRow = fromRow + move[0];
                    int newCol = fromCol + move[1];
                    if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                        moves.add(getSquareName(newRow, newCol));
                    }
                }
                break;
                
            case '♕': // Queen
                // Combine rook and bishop moves
                for (int i = 0; i < 8; i++) {
                    if (i != fromCol) moves.add(getSquareName(fromRow, i));
                    if (i != fromRow) moves.add(getSquareName(i, fromCol));
                }
                for (int i = 1; i < 8; i++) {
                    if (fromRow + i < 8 && fromCol + i < 8) 
                        moves.add(getSquareName(fromRow + i, fromCol + i));
                    if (fromRow - i >= 0 && fromCol - i >= 0) 
                        moves.add(getSquareName(fromRow - i, fromCol - i));
                    if (fromRow + i < 8 && fromCol - i >= 0) 
                        moves.add(getSquareName(fromRow + i, fromCol - i));
                    if (fromRow - i >= 0 && fromCol + i < 8) 
                        moves.add(getSquareName(fromRow - i, fromCol + i));
                }
                break;
                
            case '♔': // King
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        int newRow = fromRow + dr;
                        int newCol = fromCol + dc;
                        if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                            moves.add(getSquareName(newRow, newCol));
                        }
                    }
                }
                break;
        }
        
        return moves;
    }
    
    private String getSquareName(int row, int col) {
        char file = (char)('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }
    
    private double calculateConfidence(String sessionId, String square) {
        List<String> sequence = gazeSequences.get(sessionId);
        if (sequence == null || sequence.isEmpty()) {
            return 0.0;
        }
        
        // Calculate confidence based on gaze consistency
        long focusCount = sequence.stream().mapToLong(s -> s.equals(square) ? 1 : 0).sum();
        double consistency = (double) focusCount / sequence.size();
        
        // Factor in total gaze count for this square
        int totalCount = gazeCounts.getOrDefault(square, 0);
        double popularity = Math.min(totalCount / 10.0, 1.0);
        
        return (consistency * 0.7 + popularity * 0.3);
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