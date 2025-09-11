package com.example.chess.mcp;

import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.context.annotation.Bean;
import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MCPTransportService {
    
    private static final Logger logger = LogManager.getLogger(MCPTransportService.class);
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public void startStdioTransport() {
        logger.info("Starting MCP Chess server via stdio transport");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);
        
        String agentId = generateAgentId();
        logger.info("Generated agent ID: {}", agentId);
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonRpcRequest request = objectMapper.readValue(line, JsonRpcRequest.class);
                    logger.debug("Received request: {}", request.getMethod());
                    
                    JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
                    String responseJson = objectMapper.writeValueAsString(response);
                    
                    writer.println(responseJson);
                    writer.flush();
                    
                } catch (Exception e) {
                    logger.error("Error processing request: {}", e.getMessage());
                    JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                    writer.flush();
                }
            }
        } catch (IOException e) {
            logger.error("MCP stdio transport error: {}", e.getMessage());
        }
        
        logger.info("MCP stdio transport stopped");
    }
    
    public void startWebSocketTransport(int port) {
        logger.info("Starting MCP Chess server via WebSocket transport on port {}", port);
        
        try {
            MCPWebSocketServer server = new MCPWebSocketServer(new InetSocketAddress(port), mcpServer, objectMapper);
            server.start();
            
            logger.info("MCP WebSocket server started successfully on port {}", port);
            logger.info("MCP WebSocket endpoint available at: ws://localhost:{}/", port);
            
            // Run in separate thread to avoid blocking
            Thread serverThread = new Thread(() -> {
                try {
                    Thread.currentThread().join();
                } catch (InterruptedException e) {
                    logger.info("MCP WebSocket server thread interrupted");
                }
            });
            serverThread.setDaemon(false);
            serverThread.start();
            
        } catch (Exception e) {
            logger.error("Failed to start MCP WebSocket server: {}", e.getMessage(), e);
        }
    }
    
    private String generateAgentId() {
        return "mcp-agent-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private static class MCPWebSocketServer extends WebSocketServer {
        private final ChessMCPServer mcpServer;
        private final ObjectMapper objectMapper;
        private final ConcurrentHashMap<WebSocket, String> connectionAgentMap = new ConcurrentHashMap<>();
        private final com.example.chess.mcp.security.MCPDoubleRatchetService doubleRatchetService = new com.example.chess.mcp.security.MCPDoubleRatchetService();
        private static final Logger logger = LogManager.getLogger(MCPWebSocketServer.class);
        
        public MCPWebSocketServer(InetSocketAddress address, ChessMCPServer mcpServer, ObjectMapper objectMapper) {
            super(address);
            this.mcpServer = mcpServer;
            this.objectMapper = objectMapper;
        }
        
        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String agentId = "mcp-agent-" + UUID.randomUUID().toString().substring(0, 8);
            connectionAgentMap.put(conn, agentId);
            // Don't establish encryption here - wait for first encrypted message
            logger.info("MCP WebSocket connection opened - Agent: {}", agentId);
        }
        
        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            String agentId = connectionAgentMap.remove(conn);
            if (agentId != null) {
                doubleRatchetService.removeSession(agentId);
            }
            logger.info("MCP WebSocket connection closed - Agent: {}, Reason: {}", agentId, reason);
        }
        
        @Override
        public void onMessage(WebSocket conn, String message) {
            String agentId = connectionAgentMap.get(conn);
            logger.info("MCP WebSocket received message from {}: {}", agentId, message.substring(0, Math.min(100, message.length())) + "...");
            try {
                JsonRpcRequest request = objectMapper.readValue(message, JsonRpcRequest.class);
                
                // Check if message is encrypted
                if (request.isEncrypted()) {
                    logger.info("🔒 Received encrypted MCP message from {}: decrypting...", agentId);
                    
                    try {
                        // Establish session on first encrypted message using agent ID
                        if (!doubleRatchetService.hasSession(agentId)) {
                            doubleRatchetService.establishSession(agentId, true); // Server mode
                            logger.info("🔐 HKDF Double Ratchet SERVER session established for: {}", agentId);
                        }
                        
                        // Extract encryption details
                        String ciphertext = request.getCiphertext();
                        
                        // Create encrypted message object
                        com.example.chess.mcp.security.EncryptedMCPMessage encMsg = 
                            new com.example.chess.mcp.security.EncryptedMCPMessage(ciphertext, true);
                        
                        // Decrypt using HKDF Double Ratchet
                        String decryptedJson = doubleRatchetService.decryptMessage(agentId, encMsg);
                        request = objectMapper.readValue(decryptedJson, JsonRpcRequest.class);
                        logger.info("🔓 HKDF Double Ratchet decrypted message from {}: {} (ID: {})", agentId, request.getMethod(), request.getId());
                        
                    } catch (Exception e) {
                        logger.error("❌ Failed to decrypt message from {}: {}", agentId, e.getMessage());
                        JsonRpcResponse errorResponse = JsonRpcResponse.error(
                            request.getId(), 
                            -32002, 
                            "Decryption failed: " + e.getMessage()
                        );
                        String errorJson = objectMapper.writeValueAsString(errorResponse);
                        conn.send(errorJson);
                        return;
                    }
                } else {
                    logger.info("📝 Received plaintext MCP message from {}: {} (ID: {})", agentId, request.getMethod(), request.getId());
                }
                
                JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
                String responseJson = objectMapper.writeValueAsString(response);
                
                // Encrypt response if original message was encrypted
                if (request.isEncrypted()) {
                    com.example.chess.mcp.security.EncryptedMCPMessage encResponse = 
                        doubleRatchetService.encryptMessage(agentId, responseJson);
                    
                    String encryptedJson = String.format(
                        "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\"}",
                        encResponse.getCiphertext()
                    );
                    
                    logger.info("🔒 Sending Signal Protocol encrypted response to {}", agentId);
                    conn.send(encryptedJson);
                } else {
                    logger.info("MCP WebSocket sending response to {}: {}", agentId, responseJson.substring(0, Math.min(100, responseJson.length())) + "...");
                    conn.send(responseJson);
                }
                
            } catch (Exception e) {
                logger.error("Error processing MCP WebSocket message: {}", e.getMessage(), e);
                try {
                    JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
                    conn.send(objectMapper.writeValueAsString(errorResponse));
                } catch (Exception ex) {
                    logger.error("Failed to send error response: {}", ex.getMessage());
                }
            }
        }
        
        @Override
        public void onError(WebSocket conn, Exception ex) {
            String agentId = connectionAgentMap.get(conn);
            logger.error("MCP WebSocket error for agent {}: {}", agentId, ex.getMessage());
        }
        
        @Override
        public void onStart() {
            logger.info("MCP WebSocket server started successfully");
        }
    }
}