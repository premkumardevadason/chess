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
        int initialSize = qLearningAI.getQTableSize();
        
        // Measure training speed
        long startTime = System.currentTimeMillis();
        int gamesPlayed = 0;
        
        // Simulate rapid game training
        for (int i = 0; i < 10; i++) {
            ChessGame trainingGame = new ChessGame();
            
            // Quick game simulation
            for (int moves = 0; moves < 5 && !trainingGame.isGameOver(); moves++) {
                int[] move = qLearningAI.selectMove(trainingGame.getBoard(), 
                    trainingGame.getAllValidMoves(moves % 2 == 0), true);
                if (move != null) {
                    trainingGame.makeMove(move[0], move[1], move[2], move[3]);
                    qLearningAI.updateQValue(trainingGame.getBoardStateKey(), 
                        move[0] + "" + move[1] + move[2] + move[3], 0.01);
                }
            }
            gamesPlayed++;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double gamesPerSecond = (gamesPlayed * 1000.0) / duration;
        
        // Verify performance targets
        assertTrue(gamesPerSecond > 1.0, "Should achieve >1 game/second, got: " + gamesPerSecond);
        assertTrue(qLearningAI.getQTableSize() > initialSize, "Q-table should grow during training");
        
        System.out.println("Q-Learning Performance: " + String.format("%.2f", gamesPerSecond) + " games/second");
    }
    
    @Test
    @Timeout(45)
    void testLearningProgression() {
        int initialSize = qLearningAI.getQTableSize();
        double initialQValue = qLearningAI.getQValue(game.getBoardStateKey(), "e2e4");
        
        // Simulate learning through multiple games
        for (int game_num = 0; game_num < 5; game_num++) {
            ChessGame trainingGame = new ChessGame();
            
            // Play a few moves to generate Q-table entries
            trainingGame.makeMove(6, 4, 4, 4); // e2-e4
            String stateKey = trainingGame.getBoardStateKey();
            
            // Update Q-value with positive reward for good opening
            qLearningAI.updateQValue(stateKey, "e2e4", 0.1);
            
            trainingGame.makeMove(1, 4, 3, 4); // e7-e5
            qLearningAI.updateQValue(trainingGame.getBoardStateKey(), "e7e5", 0.05);
        }
        
        // Verify learning occurred
        int finalSize = qLearningAI.getQTableSize();
        double finalQValue = qLearningAI.getQValue(game.getBoardStateKey(), "e2e4");
        
        assertTrue(finalSize > initialSize, "Q-table should grow with learning");
        assertTrue(finalQValue > initialQValue, "Q-values should improve with positive rewards");
        
        // Test epsilon-greedy strategy
        int[] exploitMove = qLearningAI.selectMove(game.getBoard(), game.getAllValidMoves(true), false);
        int[] exploreMove = qLearningAI.selectMove(game.getBoard(), game.getAllValidMoves(true), true);
        
        assertNotNull(exploitMove, "Exploitation should select best known move");
        assertNotNull(exploreMove, "Exploration should select valid move");
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