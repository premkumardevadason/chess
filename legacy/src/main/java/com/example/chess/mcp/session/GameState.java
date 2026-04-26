package com.example.chess.mcp.session;

import java.time.LocalDateTime;
import java.util.List;

public class GameState {
    
    private final String sessionId;
    private final String agentId;
    private final String fen;
    private final List<String> moveHistory;
    private final String currentTurn;
    private final String status;
    private final int movesPlayed;
    private final double averageThinkingTime;
    private final LocalDateTime lastActivity;
    
    public GameState(String sessionId, String agentId, String fen, List<String> moveHistory,
                    String currentTurn, String status, int movesPlayed, double averageThinkingTime) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.fen = fen;
        this.moveHistory = moveHistory;
        this.currentTurn = currentTurn;
        this.status = status;
        this.movesPlayed = movesPlayed;
        this.averageThinkingTime = averageThinkingTime;
        this.lastActivity = LocalDateTime.now();
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getAgentId() { return agentId; }
    public String getFEN() { return fen; }
    public List<String> getMoveHistory() { return moveHistory; }
    public String getCurrentTurn() { return currentTurn; }
    public String getStatus() { return status; }
    public int getMovesPlayed() { return movesPlayed; }
    public double getAverageThinkingTime() { return averageThinkingTime; }
    public LocalDateTime getLastActivity() { return lastActivity; }
}