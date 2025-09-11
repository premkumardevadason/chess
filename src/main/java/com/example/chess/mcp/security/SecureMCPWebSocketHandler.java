package com.example.chess.mcp.security;

import com.example.chess.mcp.MCPWebSocketHandler;
import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Secure WebSocket handler with Double Ratchet encryption
 */
@Component
public class SecureMCPWebSocketHandler extends MCPWebSocketHandler {
    
    private static final Logger logger = LogManager.getLogger(SecureMCPWebSocketHandler.class);
    
    @Autowired
    private MCPDoubleRatchetService doubleRatchetService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        super.afterConnectionEstablished(session);
        
        String agentId = getAgentId(session);
        if (agentId != null) {
            doubleRatchetService.establishSession(agentId);
            logger.info("Established secure session for agent: {}", agentId);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String agentId = getAgentId(session);
        if (agentId == null) {
            super.handleTextMessage(session, message);
            return;
        }
        
        try {
            // Try to parse as encrypted message
            EncryptedMCPMessage encryptedMessage = parseEncryptedMessage(message.getPayload());
            
            if (encryptedMessage.isEncrypted()) {
                // Decrypt message
                String decryptedPayload = doubleRatchetService.decryptMessage(agentId, encryptedMessage);
                TextMessage decryptedMessage = new TextMessage(decryptedPayload);
                super.handleTextMessage(session, decryptedMessage);
            } else {
                // Handle as plaintext for backward compatibility
                super.handleTextMessage(session, message);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to decrypt message from agent {}, treating as plaintext: {}", agentId, e.getMessage());
            super.handleTextMessage(session, message);
        }
    }
    
    @Override
    protected void sendMessage(WebSocketSession session, JsonRpcResponse response) throws Exception {
        String agentId = getAgentId(session);
        if (agentId == null) {
            super.sendMessage(session, response);
            return;
        }
        
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            
            // Encrypt response
            EncryptedMCPMessage encryptedResponse = doubleRatchetService.encryptMessage(agentId, responseJson);
            
            if (encryptedResponse.isEncrypted()) {
                // Send encrypted response
                String encryptedJson = createEncryptedJsonResponse(encryptedResponse);
                session.sendMessage(new TextMessage(encryptedJson));
            } else {
                // Send plaintext for backward compatibility
                super.sendMessage(session, response);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to encrypt response for agent {}, sending plaintext: {}", agentId, e.getMessage());
            super.sendMessage(session, response);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String agentId = getAgentId(session);
        if (agentId != null) {
            doubleRatchetService.removeSession(agentId);
            logger.info("Removed secure session for agent: {}", agentId);
        }
        
        super.afterConnectionClosed(session, status);
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
        return (String) session.getAttributes().get("agentId");
    }
}