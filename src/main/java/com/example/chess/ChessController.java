package com.example.chess;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web controller for chess game interface.
 * Handles HTTP requests and WebSocket message broadcasting.
 */
@Controller
public class ChessController {
    
    @Autowired
    private ChessGame game;
    
    @Autowired
    private TrainingManager trainingManager;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    private final BlockingQueue<WebSocketMessage> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService backgroundThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WebSocket-Broadcaster");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean shutdownInProgress = false;
    private volatile long lastBroadcastTime = 0;
    private static final long BROADCAST_THROTTLE_MS = 100; // Minimum 100ms between broadcasts
    
    @PostConstruct
    private void initializeController() {
        game.setController(this);
        startBackgroundBroadcaster();
    }
    
    @PreDestroy
    private void cleanup() {
        shutdownInProgress = true;
        backgroundThread.shutdownNow(); // Interrupts running thread
        try {
            if (!backgroundThread.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("WebSocket broadcaster did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void startBackgroundBroadcaster() {
        backgroundThread.submit(() -> {
            while (!Thread.currentThread().isInterrupted() && !shutdownInProgress) {
                try {
                    WebSocketMessage message = messageQueue.take();
                    if (messagingTemplate != null && !shutdownInProgress) {
                        messagingTemplate.convertAndSend(message.topic, message.payload);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Ignore WebSocket errors during shutdown
                    if (!shutdownInProgress) {
                        System.err.println("WebSocket broadcast error: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private static class WebSocketMessage {
        final String topic;
        final Object payload;
        
        WebSocketMessage(String topic, Object payload) {
            this.topic = topic;
            this.payload = payload;
        }
    }
    
    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    
    public void broadcastTrainingProgress() {
        if (shutdownInProgress) return;
        
        // Throttle broadcasts to prevent buffer overflow
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBroadcastTime < BROADCAST_THROTTLE_MS) {
            return; // Skip this broadcast
        }
        lastBroadcastTime = currentTime;
        
        QLearningAI ai = game.getQLearningAI();
        if (ai == null || !ai.isTraining()) return;
        
        WebSocketController.TrainingProgressMessage progress = new WebSocketController.TrainingProgressMessage(
            ai.isTraining(), 
            ai.getGamesCompleted(), 
            ai.getQTableSize(),
            ai.getCurrentTrainingBoard(),
            ai.getTrainingStatus()
        );
        messageQueue.offer(new WebSocketMessage("/topic/trainingProgress", progress));
    }
    
    public void broadcastGameState(String[][] board, boolean whiteTurn, boolean gameOver, int[] kingInCheck, int[][] threatenedPieces, int[] aiLastMove) {
        if (shutdownInProgress) return;
        
        // Determine if checkmate occurred and who won
        boolean checkmate = false;
        String winner = null;
        
        if (gameOver) {
            // If game is over, determine if it's checkmate or stalemate
            if (kingInCheck != null) {
                // King is in check and game is over = checkmate
                checkmate = true;
                winner = whiteTurn ? "Black" : "White";
            } else {
                // Check if any king is actually in check even if kingInCheck is null
                boolean whiteKingInCheck = isKingInCheck(board, true);
                boolean blackKingInCheck = isKingInCheck(board, false);
                
                if (whiteKingInCheck || blackKingInCheck) {
                    checkmate = true;
                    winner = whiteTurn ? "Black" : "White";
                }
                // If no king in check and game over = stalemate (no winner)
            }
        }
        
        WebSocketController.GameStateMessage gameState = new WebSocketController.GameStateMessage(board, whiteTurn, gameOver, kingInCheck, threatenedPieces, true, aiLastMove, checkmate, winner);
        messageQueue.offer(new WebSocketMessage("/topic/gameState", gameState));
    }
    
    private boolean isKingInCheck(String[][] board, boolean forWhite) {
        String king = forWhite ? "♔" : "♚";
        String enemyPieces = forWhite ? "♚♛♜♝♞♟" : "♔♕♖♗♘♙";
        
        // Find king position
        int kingRow = -1, kingCol = -1;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (king.equals(board[i][j])) {
                    kingRow = i;
                    kingCol = j;
                    break;
                }
            }
        }
        
        if (kingRow == -1) return false;
        
        // Check if any enemy piece can attack the king
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && enemyPieces.contains(piece)) {
                    if (canPieceAttack(piece, i, j, kingRow, kingCol, board)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private boolean canPieceAttack(String piece, int fromRow, int fromCol, int toRow, int toCol, String[][] board) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": return fromRow - toRow == 1 && colDiff == 1; // White pawn
            case "♟": return toRow - fromRow == 1 && colDiff == 1; // Black pawn
            case "♖": case "♜": // Rooks
                return (rowDiff == 0 || colDiff == 0) && isPathClear(fromRow, fromCol, toRow, toCol, board);
            case "♗": case "♝": // Bishops
                return rowDiff == colDiff && rowDiff > 0 && isPathClear(fromRow, fromCol, toRow, toCol, board);
            case "♕": case "♛": // Queens
                return (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) && isPathClear(fromRow, fromCol, toRow, toCol, board);
            case "♘": case "♞": // Knights
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚": // Kings
                return rowDiff <= 1 && colDiff <= 1 && !(rowDiff == 0 && colDiff == 0);
        }
        return false;
    }
    
    private boolean isPathClear(int fromRow, int fromCol, int toRow, int toCol, String[][] board) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (!board[currentRow][currentCol].isEmpty()) {
                return false;
            }
            currentRow += rowDir;
            currentCol += colDir;
        }
        return true;
    }
    
    public void broadcastTrainingBoard(String[][] trainingBoard) {
        if (shutdownInProgress) return;
        
        // Throttle broadcasts to prevent buffer overflow
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBroadcastTime < BROADCAST_THROTTLE_MS) {
            return; // Skip this broadcast
        }
        lastBroadcastTime = currentTime;
        
        QLearningAI ai = game.getQLearningAI();
        if (ai == null || !ai.isTraining()) return;
        
        WebSocketController.TrainingBoardMessage boardState = new WebSocketController.TrainingBoardMessage(trainingBoard);
        messageQueue.offer(new WebSocketMessage("/topic/trainingBoard", boardState));
    }
    
    @PostMapping("/api/evaluate-training-quality")
    @ResponseBody
    public String evaluateTrainingQuality() {
        try {
            trainingManager.evaluateTrainingDataQuality(game);
            return "Training data quality evaluation completed. Check console for detailed report.";
        } catch (Exception e) {
            return "Error evaluating training quality: " + e.getMessage();
        }
    }
}