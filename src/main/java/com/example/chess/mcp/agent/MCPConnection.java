package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single MCP connection
 */
public class MCPConnection {
    
    private final String sessionId;
    private final WebSocket webSocket;
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean healthy = true;
    private volatile String agentId;
    
    public MCPConnection(String sessionId, WebSocket webSocket) {
        this.sessionId = sessionId;
        this.webSocket = webSocket;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public WebSocket getWebSocket() {
        return webSocket;
    }
    
    public void addPendingRequest(long requestId, CompletableFuture<JsonNode> future) {
        pendingRequests.put(requestId, future);
    }
    
    public CompletableFuture<JsonNode> removePendingRequest(long requestId) {
        return pendingRequests.remove(requestId);
    }
    
    public boolean isHealthy() {
        return healthy && !webSocket.isOutputClosed() && !webSocket.isInputClosed();
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public void close() {
        healthy = false;
        
        // Complete all pending requests with error
        pendingRequests.values().forEach(future -> 
            future.completeExceptionally(new RuntimeException("Connection closed"))
        );
        pendingRequests.clear();
        
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Agent shutdown");
    }
}