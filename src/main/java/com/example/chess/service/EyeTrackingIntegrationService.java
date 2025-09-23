package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;
import java.util.HashMap;

/**
 * Integration service that connects eye-tracking backend services with WebSocket communication.
 * This service acts as a bridge between the eye-tracking prediction system and the frontend UI.
 */
@Service
public class EyeTrackingIntegrationService {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private EyeTrackingService eyeTrackingService;
    
    @Autowired
    private ChessBoardMapper chessBoardMapper;
    
    @Autowired
    private GazePredictionManager gazePredictionManager;
    
    /**
     * Sends square highlighting message to frontend
     */
    @Async
    public void highlightSquare(String square) {
        try {
            Map<String, Object> highlightData = new HashMap<>();
            highlightData.put("square", square);
            highlightData.put("color", "blue");
            highlightData.put("duration", 3000);
            
            messagingTemplate.convertAndSend("/topic/squareHighlight", highlightData);
            System.out.println("Highlighting square: " + square);
        } catch (Exception e) {
            System.err.println("Error highlighting square: " + e.getMessage());
        }
    }
    
    /**
     * Removes square highlighting from frontend
     */
    @Async
    public void removeHighlight(String square) {
        try {
            Map<String, Object> removeData = new HashMap<>();
            removeData.put("square", square);
            removeData.put("action", "remove");
            
            messagingTemplate.convertAndSend("/topic/squareHighlight", removeData);
            System.out.println("Removing highlight from square: " + square);
        } catch (Exception e) {
            System.err.println("Error removing highlight: " + e.getMessage());
        }
    }
    
    /**
     * Sends piece intention analysis to frontend
     */
    @Async
    public void sendPieceIntention(String square, String piece, String intention, java.util.List<String> predictedMoves) {
        try {
            Map<String, Object> intentionData = new HashMap<>();
            intentionData.put("square", square);
            intentionData.put("piece", piece);
            intentionData.put("intention", intention);
            intentionData.put("predictedMoves", predictedMoves);
            
            messagingTemplate.convertAndSend("/topic/pieceIntention", intentionData);
            System.out.println("Piece intention: " + piece + " at " + square + " - " + intention);
        } catch (Exception e) {
            System.err.println("Error sending piece intention: " + e.getMessage());
        }
    }
    
    /**
     * Sends move prediction to frontend
     */
    @Async
    public void sendMovePrediction(String move, double confidence) {
        try {
            Map<String, Object> predictionData = new HashMap<>();
            predictionData.put("move", move);
            predictionData.put("confidence", confidence);
            predictionData.put("timestamp", System.currentTimeMillis());
            
            messagingTemplate.convertAndSend("/topic/movePrediction", predictionData);
            System.out.println("Move prediction: " + move + " (confidence: " + Math.round(confidence * 100) + "%)");
        } catch (Exception e) {
            System.err.println("Error sending move prediction: " + e.getMessage());
        }
    }
    
    /**
     * Sends eye-tracking status update to frontend
     */
    @Async
    public void sendEyeTrackingStatus(boolean webcamEnabled, String message) {
        try {
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("webcamEnabled", webcamEnabled);
            statusData.put("message", message);
            statusData.put("timestamp", System.currentTimeMillis());
            
            messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", statusData);
            System.out.println("Eye-tracking status: " + message + " (webcam: " + webcamEnabled + ")");
        } catch (Exception e) {
            System.err.println("Error sending eye-tracking status: " + e.getMessage());
        }
    }
    
    /**
     * Processes gaze data and triggers appropriate UI updates
     */
    public void processGazeData(String square, double confidence) {
        if (square != null && confidence > 0.5) {
            // Highlight the square
            highlightSquare(square);
            
            // Send move prediction if confidence is high enough
            if (confidence > 0.7) {
                // This would integrate with the actual move prediction system
                // For now, we'll just send a placeholder prediction
                sendMovePrediction("predicted_move", confidence);
            }
        }
    }
}
