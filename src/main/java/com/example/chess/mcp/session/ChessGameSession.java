package com.example.chess.mcp.session;

import com.example.chess.ChessAI;
import com.example.chess.mcp.ai.SharedAIService;
import com.example.chess.mcp.game.MCPGameState;
import com.example.chess.mcp.notifications.MCPNotificationService;
import com.example.chess.mcp.utils.UCITranslator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

public class ChessGameSession {
    
    private static final Logger logger = LogManager.getLogger(ChessGameSession.class);
    
    private final String sessionId;
    private final String agentId;
    private final MCPGameState gameState;
    private final String aiOpponent;
    private final String playerColor;
    private final int difficulty;
    private final LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    
    private final ReentrantLock gameLock = new ReentrantLock();
    
    private int movesPlayed = 0;
    private double averageThinkingTime = 0.0;
    private String gameStatus = "active";
    
    private static SharedAIService sharedAIService;
    private static MCPNotificationService notificationService;
    
    @Autowired
    public static void setSharedAIService(SharedAIService service) {
        sharedAIService = service;
    }
    
    @Autowired
    public static void setNotificationService(MCPNotificationService service) {
        notificationService = service;
    }
    
    public ChessGameSession(String sessionId, String agentId, String aiOpponent, 
                           String playerColor, int difficulty) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.gameState = new MCPGameState();
        this.aiOpponent = aiOpponent;
        this.playerColor = playerColor;
        this.difficulty = difficulty;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }
    
    public synchronized MoveResult makeMove(String uciMove) {
        gameLock.lock();
        try {
            logger.debug("Agent {} making UCI move {} in session {}", agentId, uciMove, sessionId);
            
            // Convert UCI move to coordinates
            int[] coords = UCITranslator.parseUCIMove(uciMove);
            if (coords == null) {
                java.util.List<String> legalMoves = getLegalMoves();
                return MoveResult.invalid(uciMove, legalMoves);
            }
            
            // Validate move using shared AI service
            if (!sharedAIService.isValidMove(gameState.getBoard(), coords[0], coords[1], coords[2], coords[3], gameState.isWhiteTurn())) {
                java.util.List<String> legalMoves = getLegalMoves();
                return MoveResult.invalid(uciMove, legalMoves);
            }
            
            // Make the move in the game state
            gameState.makeMove(coords[0], coords[1], coords[2], coords[3]);
            movesPlayed++;
            lastActivity = LocalDateTime.now();
            
            // Check if game is over
            if (gameState.isGameOver()) {
                gameStatus = gameState.getGameStatus();
                return MoveResult.gameOver(uciMove, gameStatus, gameState.getFEN());
            }
            
            // Get AI response using shared AI service
            long startTime = System.currentTimeMillis();
            int[] aiBestMove = sharedAIService.findBestMove(gameState.getBoard(), gameState.isWhiteTurn());
            long thinkingTime = System.currentTimeMillis() - startTime;
            
            String aiMoveUCI = null;
            // Make AI move in the game state
            if (aiBestMove != null && aiBestMove.length == 4) {
                gameState.makeMove(aiBestMove[0], aiBestMove[1], aiBestMove[2], aiBestMove[3]);
                aiMoveUCI = UCITranslator.formatMoveToUCI(aiBestMove);
                movesPlayed++;
            }
            
            updateThinkingTimeAverage(thinkingTime);
            
            // Send notifications
            if (notificationService != null) {
                notificationService.notifyGameMove(agentId, sessionId, uciMove, aiMoveUCI);
                notificationService.notifyGameStateChange(agentId, sessionId, "active");
            }
            
            // Check if game is over after AI move
            if (gameState.isGameOver()) {
                gameStatus = gameState.getGameStatus();
                if (notificationService != null) {
                    notificationService.notifyGameStateChange(agentId, sessionId, gameStatus);
                }
                return MoveResult.gameOver(uciMove, aiMoveUCI, gameStatus, gameState.getFEN(), thinkingTime);
            }
            
            return MoveResult.success(uciMove, aiMoveUCI, gameState.getFEN(), thinkingTime);
            
        } finally {
            gameLock.unlock();
        }
    }
    
    public synchronized GameState getGameState() {
        gameLock.lock();
        try {
            return new GameState(
                sessionId, agentId, gameState.getFEN(), gameState.getMoveHistory(),
                gameState.getCurrentTurn(), gameStatus, movesPlayed, averageThinkingTime
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
    public MCPGameState getGameStateObject() { return gameState; }
    public String getAIOpponent() { return aiOpponent; }
    public String getPlayerColor() { return playerColor; }
    public int getDifficulty() { return difficulty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActivity() { return lastActivity; }
    public int getMovesPlayed() { return movesPlayed; }
    public String getGameStatus() { return gameStatus; }
    
    public java.util.List<String> getLegalMoves() {
        java.util.List<int[]> coordMoves = sharedAIService.getAllValidMoves(gameState.getBoard(), gameState.isWhiteTurn());
        java.util.List<String> uciMoves = new java.util.ArrayList<>();
        for (int[] move : coordMoves) {
            String uciMove = UCITranslator.formatMoveToUCI(move);
            if (uciMove != null) {
                uciMoves.add(uciMove);
            }
        }
        return uciMoves;
    }
    
    public void makeAIOpeningMove() {
        gameLock.lock();
        try {
            if (movesPlayed > 0 || !gameState.isWhiteTurn()) {
                return; // Game already started or not White's turn
            }
            
            // Get AI's opening move (White)
            int[] aiBestMove = sharedAIService.findBestMove(gameState.getBoard(), gameState.isWhiteTurn());
            if (aiBestMove != null && aiBestMove.length == 4) {
                gameState.makeMove(aiBestMove[0], aiBestMove[1], aiBestMove[2], aiBestMove[3]);
                movesPlayed++;
                lastActivity = LocalDateTime.now();
                logger.info("AI opening move: {} [{},{}] to [{},{}]", 
                    gameState.getBoard()[aiBestMove[2]][aiBestMove[3]], 
                    aiBestMove[0], aiBestMove[1], aiBestMove[2], aiBestMove[3]);
            }
        } finally {
            gameLock.unlock();
        }
    }
    
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
        
        public static MoveResult success(String move, String aiMove, String fen, long thinkingTime) {
            return new MoveResult(true, move, aiMove, fen, "active", thinkingTime, null);
        }
        
        public String getFEN() { return gameState; }
        
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