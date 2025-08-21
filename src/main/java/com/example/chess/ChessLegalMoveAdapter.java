package com.example.chess;

import java.util.List;

/**
 * Unified adapter for legal move generation across all AI systems.
 * Ensures all AI/trainers use ChessRuleValidator for consistent legal moves.
 */
public class ChessLegalMoveAdapter {
    private final ChessRuleValidator validator;
    
    public ChessLegalMoveAdapter() {
        this.validator = new ChessRuleValidator();
    }
    
    public ChessLegalMoveAdapter(ChessRuleValidator validator) {
        this.validator = validator != null ? validator : new ChessRuleValidator();
    }
    
    /**
     * Get all legal moves for the specified side.
     */
    public List<int[]> getAllLegalMoves(String[][] board, boolean forWhite) {
        return validator.getAllValidMoves(board, forWhite, forWhite);
    }
    
    /**
     * Validate if a specific move is legal.
     */
    public boolean isLegalMove(String[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean whiteTurn) {
        return validator.isValidMove(board, fromRow, fromCol, toRow, toCol, whiteTurn);
    }
    
    /**
     * Check if game is over (no legal moves available).
     */
    public boolean isGameOver(String[][] board) {
        return getAllLegalMoves(board, true).isEmpty() && getAllLegalMoves(board, false).isEmpty();
    }
    
    /**
     * Get the underlying validator for advanced operations.
     */
    public ChessRuleValidator getValidator() {
        return validator;
    }
}