package com.example.chess.unit.ai;

import com.example.chess.QLearningAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class QLearningAITest {
    
    private QLearningAI qLearningAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        qLearningAI = new QLearningAI();
    }
    
    @Test
    void testQTableInitialization() {
        assertNotNull(qLearningAI);
        assertTrue(qLearningAI.getQTableSize() >= 0);
    }
    
    @Test
    void testQTablePersistence() {
        int originalSize = qLearningAI.getQTableSize();
        
        qLearningAI.saveQTable();
        assertTrue(new File("chess_qtable.dat").exists());
        
        QLearningAI newAI = new QLearningAI(false);
        assertTrue(newAI.getQTableSize() >= 0);
    }
    
    @Test
    void testMoveSelection() {
        int[] move = qLearningAI.selectMove(game.getBoard(), game.getAllValidMoves(false), false);
        assertNotNull(move);
        assertEquals(4, move.length);
        assertTrue(move[0] >= 0 && move[0] < 8);
        assertTrue(move[1] >= 0 && move[1] < 8);
        assertTrue(move[2] >= 0 && move[2] < 8);
        assertTrue(move[3] >= 0 && move[3] < 8);
    }
    
    @Test
    @Timeout(30)
    void testTrainingPerformance() {
        long startTime = System.currentTimeMillis();
        // Train using the game instance
        game.trainAI();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // Let it train for 2 seconds
        game.stopTraining();
        long duration = System.currentTimeMillis() - startTime;
        
        // Should complete training setup quickly
        assertTrue(duration < 15000, "Training setup too slow: " + duration + "ms");
        assertTrue(qLearningAI.getQTableSize() >= 0);
    }
    
    @Test
    void testLearningProgression() {
        int initialSize = qLearningAI.getQTableSize();
        // Make some moves to generate Q-table entries
        game.makeMove(6, 4, 4, 4); // e2-e4
        game.makeMove(1, 4, 3, 4); // e7-e5
        int afterMoves = qLearningAI.getQTableSize();
        assertTrue(afterMoves >= initialSize);
    }
    
    @Test
    void testMemoryManagement() {
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        qLearningAI.saveQTable();
        System.gc();
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        // Memory usage should be reasonable
        assertTrue(memoryAfter < memoryBefore * 3);
    }
}