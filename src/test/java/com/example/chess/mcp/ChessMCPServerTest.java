package com.example.chess.mcp;

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
public class ChessMCPServerTest {
    
    private static final Logger logger = LogManager.getLogger(ChessMCPServerTest.class);
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    @Test
    public void testInitializeHandshake() {
        logger.info("Testing MCP initialize handshake");
        
        JsonRpcRequest request = new JsonRpcRequest(1, "initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of("tools", Map.of()),
            "clientInfo", Map.of("name", "test-client", "version", "1.0.0")
        ));
        
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, "test-agent");
        
        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId());
        assertNotNull(response.getResult());
        assertNull(response.getError());
    }
    
    @Test
    public void testToolsList() {
        logger.info("Testing MCP tools list");
        
        JsonRpcRequest request = new JsonRpcRequest(2, "tools/list", Map.of());
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, "test-agent");
        
        assertNotNull(response);
        assertNotNull(response.getResult());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("tools"));
    }
    
    @Test
    public void testResourcesList() {
        logger.info("Testing MCP resources list");
        
        JsonRpcRequest request = new JsonRpcRequest(3, "resources/list", Map.of());
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, "test-agent");
        
        assertNotNull(response);
        assertNotNull(response.getResult());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("resources"));
    }
    
    @Test
    public void testCreateChessGame() {
        logger.info("Testing create chess game tool");
        
        JsonRpcRequest request = new JsonRpcRequest(4, "tools/call", Map.of(
            "name", "create_chess_game",
            "arguments", Map.of(
                "agentId", "test-agent",
                "aiOpponent", "Negamax",
                "playerColor", "white",
                "difficulty", 5
            )
        ));
        
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, "test-agent");
        
        assertNotNull(response);
        assertNull(response.getError());
        assertNotNull(response.getResult());
    }
    
    @Test
    public void testInvalidMethod() {
        logger.info("Testing invalid method handling");
        
        JsonRpcRequest request = new JsonRpcRequest(5, "invalid/method", Map.of());
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, "test-agent");
        
        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32601, response.getError().getCode());
    }
}