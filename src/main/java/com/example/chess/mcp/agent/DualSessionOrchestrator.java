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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    
    private ChessSessionProxy whiteSession; // WHITE MCP Client + BLACK Server AI
    private ChessSessionProxy blackSession; // BLACK MCP Client + WHITE Server AI
    private volatile boolean running = false;
    private int gamesCompleted = 0;
    
    // FIFO queues per requirement
    private SessionMoveQueue whiteSessionQueue; // For WHITE session
    private SessionMoveQueue blackSessionQueue; // For BLACK session
    
    public DualSessionOrchestrator(MCPConnectionManager connectionManager, AgentConfiguration config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }
    
    public void initializeSessions() throws Exception {
        System.out.println("Initializing dual chess sessions...");
        
        // Initialize FIFO queues
        whiteSessionQueue = new SessionMoveQueue("WHITE-SESSION");
        blackSessionQueue = new SessionMoveQueue("BLACK-SESSION");
        
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
        whiteSession.initializeGame(config.getWhiteAI(), config.getAiDifficulty()); // BLACK Server AI
        blackSession.initializeGame(config.getBlackAI(), config.getAiDifficulty()); // WHITE Server AI
        
        System.out.println("Sessions initialized:");
        System.out.println("  WHITE Session (BLACK Server AI): " + config.getWhiteAI());
        System.out.println("  BLACK Session (WHITE Server AI): " + config.getBlackAI());
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
        
        while (gameActive && moveCount < 200) {
            try {
                displayBoardStates(moveCount);
                
                // BLACK Session fetches WHITE move from server AI
                String whiteMove = blackSession.getAIMove();
                if (whiteMove != null) {
                    System.out.println("BLACK Session (" + config.getBlackAI() + " WHITE Server AI) generates: " + whiteMove);
                    // Insert into WHITE Session queue per requirement
                    whiteSessionQueue.enqueue(whiteMove);
                }
                
                // WHITE Session fetches BLACK move from server AI  
                String blackMove = whiteSession.getAIMove();
                if (blackMove != null) {
                    System.out.println("WHITE Session (" + config.getWhiteAI() + " BLACK Server AI) generates: " + blackMove);
                    // Insert into BLACK Session queue per requirement
                    blackSessionQueue.enqueue(blackMove);
                }
                
                // Process queued moves
                if (whiteSessionQueue.hasMove()) {
                    String move = whiteSessionQueue.dequeue();
                    whiteSession.makeMove(move);
                    System.out.println("WHITE Session processes: " + move);
                }
                
                if (blackSessionQueue.hasMove()) {
                    String move = blackSessionQueue.dequeue();
                    blackSession.makeMove(move);
                    System.out.println("BLACK Session processes: " + move);
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
            whiteSessionQueue.clear();
            blackSessionQueue.clear();
        }
    }
    
    private void displayBoardStates(int moveCount) {
        try {
            System.out.println("\n=== Board States (Move " + moveCount + ") ===");
            
            System.out.println("White Session Board:");
            String whiteBoard = whiteSession.fetchCurrentBoard();
            System.out.println(whiteBoard);
            
            System.out.println("\nBlack Session Board:");
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