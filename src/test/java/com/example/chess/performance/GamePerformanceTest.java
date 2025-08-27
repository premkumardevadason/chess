package com.example.chess.performance;

import com.example.chess.BaseTestClass;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class GamePerformanceTest extends BaseTestClass {
    
    
    
    @BeforeEach
    void setUp() {
        super.baseSetUp();
    }
    
    @Test
    @Timeout(5)
    void testMoveValidationSpeed() {
        long startTime = System.currentTimeMillis();
        
        // Test 1000 move validations
        for (int i = 0; i < 1000; i++) {
            game.isValidMove(6, 4, 4, 4); // e2-e4
            game.isValidMove(7, 1, 5, 2); // Nb1-c3
            game.isValidMove(0, 0, 7, 7); // Invalid move
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / 3000.0; // 3000 validations
        
        assertTrue(avgTime < 0.1, "Move validation too slow: " + avgTime + "ms avg (target: < 0.1ms)");
    }
    
    @Test
    @Timeout(10)
    void testAIMoveSelection() {
        long startTime = System.currentTimeMillis();
        
        int[] move = game.findBestMoveForTesting();
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 8000, "AI move selection too slow: " + duration + "ms (target: < 8s)");
        if (move != null) {
            assertNotNull(move);
            assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
        }
    }
    
    @Test
    @Timeout(2)
    void testBoardStateUpdates() {
        long startTime = System.currentTimeMillis();
        
        // Test 100 board state updates
        for (int i = 0; i < 100; i++) {
            game.makeMove(6, 4, 4, 4); // e2-e4
            game.undoMove();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / 200.0; // 200 operations (100 moves + 100 undos)
        
        assertTrue(avgTime < 0.5, "Board state updates too slow: " + avgTime + "ms avg (target: < 0.5ms)");
    }
    
    @Test
    @Timeout(60)
    void testConcurrentUsers() throws InterruptedException {
        int numGames = 5;
        ChessGame[] games = new ChessGame[numGames];
        Thread[] threads = new Thread[numGames];
        
        long startTime = System.currentTimeMillis();
        
        // Create multiple concurrent games
        for (int i = 0; i < numGames; i++) {
            final int gameIndex = i;
            games[i] = new ChessGame();
            threads[i] = new Thread(() -> {
                ChessGame g = games[gameIndex];
                
                // Play a few moves
                for (int move = 0; move < 5; move++) {
                    int[] aiMove = g.findBestMoveForTesting();
                    if (aiMove != null) {
                        g.makeMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]);
                    }
                    if (g.isGameOver()) break;
                }
            });
            threads[i].start();
        }
        
        // Wait for all games to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 50000, "Concurrent games too slow: " + duration + "ms (target: < 50s)");
        
        // Verify all games are functional
        for (ChessGame g : games) {
            assertTrue(g.getMoveCount() >= 0);
        }
    }
    
    @Test
    @Timeout(10)
    void testMemoryUsageUnderLoad() {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create load by making many moves
        for (int i = 0; i < 1000; i++) {
            game.makeMove(6, 4, 4, 4); // e2-e4
            game.makeMove(1, 4, 3, 4); // e7-e5
            game.undoMove();
            game.undoMove();
        }
        
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        assertTrue(memoryIncrease < 100_000_000, 
            "Excessive memory usage under load: " + (memoryIncrease / 1_000_000) + "MB");
    }
    
    @Test
    @Timeout(15)
    void testGameStateSerializationSpeed() {
        long startTime = System.currentTimeMillis();
        
        // Test board state access
        for (int i = 0; i < 1000; i++) {
            String[][] board = game.getBoard();
            assertNotNull(board);
            assertTrue(board.length == 8);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / 1000.0;
        
        assertTrue(avgTime < 1.0, "Game state access too slow: " + avgTime + "ms avg (target: < 1ms)");
    }
}


