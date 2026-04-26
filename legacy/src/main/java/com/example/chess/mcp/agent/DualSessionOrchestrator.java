package com.example.chess.mcp.agent;

import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Orchestrates dual chess sessions for AI vs AI training
 * 
 * WHITE Session: MCP Client (White) vs Server AI (Black)
 * BLACK Session: MCP Client (Black) vs Server AI (White)
 * 
 * Creates an AI vs AI training environment where moves are relayed between sessions
 */
public class DualSessionOrchestrator {
    
    private static final Logger logger = LogManager.getLogger(DualSessionOrchestrator.class);
    
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
        logger.debug("Initializing dual chess sessions for AI vs AI training...");
        
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
        
        logger.debug("Sessions initialized:");
        logger.debug("  WHITE Session: MCP Client (White) vs Server AI " + config.getBlackAI() + " (Black)");
        logger.debug("  BLACK Session: MCP Client (Black) vs Server AI " + config.getWhiteAI() + " (White)");
    }
    
    public void startTrainingLoop() {
        running = true;
        logger.debug("Starting training loop for " + config.getGamesPerSession() + " games...");
        
        while (running && gamesCompleted < config.getGamesPerSession()) {
            try {
                playGame();
                gamesCompleted++;
                logger.debug("Game " + gamesCompleted + " completed");
                
            } catch (Exception e) {
                logger.error("Error in game " + (gamesCompleted + 1) + ": " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
        
        logger.debug("Training loop completed. Games played: " + gamesCompleted);
    }
    
    private void playGame() throws Exception {
        boolean gameActive = true;
        int moveCount = 0;
        
        // Start with WHITE's opening move (from WHITE session)
        logger.debug("Getting opening WHITE move from WHITE session...");
        String currentMove = whiteSession.getAIMove(); // MCP Client (White) generates opening move
        
        if (currentMove != null) {
            logger.debug("WHITE opening move received: " + currentMove);
            
            // Apply WHITE's opening move to BLACK session
            String blackResponse = blackSession.makeMove(currentMove);
            logger.debug("WHITE opening move: " + currentMove + " -> BLACK responds: " + blackResponse);
            
            if (blackResponse != null) {
                currentMove = blackResponse; // Next move is BLACK's response
                moveCount++;
            } else {
                logger.error("BLACK session failed to respond to opening move");
                gameActive = false;
            }
        } else {
            logger.error("Failed to get opening WHITE move");
            gameActive = false;
        }
        
        while (gameActive && moveCount < 200 && currentMove != null) {
            try {
                displayBoardStates(moveCount);
                
                if (moveCount % 2 == 1) {
                    // Odd moves (1, 3, 5...): BLACK's turn
                    logger.debug("BLACK move: " + currentMove);
                    
                    // Apply BLACK move to WHITE session, get WHITE's response
                    String whiteResponse = whiteSession.makeMove(currentMove);
                    logger.debug("WHITE responds: " + whiteResponse);
                    
                    if (whiteResponse != null) {
                        currentMove = whiteResponse; // Next move is WHITE's response
                    } else {
                        logger.error("WHITE session failed to respond");
                        gameActive = false;
                    }
                    
                } else {
                    // Even moves (2, 4, 6...): WHITE's turn
                    logger.debug("WHITE move: " + currentMove);
                    
                    // Apply WHITE move to BLACK session, get BLACK's response
                    String blackResponse = blackSession.makeMove(currentMove);
                    logger.debug("BLACK responds: " + blackResponse);
                    
                    if (blackResponse != null) {
                        currentMove = blackResponse; // Next move is BLACK's response
                    } else {
                        logger.error("BLACK session failed to respond");
                        gameActive = false;
                    }
                }
                
                moveCount++;
                
                // Check if either session ended
                if (!whiteSession.isGameActive() || !blackSession.isGameActive()) {
                    logger.debug("One or both sessions ended - stopping game");
                    gameActive = false;
                }
                
            } catch (Exception e) {
                logger.error("Error during move " + moveCount + ": " + e.getMessage());
                e.printStackTrace();
                gameActive = false;
            }
        }
        
        String result = determineGameResult();
        logger.debug("Game result: " + result + " (moves: " + moveCount + ")");
        
        if (gamesCompleted + 1 < config.getGamesPerSession()) {
            logger.debug("Resetting games for next round...");
            whiteSession.resetGame();
            blackSession.resetGame();
        }
    }
    
    private void displayBoardStates(int moveCount) {
        try {
            logger.debug("\n=== Board States (Move " + moveCount + ") ===");
            
            logger.debug("\nWHITE Session Board (MCP Client White vs Server AI Black):");
            String whiteBoard = whiteSession.fetchCurrentBoard();
            logger.debug(whiteBoard);
            
            logger.debug("\nBLACK Session Board (MCP Client Black vs Server AI White):");
            String blackBoard = blackSession.fetchCurrentBoard();
            logger.debug(blackBoard);
            
            logger.debug("================================\n");
        } catch (Exception e) {
            logger.error("Error displaying board states: " + e.getMessage());
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
            logger.error("Error determining game result: " + e.getMessage());
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
        
        logger.debug("Dual session orchestrator shutdown");
    }
}