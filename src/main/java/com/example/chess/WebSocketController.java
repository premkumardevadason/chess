package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.example.chess.async.AsyncTrainingDataManager;

/**
 * WebSocket controller for real-time chess communication.
 * Features rate limiting, security validation, and training progress broadcasting.
 */
@Controller
public class WebSocketController {
    
    // Rate limiting: max 10 requests per second per session (disabled during training)
    private static final int MAX_REQUESTS_PER_SECOND = 10;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastResetTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> trainingActiveSessions = new ConcurrentHashMap<>();
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ChessGame game;
    
    private AsyncTrainingDataManager asyncDataManager;
    
    private boolean useAsyncIO() {
        if (asyncDataManager == null) {
            asyncDataManager = new AsyncTrainingDataManager();
        }
        return true;
    }
    
    @MessageMapping("/move")
    @SendTo("/topic/gameState")
    public GameStateMessage makeMove(MoveMessage moveMessage) {
        if (!validateMoveInput(moveMessage)) {
            return new GameStateMessage(null, false, false, null, null, false, null, false, null);
        }
        
        boolean success = game.makeMove(moveMessage.fromRow, moveMessage.fromCol, moveMessage.toRow, moveMessage.toCol);
        boolean checkmate = game.isGameOver() && game.getKingInCheckPosition() != null;
        String winner = checkmate ? (game.isWhiteTurn() ? "Black" : "White") : null;
        return new GameStateMessage(
            game.getBoard(),
            game.isWhiteTurn(),
            game.isGameOver(),
            game.getKingInCheckPosition(),
            game.getThreatenedHighValuePieces(),
            success,
            null, // No AI move for user moves
            checkmate,
            winner,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame()
        );
    }
    
    @MessageMapping("/newgame")
    @SendTo("/topic/gameState")
    public GameStateMessage newGame() {
        game.resetGame();
        return new GameStateMessage(
            game.getBoard(),
            game.isWhiteTurn(),
            game.isGameOver(),
            game.getKingInCheckPosition(),
            game.getThreatenedHighValuePieces(),
            true,
            null, // No AI move for new game
            false, // No checkmate for new game
            null, // No winner for new game
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame()
        );
    }
    
    @MessageMapping("/train")
    public void startTraining() {
        try {
            game.trainAI();
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/training", 
                    new TrainingStatusMessage("All AI training started - will run until stopped", true));
            }
        } catch (Exception e) {
            sendTrainingError("Training failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }
    

    
    @MessageMapping("/undo")
    @SendTo("/topic/gameState")
    public GameStateMessage undoMove() {
        boolean success = game.undoMove();
        boolean checkmate = game.isGameOver() && game.getKingInCheckPosition() != null;
        String winner = checkmate ? (game.isWhiteTurn() ? "Black" : "White") : null;
        return new GameStateMessage(
            game.getBoard(),
            game.isWhiteTurn(),
            game.isGameOver(),
            game.getKingInCheckPosition(),
            game.getThreatenedHighValuePieces(),
            success,
            null, // No AI move for undo
            checkmate,
            winner,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame()
        );
    }
    
    @MessageMapping("/redo")
    @SendTo("/topic/gameState")
    public GameStateMessage redoMove() {
        boolean success = game.redoMove();
        boolean checkmate = game.isGameOver() && game.getKingInCheckPosition() != null;
        String winner = checkmate ? (game.isWhiteTurn() ? "Black" : "White") : null;
        return new GameStateMessage(
            game.getBoard(),
            game.isWhiteTurn(),
            game.isGameOver(),
            game.getKingInCheckPosition(),
            game.getThreatenedHighValuePieces(),
            success,
            null, // No AI move for redo
            checkmate,
            winner,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame()
        );
    }
    
    @MessageMapping("/stop-training")
    public void stopTraining() {
        if (useAsyncIO()) {
            asyncDataManager.saveOnTrainingStop().join();
        }
        // Existing stop training code unchanged
        game.stopTraining();
        try {
            if (messagingTemplate != null && !ChessApplication.shutdownInProgress) {
                messagingTemplate.convertAndSend("/topic/training", 
                    new TrainingStatusMessage("All AI training stopped", true));
            }
        } catch (Exception ignored) {}
    }
    

    
    @MessageMapping("/validate")
    @SendTo("/topic/validation")
    public ValidationMessage validateMove(MoveMessage moveMessage) {
        boolean valid = game.isValidMove(moveMessage.fromRow, moveMessage.fromCol, moveMessage.toRow, moveMessage.toCol);
        return new ValidationMessage(valid);
    }
    
    @MessageMapping("/board")
    @SendTo("/topic/gameState")
    public GameStateMessage getBoard() {
        try {
            String[][] board = game.getBoard();
            boolean checkmate = game.isGameOver() && game.getKingInCheckPosition() != null;
            String winner = checkmate ? (game.isWhiteTurn() ? "Black" : "White") : null;
            GameStateMessage message = new GameStateMessage(
                board,
                game.isWhiteTurn(),
                game.isGameOver(),
                game.getKingInCheckPosition(),
                game.getThreatenedHighValuePieces(),
                true,
                null, // No AI move for board request
                checkmate,
                winner,
                game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame()
            );
            return message;
        } catch (Exception e) {
            e.printStackTrace();
            return new GameStateMessage(null, false, false, null, null, false, null, false, null);
        }
    }
    
    @MessageMapping("/ai-status")
    @SendTo("/topic/aiStatus")
    public AIStatusMessage getAIStatus() {
        try {
            StringBuilder status = new StringBuilder();
            
            // GPU/OpenCL status
            status.append("=== GPU ACCELERATION ===\n");
            if (OpenCLDetector.isOpenCLAvailable()) {
                status.append("AMD GPU (OpenCL): ENABLED - ").append(OpenCLDetector.getGPUInfoString()).append("\n");
            } else {
                status.append("AMD GPU (OpenCL): NOT AVAILABLE\n");
            }
            status.append("\n=== AI SYSTEMS ===\n");
            
            // Q-Learning AI
            if (game.isQLearningEnabled()) {
                status.append("Q-Learning: ").append(game.getQLearningAI().getQTableSize()).append(" entries\n");
            } else {
                status.append("Q-Learning: DISABLED\n");
            }
            
            // Deep Learning AI
            if (game.isDeepLearningEnabled()) {
                status.append("Deep Learning: ");
                if (game.getDeepLearningAI().modelFileExists()) {
                    status.append("Model saved, ");
                } else {
                    status.append("No model, ");
                }
                status.append(game.getDeepLearningAI().getTrainingIterations()).append(" iterations\n");
                status.append("Backend: ").append(game.getDeepLearningAI().getBackendInfo()).append("\n");
            } else {
                status.append("Deep Learning: DISABLED\n");
            }
            
            // CNN Deep Learning AI
            if (game.isDeepLearningCNNEnabled()) {
                status.append("CNN Deep Learning: ");
                if (game.getDeepLearningCNNAI().modelFileExists()) {
                    status.append("Model saved, ");
                } else {
                    status.append("No model, ");
                }
                status.append(game.getDeepLearningCNNAI().getTrainingIterations()).append(" iterations\n");
                status.append("CNN Backend: ").append(game.getDeepLearningCNNAI().getBackendInfo()).append("\n");
                status.append("Game Data: ").append(game.getDeepLearningCNNAI().getGameDataSize()).append(" positions\n");
            } else {
                status.append("CNN Deep Learning: DISABLED\n");
            }
            
            // DQN AI
            if (game.isDQNEnabled()) {
                status.append("DQN: Enabled with experience replay\n");
            } else {
                status.append("DQN: DISABLED\n");
            }
            
            // MCTS AI
            if (game.isMCTSEnabled()) {
                status.append("MCTS: ").append(game.getMCTSAI().getSimulationsPerMove()).append(" simulations per move\n");
            } else {
                status.append("MCTS: DISABLED\n");
            }
            
            // AlphaZero AI
            if (game.isAlphaZeroEnabled()) {
                String alphaStatus = game.getAlphaZeroAI().getTrainingStatus();
                // Replace "Iterations" with "Episodes" for AlphaZero
                alphaStatus = alphaStatus.replace("Iterations:", "Episodes:");
                status.append("AlphaZero: ").append(alphaStatus).append("\n");
            } else {
                status.append("AlphaZero: DISABLED\n");
            }
            
            // Negamax AI
            if (game.isNegamaxEnabled()) {
                status.append("Negamax: Enabled with alpha-beta pruning\n");
            } else {
                status.append("Negamax: DISABLED\n");
            }
            
            // OpenAI AI
            if (game.isOpenAiEnabled()) {
                status.append("OpenAI: Enabled with GPT-4\n");
            } else {
                status.append("OpenAI: DISABLED\n");
            }
            
            // LeelaZero AI
            if (game.isLeelaZeroEnabled()) {
                status.append("LeelaZero: Enabled with opening book\n");
            } else {
                status.append("LeelaZero: DISABLED\n");
            }
            
            // Genetic Algorithm AI
            if (game.isGeneticEnabled()) {
                status.append("Genetic Algorithm: Enabled with evolution\n");
            } else {
                status.append("Genetic Algorithm: DISABLED\n");
            }
            
            // AlphaFold3 AI
            if (game.isAlphaFold3Enabled()) {
                int episodes = game.getAlphaFold3AI().getTrainingIterations();
                String trainingStatus = game.getAlphaFold3AI().getTrainingStatus();
                status.append("AlphaFold3: Episodes: ").append(episodes)
                      .append(", Training: ").append(trainingStatus).append("\n");
            } else {
                status.append("AlphaFold3: DISABLED\n");
            }
            
            status.append("\n=== TOTAL: ").append(getEnabledAICount()).append("/11 AI SYSTEMS ENABLED ===\n");
            return new AIStatusMessage(status.toString());
        } catch (Exception e) {
            return new AIStatusMessage("Error getting AI status: " + e.getMessage());
        }
    }
    
    private int getEnabledAICount() {
        int count = 0;
        if (game.isQLearningEnabled()) count++;
        if (game.isDeepLearningEnabled()) count++;
        if (game.isDeepLearningCNNEnabled()) count++;
        if (game.isDQNEnabled()) count++;
        if (game.isMCTSEnabled()) count++;
        if (game.isAlphaZeroEnabled()) count++;
        if (game.isNegamaxEnabled()) count++;
        if (game.isOpenAiEnabled()) count++;
        if (game.isLeelaZeroEnabled()) count++;
        if (game.isGeneticEnabled()) count++;
        if (game.isAlphaFold3Enabled()) count++;
        return count;
    }
    
    @MessageMapping("/delete-training")
    @SendTo("/topic/training")
    public TrainingStatusMessage deleteTraining() {
        try {
            boolean allDeleted = game.deleteAllTrainingData();
            if (allDeleted) {
                return new TrainingStatusMessage("All AI training data deleted successfully", true);
            } else {
                return new TrainingStatusMessage("Some training files could not be deleted", false);
            }
        } catch (IllegalStateException e) {
            return new TrainingStatusMessage("Cannot delete: " + e.getMessage(), false);
        } catch (Exception e) {
            return new TrainingStatusMessage("Failed to delete training data: " + e.getMessage(), false);
        }
    }
    

    
    @MessageMapping("/training-progress")
    @SendTo("/topic/trainingProgress")
    public TrainingProgressMessage getTrainingProgress() {
        try {
            QLearningAI ai = game.getQLearningAI();
            if (ai == null) {
                return new TrainingProgressMessage(false, 0, 0, null, "Q-Learning AI not initialized");
            }
            return new TrainingProgressMessage(
                ai.isTraining(), 
                ai.getGamesCompleted(), 
                ai.getQTableSize(),
                ai.getCurrentTrainingBoard(),
                ai.getTrainingStatus()
            );
        } catch (Exception e) {
            return new TrainingProgressMessage(false, 0, 0, null, "Error: " + e.getMessage());
        }
    }
    

    
    public void sendTrainingProgress(TrainingProgressMessage progress) {
        // Don't send during shutdown
        if (ChessApplication.shutdownInProgress) return;
        
        // Check if training is still active
        if (progress == null || !progress.isTraining) return;
        
        // Async send to prevent training thread blocking
        new Thread(() -> {
            try {
                if (messagingTemplate != null && progress.isTraining && !ChessApplication.shutdownInProgress) {
                    messagingTemplate.convertAndSend("/topic/trainingProgress", progress);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    public void broadcastTrainingBoard(String[][] trainingBoard) {
        // Don't broadcast during shutdown
        if (ChessApplication.shutdownInProgress) return;
        
        // Check if training is active via game state
        if (game == null || game.getQLearningAI() == null || !game.getQLearningAI().isTraining()) return;
        
        // Async broadcast to prevent training thread blocking
        new Thread(() -> {
            try {
                if (messagingTemplate != null && game.getQLearningAI().isTraining() && !ChessApplication.shutdownInProgress) {
                    TrainingBoardMessage boardMessage = new TrainingBoardMessage(trainingBoard);
                    messagingTemplate.convertAndSend("/topic/trainingBoard", boardMessage);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    public void sendGameUpdate(GameStateMessage gameState) {
        // Async send to prevent AI move blocking
        new Thread(() -> {
            try {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/gameState", gameState);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    // Message classes
    public static class MoveMessage {
        public int fromRow, fromCol, toRow, toCol;
    }
    
    public static class TrainingMessage {
        public int games;
    }
    
    public static class GameStateMessage {
        public String[][] board;
        public boolean whiteTurn;
        public boolean gameOver;
        public int[] kingInCheck;
        public int[][] threatenedPieces;
        public boolean success;
        public int[] aiLastMove; // [fromRow, fromCol, toRow, toCol] for AI move blinking
        public boolean checkmate; // New field for checkmate detection
        public String winner; // New field for winner information
        public String selectedAI; // AI currently being used for this game
        public String lastMoveAI; // AI that made the last move
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success) {
            this.board = board;
            this.whiteTurn = whiteTurn;
            this.gameOver = gameOver;
            this.kingInCheck = kingInCheck;
            this.threatenedPieces = threatenedPieces;
            this.success = success;
            this.aiLastMove = null;
            this.checkmate = false;
            this.winner = null;
            this.selectedAI = null;
        }
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success, int[] aiLastMove) {
            this.board = board;
            this.whiteTurn = whiteTurn;
            this.gameOver = gameOver;
            this.kingInCheck = kingInCheck;
            this.threatenedPieces = threatenedPieces;
            this.success = success;
            this.aiLastMove = aiLastMove;
            this.checkmate = false;
            this.winner = null;
            this.selectedAI = null;
        }
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success, int[] aiLastMove, 
                               boolean checkmate, String winner) {
            this.board = board;
            this.whiteTurn = whiteTurn;
            this.gameOver = gameOver;
            this.kingInCheck = kingInCheck;
            this.threatenedPieces = threatenedPieces;
            this.success = success;
            this.aiLastMove = aiLastMove;
            this.checkmate = checkmate;
            this.winner = winner;
            this.selectedAI = null;
        }
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success, int[] aiLastMove, 
                               boolean checkmate, String winner, String selectedAI) {
            this.board = board;
            this.whiteTurn = whiteTurn;
            this.gameOver = gameOver;
            this.kingInCheck = kingInCheck;
            this.threatenedPieces = threatenedPieces;
            this.success = success;
            this.aiLastMove = aiLastMove;
            this.checkmate = checkmate;
            this.winner = winner;
            this.selectedAI = selectedAI;
            this.lastMoveAI = null;
        }
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success, int[] aiLastMove, 
                               boolean checkmate, String winner, String selectedAI, String lastMoveAI) {
            this.board = board;
            this.whiteTurn = whiteTurn;
            this.gameOver = gameOver;
            this.kingInCheck = kingInCheck;
            this.threatenedPieces = threatenedPieces;
            this.success = success;
            this.aiLastMove = aiLastMove;
            this.checkmate = checkmate;
            this.winner = winner;
            this.selectedAI = selectedAI;
            this.lastMoveAI = lastMoveAI;
        }
    }
    
    public static class TrainingStatusMessage {
        public String message;
        public boolean success;
        
        public TrainingStatusMessage(String message, boolean success) {
            this.message = message;
            this.success = success;
        }
    }
    
    public static class ValidationMessage {
        public boolean valid;
        
        public ValidationMessage(boolean valid) {
            this.valid = valid;
        }
    }
    
    public static class AIStatusMessage {
        public String status;
        
        public AIStatusMessage(String status) {
            this.status = status;
        }
    }
    
    public static class TrainingProgressMessage {
        public boolean isTraining;
        public int gamesCompleted;
        public int qTableSize;
        public String[][] trainingBoard;
        public String status;
        
        public TrainingProgressMessage(boolean isTraining, int gamesCompleted, int qTableSize, String[][] trainingBoard, String status) {
            this.isTraining = isTraining;
            this.gamesCompleted = gamesCompleted;
            this.qTableSize = qTableSize;
            this.trainingBoard = trainingBoard;
            this.status = status;
        }
    }
    
    public static class TrainingBoardMessage {
        public String[][] board;
        
        public TrainingBoardMessage(String[][] board) {
            this.board = board;
        }
    }
    

    
    // Security validation methods (simplified without authentication)
    private boolean isValidRequest(SimpMessageHeaderAccessor headerAccessor) {
        return headerAccessor != null && headerAccessor.getSessionId() != null;
    }
    
    private boolean checkRateLimit(String sessionId) {
        long currentTime = System.currentTimeMillis();
        AtomicLong lastReset = lastResetTime.computeIfAbsent(sessionId, k -> new AtomicLong(currentTime));
        AtomicInteger count = requestCounts.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        
        // Reset counter every second
        if (currentTime - lastReset.get() > 1000) {
            count.set(0);
            lastReset.set(currentTime);
        }
        
        return count.incrementAndGet() <= MAX_REQUESTS_PER_SECOND;
    }
    
    private boolean validateMoveInput(MoveMessage move) {
        if (move == null) return false;
        // Validate chess board coordinates (0-7)
        return move.fromRow >= 0 && move.fromRow <= 7 &&
               move.fromCol >= 0 && move.fromCol <= 7 &&
               move.toRow >= 0 && move.toRow <= 7 &&
               move.toCol >= 0 && move.toCol <= 7;
    }
    

    
    private String sanitizeErrorMessage(String message) {
        if (message == null) return "Unknown error";
        // Remove potentially sensitive information
        return message.replaceAll("(?i)(password|token|key|secret)", "***")
                     .substring(0, Math.min(message.length(), 200));
    }
    
    private void sendTrainingError(String message) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/training", 
                    new TrainingStatusMessage(message, false));
            }
        } catch (Exception ignored) {}
    }
    
    // Cleanup method for session management
    public void cleanupSession(String sessionId) {
        requestCounts.remove(sessionId);
        lastResetTime.remove(sessionId);
        trainingActiveSessions.remove(sessionId);
    }
}