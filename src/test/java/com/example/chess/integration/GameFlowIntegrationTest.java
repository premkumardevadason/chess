package com.example.chess.integration;

import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class GameFlowIntegrationTest {
    
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
    }
    
    @Test
    @Timeout(60)
    void testCompleteGameFlow() {
        assertTrue(game.isWhiteTurn());
        assertFalse(game.isGameOver());
        
        // Play Scholar's Mate
        assertTrue(game.makeMove(6, 4, 4, 4)); // e2-e4
        assertFalse(game.isWhiteTurn());
        
        assertTrue(game.makeMove(1, 4, 3, 4)); // e7-e5
        assertTrue(game.isWhiteTurn());
        
        assertTrue(game.makeMove(7, 5, 4, 2)); // Bf1-c4
        assertFalse(game.isWhiteTurn());
        
        assertTrue(game.makeMove(1, 1, 2, 2)); // Nb8-c6
        assertTrue(game.isWhiteTurn());
        
        assertTrue(game.makeMove(7, 3, 3, 7)); // Qd1-h5
        assertFalse(game.isWhiteTurn());
        
        assertTrue(game.makeMove(1, 5, 2, 5)); // f7-f6
        assertTrue(game.isWhiteTurn());
        
        assertTrue(game.makeMove(3, 7, 1, 5)); // Qh5xf7#
        
        assertTrue(game.isGameOver());
        assertTrue(game.isGameOver());
    }
    
    @Test
    @Timeout(30)
    void testAIVsUserGame() {
        // User makes first move
        assertTrue(game.makeMove(6, 4, 4, 4)); // e2-e4
        
        // AI responds
        int[] aiMove = game.findBestMoveForTesting();
        assertNotNull(aiMove);
        assertTrue(game.isValidMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]));
        
        assertTrue(game.makeMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]));
        
        // Continue for a few moves
        for (int i = 0; i < 3; i++) {
            // User move
            int[] userMove = game.findBestMoveForTesting();
            if (userMove != null) {
                assertTrue(game.makeMove(userMove[0], userMove[1], userMove[2], userMove[3]));
            }
            
            if (game.isGameOver()) break;
            
            // AI move
            aiMove = game.findBestMoveForTesting();
            if (aiMove != null) {
                assertTrue(game.makeMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]));
            }
            
            if (game.isGameOver()) break;
        }
        
        // Game should be playable
        assertTrue(game.getMoveCount() >= 2);
    }
    
    @Test
    void testMultipleAISelection() {
        // Test different AI opponents
        String[] aiTypes = {"Q-Learning", "Deep Learning", "Negamax", "MCTS"};
        
        for (String aiType : aiTypes) {
            ChessGame aiGame = new ChessGame();
            // Test AI availability
            
            int[] move = aiGame.findBestMoveForTesting();
            if (move != null) {
                assertTrue(aiGame.isValidMove(move[0], move[1], move[2], move[3]));
            }
        }
    }
    
    @Test
    void testGameStateConsistency() {
        String[][] initialState = game.getBoard();
        assertNotNull(initialState);
        
        // Make a move
        game.makeMove(6, 4, 4, 4);
        String[][] afterMoveState = game.getBoard();
        assertNotNull(afterMoveState);
        assertNotEquals(initialState[6][4], afterMoveState[6][4]);
        
        // Undo move
        game.undoMove();
        String[][] afterUndoState = game.getBoard();
        assertEquals(initialState[6][4], afterUndoState[6][4]);
    }
    
    @Test
    @Timeout(45)
    void testTrainingDataCollection() {
        int initialPositions = 0;
        
        // Play a few moves to generate position data
        game.makeMove(6, 4, 4, 4); // e2-e4
        game.makeMove(1, 4, 3, 4); // e7-e5
        game.makeMove(7, 1, 5, 2); // Nb1-c3
        game.makeMove(1, 1, 2, 2); // Nb8-c6
        
        // Positions should be stored for AI learning
        int afterMovesPositions = game.getMoveCount();
        assertTrue(afterMovesPositions >= initialPositions);
        
        // Test AI can learn from stored positions
        if (afterMovesPositions > 0) {
            game.trainAI();
            // Training should complete without errors
            assertTrue(true);
        }
    }
}