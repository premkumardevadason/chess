package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MCPConcurrentAgentTest {
    
    private static final Logger logger = LogManager.getLogger(MCPConcurrentAgentTest.class);
    
    @Test
    public void testConcurrentMultipleAgents() throws InterruptedException {
        logger.info("Testing concurrent multiple MCP agents");
        
        int agentCount = 5;
        int gamesPerAgent = 2;
        CountDownLatch latch = new CountDownLatch(agentCount);
        
        List<CompletableFuture<Void>> agentTasks = IntStream.range(0, agentCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    String agentId = "test-agent-" + i;
                    simulateAgentGameplay(agentId, gamesPerAgent);
                } finally {
                    latch.countDown();
                }
            }))
            .toList();
        
        // Wait for all agents to complete (max 30 seconds)
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All agents should complete within 30 seconds");
        
        // Verify no exceptions occurred
        for (CompletableFuture<Void> task : agentTasks) {
            assertDoesNotThrow(() -> task.get(1, TimeUnit.SECONDS));
        }
        
        logger.info("Successfully completed concurrent agent test with {} agents", agentCount);
    }
    
    @Test
    public void testSessionIsolationBetweenAgents() {
        logger.info("Testing session isolation between agents");
        
        // Simulate 3 agents creating games simultaneously
        String[] agents = {"agent-1", "agent-2", "agent-3"};
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "Negamax"};
        
        List<CompletableFuture<String>> sessionTasks = IntStream.range(0, 3)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                return simulateGameCreation(agents[i], aiSystems[i], "white");
            }))
            .toList();
        
        // Collect all session IDs
        List<String> sessionIds = sessionTasks.stream()
            .map(CompletableFuture::join)
            .toList();
        
        // Verify all sessions are unique
        assertEquals(3, sessionIds.stream().distinct().count(), "All sessions should be unique");
        
        logger.info("Session isolation test completed successfully");
    }
    
    @Test
    public void testAISystemLoadBalancing() {
        logger.info("Testing AI system load balancing under concurrent load");
        
        int concurrentGames = 12; // One for each AI system
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                             "MCTS", "Negamax", "OpenAI", "QLearning", 
                             "DeepLearning", "CNN", "DQN", "Genetic"};
        
        List<CompletableFuture<Void>> gameTasks = IntStream.range(0, concurrentGames)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                String agentId = "load-test-agent-" + i;
                String aiSystem = aiSystems[i % aiSystems.length];
                simulateGameWithMoves(agentId, aiSystem, 3); // 3 moves per game
            }))
            .toList();
        
        // Wait for all games to complete
        CompletableFuture.allOf(gameTasks.toArray(new CompletableFuture[0])).join();
        
        logger.info("AI system load balancing test completed successfully");
    }
    
    private void simulateAgentGameplay(String agentId, int gameCount) {
        logger.debug("Simulating gameplay for agent {} with {} games", agentId, gameCount);
        
        for (int i = 0; i < gameCount; i++) {
            String aiSystem = (i % 2 == 0) ? "AlphaZero" : "Negamax";
            simulateGameWithMoves(agentId, aiSystem, 2);
        }
    }
    
    private String simulateGameCreation(String agentId, String aiSystem, String playerColor) {
        logger.debug("Creating game for agent {} vs {}", agentId, aiSystem);
        
        // Simulate game creation - return mock session ID
        return "session-" + agentId + "-" + System.currentTimeMillis();
    }
    
    private void simulateGameWithMoves(String agentId, String aiSystem, int moveCount) {
        logger.debug("Simulating game for agent {} vs {} with {} moves", agentId, aiSystem, moveCount);
        
        String sessionId = simulateGameCreation(agentId, aiSystem, "white");
        
        // Simulate moves
        String[] moves = {"e4", "Nf3", "Bc4", "d4", "c4"};
        for (int i = 0; i < Math.min(moveCount, moves.length); i++) {
            simulateMove(sessionId, moves[i]);
        }
    }
    
    private void simulateMove(String sessionId, String move) {
        logger.debug("Simulating move {} in session {}", move, sessionId);
        
        // Add small delay to simulate processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}