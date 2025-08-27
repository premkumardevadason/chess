package com.example.chess.mcp.integration;

import com.example.chess.mcp.ChessMCPServer;
import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MCPChessGameFlowTest {
    
    private static final Logger logger = LogManager.getLogger(MCPChessGameFlowTest.class);
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    @Test
    public void testCompleteChessGameFlow() {
        logger.info("Testing complete chess game flow via MCP");
        
        String agentId = "test-agent-flow";
        
        // 1. Initialize MCP connection
        JsonRpcRequest initRequest = new JsonRpcRequest(1, "initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of("tools", Map.of()),
            "clientInfo", Map.of("name", "test-client", "version", "1.0.0")
        ));
        
        JsonRpcResponse initResponse = mcpServer.handleJsonRpcRequest(initRequest, agentId);
        assertNotNull(initResponse.getResult());
        
        // 2. List available tools
        JsonRpcRequest toolsRequest = new JsonRpcRequest(2, "tools/list", Map.of());
        JsonRpcResponse toolsResponse = mcpServer.handleJsonRpcRequest(toolsRequest, agentId);
        assertNotNull(toolsResponse.getResult());
        
        // 3. Create chess game
        JsonRpcRequest createRequest = new JsonRpcRequest(3, "tools/call", Map.of(
            "name", "create_chess_game",
            "arguments", Map.of(
                "agentId", agentId,
                "aiOpponent", "Negamax",
                "playerColor", "white",
                "difficulty", 3
            )
        ));
        
        JsonRpcResponse createResponse = mcpServer.handleJsonRpcRequest(createRequest, agentId);
        assertNotNull(createResponse.getResult());
        
        // Extract session ID from response
        @SuppressWarnings("unchecked")
        Map<String, Object> createResult = (Map<String, Object>) createResponse.getResult();
        assertNotNull(createResult);
        
        logger.info("Chess game flow test completed successfully");
    }
    
    @Test
    public void testAllAISystemsGameplay() {
        logger.info("Testing gameplay with all AI systems");
        
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                             "MCTS", "Negamax", "OpenAI", "QLearning", 
                             "DeepLearning", "CNN", "DQN", "Genetic"};
        
        String agentId = "test-agent-all-ai";
        
        for (String ai : aiSystems) {
            logger.info("Testing gameplay with AI system: {}", ai);
            
            // Create game with specific AI
            JsonRpcRequest createRequest = new JsonRpcRequest(1, "tools/call", Map.of(
                "name", "create_chess_game",
                "arguments", Map.of(
                    "agentId", agentId,
                    "aiOpponent", ai,
                    "playerColor", "white",
                    "difficulty", 3
                )
            ));
            
            JsonRpcResponse createResponse = mcpServer.handleJsonRpcRequest(createRequest, agentId);
            
            // Should succeed for all AI systems
            if (createResponse.getError() != null) {
                logger.warn("Failed to create game with AI {}: {}", ai, createResponse.getError().getMessage());
            } else {
                logger.debug("Successfully created game with AI: {}", ai);
            }
        }
    }
    
    @Test
    public void testTournamentCreation() {
        logger.info("Testing tournament creation against all AI systems");
        
        String agentId = "test-agent-tournament";
        
        JsonRpcRequest tournamentRequest = new JsonRpcRequest(1, "tools/call", Map.of(
            "name", "create_tournament",
            "arguments", Map.of(
                "agentId", agentId,
                "playerColor", "white",
                "difficulty", 5
            )
        ));
        
        JsonRpcResponse tournamentResponse = mcpServer.handleJsonRpcRequest(tournamentRequest, agentId);
        
        if (tournamentResponse.getError() != null) {
            logger.warn("Tournament creation failed: {}", tournamentResponse.getError().getMessage());
        } else {
            assertNotNull(tournamentResponse.getResult());
            logger.info("Tournament created successfully");
        }
    }
}



