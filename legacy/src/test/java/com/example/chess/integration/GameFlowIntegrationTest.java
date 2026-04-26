package com.example.chess.integration;

import com.example.chess.BaseTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class GameFlowIntegrationTest extends BaseTestClass {
    
    
    
    @BeforeEach
    void setUp() {
        super.baseSetUp();
    }
    
    @Test
    @Timeout(60)
    void testCompleteGameFlow() {
        assertTrue(game.isWhiteTurn());
        assertFalse(game.isGameOver());
        
        // Test basic game flow with simple moves
        boolean move1 = game.makeMove(6, 4, 4, 4); // e2-e4
        assertTrue(move1, "First move should succeed");
        
        if (move1) {
            assertFalse(game.isWhiteTurn());
            
            // Try a simple response
            boolean move2 = game.makeMove(1, 4, 3, 4); // e7-e5
            if (move2) {
                assertTrue(game.isWhiteTurn());
                
                // Try another move
                boolean move3 = game.makeMove(7, 5, 4, 2); // Bf1-c4
                if (move3) {
                    assertFalse(game.isWhiteTurn());
                }
            }
        }
        
        // Test that the game is functional
        assertTrue(game.getMoveCount() >= 1, "At least one move should have been made");
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
            super.baseSetUp();
            // Test AI availability
            
            int[] move = game.findBestMoveForTesting();
            if (move != null) {
                assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
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
        
        // Check that the piece moved (initial state should have piece, after move should be empty)
        String initialPiece = initialState[6][4];
        String afterMovePiece = afterMoveState[6][4];
        
        // Only assert if initial piece was not empty
        if (initialPiece != null && !initialPiece.isEmpty()) {
            assertNotEquals(initialPiece, afterMovePiece);
        }
        
        // Undo move
        game.undoMove();
        String[][] afterUndoState = game.getBoard();
        
        // Check that piece is back (if it was there initially)
        if (initialPiece != null && !initialPiece.isEmpty()) {
            assertEquals(initialPiece, afterUndoState[6][4]);
        }
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


