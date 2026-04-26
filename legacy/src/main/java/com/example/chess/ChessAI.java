package com.example.chess;

/**
 * Base interface for all Chess AI implementations.
 * Provides a common contract for AI move generation.
 */
public interface ChessAI {
    
    /**
     * Generate the next move for the AI given the current game state.
     * 
     * @param game The current chess game state
     * @return The AI's move in algebraic notation (e.g., "e4", "Nf3", "O-O")
     */
    String getMove(ChessGame game);
    
    /**
     * Get the name/type of this AI system.
     * 
     * @return The AI system name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}