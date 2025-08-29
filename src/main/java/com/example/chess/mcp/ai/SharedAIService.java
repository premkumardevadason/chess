package com.example.chess.mcp.ai;

import com.example.chess.ChessGame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared AI Service for MCP Sessions
 * 
 * This service provides access to the main ChessGame's AI systems
 * for all MCP sessions, avoiding the need to create new AI instances
 * for each session.
 */
@Service
public class SharedAIService {
    
    private static final Logger logger = LogManager.getLogger(SharedAIService.class);
    
    @Autowired
    private ChessGame mainChessGame;
    
    /**
     * Get the best move from the main ChessGame's AI systems
     * 
     * @param board Current board state
     * @param isWhiteTurn Current turn
     * @return Best move as [fromRow, fromCol, toRow, toCol] or null
     */
    public int[] findBestMove(String[][] board, boolean isWhiteTurn) {
        // Temporarily set the main game's board state
        String[][] originalBoard = mainChessGame.getBoard();
        boolean originalTurn = mainChessGame.isWhiteTurn();
        
        try {
            // Set the board state for AI evaluation
            mainChessGame.setBoard(board);
            mainChessGame.setWhiteTurn(isWhiteTurn);
            
            // Use the main game's AI systems to find the best move
            // Each AI system should handle opening book internally
            int[] bestMove = mainChessGame.findBestMoveForTesting();
            
            logger.debug("SharedAIService found move: {}", 
                bestMove != null ? java.util.Arrays.toString(bestMove) : "null");
            
            return bestMove;
            
        } finally {
            // Restore original board state
            mainChessGame.setBoard(originalBoard);
            mainChessGame.setWhiteTurn(originalTurn);
        }
    }
    
    /**
     * Get all valid moves using the main ChessGame's rule validator
     */
    public java.util.List<int[]> getAllValidMoves(String[][] board, boolean forWhite) {
        String[][] originalBoard = mainChessGame.getBoard();
        boolean originalTurn = mainChessGame.isWhiteTurn();
        
        try {
            mainChessGame.setBoard(board);
            mainChessGame.setWhiteTurn(forWhite);
            
            return mainChessGame.getAllValidMoves(forWhite);
            
        } finally {
            mainChessGame.setBoard(originalBoard);
            mainChessGame.setWhiteTurn(originalTurn);
        }
    }
    
    /**
     * Check if a move is valid using the main ChessGame's validator
     */
    public boolean isValidMove(String[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isWhiteTurn) {
        String[][] originalBoard = mainChessGame.getBoard();
        boolean originalTurn = mainChessGame.isWhiteTurn();
        
        try {
            mainChessGame.setBoard(board);
            mainChessGame.setWhiteTurn(isWhiteTurn);
            
            return mainChessGame.isValidMove(fromRow, fromCol, toRow, toCol);
            
        } finally {
            mainChessGame.setBoard(originalBoard);
            mainChessGame.setWhiteTurn(originalTurn);
        }
    }
}