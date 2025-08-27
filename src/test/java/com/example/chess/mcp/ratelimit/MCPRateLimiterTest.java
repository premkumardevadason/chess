package com.example.chess.mcp.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MCPRateLimiterTest {
    
    private MCPRateLimiter rateLimiter;
    
    @BeforeEach
    void setUp() {
        rateLimiter = new MCPRateLimiter();
    }
    
    @Test
    void testAllowRequest() {
        assertTrue(rateLimiter.allowRequest("agent1", "general"));
        assertTrue(rateLimiter.allowRequest("agent1", "general"));
    }
    
    @Test
    void testRateLimitStatus() {
        rateLimiter.allowRequest("agent1", "general");
        rateLimiter.allowRequest("agent1", "general");
        
        MCPRateLimiter.RateLimitStatus status = rateLimiter.getStatus("agent1");
        assertEquals(2, status.getCurrentRequests());
        assertEquals(100, status.getMaxRequests());
        assertEquals(98, status.getRemainingRequests());
    }
    
    @Test
    void testBurstLimit() {
        String agentId = "agent1";
        
        // Should allow up to burst limit
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.allowRequest(agentId, "burst"));
        }
        
        // Should deny after burst limit
        assertFalse(rateLimiter.allowRequest(agentId, "burst"));
    }
    
    @Test
    void testMoveLimit() {
        String agentId = "agent1";
        
        // Should allow moves up to limit
        for (int i = 0; i < 60; i++) {
            assertTrue(rateLimiter.allowRequest(agentId, "move"));
        }
        
        // Should deny after move limit
        assertFalse(rateLimiter.allowRequest(agentId, "move"));
    }
    
    @Test
    void testDifferentAgents() {
        assertTrue(rateLimiter.allowRequest("agent1", "general"));
        assertTrue(rateLimiter.allowRequest("agent2", "general"));
        
        MCPRateLimiter.RateLimitStatus status1 = rateLimiter.getStatus("agent1");
        MCPRateLimiter.RateLimitStatus status2 = rateLimiter.getStatus("agent2");
        
        assertEquals(1, status1.getCurrentRequests());
        assertEquals(1, status2.getCurrentRequests());
    }
}