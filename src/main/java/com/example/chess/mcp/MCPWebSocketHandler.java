package com.example.chess.mcp;

import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Component
public class MCPWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LogManager.getLogger(MCPWebSocketHandler.class);
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, String> sessionAgentMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String agentId = generateAgentId();
        sessionAgentMap.put(session.getId(), agentId);
        logger.info("MCP WebSocket connection established - Session: {}, Agent: {}", session.getId(), agentId);
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            String agentId = sessionAgentMap.get(session.getId());
            
            try {
                JsonRpcRequest request = objectMapper.readValue(payload, JsonRpcRequest.class);
                logger.debug("MCP WebSocket request from {}: {}", agentId, request.getMethod());
                
                JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
                String responseJson = objectMapper.writeValueAsString(response);
                
                session.sendMessage(new TextMessage(responseJson));
                
            } catch (Exception e) {
                logger.error("Error processing MCP WebSocket message: {}", e.getMessage());
                JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            }
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String agentId = sessionAgentMap.get(session.getId());
        logger.error("MCP WebSocket transport error for agent {}: {}", agentId, exception.getMessage());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String agentId = sessionAgentMap.remove(session.getId());
        logger.info("MCP WebSocket connection closed - Agent: {}, Status: {}", agentId, closeStatus);
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    private String generateAgentId() {
        return "mcp-agent-" + UUID.randomUUID().toString().substring(0, 8);
    }
}