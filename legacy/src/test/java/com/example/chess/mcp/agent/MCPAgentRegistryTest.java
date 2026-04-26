package com.example.chess.mcp.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MCPAgentRegistryTest {
    
    private MCPAgentRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new MCPAgentRegistry();
    }
    
    @Test
    void testRegisterAgent() {
        String agentId = registry.registerAgent("test-client", "stdio");
        
        assertNotNull(agentId);
        assertTrue(agentId.startsWith("agent-"));
        assertEquals(1, registry.getActiveAgentCount());
    }
    
    @Test
    void testGetAgent() {
        String agentId = registry.registerAgent("test-client", "stdio");
        MCPAgentRegistry.MCPAgent agent = registry.getAgent(agentId);
        
        assertNotNull(agent);
        assertEquals(agentId, agent.getAgentId());
        assertEquals("test-client", agent.getClientInfo());
        assertEquals("stdio", agent.getTransport());
    }
    
    @Test
    void testRemoveAgent() {
        String agentId = registry.registerAgent("test-client", "stdio");
        assertEquals(1, registry.getActiveAgentCount());
        
        registry.removeAgent(agentId);
        assertEquals(0, registry.getActiveAgentCount());
        assertNull(registry.getAgent(agentId));
    }
    
    @Test
    void testUpdateAgentActivity() {
        String agentId = registry.registerAgent("test-client", "stdio");
        
        assertDoesNotThrow(() -> registry.updateAgentActivity(agentId));
    }
    
    @Test
    void testGetActiveAgents() {
        String agentId1 = registry.registerAgent("client1", "stdio");
        String agentId2 = registry.registerAgent("client2", "websocket");
        
        assertEquals(2, registry.getActiveAgents().size());
        assertEquals(2, registry.getActiveAgentCount());
    }
}



