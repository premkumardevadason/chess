package com.example.chess.mcp.ai;

import com.example.chess.ChessGame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;

/**
 * Shared AI Service for MCP Sessions
 * 
 * This service provides access to the main ChessGame's AI systems
 * for all MCP sessions, avoiding the need to create new AI instances
 * for each session. Ensures AI systems are properly initialized
 * with opening books before use.
 */
@Service
public class SharedAIService {
    
    private static final Logger logger = LogManager.getLogger(SharedAIService.class);
    
    @Autowired
    private ChessGame mainChessGame;
    
    @PostConstruct
    public void initialize() {
        // Ensure AI systems are initialized when SharedAIService is created
        if (mainChessGame != null) {
            mainChessGame.ensureAISystemsInitialized();
            logger.info("SharedAIService: AI systems initialized for MCP server");
        }
    }
    
    /**
     * Get the best move from the main ChessGame's AI systems
     * Uses completely isolated board state to prevent session contamination
     * 
     * @param board Current board state
     * @param isWhiteTurn Current turn
     * @return Best move as [fromRow, fromCol, toRow, toCol] or null
     */
    public synchronized int[] findBestMove(String[][] board, boolean isWhiteTurn) {
        try {
            // Ensure AI systems are initialized before use
            mainChessGame.ensureAISystemsInitialized();
            
            // CRITICAL FIX: Create completely isolated board copy to prevent contamination
            String[][] isolatedBoard = deepCopyBoard(board);
            
            logger.info("SharedAIService: Finding move for isWhiteTurn={} with ISOLATED board (session-safe)", isWhiteTurn);
            
            // Debug: Log current board state with session isolation
            logger.info("SharedAIService: ISOLATED Board state - piece at c2: '{}', piece at c4: '{}'", 
                isolatedBoard[6][2], isolatedBoard[4][2]);
            
            // CRITICAL FIX: Use completely isolated evaluation that never touches mainChessGame board
            int[] bestMove = mainChessGame.findBestMoveForColorIsolated(isolatedBoard, isWhiteTurn);
            
            logger.info("SharedAIService found ISOLATED move: {}", 
                bestMove != null ? java.util.Arrays.toString(bestMove) : "null");
            
            if (bestMove == null) {
                logger.error("SharedAIService: findBestMoveForColorIsolated returned null - checking AI systems status");
                logger.error("AI Systems Status: {}", getAISystemStatus());
            }
            
            return bestMove;
            
        } catch (Exception e) {
            logger.error("Error in findBestMove: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * CRITICAL FIX: Get all valid moves using completely isolated board state
     * Ensures White and Black MCP sessions never interfere with each other
     */
    public synchronized java.util.List<int[]> getAllValidMoves(String[][] board, boolean forWhite) {
        try {
            // Ensure AI systems are initialized before use
            mainChessGame.ensureAISystemsInitialized();
            
            // CRITICAL FIX: Create completely isolated board copy to prevent session contamination
            String[][] isolatedBoard = deepCopyBoard(board);
            
            logger.debug("SharedAIService: Getting valid moves for {} with ISOLATED board", forWhite ? "WHITE" : "BLACK");
            
            return mainChessGame.getAllValidMovesIsolated(isolatedBoard, forWhite);
            
        } catch (Exception e) {
            logger.error("Error in getAllValidMoves: {}", e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }
    
    /**
     * Check if a move is valid using isolated board state
     */
    public synchronized boolean isValidMove(String[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isWhiteTurn) {
        try {
            // Ensure AI systems are initialized before use
            mainChessGame.ensureAISystemsInitialized();
            
            // Create isolated board copy
            String[][] isolatedBoard = deepCopyBoard(board);
            
            return mainChessGame.isValidMoveIsolated(isolatedBoard, fromRow, fromCol, toRow, toCol, isWhiteTurn);
            
        } catch (Exception e) {
            logger.error("Error in isValidMove: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Verify that AI systems and opening books are properly initialized
     */
    public String getAISystemStatus() {
        StringBuilder status = new StringBuilder();
        
        try {
            mainChessGame.ensureAISystemsInitialized();
            
            // Check Q-Learning AI
            if (mainChessGame.isQLearningEnabled()) {
                var qAI = mainChessGame.getQLearningAI();
                status.append("Q-Learning: ENABLED\n");
            } else {
                status.append("Q-Learning: DISABLED\n");
            }
            
            // Check Deep Learning AI
            if (mainChessGame.isDeepLearningEnabled()) {
                var dlAI = mainChessGame.getDeepLearningAI();
                status.append("Deep Learning: ENABLED\n");
            } else {
                status.append("Deep Learning: DISABLED\n");
            }
            
            // Check Leela Chess Zero AI and opening book
            if (mainChessGame.isLeelaZeroEnabled()) {
                var leelaAI = mainChessGame.getLeelaZeroAI();
                var openingBook = mainChessGame.getLeelaOpeningBook();
                status.append("LeelaZero: ENABLED");
                if (openingBook != null) {
                    status.append(" (Opening Book: AVAILABLE)\n");
                } else {
                    status.append(" (Opening Book: MISSING)\n");
                }
            } else {
                status.append("LeelaZero: DISABLED\n");
            }
            
            // Check other AI systems
            status.append("AlphaZero: ").append(mainChessGame.isAlphaZeroEnabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("Negamax: ").append(mainChessGame.isNegamaxEnabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("MCTS: ").append(mainChessGame.isMCTSEnabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("DQN: ").append(mainChessGame.isDQNEnabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("Genetic: ").append(mainChessGame.isGeneticEnabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("AlphaFold3: ").append(mainChessGame.isAlphaFold3Enabled() ? "ENABLED" : "DISABLED").append("\n");
            status.append("A3C: ").append(mainChessGame.isA3CEnabled() ? "ENABLED" : "DISABLED").append("\n");
            
        } catch (Exception e) {
            status.append("ERROR: ").append(e.getMessage()).append("\n");
        }
        
        return status.toString();
    }
    
    /**
     * CRITICAL FIX: Create a completely isolated deep copy of the board to prevent session contamination
     * This ensures White and Black MCP sessions never share board state
     */
    private String[][] deepCopyBoard(String[][] original) {
        if (original == null) {
            logger.error("CRITICAL: Attempted to copy null board - creating empty board");
            return new String[8][8];
        }
        
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                // CRITICAL: Ensure each string is a new instance to prevent reference sharing
                copy[i][j] = original[i][j] != null ? new String(original[i][j]) : "";
            }
        }
        
        logger.debug("SharedAIService: Created ISOLATED board copy - no session contamination possible");
        return copy;
    }
}