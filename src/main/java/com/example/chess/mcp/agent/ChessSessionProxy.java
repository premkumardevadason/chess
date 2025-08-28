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
        
        // Request AI move hint to get AI's suggested move
        Map<String, Object> params = Map.of(
            "name", "get_move_hint",
            "arguments", Map.of(
                "sessionId", gameSessionId,
                "hintLevel", "master"
            )
        );
        
        JsonRpcRequest getMoveRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, getMoveRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("content")) {
            JsonNode content = result.get("result").get("content");
            // Look for AI move in resource content
            for (JsonNode item : content) {
                if (item.has("resource") && item.get("resource").has("text")) {
                    String resourceText = item.get("resource").get("text").asText();
                    JsonNode resourceData = objectMapper.readTree(resourceText);
                    if (resourceData.has("suggestedMove")) {
                        String move = resourceData.get("suggestedMove").asText();
                        // Convert algebraic notation to UCI if needed
                        return convertToUCI(move);
                    }
                }
            }
        }
        
        return null;
    }
    
    private String convertToUCI(String move) {
        // Simple algebraic to UCI conversion for common moves
        switch (move) {
            case "e4": return "e2e4";
            case "d4": return "d2d4";
            case "c4": return "c2c4";
            case "f4": return "f2f4";
            case "g3": return "g2g3";
            case "h3": return "h2h3";
            case "a3": return "a2a3";
            case "b3": return "b2b3";
            case "Nf3": return "g1f3";
            case "Nc3": return "b1c3";
            case "Ne2": return "g1e2";
            case "Nh3": return "g1h3";
            case "Nf6": return "g8f6";
            case "Nc6": return "b8c6";
            case "Ne7": return "g8e7";
            case "Nh6": return "g8h6";
            case "Bc4": return "f1c4";
            case "Bb5": return "f1b5";
            case "Be2": return "f1e2";
            case "Bd3": return "f1d3";
            case "Bc5": return "f8c5";
            case "Be7": return "f8e7";
            case "Bd6": return "f8d6";
            case "O-O": return "e1g1";
            case "O-O-O": return "e1c1";
            default: return move; // Return as-is if already UCI or unknown
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
                .put("fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
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
        
        return result.has("result") ? result.get("result") : objectMapper.createObjectNode();
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