package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates dual chess sessions for AI vs AI training
 */
public class DualSessionOrchestrator {
    
    private final MCPConnectionManager connectionManager;
    private final AgentConfiguration config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    
    private ChessSessionProxy whiteSession;
    private ChessSessionProxy blackSession;
    private volatile boolean running = false;
    private int gamesCompleted = 0;
    
    public DualSessionOrchestrator(MCPConnectionManager connectionManager, AgentConfiguration config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }
    
    public void initializeSessions() throws Exception {
        System.out.println("Initializing dual chess sessions...");
        
        // Create white session (plays as white)
        whiteSession = new ChessSessionProxy(
            "white-session", 
            "white", 
            connectionManager, 
            config,
            requestIdCounter
        );
        
        // Create black session (plays as black)  
        blackSession = new ChessSessionProxy(
            "black-session",
            "black",
            connectionManager,
            config,
            requestIdCounter
        );
        
        // Initialize both sessions
        whiteSession.initializeGame(config.getWhiteAI(), config.getAiDifficulty());
        blackSession.initializeGame(config.getBlackAI(), config.getAiDifficulty());
        
        System.out.println("Sessions initialized:");
        System.out.println("  White: " + config.getWhiteAI());
        System.out.println("  Black: " + config.getBlackAI());
    }
    
    public void startTrainingLoop() {
        running = true;
        System.out.println("Starting training loop for " + config.getGamesPerSession() + " games...");
        
        while (running && gamesCompleted < config.getGamesPerSession()) {
            try {
                playGame();
                gamesCompleted++;
                System.out.println("Game " + gamesCompleted + " completed");
                
                // Brief pause between games
                Thread.sleep(1000);
                
            } catch (Exception e) {
                System.err.println("Error in game " + (gamesCompleted + 1) + ": " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
        
        System.out.println("Training loop completed. Games played: " + gamesCompleted);
    }
    
    private void playGame() throws Exception {
        // Reset sessions for new game
        whiteSession.resetGame();
        blackSession.resetGame();
        
        boolean gameActive = true;
        int moveCount = 0;
        
        while (gameActive && moveCount < 200) { // Max 200 moves per game
            try {
                // White makes move and gets AI response
                String whiteMove = whiteSession.getAIMove();
                if (whiteMove != null) {
                    System.out.println("Move " + (moveCount + 1) + ": White plays " + whiteMove);
                    
                    // Send White's move to Black, get Black's response
                    String blackMove = blackSession.makeMove(whiteMove);
                    if (blackMove != null) {
                        System.out.println("Move " + (moveCount + 2) + ": Black plays " + blackMove);
                        
                        // Send Black's move to White for next iteration
                        whiteSession.makeMove(blackMove);
                        moveCount += 2;
                    } else {
                        gameActive = false;
                    }
                } else {
                    gameActive = false;
                }
                
                // Check if game is over
                if (!whiteSession.isGameActive() || !blackSession.isGameActive()) {
                    gameActive = false;
                }
                
            } catch (Exception e) {
                System.err.println("Error during move " + (moveCount + 1) + ": " + e.getMessage());
                gameActive = false;
            }
        }
        
        // Determine game result
        String result = determineGameResult();
        System.out.println("Game result: " + result + " (moves: " + moveCount + ")");
    }
    
    private String determineGameResult() {
        try {
            JsonNode whiteState = whiteSession.getBoardState();
            JsonNode blackState = blackSession.getBoardState();
            
            // Simple result determination based on game status
            if (whiteState.has("gameOver") && whiteState.get("gameOver").asBoolean()) {
                if (whiteState.has("winner")) {
                    return whiteState.get("winner").asText();
                }
            }
            
            return "Draw";
        } catch (Exception e) {
            return "Unknown";
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