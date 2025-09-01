package com.example.chess.mcp.agent;

import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Orchestrates dual chess sessions for AI vs AI training
 * 
 * BLACK Session: BLACK MCP Client + WHITE Server AI (Leela)
 * WHITE Session: WHITE MCP Client + BLACK Server AI (AlphaZero)
 */
public class DualSessionOrchestrator {
    
    private final MCPConnectionManager connectionManager;
    private final AgentConfiguration config;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    
    private ChessSessionProxy whiteSession; // WHITE MCP Client + BLACK Server AI
    private ChessSessionProxy blackSession; // BLACK MCP Client + WHITE Server AI
    private volatile boolean running = false;
    private int gamesCompleted = 0;
    
    // Removed FIFO queues - using direct move chaining
    
    public DualSessionOrchestrator(MCPConnectionManager connectionManager, AgentConfiguration config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }
    
    public void initializeSessions() throws Exception {
        System.out.println("Initializing dual chess sessions...");
        
        // Using direct move chaining - no queues needed
        
        // WHITE Session: WHITE MCP Client + BLACK Server AI
        whiteSession = new ChessSessionProxy(
            "white-session", 
            "white", 
            connectionManager,
            config,
            requestIdCounter
        );
        
        // BLACK Session: BLACK MCP Client + WHITE Server AI
        blackSession = new ChessSessionProxy(
            "black-session",
            "black",
            connectionManager,
            config,
            requestIdCounter
        );
        
        // Initialize sessions
        whiteSession.initializeGame(config.getBlackAI(), config.getAiDifficulty()); // BLACK Server AI
        blackSession.initializeGame(config.getWhiteAI(), config.getAiDifficulty()); // WHITE Server AI
        
        System.out.println("Sessions initialized:");
        System.out.println("  WHITE Session (BLACK Server AI): " + config.getBlackAI());
        System.out.println("  BLACK Session (WHITE Server AI): " + config.getWhiteAI());
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
        
        // Start with opening WHITE move
        String currentMove = blackSession.getAIMove(); // Get opening WHITE move
        
        // Apply opening WHITE move to BLACK session
        if (currentMove != null) {
            String blackResponse = blackSession.makeMove(currentMove);
            System.out.println("Opening WHITE move: " + currentMove + " -> BLACK responds: " + blackResponse);
            currentMove = blackResponse; // Next move is BLACK's response
            moveCount++;
        }
        
        while (gameActive && moveCount < 200 && currentMove != null) {
            try {
                displayBoardStates(moveCount);
                
                if (moveCount % 2 == 1) {
                    // BLACK move: Apply to WHITE session, get WHITE response
                    String aiResponse = whiteSession.makeMove(currentMove);
                    System.out.println("BLACK move: " + currentMove + " -> WHITE responds: " + aiResponse);
                    currentMove = aiResponse; // Next move is WHITE's response
                } else {
                    // WHITE move: Apply to BLACK session, get BLACK response  
                    String aiResponse = blackSession.makeMove(currentMove);
                    System.out.println("WHITE move: " + currentMove + " -> BLACK responds: " + aiResponse);
                    currentMove = aiResponse; // Next move is BLACK's response
                }
                
                moveCount++;
                
                if (!whiteSession.isGameActive() || !blackSession.isGameActive()) {
                    gameActive = false;
                }
                
            } catch (Exception e) {
                System.err.println("Error during move: " + e.getMessage());
                gameActive = false;
            }
        }
        
        String result = determineGameResult();
        System.out.println("Game result: " + result + " (moves: " + moveCount + ")");
        
        if (gamesCompleted + 1 < config.getGamesPerSession()) {
            whiteSession.resetGame();
            blackSession.resetGame();
        }
    }
    
    private void displayBoardStates(int moveCount) {
        try {
            System.out.println("\n=== Board States (Move " + moveCount + ") ===");
            
            System.out.println("\nBlack Session Board:");
            String blackBoard = blackSession.fetchCurrentBoard();
            System.out.println(blackBoard);
            
            System.out.println("White Session Board:");
            String whiteBoard = whiteSession.fetchCurrentBoard();
            System.out.println(whiteBoard);
            
            System.out.println("================================\n");
        } catch (Exception e) {
            System.err.println("Error displaying board states: " + e.getMessage());
        }
    }
    
    private String determineGameResult() {
        try {
            JsonNode whiteState = whiteSession.getBoardState();
            JsonNode blackState = blackSession.getBoardState();
            
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