package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages MCP connections and JSON-RPC communication
 */
public class MCPConnectionManager {
    
    private final AgentConfiguration config;
    private final ConcurrentHashMap<String, MCPConnection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final HttpClient httpClient;
    private final com.example.chess.mcp.security.MCPDoubleRatchetService doubleRatchetService = new com.example.chess.mcp.security.MCPDoubleRatchetService();
    private final boolean encryptionEnabled = true; // Signal Protocol Double Ratchet enabled
    
    public MCPConnectionManager(AgentConfiguration config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
    }
    
    public MCPConnection createConnection(String sessionId) throws Exception {
        if ("websocket".equals(config.getTransportType())) {
            return createWebSocketConnection(sessionId);
        } else {
            throw new UnsupportedOperationException("stdio transport not implemented yet");
        }
    }
    
    private MCPConnection createWebSocketConnection(String sessionId) throws Exception {
        URI serverUri = URI.create(config.getServerUrl());
        CompletableFuture<WebSocket> webSocketFuture = new CompletableFuture<>();
        
        WebSocket.Listener listener = new WebSocket.Listener() {
            private StringBuilder messageBuffer = new StringBuilder();
            
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageBuffer.append(data);
                if (last) {
                    String message = messageBuffer.toString();
                    messageBuffer.setLength(0);
                    handleMessage(sessionId, message);
                }
                webSocket.request(1); // Request next message
                return CompletableFuture.completedFuture(null);
            }
            
            @Override
            public void onOpen(WebSocket webSocket) {
                System.out.println("WebSocket connection opened for session: " + sessionId);
                webSocket.request(1); // Request first message
                webSocketFuture.complete(webSocket);
            }
            
            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                System.out.println("WebSocket connection closed for session: " + sessionId + " (" + statusCode + ": " + reason + ")");
                connections.remove(sessionId);
                return CompletableFuture.completedFuture(null);
            }
            
            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.err.println("WebSocket error for session " + sessionId + ": " + error.getMessage());
                error.printStackTrace();
                webSocketFuture.completeExceptionally(error);
            }
        };
        
        WebSocket webSocket = httpClient.newWebSocketBuilder()
            .buildAsync(serverUri, listener)
            .get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        MCPConnection connection = new MCPConnection(sessionId, webSocket);
        connections.put(sessionId, connection);
        
        // Skip encryption setup here - will be done after getting agent ID from server
        if (encryptionEnabled) {
            System.out.println("⚙️ HKDF Double Ratchet will be established after server handshake for: " + sessionId);
        } else {
            System.out.println("⚠️ Encryption disabled for session: " + sessionId);
        }
        
        // Initialize MCP protocol
        initializeMCPProtocol(connection);
        
        return connection;
    }
    
    private void initializeMCPProtocol(MCPConnection connection) throws Exception {
        // Send initialize request
        Map<String, Object> params = Map.of(
            "protocolVersion", "2024-11-05",
            "clientInfo", Map.of(
                "name", "mcp-chess-agent",
                "version", "1.0.0"
            )
        );
        
        long initRequestId = requestIdCounter.getAndIncrement();
        JsonRpcRequest initRequest = new JsonRpcRequest(
            initRequestId,
            "initialize",
            params
        );
        
        System.out.println("Sending initialize request with ID: " + initRequestId);
        JsonNode initResponse = sendRequest(connection.getSessionId(), initRequest).get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        // Establish encryption after successful initialize
        if (encryptionEnabled && initResponse.has("result")) {
            try {
                // Debug: Print the full response
                System.out.println("🔍 Initialize response: " + initResponse.toString());
                
                // Extract agent ID from server response
                String agentId = null;
                if (initResponse.get("result").has("serverInfo") && 
                    initResponse.get("result").get("serverInfo").has("agentId")) {
                    agentId = initResponse.get("result").get("serverInfo").get("agentId").asText();
                    System.out.println("🎯 Extracted agent ID from server: " + agentId);
                } else {
                    System.out.println("🔍 ServerInfo structure: " + initResponse.get("result").get("serverInfo").toString());
                }
                
                if (agentId != null) {
                    doubleRatchetService.establishSession(agentId, false); // Client mode
                    connection.setAgentId(agentId);
                    System.out.println("✅ HKDF Double Ratchet CLIENT established with server agent ID: " + agentId);
                } else {
                    System.err.println("❌ No agent ID found in server response - using fallback");
                    // Fallback: use session ID
                    agentId = connection.getSessionId();
                    doubleRatchetService.establishSession(agentId, false); // Client mode
                    connection.setAgentId(agentId);
                    System.out.println("⚠️ HKDF Double Ratchet CLIENT established with fallback ID: " + agentId);
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to establish encryption: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Initialize request completed successfully");
    }
    
    public CompletableFuture<JsonNode> sendRequest(String sessionId, JsonRpcRequest request) {
        MCPConnection connection = connections.get(sessionId);
        if (connection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No connection for session: " + sessionId));
        }
        
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
            
            connection.addPendingRequest(request.getId(), responseFuture);
            
            // Encrypt message if encryption is enabled
            String messageToSend = requestJson;
            if (encryptionEnabled && connection.getAgentId() != null) {
                com.example.chess.mcp.security.EncryptedMCPMessage encMsg = 
                    doubleRatchetService.encryptMessage(connection.getAgentId(), requestJson);
                
                messageToSend = String.format(
                    "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\"}",
                    encMsg.getCiphertext()
                );
            }
            
            connection.getWebSocket().sendText(messageToSend, true);
            
            return responseFuture;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private void handleMessage(String sessionId, String message) {
        try {
            String actualMessage = message;
            
            // Decrypt message if encryption is enabled and message is encrypted
            if (encryptionEnabled && message.contains("\"encrypted\":true")) {
                MCPConnection connection = connections.get(sessionId);
                if (connection != null && connection.getAgentId() != null) {
                    // Extract ciphertext from JSON
                    String ciphertext = extractJsonValue(message, "ciphertext");
                    com.example.chess.mcp.security.EncryptedMCPMessage encMsg = 
                        new com.example.chess.mcp.security.EncryptedMCPMessage(ciphertext, true);
                    
                    actualMessage = doubleRatchetService.decryptMessage(connection.getAgentId(), encMsg);
                }
            }
            
            JsonNode jsonMessage = objectMapper.readTree(actualMessage);
            MCPConnection connection = connections.get(sessionId);
            
            if (connection != null && jsonMessage.has("id")) {
                long id = jsonMessage.get("id").asLong();
                CompletableFuture<JsonNode> pendingRequest = connection.removePendingRequest(id);
                
                if (pendingRequest != null) {
                    if (jsonMessage.has("error")) {
                        pendingRequest.completeExceptionally(
                            new RuntimeException("MCP Error: " + jsonMessage.get("error").toString())
                        );
                    } else {
                        pendingRequest.complete(jsonMessage);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling message for session " + sessionId + ": " + e.getMessage());
        }
    }
    
    public void closeConnection(String sessionId) {
        MCPConnection connection = connections.remove(sessionId);
        if (connection != null) {
            connection.close();
        }
        
        // Clean up encryption session
        if (encryptionEnabled && connection != null && connection.getAgentId() != null) {
            doubleRatchetService.removeSession(connection.getAgentId());
        }
    }
    
    public boolean isConnectionHealthy(String sessionId) {
        MCPConnection connection = connections.get(sessionId);
        return connection != null && connection.isHealthy();
    }
    
    public void shutdown() {
        connections.values().forEach(MCPConnection::close);
        connections.clear();
    }
    
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}