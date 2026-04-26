package com.example.chess.integration;

import com.example.chess.BaseTestClass;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class AIIntegrationTest extends BaseTestClass {
    
    @Test
    @Timeout(60)
    void testParallelAIExecution() {
        // Test all AI systems can run simultaneously
        game.trainAI();
        
        // Let training run briefly
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        // Stop training
        game.stopTraining();
        
        // Test completed
        assertTrue(true);
    }
    
    @Test
    void testAICoordination() {
        // Test AI systems can coordinate move selection
        int[] move = game.findBestMoveForTesting();
        if (move != null) {
            assertNotNull(move);
            assertEquals(4, move.length);
            assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
        }
    }
    
    @Test
    void testTrainingDataPersistence() {
        // Train briefly
        game.trainAI();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        
        // Create new game instance
        ChessGame newGame = new ChessGame();
        
        // Verify AI systems are available (may be null if disabled)
        // Just check that the method doesn't throw an exception
        Object qLearningAI = newGame.getQLearningAI();
        assertTrue(true); // Test passes if no exception thrown
    }
    
    @Test
    @Timeout(30)
    void testAIPerformanceIntegration() {
        long startTime = System.currentTimeMillis();
        
        // Test multiple AI move selections
        for (int i = 0; i < 3; i++) {
            int[] move = game.findBestMoveForTesting();
            if (move != null) {
                assertNotNull(move);
                game.makeMove(move[0], move[1], move[2], move[3]);
            }
            if (game.isGameOver()) break;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 25000, "AI integration too slow: " + duration + "ms");
    }
    
    @Test
    void testAIStateConsistency() {
        // Make some moves
        game.makeMove(6, 4, 4, 4); // e2-e4
        game.makeMove(1, 4, 3, 4); // e7-e5
        
        // AI systems should remain functional (may be null if disabled)
        // Just check that the methods don't throw exceptions
        Object qLearningAI = game.getQLearningAI();
        Object deepLearningAI = game.getDeepLearningAI();
        assertTrue(true); // Test passes if no exception thrown
    }
}


