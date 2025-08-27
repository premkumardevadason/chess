package com.example.chess.mcp.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MCPAgentRegistry {
    
    private static final Logger logger = LogManager.getLogger(MCPAgentRegistry.class);
    
    private final ConcurrentHashMap<String, MCPAgent> activeAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> agentLastActivity = new ConcurrentHashMap<>();
    
    public String registerAgent(String clientInfo, String transport) {
        String agentId = generateAgentId();
        MCPAgent agent = new MCPAgent(agentId, clientInfo, transport);
        
        activeAgents.put(agentId, agent);
        agentLastActivity.put(agentId, LocalDateTime.now());
        
        logger.info("Registered new MCP agent: {} via {}", agentId, transport);
        return agentId;
    }
    
    public void updateAgentActivity(String agentId) {
        agentLastActivity.put(agentId, LocalDateTime.now());
    }
    
    public List<MCPAgent> getActiveAgents() {
        return new ArrayList<>(activeAgents.values());
    }
    
    public int getActiveAgentCount() {
        return activeAgents.size();
    }
    
    public MCPAgent getAgent(String agentId) {
        return activeAgents.get(agentId);
    }
    
    public void removeAgent(String agentId) {
        activeAgents.remove(agentId);
        agentLastActivity.remove(agentId);
        logger.info("Removed agent: {}", agentId);
    }
    
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupInactiveAgents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        
        agentLastActivity.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                String agentId = entry.getKey();
                activeAgents.remove(agentId);
                logger.info("Removed inactive agent: {}", agentId);
                return true;
            }
            return false;
        });
    }
    
    private String generateAgentId() {
        return "agent-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    public static class MCPAgent {
        private final String agentId;
        private final String clientInfo;
        private final String transport;
        private final LocalDateTime registeredAt;
        
        public MCPAgent(String agentId, String clientInfo, String transport) {
            this.agentId = agentId;
            this.clientInfo = clientInfo;
            this.transport = transport;
            this.registeredAt = LocalDateTime.now();
        }
        
        public String getAgentId() { return agentId; }
        public String getClientInfo() { return clientInfo; }
        public String getTransport() { return transport; }
        public LocalDateTime getRegisteredAt() { return registeredAt; }
    }
}