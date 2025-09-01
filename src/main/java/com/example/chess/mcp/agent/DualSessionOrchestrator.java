package com.example.chess.mcp.agent;

import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Orchestrates dual chess sessions for AI vs AI training
 * 
 * WHITE Session: MCP Client (White) vs Server AI (Black)
 * BLACK Session: MCP Client (Black) vs Server AI (White)
 * 
 * Creates an AI vs AI training environment where moves are relayed between sessions
 */
public class DualSessionOrchestrator {
    
    private final MCPConnectionManager connectionManager;
    private final AgentConfiguration config;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    
    // WHITE SESSION: MCP Client plays White pieces, Server AI plays Black pieces
    private ChessSessionProxy whiteSession;
    
    // BLACK SESSION: MCP Client plays Black pieces, Server AI plays White pieces
    private ChessSessionProxy blackSession;
    
    private volatile boolean running = false;
    private int gamesCompleted = 0;
    
    public DualSessionOrchestrator(MCPConnectionManager connectionManager, AgentConfiguration config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }
    
    public void initializeSessions() throws Exception {
        System.out.println("Initializing dual chess sessions for AI vs AI training...");
        
        // WHITE SESSION: MCP Client (White) vs Server AI (Black)
        whiteSession = new ChessSessionProxy(
            "white-session", 
            "white",  // MCP Client plays White pieces
            connectionManager,
            config,
            requestIdCounter
        );
        // Server AI opponent will be Black pieces
        whiteSession.initializeGame(config.getBlackAI(), config.getAiDifficulty());
        
        // BLACK SESSION: MCP Client (Black) vs Server AI (White)
        blackSession = new ChessSessionProxy(
            "black-session",
            "black",  // MCP Client plays Black pieces
            connectionManager,
            config,
            requestIdCounter
        );
        // Server AI opponent will be White pieces
        blackSession.initializeGame(config.getWhiteAI(), config.getAiDifficulty());
        
        System.out.println("Sessions initialized:");
        System.out.println("  WHITE Session: MCP Client (White) vs Server AI " + config.getBlackAI() + " (Black)");
        System.out.println("  BLACK Session: MCP Client (Black) vs Server AI " + config.getWhiteAI() + " (White)");
    }
    
    public void startTrainingLoop() {
        running = true;
        System.out.println("Starting training loop for " + config.getGamesPerSession() + " games...");
        
        while (running && gamesCompleted < config.getGamesPerSession()) {
            try {
                playGame();
                gamesCompleted++;
                System.out.println("Game " + gamesCompleted + " completed");
                
            } catch (Exception e) {
                System.err.println("Error in game " + (gamesCompleted + 1) + ": " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
        
        System.out.println("Training loop completed. Games played: " + gamesCompleted);
    }
    
    private void playGame() throws Exception {
        boolean gameActive = true;
        int moveCount = 0;
        
        // Start with WHITE's opening move (from WHITE session)
        System.out.println("Getting opening WHITE move from WHITE session...");
        String currentMove = whiteSession.getAIMove(); // MCP Client (White) generates opening move
        
        if (currentMove != null) {
            System.out.println("WHITE opening move received: " + currentMove);
            
            // Apply WHITE's opening move to BLACK session
            String blackResponse = blackSession.makeMove(currentMove);
            System.out.println("WHITE opening move: " + currentMove + " -> BLACK responds: " + blackResponse);
            
            if (blackResponse != null) {
                currentMove = blackResponse; // Next move is BLACK's response
                moveCount++;
            } else {
                System.err.println("BLACK session failed to respond to opening move");
                gameActive = false;
            }
        } else {
            System.err.println("Failed to get opening WHITE move");
            gameActive = false;
        }
        
        while (gameActive && moveCount < 200 && currentMove != null) {
            try {
                displayBoardStates(moveCount);
                
                if (moveCount % 2 == 1) {
                    // Odd moves (1, 3, 5...): BLACK's turn
                    System.out.println("BLACK move: " + currentMove);
                    
                    // Apply BLACK move to WHITE session, get WHITE's response
                    String whiteResponse = whiteSession.makeMove(currentMove);
                    System.out.println("WHITE responds: " + whiteResponse);
                    
                    if (whiteResponse != null) {
                        currentMove = whiteResponse; // Next move is WHITE's response
                    } else {
                        System.err.println("WHITE session failed to respond");
                        gameActive = false;
                    }
                    
                } else {
                    // Even moves (2, 4, 6...): WHITE's turn
                    System.out.println("WHITE move: " + currentMove);
                    
                    // Apply WHITE move to BLACK session, get BLACK's response
                    String blackResponse = blackSession.makeMove(currentMove);
                    System.out.println("BLACK responds: " + blackResponse);
                    
                    if (blackResponse != null) {
                        currentMove = blackResponse; // Next move is BLACK's response
                    } else {
                        System.err.println("BLACK session failed to respond");
                        gameActive = false;
                    }
                }
                
                moveCount++;
                
                // Check if either session ended
                if (!whiteSession.isGameActive() || !blackSession.isGameActive()) {
                    System.out.println("One or both sessions ended - stopping game");
                    gameActive = false;
                }
                
            } catch (Exception e) {
                System.err.println("Error during move " + moveCount + ": " + e.getMessage());
                e.printStackTrace();
                gameActive = false;
            }
        }
        
        String result = determineGameResult();
        System.out.println("Game result: " + result + " (moves: " + moveCount + ")");
        
        if (gamesCompleted + 1 < config.getGamesPerSession()) {
            System.out.println("Resetting games for next round...");
            whiteSession.resetGame();
            blackSession.resetGame();
        }
    }
    
    private void displayBoardStates(int moveCount) {
        try {
            System.out.println("\n=== Board States (Move " + moveCount + ") ===");
            
            System.out.println("\nWHITE Session Board (MCP Client White vs Server AI Black):");
            String whiteBoard = whiteSession.fetchCurrentBoard();
            System.out.println(whiteBoard);
            
            System.out.println("\nBLACK Session Board (MCP Client Black vs Server AI White):");
            String blackBoard = blackSession.fetchCurrentBoard();
            System.out.println(blackBoard);
            
            System.out.println("================================\n");
        } catch (Exception e) {
            System.err.println("Error displaying board states: " + e.getMessage());
        }
    }
    
    private String determineGameResult() {
        try {
            JsonNode whiteState = whiteSession.getBoardState();
            JsonNode blackState = blackSession.getBoardState();
            
            // Prefer explicit winner fields if present
            if (whiteState.has("gameOver") && whiteState.get("gameOver").asBoolean()) {
                if (whiteState.has("winner")) {
                    return whiteState.get("winner").asText();
                }
            }
            
            if (blackState.has("gameOver") && blackState.get("gameOver").asBoolean()) {
                if (blackState.has("winner")) {
                    return blackState.get("winner").asText();
                }
            }
            
            // Fallback: map gameStatus if available
            String whiteStatus = whiteState.path("gameStatus").asText("");
            if (!whiteStatus.isEmpty() && !"active".equals(whiteStatus)) {
                return mapStatusToResult(whiteStatus);
            }
            String blackStatus = blackState.path("gameStatus").asText("");
            if (!blackStatus.isEmpty() && !"active".equals(blackStatus)) {
                return mapStatusToResult(blackStatus);
            }
            
            return "Draw";
        } catch (Exception e) {
            System.err.println("Error determining game result: " + e.getMessage());
            return "Unknown";
        }
    }
    
    private String mapStatusToResult(String status) {
        switch (status) {
            case "white_wins":
            case "WHITE_WINS":
            case "white-checkmates":
                return "White";
            case "black_wins":
            case "BLACK_WINS":
            case "black-checkmates":
                return "Black";
            case "draw":
            case "stalemate":
            case "insufficient_material":
            case "threefold_repetition":
                return "Draw";
            default:
                return status;
        }
    }
    
    public void shutdown() {
        running = false;
        
        if (whiteSession != null) {
            whiteSession.close();
        }
        
        if (blackSession != null) {
            blackSession.close();
        }
        
        System.out.println("Dual session orchestrator shutdown");
    }
}