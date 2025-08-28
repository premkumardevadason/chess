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
                System.out.println("Agent onText called - data: " + data + ", last: " + last);
                messageBuffer.append(data);
                if (last) {
                    String message = messageBuffer.toString();
                    System.out.println("Agent complete message received: " + message);
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
        sendRequest(connection.getSessionId(), initRequest).get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
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
            connection.getWebSocket().sendText(requestJson, true);
            
            return responseFuture;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private void handleMessage(String sessionId, String message) {
        try {
            System.out.println("Agent received message: " + message);
            JsonNode jsonMessage = objectMapper.readTree(message);
            MCPConnection connection = connections.get(sessionId);
            
            if (connection != null && jsonMessage.has("id")) {
                long id = jsonMessage.get("id").asLong();
                System.out.println("Looking for pending request with ID: " + id);
                CompletableFuture<JsonNode> pendingRequest = connection.removePendingRequest(id);
                
                if (pendingRequest != null) {
                    System.out.println("Found pending request, completing it");
                    if (jsonMessage.has("error")) {
                        pendingRequest.completeExceptionally(
                            new RuntimeException("MCP Error: " + jsonMessage.get("error").toString())
                        );
                    } else {
                        pendingRequest.complete(jsonMessage);
                    }
                } else {
                    System.out.println("No pending request found for ID: " + id);
                }
            } else {
                System.out.println("Message has no ID or no connection found");
            }
        } catch (Exception e) {
            System.err.println("Error handling message for session " + sessionId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void closeConnection(String sessionId) {
        MCPConnection connection = connections.remove(sessionId);
        if (connection != null) {
            connection.close();
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
}