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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages MCP connections and JSON-RPC communication
 */
public class MCPConnectionManager {
    
    private static final Logger logger = LogManager.getLogger(MCPConnectionManager.class);
    
    private final AgentConfiguration config;
    private final ConcurrentHashMap<String, MCPConnection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final HttpClient httpClient;
    private final com.example.chess.mcp.security.DoubleRatchetService doubleRatchetService = new com.example.chess.mcp.security.MCPDoubleRatchetService();
    private final boolean encryptionEnabled = true; // encryption enabled
    private final boolean useSignal = Boolean.parseBoolean(System.getProperty("mcp.encryption.signal", "false"));
    
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
                logger.info("WebSocket connection opened for session: " + sessionId);
                webSocket.request(1); // Request first message
                webSocketFuture.complete(webSocket);
            }
            
            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                logger.info("WebSocket connection closed for session: " + sessionId + " (" + statusCode + ": " + reason + ")");
                connections.remove(sessionId);
                return CompletableFuture.completedFuture(null);
            }
            
            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.error("WebSocket error for session " + sessionId + ": " + error.getMessage(), error);
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
            logger.info("⚙️ HKDF Double Ratchet will be established after server handshake for: " + sessionId);
        } else {
            logger.warn("⚠️ Encryption disabled for session: " + sessionId);
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
        
        logger.debug("Sending initialize request with ID: " + initRequestId);
        JsonNode initResponse = sendRequest(connection.getSessionId(), initRequest).get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        // Establish encryption after successful initialize
        if (encryptionEnabled && initResponse.has("result")) {
            try {
                // Debug: Print the full response
                logger.debug("🔍 Initialize response: " + initResponse.toString());
                
                // Extract agent ID from server response
                String agentId = null;
                if (initResponse.get("result").has("serverInfo") && 
                    initResponse.get("result").get("serverInfo").has("agentId")) {
                    agentId = initResponse.get("result").get("serverInfo").get("agentId").asText();
                    logger.info("🎯 Extracted agent ID from server: " + agentId);
                } else {
                    logger.debug("🔍 ServerInfo structure: " + initResponse.get("result").get("serverInfo").toString());
                }
                
                if (agentId != null) {
                    connection.setAgentId(agentId);
                    if (useSignal) {
                        // Fetch PreKey bundle from server and initialize Signal session
                        String preKeyUrl = ("http://" + config.getServerHost() + ":" + config.getServerPort() + "/mcp/keys/prekey?agentId=" + agentId);
                        var req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(preKeyUrl))
                            .GET()
                            .build();
                        var resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() / 100 != 2) {
                            throw new RuntimeException("Failed to fetch PreKey bundle: HTTP " + resp.statusCode());
                        }
                        com.fasterxml.jackson.databind.JsonNode preKeyJson = objectMapper.readTree(resp.body());
                        com.example.chess.mcp.security.PreKeyBundleDto dto = new com.example.chess.mcp.security.PreKeyBundleDto(
                            preKeyJson.get("registrationId").asInt(),
                            preKeyJson.get("deviceId").asInt(),
                            preKeyJson.get("preKeyId").asInt(),
                            preKeyJson.get("preKeyPublic").asText(),
                            preKeyJson.get("signedPreKeyId").asInt(),
                            preKeyJson.get("signedPreKeyPublic").asText(),
                            preKeyJson.get("signedPreKeySignature").asText(),
                            preKeyJson.get("identityKey").asText()
                        );

                        if (doubleRatchetService instanceof com.example.chess.mcp.security.SignalDoubleRatchetService s) {
                            s.initializeWithPreKeyBundle(agentId, dto);
                        }
                        logger.info("✅ Signal session initialized with PreKey bundle for agent: " + agentId);
                    } else {
                        doubleRatchetService.establishSession(agentId, false); // HKDF client mode
                        logger.info("✅ HKDF Double Ratchet CLIENT established with server agent ID: " + agentId);
                    }
                } else {
                    logger.error("❌ No agent ID found in server response - using fallback");
                    // Fallback: use session ID
                    agentId = connection.getSessionId();
                    doubleRatchetService.establishSession(agentId, false); // Client mode
                    connection.setAgentId(agentId);
                    logger.warn("⚠️ HKDF Double Ratchet CLIENT established with fallback ID: " + agentId);
                }
            } catch (Exception e) {
                logger.error("❌ Failed to establish encryption: " + e.getMessage(), e);
            }
        }
        
        logger.info("Initialize request completed successfully");
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
                
                if (useSignal) {
                    // Signal path: ciphertext only
                    messageToSend = String.format(
                        "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\"}",
                        encMsg.getCiphertext()
                    );
                } else {
                    // HKDF path: include iv and header
                    String iv = encMsg.getIv();
                    String header = encMsg.getHeader() == null ? null : String.format(
                        "\"ratchet_header\":{\"dh_public_key\":\"%s\",\"previous_counter\":%d,\"message_counter\":%d}",
                        encMsg.getHeader().getDhPublicKey(),
                        encMsg.getHeader().getPreviousCounter(),
                        encMsg.getHeader().getMessageCounter()
                    );
                    messageToSend = String.format(
                        "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\",\"iv\":\"%s\"%s}",
                        encMsg.getCiphertext(),
                        iv,
                        header == null ? "" : "," + header
                    );
                }
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
            logger.error("Error handling message for session " + sessionId + ": " + e.getMessage(), e);
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