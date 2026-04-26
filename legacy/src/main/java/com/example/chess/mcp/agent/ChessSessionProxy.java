package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Proxy for a single chess session via MCP
 */
public class ChessSessionProxy {
    
    private static final Logger logger = LogManager.getLogger(ChessSessionProxy.class);
    
    private final String sessionId;
    private final String playerColor;
    private final MCPConnectionManager connectionManager;
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter;
    
    private MCPConnection connection;
    private String gameSessionId;
    private boolean gameActive = false;
    
    public ChessSessionProxy(String sessionId, String playerColor, 
                           MCPConnectionManager connectionManager, 
                           AgentConfiguration config,
                           AtomicLong requestIdCounter) {
        this.sessionId = sessionId;
        this.playerColor = playerColor;
        this.connectionManager = connectionManager;
        this.config = config;
        this.requestIdCounter = requestIdCounter;
    }
    
    public void initializeConnection() throws Exception {
        if (connection == null) {
            // Create connection only once
            connection = connectionManager.createConnection(sessionId);
            logger.debug("Connection created for session: " + sessionId);
        }
    }
    
    public void initializeGame(String aiOpponent, int difficulty) throws Exception {
        // Ensure connection exists
        initializeConnection();
        
        // Create chess game
        Map<String, Object> params = Map.of(
            "name", "create_chess_game",
            "arguments", Map.of(
                "aiOpponent", aiOpponent,
                "playerColor", playerColor,
                "difficulty", difficulty
            )
        );
        
        long createGameRequestId = requestIdCounter.getAndIncrement();
        JsonRpcRequest createGameRequest = new JsonRpcRequest(
            createGameRequestId,
            "tools/call",
            params
        );
        
        logger.debug("Sending create_chess_game request with ID: " + createGameRequestId + 
                          " for session: " + sessionId + " (Player: " + playerColor + " vs AI: " + aiOpponent + ")");
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, createGameRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            // Look for resource content with sessionId
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    try {
                        JsonNode resourceData = objectMapper.readTree(resourceText);
                        if (resourceData.has("sessionId")) {
                            gameSessionId = resourceData.get("sessionId").asText();
                            gameActive = true;
                            logger.debug("Game initialized for " + sessionId + " with session ID: " + gameSessionId);
                            return;
                        } else {
                            logger.debug("No sessionId found in response. Available fields: " + resourceData.fieldNames());
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing resource text: " + e.getMessage());
                    }
                }
            }
        } else if (result.has("error")) {
            logger.error("Error creating chess game: " + result.get("error").toString());
            throw new RuntimeException("Failed to create chess game: " + result.get("error").toString());
        }
        
        logger.debug("Failed to create chess game. Response: " + result.toString());
        throw new RuntimeException("Failed to create chess game: " + result.toString());
    }
    

    
    public String getAIMove() throws Exception {
        if (!gameActive) {
            logger.debug("Session " + sessionId + " is not active - cannot get AI move");
            return null;
        }
        
        // Use get_move_hint to get AI-generated move with opening book logic
        Map<String, Object> hintParams = Map.of(
            "name", "get_move_hint",
            "arguments", Map.of("sessionId", gameSessionId)
        );
        
        JsonRpcRequest hintRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            hintParams
        );
        
        logger.debug("Getting AI move hint for session: " + sessionId);
        
        CompletableFuture<JsonNode> hintResponse = connectionManager.sendRequest(sessionId, hintRequest);
        JsonNode hintResult = hintResponse.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (hintResult.has("result") && hintResult.get("result").has("content")) {
            JsonNode content = hintResult.get("result").get("content");
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    try {
                        JsonNode resourceData = objectMapper.readTree(resourceText);
                        
                        // Try multiple possible field names for move suggestions
                        if (resourceData.has("suggestedMove")) {
                            String move = resourceData.get("suggestedMove").asText();
                            logger.debug("AI move hint received: " + move);
                            return move;
                        } else if (resourceData.has("move")) {
                            String move = resourceData.get("move").asText();
                            logger.debug("AI move received: " + move);
                            return move;
                        } else if (resourceData.has("aiMove")) {
                            String move = resourceData.get("aiMove").asText();
                            logger.debug("AI move received: " + move);
                            return move;
                        } else {
                            logger.debug("No move field found in response. Available fields: " + resourceData.fieldNames());
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing resource text: " + e.getMessage());
                    }
                }
            }
        } else if (hintResult.has("error")) {
            logger.error("Error getting AI move: " + hintResult.get("error").toString());
        }
        
        logger.debug("Failed to get AI move from session: " + sessionId);
        return null;
    }
    

    
    public String makeMove(String uciMove) throws Exception {
        if (!gameActive) {
            logger.debug("Session " + sessionId + " is not active - cannot make move");
            return null;
        }
        
        Map<String, Object> params = Map.of(
            "name", "make_chess_move",
            "arguments", Map.of(
                "sessionId", gameSessionId,
                "move", uciMove
            )
        );
        
        logger.debug("Making move " + uciMove + " in session: " + sessionId);
        
        JsonRpcRequest makeMoveRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, makeMoveRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            // Look for AI move in resource content
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    try {
                        JsonNode resourceData = objectMapper.readTree(resourceText);
                        
                        // Try multiple possible field names for AI moves
                        if (resourceData.has("aiMove")) {
                            String aiMove = resourceData.get("aiMove").asText();
                            logger.debug("AI response move: " + aiMove);
                            
                            // Check if game ended
                            if (resourceData.has("gameStatus") && !"active".equals(resourceData.get("gameStatus").asText())) {
                                logger.debug("Game ended in session " + sessionId + " with status: " + resourceData.get("gameStatus").asText());
                                gameActive = false;
                            }
                            return aiMove;
                        } else if (resourceData.has("move")) {
                            String aiMove = resourceData.get("move").asText();
                            logger.debug("AI response move: " + aiMove);
                            
                            // Check if game ended
                            if (resourceData.has("gameStatus") && !"active".equals(resourceData.get("gameStatus").asText())) {
                                logger.debug("Game ended in session " + sessionId + " with status: " + resourceData.get("gameStatus").asText());
                                gameActive = false;
                            }
                            return aiMove;
                        } else {
                            logger.debug("No AI move field found in response. Available fields: " + resourceData.fieldNames());
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing resource text: " + e.getMessage());
                    }
                }
            }
        } else if (result.has("error")) {
            logger.error("Error making move: " + result.get("error").toString());
        }
        
        logger.debug("Failed to get AI response move from session: " + sessionId);
        return null;
    }
    
    public JsonNode getBoardState() throws Exception {
        if (!gameActive) {
            return objectMapper.createObjectNode()
                .put("gameStatus", "inactive")
                .put("movesPlayed", 0);
        }
        
        Map<String, Object> params = Map.of(
            "name", "get_board_state",
            "arguments", Map.of(
                "sessionId", gameSessionId
            )
        );
        
        JsonRpcRequest getBoardRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, getBoardRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    return objectMapper.readTree(resourceText);
                }
            }
        }
        
        return objectMapper.createObjectNode();
    }
    
    public String fetchCurrentBoard() throws Exception {
        if (!gameActive) {
            return "Game not active";
        }
        
        Map<String, Object> params = Map.of(
            "name", "fetch_current_board",
            "arguments", Map.of(
                "sessionId", gameSessionId
            )
        );
        
        JsonRpcRequest fetchBoardRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, fetchBoardRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("asciiBoard")) {
                        return resourceData.get("asciiBoard").asText();
                    }
                }
            }
        }
        
        return "Board not available";
    }
    
    public String getMoveHint() throws Exception {
        if (!gameActive) {
            return null;
        }
        
        Map<String, Object> params = Map.of(
            "name", "get_move_hint",
            "arguments", Map.of(
                "sessionId", gameSessionId,
                "hintLevel", "intermediate"
            )
        );
        
        JsonRpcRequest hintRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, hintRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("suggestedMove")) {
                        return resourceData.get("suggestedMove").asText();
                    }
                }
            }
        }
        
        return null;
    }
    
    public void resetGame() throws Exception {
        // Only reset when game is over - create new game session with existing connection
        if (!gameActive || isGameOver()) {
            logger.debug("Game over - creating new game for session: " + sessionId + " (reusing connection)");
            initializeGame(
                "white".equals(playerColor) ? config.getBlackAI() : config.getWhiteAI(),
                config.getAiDifficulty()
            );
        } else {
            logger.debug("Game still active for session: " + sessionId + " - no reset needed");
        }
    }
    
    private boolean isGameOver() {
        try {
            JsonNode boardState = getBoardState();
            String status = boardState.path("gameStatus").asText("active");
            return !"active".equals(status);
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void close() {
        gameActive = false;
        if (connection != null) {
            connectionManager.closeConnection(sessionId);
        }
    }
}