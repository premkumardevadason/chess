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
        
        JsonRpcRequest createGameRequest = new JsonRpcRequest(
            requestIdCounter.getAndIncrement(),
            "tools/call",
            params
        );
        
        CompletableFuture<JsonNode> response = connectionManager.sendRequest(sessionId, createGameRequest);
        JsonNode result = response.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        
        if (result.has("result") && result.get("result").has("sessionId")) {
            gameSessionId = result.get("result").get("sessionId").asText();
            gameActive = true;
            System.out.println("Game initialized for " + sessionId + " with session ID: " + gameSessionId);
        } else {
            throw new RuntimeException("Failed to create chess game: " + result.toString());
        }
    }
    
    public String getAIMove() throws Exception {
        if (!gameActive) {
            return null;
        }
        
        // Request AI move by getting board state (AI will make move automatically)
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
        
        if (result.has("result")) {
            JsonNode gameState = result.get("result");
            
            // Check if game is over
            if (gameState.has("gameStatus") && !"active".equals(gameState.get("gameStatus").asText())) {
                gameActive = false;
                return null;
            }
            
            // Extract last move in UCI format if available
            if (gameState.has("lastMove")) {
                return gameState.get("lastMove").asText();
            }
        }
        
        return null;
    }
    
    public void makeMove(String uciMove) throws Exception {
        if (!gameActive) {
            return;
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
        
        if (result.has("result")) {
            JsonNode moveResult = result.get("result");
            
            // Check if game ended
            if (moveResult.has("gameStatus") && !"active".equals(moveResult.get("gameStatus").asText())) {
                gameActive = false;
            }
        }
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