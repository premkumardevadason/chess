package com.example.chess.mcp.security;

import com.example.chess.mcp.MCPWebSocketHandler;
import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * Secure WebSocket handler with Double Ratchet encryption
 */
@Component
@ConditionalOnProperty(name = "mcp.encryption.enabled", havingValue = "true", matchIfMissing = false)
public class SecureMCPWebSocketHandler extends MCPWebSocketHandler {
    
    private static final Logger logger = LogManager.getLogger(SecureMCPWebSocketHandler.class);
    
    @Autowired
    private DoubleRatchetService doubleRatchetService;
    
    @Autowired
    private com.example.chess.mcp.ChessMCPServer mcpServer;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        
        String agentId = getAgentId(session);
        if (agentId != null) {
            doubleRatchetService.establishSession(agentId);
            logger.info("Established secure session for agent: {}", agentId);
        }
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (!(message instanceof TextMessage)) {
            super.handleMessage(session, message);
            return;
        }
        
        String agentId = getAgentId(session);
        if (agentId == null) {
            super.handleMessage(session, message);
            return;
        }
        
        try {
            String payload = ((TextMessage) message).getPayload();
            
            // Try to parse as encrypted message
            EncryptedMCPMessage encryptedMessage = parseEncryptedMessage(payload);
            
            if (encryptedMessage.isEncrypted()) {
                // Decrypt message and process
                String decryptedPayload = doubleRatchetService.decryptMessage(agentId, encryptedMessage);
                processDecryptedMessage(session, agentId, decryptedPayload);
            } else {
                // Handle as plaintext for backward compatibility
                super.handleMessage(session, message);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to decrypt message from agent {}, treating as plaintext: {}", agentId, e.getMessage());
            super.handleMessage(session, message);
        }
    }
    
    private void processDecryptedMessage(WebSocketSession session, String agentId, String payload) throws Exception {
        try {
            JsonRpcRequest request = objectMapper.readValue(payload, JsonRpcRequest.class);
            logger.debug("Decrypted MCP request from {}: {}", agentId, request.getMethod());
            
            JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
            
            // Encrypt response
            String responseJson = objectMapper.writeValueAsString(response);
            EncryptedMCPMessage encryptedResponse = doubleRatchetService.encryptMessage(agentId, responseJson);
            
            if (encryptedResponse.isEncrypted()) {
                String encryptedJson = createEncryptedJsonResponse(encryptedResponse);
                session.sendMessage(new TextMessage(encryptedJson));
            } else {
                session.sendMessage(new TextMessage(responseJson));
            }
            
        } catch (Exception e) {
            logger.error("Error processing decrypted MCP message: {}", e.getMessage());
            JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String agentId = getAgentId(session);
        if (agentId != null) {
            doubleRatchetService.removeSession(agentId);
            logger.info("Removed secure session for agent: {}", agentId);
        }
        
        super.afterConnectionClosed(session, closeStatus);
    }
    
    private EncryptedMCPMessage parseEncryptedMessage(String payload) throws Exception {
        try {
            JsonRpcRequest request = objectMapper.readValue(payload, JsonRpcRequest.class);
            
            // Check if message has encryption markers
            if (request.getParams() != null && request.getParams().containsKey("encrypted")) {
                Boolean encrypted = (Boolean) request.getParams().get("encrypted");
                if (encrypted != null && encrypted) {
                    String ciphertext = (String) request.getParams().get("ciphertext");
                    String iv = (String) request.getParams().get("iv");
                    
                    // Parse ratchet header
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> headerMap = (java.util.Map<String, Object>) request.getParams().get("ratchet_header");
                    RatchetHeader header = null;
                    if (headerMap != null) {
                        header = new RatchetHeader(
                            (String) headerMap.get("dh_public_key"),
                            (Integer) headerMap.get("previous_counter"),
                            (Integer) headerMap.get("message_counter")
                        );
                    }
                    
                    return new EncryptedMCPMessage(ciphertext, iv, header, true);
                }
            }
            
            // Not encrypted, return as plaintext
            return new EncryptedMCPMessage(payload, false);
            
        } catch (Exception e) {
            // If parsing fails, treat as plaintext
            return new EncryptedMCPMessage(payload, false);
        }
    }
    
    private String createEncryptedJsonResponse(EncryptedMCPMessage encryptedMessage) throws Exception {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("encrypted", true);
        response.put("ciphertext", encryptedMessage.getCiphertext());
        response.put("iv", encryptedMessage.getIv());
        
        if (encryptedMessage.getHeader() != null) {
            java.util.Map<String, Object> headerMap = new java.util.HashMap<>();
            headerMap.put("dh_public_key", encryptedMessage.getHeader().getDhPublicKey());
            headerMap.put("previous_counter", encryptedMessage.getHeader().getPreviousCounter());
            headerMap.put("message_counter", encryptedMessage.getHeader().getMessageCounter());
            response.put("ratchet_header", headerMap);
        }
        
        return objectMapper.writeValueAsString(response);
    }
    
    private String getAgentId(WebSocketSession session) {
        // Access the sessionAgentMap from parent class via reflection or use session ID
        return "mcp-agent-" + session.getId().substring(0, 8);
    }
}