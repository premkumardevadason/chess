package com.example.chess.mcp.session;

import com.example.chess.ChessGame;
import com.example.chess.ChessAI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

public class ChessGameSession {
    
    private static final Logger logger = LogManager.getLogger(ChessGameSession.class);
    
    private final String sessionId;
    private final String agentId;
    private final ChessGame game;
    private final ChessAI ai;
    private final String playerColor;
    private final int difficulty;
    private final LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    
    private final ReentrantLock gameLock = new ReentrantLock();
    
    private int movesPlayed = 0;
    private double averageThinkingTime = 0.0;
    private String gameStatus = "active";
    
    public ChessGameSession(String sessionId, String agentId, ChessGame game, 
                           ChessAI ai, String playerColor, int difficulty) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.game = game;
        this.ai = ai;
        this.playerColor = playerColor;
        this.difficulty = difficulty;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }
    
    public synchronized MoveResult makeMove(String move) {
        gameLock.lock();
        try {
            logger.debug("Agent {} making move {} in session {}", agentId, move, sessionId);
            
            // Mock move validation for now
            boolean moveValid = true; // Assume all moves are valid for testing
            if (!moveValid) {
                return MoveResult.invalid(move, java.util.Arrays.asList("e4", "d4", "Nf3"));
            }
            
            movesPlayed++;
            lastActivity = LocalDateTime.now();
            
            if (false) { // Mock - game never over for testing
                gameStatus = "checkmate";
                return MoveResult.gameOver(move, gameStatus, "mock-fen");
            }
            
            long startTime = System.currentTimeMillis();
            String aiMove = ai.getMove(game);
            long thinkingTime = System.currentTimeMillis() - startTime;
            
            // Mock AI move execution
            movesPlayed++;
            
            updateThinkingTimeAverage(thinkingTime);
            
            if (false) { // Mock - game never over
                gameStatus = "checkmate";
                return MoveResult.gameOver(move, aiMove, gameStatus, "mock-fen", thinkingTime);
            }
            
            return MoveResult.success(move, aiMove, "mock-fen-updated", thinkingTime);
            
        } finally {
            gameLock.unlock();
        }
    }
    
    public synchronized GameState getGameState() {
        gameLock.lock();
        try {
            return new GameState(
                sessionId, agentId, "mock-fen", java.util.Arrays.asList("e4", "e5"),
                "white", gameStatus, movesPlayed, averageThinkingTime
            );
        } finally {
            gameLock.unlock();
        }
    }
    
    private void updateThinkingTimeAverage(long thinkingTime) {
        averageThinkingTime = (averageThinkingTime * (movesPlayed - 1) + thinkingTime) / movesPlayed;
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getAgentId() { return agentId; }
    public ChessGame getGame() { return game; }
    public ChessAI getAI() { return ai; }
    public String getPlayerColor() { return playerColor; }
    public int getDifficulty() { return difficulty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActivity() { return lastActivity; }
    public int getMovesPlayed() { return movesPlayed; }
    public String getGameStatus() { return gameStatus; }
    
    public static class MoveResult {
        private final boolean valid;
        private final String move;
        private final String aiMove;
        private final String gameState;
        private final String status;
        private final long thinkingTime;
        private final java.util.List<String> legalMoves;
        
        private MoveResult(boolean valid, String move, String aiMove, String gameState, 
                          String status, long thinkingTime, java.util.List<String> legalMoves) {
            this.valid = valid;
            this.move = move;
            this.aiMove = aiMove;
            this.gameState = gameState;
            this.status = status;
            this.thinkingTime = thinkingTime;
            this.legalMoves = legalMoves;
        }
        
        public static MoveResult success(String move, String aiMove, String gameState, long thinkingTime) {
            return new MoveResult(true, move, aiMove, gameState, "active", thinkingTime, null);
        }
        
        public static MoveResult invalid(String move, java.util.List<String> legalMoves) {
            return new MoveResult(false, move, null, null, null, 0, legalMoves);
        }
        
        public static MoveResult gameOver(String move, String status, String gameState) {
            return new MoveResult(true, move, null, gameState, status, 0, null);
        }
        
        public static MoveResult gameOver(String move, String aiMove, String status, String gameState, long thinkingTime) {
            return new MoveResult(true, move, aiMove, gameState, status, thinkingTime, null);
        }
        
        public boolean isValid() { return valid; }
        public String getMove() { return move; }
        public String getAiMove() { return aiMove; }
        public String getGameState() { return gameState; }
        public String getStatus() { return status; }
        public long getThinkingTime() { return thinkingTime; }
        public java.util.List<String> getLegalMoves() { return legalMoves; }
    }
    
    public static class GameState {
        private final String sessionId;
        private final String agentId;
        private final String fen;
        private final java.util.List<String> moveHistory;
        private final String currentTurn;
        private final String status;
        private final int movesPlayed;
        private final double averageThinkingTime;
        
        public GameState(String sessionId, String agentId, String fen, java.util.List<String> moveHistory,
                        String currentTurn, String status, int movesPlayed, double averageThinkingTime) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.fen = fen;
            this.moveHistory = moveHistory;
            this.currentTurn = currentTurn;
            this.status = status;
            this.movesPlayed = movesPlayed;
            this.averageThinkingTime = averageThinkingTime;
        }
        
        public String getSessionId() { return sessionId; }
        public String getAgentId() { return agentId; }
        public String getFEN() { return fen; }
        public java.util.List<String> getMoveHistory() { return moveHistory; }
        public String getCurrentTurn() { return currentTurn; }
        public String getStatus() { return status; }
        public int getMovesPlayed() { return movesPlayed; }
        public double getAverageThinkingTime() { return averageThinkingTime; }
    }
}