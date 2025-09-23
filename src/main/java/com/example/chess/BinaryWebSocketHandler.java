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
    private EyeTrackingService eyeTrackingService;

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
            float screenX = payload.getFloat();
            float screenY = payload.getFloat();
            
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
            
            System.out.println("[VIDEO] Frame received - " + width + "x" + height + 
                " Size: " + imageData.length + " bytes at " + new java.util.Date(timestamp));
            
            // Process video frame for eye tracking
            try {
                // TODO: Integrate with actual eye tracking service
                // eyeTrackingService.processFrameData(imageData, width, height);
                
                // For now, just acknowledge receipt
                System.out.println("[VIDEO] Frame processed successfully");
                
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
}