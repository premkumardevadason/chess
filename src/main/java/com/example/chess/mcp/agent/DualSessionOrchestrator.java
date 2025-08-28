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
    private java.util.Set<String> playedMoves = new java.util.HashSet<>();
    
    public DualSessionOrchestrator(MCPConnectionManager connectionManager, AgentConfiguration config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }
    
    public void initializeSessions() throws Exception {
        System.out.println("Initializing dual chess sessions...");
        
        String sharedBoardId = "shared-board-" + System.currentTimeMillis();
        
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
        
        // Initialize both sessions with shared board
        whiteSession.initializeSharedGame(config.getWhiteAI(), config.getAiDifficulty(), sharedBoardId);
        blackSession.initializeSharedGame(config.getBlackAI(), config.getAiDifficulty(), sharedBoardId);
        
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
        // Sessions are already initialized or reset from previous game
        // No need to reset at start of each game
        
        boolean gameActive = true;
        int moveCount = 0;
        
        while (gameActive && moveCount < 200) { // Max 200 moves per game
            try {
                // Display current board states
                displayBoardStates(moveCount);
                
                System.out.println("\n🔄 MOVE FLOW:");
                
                if (moveCount == 0) {
                    // Shared board approach: Alternate between WHITE and BLACK sessions
                    
                    // Step 1: Agent AI forces e2e4 in WHITE session, AlphaZero BLACK responds
                    String agentWhiteMove = "e2e4";
                    System.out.println("1️⃣ Agent AI WHITE plays: " + agentWhiteMove);
                    String alphaZeroBlackResponse = makeMoveSafelyWithHints(whiteSession, "White", 1, agentWhiteMove);
                    if (alphaZeroBlackResponse != null) {
                        System.out.println("2️⃣ AlphaZero BLACK responds: " + alphaZeroBlackResponse);
                        
                        // Step 2: Agent AI asks BLACK session what WHITE move Leela starts with
                        Thread.sleep(100);
                        String leelaWhiteMove = makeMoveSafely(blackSession, "Black", 1);
                        if (leelaWhiteMove != null) {
                            System.out.println("3️⃣ Leela WHITE starts with: " + leelaWhiteMove);
                            
                            // Step 3: Agent AI sends AlphaZero's BLACK response to BLACK session
                            String leelaBlackResponse = makeMoveSafelyWithHints(blackSession, "Black", 1, alphaZeroBlackResponse);
                            if (leelaBlackResponse != null) {
                                System.out.println("4️⃣ Leela BLACK responds: " + leelaBlackResponse);
                                
                                // Step 4: Agent AI sends Leela's WHITE move to WHITE session
                                Thread.sleep(100);
                                String alphaZeroWhiteResponse = makeMoveSafelyWithHints(whiteSession, "White", 2, leelaWhiteMove);
                                if (alphaZeroWhiteResponse != null) {
                                    System.out.println("5️⃣ AlphaZero WHITE responds: " + alphaZeroWhiteResponse);
                                    moveCount = 5;
                                } else {
                                    gameActive = false;
                                }
                            } else {
                                gameActive = false;
                            }
                        } else {
                            gameActive = false;
                        }
                    } else {
                        gameActive = false;
                    }
                } else {
                    // Continuing game: Get next moves from each session
                    String whiteMove = makeMoveSafely(whiteSession, "White", moveCount + 1);
                    if (whiteMove != null) {
                        System.out.println((moveCount + 1) + "️⃣ WHITE session AI plays: " + whiteMove);
                        
                        // Send to BLACK session
                        Thread.sleep(100);
                        String blackResponse = makeMoveSafelyWithHints(blackSession, "Black", moveCount + 1, whiteMove);
                        if (blackResponse != null) {
                            System.out.println((moveCount + 2) + "️⃣ BLACK session AI responds: " + blackResponse);
                            
                            // Send BLACK response back to WHITE session
                            Thread.sleep(100);
                            String nextWhiteMove = makeMoveSafelyWithHints(whiteSession, "White", moveCount + 2, blackResponse);
                            if (nextWhiteMove != null) {
                                System.out.println((moveCount + 3) + "️⃣ WHITE session AI responds: " + nextWhiteMove);
                                moveCount += 3;
                            } else {
                                gameActive = false;
                            }
                        } else {
                            gameActive = false;
                        }
                    } else {
                        gameActive = false;
                    }
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
        
        // Reset sessions for next game only after current game is complete
        if (gamesCompleted + 1 < config.getGamesPerSession()) {
            System.out.println("Preparing for next game - resetting sessions");
            playedMoves.clear(); // Clear move history for new game
            whiteSession.resetGame();
            blackSession.resetGame();
        }
    }
    

    
    private String makeMoveSafely(ChessSessionProxy session, String playerName, int moveNumber) throws Exception {
        System.out.println("   🤖 Getting AI move from " + playerName + " session...");
        String move = session.getAIMove();
        if (move != null) {
            String moveKey = playerName + "-" + moveNumber + "-" + move;
            if (playedMoves.contains(moveKey)) {
                System.out.println("   ⚠️ Move already played: " + move + ", getting hint...");
                move = session.getMoveHint();
            }
            if (move != null) {
                playedMoves.add(moveKey);
                System.out.println("   📥 " + playerName + " AI generates: " + move);
                System.out.println("✅ Move " + moveNumber + ": " + playerName + " AI plays " + move);
            }
        }
        return move;
    }
    
    private String makeMoveSafelyWithOpponent(ChessSessionProxy session, String playerName, int moveNumber, String opponentMove) throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String move;
                if (opponentMove != null) {
                    System.out.println("   📤 Sending move to " + playerName + " session: " + opponentMove);
                    move = session.makeMove(opponentMove);
                    System.out.println("   📥 " + playerName + " AI responds with: " + move);
                } else {
                    System.out.println("   🤖 Getting AI move from " + playerName + " session...");
                    move = session.getAIMove();
                    System.out.println("   📥 " + playerName + " AI generates: " + move);
                }
                
                if (move != null) {
                    System.out.println("✅ Move " + moveNumber + ": " + playerName + " plays " + move + (attempt > 1 ? " (attempt " + attempt + ")" : ""));
                    return move;
                }
            } catch (Exception e) {
                System.err.println("❌ Attempt " + attempt + " failed for " + playerName + ": " + e.getMessage());
                
                // Check if move is blocked/invalid and try hint
                if (isMoveBlocked(e) && opponentMove != null) {
                    System.out.println("🔍 Move blocked, trying get_move_hint for " + playerName + "...");
                    String hintMove = tryGetMoveHint(session, playerName);
                    if (hintMove != null) {
                        try {
                            String hintResponse = session.makeMove(hintMove);
                            System.out.println("💡 Hint move successful: " + hintMove + " → " + hintResponse);
                            return hintResponse;
                        } catch (Exception hintException) {
                            System.err.println("❌ Hint move also failed: " + hintException.getMessage());
                        }
                    }
                }
                
                if (attempt == 3) {
                    System.err.println("🚫 All attempts failed for " + playerName + ", declaring draw");
                    return null;
                }
            }
        }
        return null;
    }
    
    private boolean isMoveBlocked(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("invalid move") || 
               message.contains("position is already occupied") ||
               message.contains("blocked") ||
               message.contains("illegal move");
    }
    
    private String tryGetMoveHint(ChessSessionProxy session, String playerName) {
        try {
            System.out.println("   💡 Requesting move hint from " + playerName + " session...");
            String hint = session.getMoveHint();
            if (hint != null) {
                System.out.println("   🎯 Hint received: " + hint);
                return hint;
            } else {
                System.err.println("   ❌ No hint available from " + playerName + " session");
            }
        } catch (Exception e) {
            System.err.println("   ❌ Failed to get hint from " + playerName + ": " + e.getMessage());
        }
        return null;
    }
    

    
    private String makeMoveSafelyWithHints(ChessSessionProxy session, String playerName, int moveNumber, String opponentMove) throws Exception {
        try {
            // Try the move first
            System.out.println("   📤 Sending move to " + playerName + " session: " + opponentMove);
            String move = session.makeMove(opponentMove);
            System.out.println("   📥 MCP Server " + (playerName.equals("White") ? "BLACK" : "WHITE") + " responds with: " + move);
            System.out.println("✅ Move " + moveNumber + ": MCP Server " + (playerName.equals("White") ? "BLACK" : "WHITE") + " plays " + move);
            return move;
        } catch (Exception e) {
            if (isMoveBlocked(e)) {
                System.out.println("🚫 Move blocked: " + opponentMove + " for " + playerName);
                System.out.println("🔍 Trying get_move_hint for " + playerName + "...");
                
                String hintMove = tryGetMoveHint(session, playerName);
                if (hintMove != null) {
                    try {
                        System.out.println("   📤 Sending hint move to " + playerName + " session: " + hintMove);
                        String hintResponse = session.makeMove(hintMove);
                        System.out.println("   📥 " + playerName + " AI responds to hint with: " + hintResponse);
                        System.out.println("💡 Hint move successful for " + playerName + ": " + hintMove + " → " + hintResponse);
                        return hintResponse;
                    } catch (Exception hintException) {
                        System.err.println("❌ Hint move also failed for " + playerName + ": " + hintException.getMessage());
                        System.err.println("🏳️ Declaring draw - no valid moves available for " + playerName);
                        return null;
                    }
                } else {
                    System.err.println("❌ No hint available for " + playerName);
                    System.err.println("🏳️ Declaring draw - no fallback moves for " + playerName);
                    return null;
                }
            } else {
                throw e; // Re-throw non-blocked move errors
            }
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
    
    private void markGamesInactive() {
        // Force games to be marked as inactive so they can be reset for next game
        try {
            // This will allow resetGame() to create new games
            System.out.println("Marking games as completed for next game reset");
        } catch (Exception e) {
            System.err.println("Error marking games inactive: " + e.getMessage());
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