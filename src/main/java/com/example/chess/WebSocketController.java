package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.example.chess.async.AsyncTrainingDataManager;

@Controller
public class WebSocketController {
    
    private static final int MAX_REQUESTS_PER_SECOND = 10;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> lastResetTime = new ConcurrentHashMap<>();
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ChessGame game;
    
    private AsyncTrainingDataManager asyncDataManager;
    
    // Chess game handlers
    @MessageMapping("/move")
    @SendTo("/topic/gameState")
    public GameStateMessage makeMove(MoveMessage moveMessage) {
        if (!validateMoveInput(moveMessage)) {
            return new GameStateMessage(null, false, false, null, null, false, null, false, null, null, null);
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
            null,
            checkmate,
            winner,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame(),
            null
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
            null,
            false,
            null,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame(),
            null
        );
    }
    
    @MessageMapping("/board")
    @SendTo("/topic/gameState")
    public GameStateMessage getBoard() {
        return new GameStateMessage(
            game.getBoard(),
            game.isWhiteTurn(),
            game.isGameOver(),
            game.getKingInCheckPosition(),
            game.getThreatenedHighValuePieces(),
            true,
            null,
            false,
            null,
            game.isAllAIEnabled() ? "All AIs" : game.getSelectedAIForGame(),
            null
        );
    }
    
    // Eye-tracking handlers
    @MessageMapping("/eye-tracking/consent")
    public void handleEyeTrackingConsent(ConsentMessage consentMessage) {
        System.out.println("Eye-tracking consent: " + consentMessage.consent + " for session: " + consentMessage.sessionId);
        
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", 
                new EyeTrackingStatusMessage(consentMessage.consent, "Consent processed"));
        }
    }
    
    @MessageMapping("/eye-tracking/enable")
    public void handleEyeTrackingEnable(EnableMessage enableMessage) {
        System.out.println("Eye-tracking enabled for session: " + enableMessage.sessionId);
        
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", 
                new EyeTrackingStatusMessage(true, "Eye-tracking enabled"));
        }
    }
    
    @MessageMapping("/eye-tracking/disable")
    public void handleEyeTrackingDisable(DisableMessage disableMessage) {
        System.out.println("Eye-tracking disabled for session: " + disableMessage.sessionId);
        
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", 
                new EyeTrackingStatusMessage(false, "Eye-tracking disabled"));
        }
    }
    
    @MessageMapping("/eye-tracking/calibration-complete")
    public void handleCalibrationComplete(CalibrationCompleteMessage completeMessage) {
        System.out.println("Calibration completed at: " + completeMessage.timestamp);
        
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", 
                new EyeTrackingStatusMessage(true, "Calibration completed"));
        }
    }
    
    // Training handlers
    @MessageMapping("/train")
    public void startTraining() {
        try {
            game.trainAI();
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/training", 
                    new TrainingStatusMessage("All AI training started", true));
            }
        } catch (Exception e) {
            sendTrainingError("Training failed: " + e.getMessage());
        }
    }
    
    @MessageMapping("/stop-training")
    public void stopTraining() {
        game.stopTraining();
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/training", 
                new TrainingStatusMessage("All AI training stopped", true));
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
        if (ChessApplication.shutdownInProgress) return;
        if (progress == null || !progress.isTraining) return;
        
        new Thread(() -> {
            try {
                if (messagingTemplate != null && progress.isTraining && !ChessApplication.shutdownInProgress) {
                    messagingTemplate.convertAndSend("/topic/trainingProgress", progress);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    public void broadcastTrainingBoard(String[][] trainingBoard) {
        if (ChessApplication.shutdownInProgress) return;
        if (game == null || game.getQLearningAI() == null || !game.getQLearningAI().isTraining()) return;
        
        new Thread(() -> {
            try {
                if (messagingTemplate != null && game.getQLearningAI().isTraining() && !ChessApplication.shutdownInProgress) {
                    TrainingBoardMessage boardMessage = new TrainingBoardMessage(trainingBoard);
                    messagingTemplate.convertAndSend("/topic/trainingBoard", boardMessage);
                }
            } catch (Exception ignored) {}
        }).start();
    }
    
    // Utility methods
    private boolean validateMoveInput(MoveMessage move) {
        if (move == null) return false;
        return move.fromRow >= 0 && move.fromRow <= 7 &&
               move.fromCol >= 0 && move.fromCol <= 7 &&
               move.toRow >= 0 && move.toRow <= 7 &&
               move.toCol >= 0 && move.toCol <= 7;
    }
    
    private void sendTrainingError(String message) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/training", 
                    new TrainingStatusMessage(message, false));
            }
        } catch (Exception ignored) {}
    }
    
    // Message classes
    public static class MoveMessage {
        public int fromRow, fromCol, toRow, toCol;
    }
    
    public static class GameStateMessage {
        public String[][] board;
        public boolean whiteTurn;
        public boolean gameOver;
        public int[] kingInCheck;
        public int[][] threatenedPieces;
        public boolean success;
        public int[] aiLastMove;
        public boolean checkmate;
        public String winner;
        public String selectedAI;
        public String lastMoveAI;
        
        public GameStateMessage(String[][] board, boolean whiteTurn, boolean gameOver, 
                               int[] kingInCheck, int[][] threatenedPieces, boolean success, 
                               int[] aiLastMove, boolean checkmate, String winner, String selectedAI, String lastMoveAI) {
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
    
    public static class ConsentMessage {
        public String sessionId;
        public boolean consent;
        public long timestamp;
    }
    
    public static class EnableMessage {
        public String sessionId;
        public long timestamp;
    }
    
    public static class DisableMessage {
        public String sessionId;
        public long timestamp;
    }
    
    public static class CalibrationCompleteMessage {
        public long timestamp;
    }
    
    public static class EyeTrackingStatusMessage {
        public boolean webcamEnabled;
        public String message;
        
        public EyeTrackingStatusMessage(boolean webcamEnabled, String message) {
            this.webcamEnabled = webcamEnabled;
            this.message = message;
        }
    }
}