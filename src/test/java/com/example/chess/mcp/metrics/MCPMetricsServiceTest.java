package com.example.chess.mcp.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MCPMetricsServiceTest {
    
    private MCPMetricsService metricsService;
    
    @BeforeEach
    void setUp() {
        metricsService = new MCPMetricsService();
    }
    
    @Test
    void testRecordRequest() {
        metricsService.recordRequest("agent1", "create_chess_game", 100);
        
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        assertEquals(1, metrics.getTotalRequests());
        assertEquals(1, metrics.getToolCallCounts().get("create_chess_game").get());
        assertEquals(1, metrics.getAgentRequestCounts().get("agent1").get());
    }
    
    @Test
    void testRecordError() {
        metricsService.recordError("agent1", "make_chess_move", "invalid_move");
        
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        assertEquals(1, metrics.getTotalErrors());
    }
    
    @Test
    void testAverageResponseTime() {
        metricsService.recordRequest("agent1", "create_chess_game", 100);
        metricsService.recordRequest("agent1", "create_chess_game", 200);
        
        double avgTime = metricsService.getAverageResponseTime("create_chess_game");
        assertEquals(150.0, avgTime, 0.1);
    }
    
    @Test
    void testMultipleAgents() {
        metricsService.recordRequest("agent1", "create_chess_game", 100);
        metricsService.recordRequest("agent2", "make_chess_move", 50);
        
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        assertEquals(2, metrics.getTotalRequests());
        assertEquals(2, metrics.getAgentRequestCounts().size());
    }
}