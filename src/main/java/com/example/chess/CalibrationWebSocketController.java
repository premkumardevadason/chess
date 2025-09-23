package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.SendTo;
import com.example.chess.service.CalibrationService;
import com.example.chess.service.EyeTrackingService;

/**
 * Dedicated WebSocket controller for calibration data processing
 * Integrates with calibration and gaze correction pipeline
 */
@Controller
public class CalibrationWebSocketController {
    
    @Autowired
    private CalibrationService calibrationService;
    
    @Autowired
    private EyeTrackingService eyeTrackingService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @MessageMapping("/eye-tracking/calibration")
    @SendTo("/topic/calibrationStatus")
    public CalibrationResponse handleCalibration(CalibrationMessage calibrationMessage) {
        try {
            System.out.println("[CALIBRATION] Received calibration point " + calibrationMessage.point + 
                " at (" + calibrationMessage.screenX + ", " + calibrationMessage.screenY + ")");
            
            // Start calibration session if not already started
            CalibrationService.CalibrationSession session = calibrationService.startCalibration("calibration-session");
            
            // Parse percentage coordinates to actual pixel coordinates
            // Assuming a standard screen resolution for now (will be improved with actual screen dimensions)
            double screenWidth = 1920.0; // Default screen width
            double screenHeight = 1080.0; // Default screen height
            
            double xPercent = parsePercentage(calibrationMessage.screenX);
            double yPercent = parsePercentage(calibrationMessage.screenY);
            
            double actualX = (xPercent / 100.0) * screenWidth;
            double actualY = (yPercent / 100.0) * screenHeight;
            
            // Create target point and gaze samples for calibration
            java.awt.geom.Point2D targetPoint = new java.awt.geom.Point2D.Double(actualX, actualY);
            
            java.util.List<java.awt.geom.Point2D> gazeSamples = new java.util.ArrayList<>();
            gazeSamples.add(targetPoint); // Placeholder - would be actual gaze data
            
            // Record calibration point
            calibrationService.recordCalibrationPoint(
                "calibration-session",
                calibrationMessage.point,
                targetPoint,
                gazeSamples
            );
            
            System.out.println("[CALIBRATION] Processed calibration point " + calibrationMessage.point + 
                " -> actual coordinates (" + actualX + ", " + actualY + ")");
            
            return new CalibrationResponse(true, "Calibration point " + calibrationMessage.point + " recorded successfully");
            
        } catch (Exception e) {
            System.err.println("[CALIBRATION] Error: " + e.getMessage());
            e.printStackTrace();
            return new CalibrationResponse(false, "Error processing calibration point: " + e.getMessage());
        }
    }
    
    private double parsePercentage(String percentageStr) {
        if (percentageStr == null || percentageStr.isEmpty()) {
            return 0.0;
        }
        
        // Remove % symbol and parse as double
        String cleanStr = percentageStr.replace("%", "").trim();
        return Double.parseDouble(cleanStr);
    }
    
    @MessageMapping("/eye-tracking/calibration-complete")
    public void handleCalibrationComplete(CalibrationCompleteMessage completeMessage) {
        try {
            System.out.println("[CALIBRATION] Calibration completed at: " + completeMessage.timestamp);
            
            // Complete the calibration session
            calibrationService.completeCalibration("calibration-session");
            
            // Send completion status to frontend
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/eyeTrackingStatus", 
                    new EyeTrackingStatusMessage(true, "Calibration completed"));
            }
        } catch (Exception e) {
            System.err.println("[CALIBRATION] Error handling calibration completion: " + e.getMessage());
        }
    }
    
    public static class CalibrationMessage {
        public int point;
        public String screenX;
        public String screenY;
        public String frameData;
        public long timestamp;
    }
    
    public static class CalibrationCompleteMessage {
        public long timestamp;
    }
    
    public static class EyeTrackingStatusMessage {
        public boolean enabled;
        public String message;
        
        public EyeTrackingStatusMessage(boolean enabled, String message) {
            this.enabled = enabled;
            this.message = message;
        }
    }
    
    public static class CalibrationResponse {
        public boolean success;
        public String message;
        public long timestamp;
        
        public CalibrationResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
}