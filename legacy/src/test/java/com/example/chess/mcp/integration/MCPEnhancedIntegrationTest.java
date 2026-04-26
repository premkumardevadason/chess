package com.example.chess.mcp.integration;

import com.example.chess.mcp.ChessMCPServer;
import com.example.chess.mcp.agent.MCPAgentRegistry;
import com.example.chess.mcp.metrics.MCPMetricsService;
import com.example.chess.mcp.notifications.MCPNotificationService;
import com.example.chess.mcp.ratelimit.MCPRateLimiter;
import com.example.chess.mcp.resources.ChessResourceProvider;
import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MCPEnhancedIntegrationTest {
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    @Autowired
    private MCPAgentRegistry agentRegistry;
    
    @Autowired
    private MCPMetricsService metricsService;
    
    @Autowired
    private MCPNotificationService notificationService;
    
    @Autowired
    private MCPRateLimiter rateLimiter;
    
    @Autowired
    private ChessResourceProvider resourceProvider;
    
    @Test
    public void testEnhancedMCPComponents() {
        // Test agent registry
        String agentId = agentRegistry.registerAgent("test-client", "stdio");
        assertNotNull(agentId);
        assertEquals(1, agentRegistry.getActiveAgentCount());
        
        // Test rate limiter
        assertTrue(rateLimiter.allowRequest(agentId, "general"));
        MCPRateLimiter.RateLimitStatus status = rateLimiter.getStatus(agentId);
        assertEquals(1, status.getCurrentRequests());
        
        // Test notifications
        AtomicInteger notificationCount = new AtomicInteger(0);
        MCPNotificationService.NotificationListener listener = notification -> 
            notificationCount.incrementAndGet();
        
        notificationService.subscribe(agentId, listener);
        notificationService.notifyGameMove(agentId, "session1", "e4", "e5");
        assertEquals(1, notificationCount.get());
        
        // Test metrics
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        assertNotNull(metrics);
        
        // Test server status
        Map<String, Object> serverStatus = mcpServer.getServerStatus();
        assertNotNull(serverStatus);
        assertTrue(serverStatus.containsKey("activeAgents"));
        assertTrue(serverStatus.containsKey("totalRequests"));
    }
    
    @Test
    public void testIntegratedMCPRequest() {
        String agentId = "test-agent-enhanced";
        
        // Test initialize with enhanced features
        JsonRpcRequest initRequest = new JsonRpcRequest(1, "initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "clientInfo", Map.of("name", "enhanced-test-client", "version", "1.0.0")
        ));
        
        JsonRpcResponse initResponse = mcpServer.handleJsonRpcRequest(initRequest, agentId);
        assertNotNull(initResponse.getResult());
        
        // Verify metrics were recorded
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        assertTrue(metrics.getTotalRequests() > 0);
        
        // Test resource reading with enhanced provider
        JsonRpcRequest resourceRequest = new JsonRpcRequest(2, "resources/read", Map.of(
            "uri", "chess://ai-systems"
        ));
        
        JsonRpcResponse resourceResponse = mcpServer.handleJsonRpcRequest(resourceRequest, agentId);
        assertNotNull(resourceResponse.getResult());
    }
    
    @Test
    public void testRateLimitingIntegration() {
        String agentId = "test-agent-rate-limit";
        
        // Make requests up to burst limit
        for (int i = 0; i < 10; i++) {
            JsonRpcRequest request = new JsonRpcRequest(i, "tools/list", Map.of());
            JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
            assertNotNull(response.getResult());
        }
        
        // Next request should be rate limited
        JsonRpcRequest request = new JsonRpcRequest(11, "tools/list", Map.of());
        JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
        assertNotNull(response.getError());
        assertEquals(-32099, response.getError().getCode());
    }
}



