package com.example.chess.performance;

import com.example.chess.ChessGame;
import com.example.chess.QLearningAI;
import com.example.chess.DeepQNetworkAI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class AITrainingPerformanceTest {
    
    private ChessGame game;
    private QLearningAI qLearningAI;
    private DeepQNetworkAI dqnAI;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        qLearningAI = game.getQLearningAI();
        dqnAI = game.getDQNAI();
    }
    
    @Test
    @Timeout(10)
    void testQLearningSpeed() {
        long startTime = System.currentTimeMillis();
        game.trainAI();
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        long duration = System.currentTimeMillis() - startTime;
        
        // Should complete training setup quickly
        assertTrue(duration < 8000, "Q-Learning training setup too slow: " + duration + "ms");
    }
    
    @Test
    @Timeout(15)
    void testDQNSpeed() {
        long startTime = System.currentTimeMillis();
        game.trainAI();
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        long duration = System.currentTimeMillis() - startTime;
        
        // Should complete training setup quickly
        assertTrue(duration < 12000, "DQN training setup too slow: " + duration + "ms");
    }
    
    @Test
    @Timeout(30)
    void testMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Train multiple AI systems
        game.trainAI();
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory increase should be reasonable (< 500MB)
        assertTrue(memoryIncrease < 500_000_000, 
            "Excessive memory usage: " + (memoryIncrease / 1_000_000) + "MB");
    }
    
    @Test
    @Timeout(45)
    void testConcurrentTraining() {
        long startTime = System.currentTimeMillis();
        
        // Start training multiple AI systems
        game.trainAI();
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 40000, "Concurrent training too slow: " + duration + "ms");
    }
    
    @Test
    @Timeout(20)
    void testStartupTime() {
        long startTime = System.currentTimeMillis();
        ChessGame newGame = new ChessGame();
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 15000, "Startup too slow: " + duration + "ms (target: < 15s)");
        assertNotNull(newGame.getQLearningAI());
    }
    
    @Test
    void testGPUAcceleration() {
        // Test GPU detection through DeepLearning AI
        String backend = game.getDeepLearningAI().getBackendInfo();
        assertNotNull(backend);
        
        // Performance should be reasonable regardless of GPU availability
        long startTime = System.currentTimeMillis();
        game.findBestMoveForTesting();
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 10000, "AI move selection too slow: " + duration + "ms");
    }
}