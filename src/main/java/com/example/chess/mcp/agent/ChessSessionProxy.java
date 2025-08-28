package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Proxy for a single chess session via MCP
 */
public class ChessSessionProxy {
    
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
    
    public void initializeGame(String aiOpponent, int difficulty) throws Exception {
        // Create connection
        connection = connectionManager.createConnection(sessionId);
        
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
        
        System.out.println("Sending create_chess_game request with ID: " + createGameRequestId);
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, createGameRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            // Look for resource content with sessionId
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("sessionId")) {
                        gameSessionId = resourceData.get("sessionId").asText();
                        gameActive = true;
                        System.out.println("Game initialized for " + sessionId + " with session ID: " + gameSessionId);
                        return;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to create chess game: " + result.toString());
    }
    
    public String getAIMove() throws Exception {
        if (!gameActive) {
            return null;
        }
        
        // Get legal moves first to ensure UCI format
        Map<String, Object> legalMovesParams = Map.of(
            "name", "get_legal_moves",
            "arguments", Map.of("sessionId", gameSessionId)
        );
        
        JsonRpcRequest legalMovesRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            legalMovesParams
        );
        
        CompletableFuture<JsonNode> legalMovesResponse = connectionManager.sendRequest(sessionId, legalMovesRequest);
        JsonNode legalResult = legalMovesResponse.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        java.util.List<String> legalMoves = new java.util.ArrayList<>();
        if (legalResult.has("result") && legalResult.get("result").has("content")) {
            JsonNode content = legalResult.get("result").get("content");
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("legalMoves")) {
                        JsonNode movesArray = resourceData.get("legalMoves");
                        if (movesArray.isArray()) {
                            for (JsonNode moveNode : movesArray) {
                                legalMoves.add(moveNode.asText());
                            }
                        }
                    }
                }
            }
        }
        
        if (legalMoves.isEmpty()) {
            return null;
        }
        
        // For White's opening move, use aggressive gambits
        if (isWhiteOpeningMove()) {
            String[] openingGambits = {"e2e4", "d2d4", "g1f3", "b1c3"};
            for (String gambit : openingGambits) {
                if (legalMoves.contains(gambit)) {
                    return gambit;
                }
            }
        }
        
        // Return first legal move as fallback (all moves are in UCI format)
        return legalMoves.get(0);
    }
    
    private boolean isWhiteOpeningMove() {
        try {
            JsonNode boardState = getBoardState();
            if (boardState.has("movesPlayed")) {
                int movesPlayed = boardState.get("movesPlayed").asInt();
                return movesPlayed == 0 && "white".equals(playerColor);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    public String makeMove(String uciMove) throws Exception {
        if (!gameActive) {
            return null;
        }
        
        Map<String, Object> params = Map.of(
            "name", "make_chess_move",
            "arguments", Map.of(
                "sessionId", gameSessionId,
                "move", uciMove
            )
        );
        
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
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("aiMove")) {
                        String aiMove = resourceData.get("aiMove").asText();
                        // Check if game ended
                        if (resourceData.has("gameStatus") && !"active".equals(resourceData.get("gameStatus").asText())) {
                            gameActive = false;
                        }
                        return aiMove;
                    }
                }
            }
        }
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
    
    public void resetGame() throws Exception {
        if (gameSessionId != null) {
            // Create new game session
            initializeGame(
                "white".equals(playerColor) ? config.getWhiteAI() : config.getBlackAI(),
                config.getAiDifficulty()
            );
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