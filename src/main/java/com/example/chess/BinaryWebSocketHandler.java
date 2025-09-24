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
        // WebSocket connection logging removed
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
            
            // Calibration data logging removed
            
            // Start calibration session if not already started
            try {
                calibrationService.startCalibration(session.getId());
            } catch (Exception e) {
                // Session might already exist, continue
                // Calibration session logging removed
            }
            
            // Process calibration with actual gaze detection
            java.awt.geom.Point2D targetPoint = new java.awt.geom.Point2D.Double(screenX, screenY);
            java.util.List<java.awt.geom.Point2D> gazeSamples = new java.util.ArrayList<>();
            
            // Use actual gaze detection from image data
            if (basicEyeTrackingService != null && imageData.length > 0) {
                // Determine frame dimensions from image data size
                int frameSize = imageData.length / 4; // RGBA = 4 bytes per pixel
                int frameWidth = (int) Math.sqrt(frameSize * (4.0/3.0)); // Assume 4:3 aspect ratio
                int frameHeight = frameSize / frameWidth;
                
                // Fallback to common resolutions if calculation seems wrong
                if (frameWidth < 100 || frameHeight < 100) {
                    frameWidth = 320;
                    frameHeight = 240;
                }
                
                java.awt.geom.Point2D actualGaze = basicEyeTrackingService.processVideoFrame(imageData, frameWidth, frameHeight);
                if (actualGaze != null) {
                    gazeSamples.add(actualGaze);
                } else {
                    // Fallback to target if gaze detection fails
                    gazeSamples.add(targetPoint);
                }
            } else {
                // Fallback to target point
                gazeSamples.add(targetPoint);
            }
            
            // Record calibration point
            calibrationService.recordCalibrationPoint(
                session.getId(),
                point,
                targetPoint,
                gazeSamples
            );
            
            // Calibration point logging removed
            
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
            
            // Extract board position (added for dynamic coordinate mapping)
            int boardLeft = payload.getInt();
            int boardTop = payload.getInt();
            int boardWidth = payload.getInt();
            int boardHeight = payload.getInt();
            
            // Update chess board mapper with current board position
            if (basicEyeTrackingService != null) {
                basicEyeTrackingService.updateBoardPosition(boardLeft, boardTop, boardWidth, boardHeight);
            }
            
            // Remaining bytes are image data
            byte[] imageData = new byte[payload.remaining()];
            payload.get(imageData);
            
            // Validate frame size - expect 640x480 or smaller
            int expectedSize = width * height * 4; // RGBA = 4 bytes per pixel
            if (imageData.length != expectedSize) {
                System.err.println("[VIDEO] Frame size mismatch: width=" + width + ", height=" + height + ", dataSize=" + imageData.length + ", expected=" + expectedSize);
                return;
            }
            
            // Frame metadata logging removed - too verbose
            
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
                    
                    // Gaze detection logging removed - too verbose
                    
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
                            
                            // Try LSTM prediction first - only if we have exactly 20 timesteps
                            com.example.chess.service.LSTMMovePredictionAI.MovePrediction lstmPrediction = null;
                            if (lstmPredictionAI != null && gazeSequence.size() == 20) {
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
                                
                                // Prediction logging removed
                            }
                            
                            // Gaze square logging removed - too verbose
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
        // WebSocket error logging removed
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        // WebSocket disconnection logging removed
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    private java.util.List<java.awt.geom.Point2D> getGazeSequence(String sessionId) {
        return basicEyeTrackingService.getGazeSequence(sessionId);
    }
    

}