package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AIPrecomputationService {
    
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(6);
    private final Map<String, String> precomputedResponses = new ConcurrentHashMap<>();
    private volatile String currentPredictedMove = null;
    private volatile CompletableFuture<Void> activeComputation = null;
    
    @Autowired
    private com.example.chess.mcp.ai.SharedAIService sharedAIService;
    
    @Autowired
    private com.example.chess.ChessGame chessGame;
    
    public void precomputeResponse(String predictedMove, double confidence) {
        if (confidence < 0.7 || predictedMove == null) {
            return; // Skip low confidence predictions
        }
        
        // Cancel previous computation if different move
        if (activeComputation != null && !predictedMove.equals(currentPredictedMove)) {
            activeComputation.cancel(true);
        }
        
        currentPredictedMove = predictedMove;
        String cacheKey = getCurrentPosition() + ":" + predictedMove;
        
        if (precomputedResponses.containsKey(cacheKey)) {
            return; // Already computed
        }
        
        // Start precomputation for top AIs
        activeComputation = CompletableFuture.runAsync(() -> {
            try {
                // Simulate the predicted move
                String[][] simulatedBoard = simulateMove(predictedMove);
                if (simulatedBoard == null) return;
                
                // Get AI response for simulated position
                String aiResponse = getAIResponse(simulatedBoard);
                if (aiResponse != null && !Thread.currentThread().isInterrupted()) {
                    precomputedResponses.put(cacheKey, aiResponse);
                    System.out.println("Precomputed response for " + predictedMove + ": " + aiResponse);
                }
                
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("Error in precomputation: " + e.getMessage());
                }
            }
        }, aiExecutor);
    }
    
    public String getPrecomputedResponse(String actualMove) {
        String cacheKey = getCurrentPosition() + ":" + actualMove;
        String response = precomputedResponses.remove(cacheKey);
        
        if (response != null) {
            System.out.println("Using precomputed response for " + actualMove + ": " + response);
            return response;
        }
        
        return null; // No precomputed response available
    }
    
    private String[][] simulateMove(String move) {
        try {
            if (chessGame == null) return null;
            
            String[][] currentBoard = chessGame.getBoard();
            if (currentBoard == null) return null;
            
            // Create a copy of the board
            String[][] simulatedBoard = new String[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(currentBoard[i], 0, simulatedBoard[i], 0, 8);
            }
            
            // Parse and apply the move (simplified)
            if (move.length() >= 4) {
                int fromCol = move.charAt(0) - 'a';
                int fromRow = 8 - Character.getNumericValue(move.charAt(1));
                int toCol = move.charAt(2) - 'a';
                int toRow = 8 - Character.getNumericValue(move.charAt(3));
                
                if (isValidCoordinate(fromRow, fromCol) && isValidCoordinate(toRow, toCol)) {
                    String piece = simulatedBoard[fromRow][fromCol];
                    simulatedBoard[toRow][toCol] = piece;
                    simulatedBoard[fromRow][fromCol] = "";
                    return simulatedBoard;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error simulating move: " + e.getMessage());
        }
        
        return null;
    }
    
    private boolean isValidCoordinate(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
    
    private String getAIResponse(String[][] board) {
        try {
            if (sharedAIService != null) {
                // Get best move from AI service
                int[] bestMove = sharedAIService.findBestMove(board, false); // AI plays as black
                if (bestMove != null && bestMove.length >= 4) {
                    // Convert to string format
                    char fromFile = (char)('a' + bestMove[1]);
                    int fromRank = 8 - bestMove[0];
                    char toFile = (char)('a' + bestMove[3]);
                    int toRank = 8 - bestMove[2];
                    return "" + fromFile + fromRank + toFile + toRank;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting AI response: " + e.getMessage());
        }
        
        return null;
    }
    
    private String getCurrentPosition() {
        try {
            if (chessGame != null) {
                String[][] board = chessGame.getBoard();
                if (board != null) {
                    // Simple position hash
                    StringBuilder sb = new StringBuilder();
                    for (String[] row : board) {
                        for (String piece : row) {
                            sb.append(piece == null ? "." : piece);
                        }
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting current position: " + e.getMessage());
        }
        
        return "unknown";
    }
    
    public void clearCache() {
        precomputedResponses.clear();
        if (activeComputation != null) {
            activeComputation.cancel(true);
            activeComputation = null;
        }
        currentPredictedMove = null;
    }
    
    public int getCacheSize() {
        return precomputedResponses.size();
    }
}