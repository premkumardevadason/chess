package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import com.example.chess.service.CalibrationService;
import com.example.chess.service.EyeTrackingService;
import java.nio.ByteBuffer;

@Component
public class BinaryWebSocketHandler implements WebSocketHandler {

    @Autowired
    private CalibrationService calibrationService;
    
    @Autowired
    private com.example.chess.service.BasicEyeTrackingService basicEyeTrackingService;
    
    @Autowired
    private com.example.chess.service.SimpleMovePredictor simpleMovePredictor;
    
    @Autowired
    private com.example.chess.service.AIPrecomputationService aiPrecomputationService;
    
    @Autowired
    private com.example.chess.service.LSTMMovePredictionAI lstmPredictionAI;
    
    @Autowired
    private com.example.chess.service.DynamicBoardDetector boardDetector;
    
    @Autowired
    private com.example.chess.service.ResourceMonitor resourceMonitor;
    
    @Autowired
    private com.example.chess.service.VisualTrainingDataManager trainingDataManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Binary WebSocket connected: " + session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof BinaryMessage) {
            BinaryMessage binaryMessage = (BinaryMessage) message;
            ByteBuffer payload = binaryMessage.getPayload();
            
            // First byte indicates message type: 1=calibration, 2=video frame
            byte messageType = payload.get();
            
            if (messageType == 1) {
                handleCalibrationData(session, payload);
            } else if (messageType == 2) {
                handleVideoFrame(session, payload);
            }
        }
    }

    private void handleCalibrationData(WebSocketSession session, ByteBuffer payload) {
        try {
            // Extract calibration point info
            int point = payload.getInt();
            int screenX = payload.getInt();
            int screenY = payload.getInt();
            
            // Validate coordinates are reasonable
            if (screenX < 0 || screenX > 10000 || screenY < 0 || screenY > 10000) {
                System.err.println("[CALIBRATION] Invalid coordinates detected: (" + screenX + ", " + screenY + ")");
                return;
            }
            
            // Remaining bytes are image data
            byte[] imageData = new byte[payload.remaining()];
            payload.get(imageData);
            
            System.out.println("[CALIBRATION] Binary data received - Point: " + point + 
                " at (" + screenX + ", " + screenY + ") Image size: " + imageData.length + " bytes");
            
            // Start calibration session if not already started
            try {
                calibrationService.startCalibration(session.getId());
            } catch (Exception e) {
                // Session might already exist, continue
                System.out.println("[CALIBRATION] Session already exists or error starting: " + e.getMessage());
            }
            
            // Process calibration with actual gaze detection
            java.awt.geom.Point2D targetPoint = new java.awt.geom.Point2D.Double(screenX, screenY);
            java.util.List<java.awt.geom.Point2D> gazeSamples = new java.util.ArrayList<>();
            
            // For now, use target point as gaze (placeholder for actual gaze detection)
            // TODO: Integrate with actual gaze detection from image data
            gazeSamples.add(targetPoint);
            
            // Record calibration point
            calibrationService.recordCalibrationPoint(
                session.getId(),
                point,
                targetPoint,
                gazeSamples
            );
            
            System.out.println("[CALIBRATION] Successfully recorded calibration point " + point);
            
        } catch (Exception e) {
            System.err.println("[CALIBRATION] Error processing binary data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleVideoFrame(WebSocketSession session, ByteBuffer payload) {
        try {
            // Extract frame metadata
            long timestamp = payload.getLong();
            int width = payload.getInt();
            int height = payload.getInt();
            
            // Remaining bytes are image data
            byte[] imageData = new byte[payload.remaining()];
            payload.get(imageData);
            
            System.out.println("[VIDEO] Frame metadata: width=" + width + ", height=" + height + ", dataSize=" + imageData.length + ", expected=" + (width * height * 4));
            
            // Process video frame for eye tracking
            try {
                // Check if system can handle frame processing
                if (resourceMonitor != null && !resourceMonitor.canProcessFrame()) {
                    return; // Skip frame due to high system load
                }
                
                if (resourceMonitor != null) {
                    resourceMonitor.startFrameProcessing();
                }
                
                try {
                    // Get gaze point from advanced eye tracking
                    java.awt.geom.Point2D gazePoint = basicEyeTrackingService.processVideoFrame(imageData, width, height);
                    
                    if (gazePoint == null) {
                        System.out.println("[VIDEO] No gaze point detected from frame");
                    }
                    
                    if (gazePoint != null) {
                        // Map gaze to chess square
                        String focusedSquare = basicEyeTrackingService.mapGazeToChessSquare(gazePoint);
                        
                        if (focusedSquare != null) {
                            // Record gaze point for training
                            basicEyeTrackingService.recordGazePoint(session.getId(), gazePoint);
                            
                            // Save encrypted training data
                            if (trainingDataManager != null) {
                                trainingDataManager.saveGazeData(session.getId(), gazePoint, focusedSquare, timestamp);
                            }
                            
                            // Get gaze sequence for LSTM prediction
                            java.util.List<java.awt.geom.Point2D> gazeSequence = getGazeSequence(session.getId());
                            
                            // Try LSTM prediction first
                            com.example.chess.service.LSTMMovePredictionAI.MovePrediction lstmPrediction = null;
                            if (lstmPredictionAI != null && gazeSequence.size() >= 10) {
                                lstmPrediction = lstmPredictionAI.predictMove(session.getId(), gazeSequence, focusedSquare);
                            }
                            
                            // Fallback to simple prediction
                            com.example.chess.service.SimpleMovePredictor.MovePrediction simplePrediction = 
                                simpleMovePredictor.predictMove(session.getId(), focusedSquare);
                            
                            // Use best prediction
                            String predictedMove = null;
                            double confidence = 0.0;
                            String method = "none";
                            
                            if (lstmPrediction != null && lstmPrediction.confidence > 0.7) {
                                predictedMove = lstmPrediction.move;
                                confidence = lstmPrediction.confidence;
                                method = "LSTM";
                            } else if (simplePrediction.move != null && simplePrediction.confidence > 0.6) {
                                predictedMove = simplePrediction.move;
                                confidence = simplePrediction.confidence;
                                method = "Simple";
                            }
                            
                            if (predictedMove != null) {
                                // Precompute AI response
                                aiPrecomputationService.precomputeResponse(predictedMove, confidence);
                                
                                System.out.println("[PREDICTION] " + method + " - Move: " + predictedMove + 
                                    " Confidence: " + String.format("%.2f", confidence));
                            }
                            
                            System.out.println("[GAZE] Square: " + focusedSquare + 
                                " Point: (" + String.format("%.1f", gazePoint.getX()) + 
                                ", " + String.format("%.1f", gazePoint.getY()) + ")");
                        }
                    }
                } finally {
                    if (resourceMonitor != null) {
                        resourceMonitor.endFrameProcessing();
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[VIDEO] Error in eye tracking processing: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("[VIDEO] Error processing frame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Binary WebSocket error: " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        System.out.println("Binary WebSocket disconnected: " + session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    private java.util.List<java.awt.geom.Point2D> getGazeSequence(String sessionId) {
        // Get recent gaze points for LSTM prediction
        java.util.List<java.awt.geom.Point2D> sequence = new java.util.ArrayList<>();
        
        // Get last 30 gaze points (1 second at 30 FPS)
        for (int i = 0; i < 30; i++) {
            java.awt.geom.Point2D lastPoint = basicEyeTrackingService.getLastGazePoint(sessionId);
            if (lastPoint != null) {
                sequence.add(lastPoint);
            }
        }
        
        return sequence;
    }
    

}