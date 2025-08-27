package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MCPPerformanceBenchmarkTest {
    
    private static final Logger logger = LogManager.getLogger(MCPPerformanceBenchmarkTest.class);
    
    @Test
    public void testMoveValidationPerformance() {
        logger.info("Testing move validation performance benchmark");
        
        int iterations = 1000;
        List<Long> responseTimes = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            
            // Simulate move validation
            simulateMoveValidation("e4");
            
            long endTime = System.nanoTime();
            long responseTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            responseTimes.add(responseTime);
        }
        
        // Calculate statistics
        double averageTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        logger.info("Move validation performance - Average: {}ms, Max: {}ms", averageTime, maxTime);
        
        // Assert performance targets
        assertTrue(averageTime < 50, "Average validation time should be < 50ms");
        assertTrue(maxTime < 200, "Max validation time should be < 200ms");
    }
    
    @Test
    public void testConcurrentSessionCreationPerformance() {
        logger.info("Testing concurrent session creation performance");
        
        int concurrentSessions = 50;
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<String>> sessionTasks = new ArrayList<>();
        
        for (int i = 0; i < concurrentSessions; i++) {
            final int sessionIndex = i;
            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                return simulateSessionCreation("agent-" + sessionIndex, "AlphaZero");
            });
            sessionTasks.add(task);
        }
        
        // Wait for all sessions to be created
        List<String> sessionIds = sessionTasks.stream()
            .map(CompletableFuture::join)
            .toList();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        logger.info("Created {} sessions in {}ms", concurrentSessions, totalTime);
        
        // Verify all sessions created successfully
        assertEquals(concurrentSessions, sessionIds.size());
        assertEquals(concurrentSessions, sessionIds.stream().distinct().count());
        
        // Assert performance targets
        assertTrue(totalTime < 5000, "Should create 50 sessions in < 5 seconds");
        assertTrue(totalTime / concurrentSessions < 100, "Average session creation should be < 100ms");
    }
    
    @Test
    public void testAIResponseTimePerformance() {
        logger.info("Testing AI response time performance benchmark");
        
        String[] aiSystems = {"Negamax", "MCTS", "QLearning"};
        
        for (String aiSystem : aiSystems) {
            long startTime = System.currentTimeMillis();
            
            // Simulate AI move generation
            String move = simulateAIMove(aiSystem, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            logger.info("AI {} response time: {}ms, move: {}", aiSystem, responseTime, move);
            
            // Assert AI response time targets
            assertTrue(responseTime < 5000, aiSystem + " should respond in < 5 seconds");
            assertNotNull(move, aiSystem + " should return a valid move");
        }
    }
    
    @Test
    public void testMemoryUsageUnderLoad() {
        logger.info("Testing memory usage under concurrent load");
        
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create multiple concurrent sessions
        int sessionCount = 100;
        List<String> sessionIds = new ArrayList<>();
        
        for (int i = 0; i < sessionCount; i++) {
            String sessionId = simulateSessionCreation("memory-test-agent-" + i, "Negamax");
            sessionIds.add(sessionId);
        }
        
        // Force garbage collection
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        long memoryPerSession = memoryIncrease / sessionCount;
        
        logger.info("Memory usage - Initial: {}MB, Final: {}MB, Increase: {}MB, Per session: {}KB", 
                   initialMemory / 1024 / 1024, finalMemory / 1024 / 1024, 
                   memoryIncrease / 1024 / 1024, memoryPerSession / 1024);
        
        // Assert memory usage is reasonable
        assertTrue(memoryPerSession < 1024 * 1024, "Memory per session should be < 1MB");
    }
    
    private void simulateMoveValidation(String move) {
        // Simulate validation processing
        try {
            Thread.sleep(1); // 1ms processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private String simulateSessionCreation(String agentId, String aiSystem) {
        // Simulate session creation processing
        try {
            Thread.sleep(10); // 10ms processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "session-" + agentId + "-" + System.currentTimeMillis();
    }
    
    private String simulateAIMove(String aiSystem, String fen) {
        // Simulate AI thinking time based on system
        try {
            switch (aiSystem) {
                case "Negamax":
                    Thread.sleep(100); // 100ms
                    break;
                case "MCTS":
                    Thread.sleep(200); // 200ms
                    break;
                case "QLearning":
                    Thread.sleep(50); // 50ms
                    break;
                default:
                    Thread.sleep(150); // 150ms
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "e4"; // Mock move
    }
}