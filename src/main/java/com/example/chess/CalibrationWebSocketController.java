package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
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
    
    @MessageMapping("/eye-tracking/calibration")
    public void handleCalibration(CalibrationMessage calibrationMessage) {
        try {
            // Start calibration session if not already started
            CalibrationService.CalibrationSession session = calibrationService.startCalibration("calibration-session");
            
            // Create target point and gaze samples for calibration
            java.awt.geom.Point2D targetPoint = new java.awt.geom.Point2D.Double(
                Double.parseDouble(calibrationMessage.screenX),
                Double.parseDouble(calibrationMessage.screenY)
            );
            
            java.util.List<java.awt.geom.Point2D> gazeSamples = new java.util.ArrayList<>();
            gazeSamples.add(targetPoint); // Placeholder - would be actual gaze data
            
            // Record calibration point
            calibrationService.recordCalibrationPoint(
                "calibration-session",
                calibrationMessage.point,
                targetPoint,
                gazeSamples
            );
            
        } catch (Exception e) {
            System.err.println("[CALIBRATION] Error: " + e.getMessage());
        }
    }
    
    public static class CalibrationMessage {
        public int point;
        public String screenX;
        public String screenY;
        public String frameData;
        public long timestamp;
    }
}