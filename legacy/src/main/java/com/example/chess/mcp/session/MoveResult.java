package com.example.chess.mcp.session;

import java.util.List;

public class MoveResult {
    
    private final boolean success;
    private final String playerMove;
    private final String aiMove;
    private final String gameStatus;
    private final String fen;
    private final double thinkingTime;
    private final String error;
    private final List<String> legalMoves;
    
    private MoveResult(boolean success, String playerMove, String aiMove, String gameStatus, 
                      String fen, double thinkingTime, String error, List<String> legalMoves) {
        this.success = success;
        this.playerMove = playerMove;
        this.aiMove = aiMove;
        this.gameStatus = gameStatus;
        this.fen = fen;
        this.thinkingTime = thinkingTime;
        this.error = error;
        this.legalMoves = legalMoves;
    }
    
    public static MoveResult success(String playerMove, String aiMove, String fen, double thinkingTime) {
        return new MoveResult(true, playerMove, aiMove, "active", fen, thinkingTime, null, null);
    }
    
    public static MoveResult gameOver(String playerMove, String gameStatus, String fen) {
        return new MoveResult(true, playerMove, null, gameStatus, fen, 0, null, null);
    }
    
    public static MoveResult gameOver(String playerMove, String aiMove, String gameStatus, String fen, double thinkingTime) {
        return new MoveResult(true, playerMove, aiMove, gameStatus, fen, thinkingTime, null, null);
    }
    
    public static MoveResult invalid(String playerMove, List<String> legalMoves) {
        return new MoveResult(false, playerMove, null, null, null, 0, "Invalid move", legalMoves);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getPlayerMove() { return playerMove; }
    public String getAiMove() { return aiMove; }
    public String getGameStatus() { return gameStatus; }
    public String getFEN() { return fen; }
    public double getThinkingTime() { return thinkingTime; }
    public String getError() { return error; }
    public List<String> getLegalMoves() { return legalMoves; }
}