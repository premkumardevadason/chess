package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import com.example.chess.service.EyeTrackingService;
import com.example.chess.service.GazePredictionManager;

/**
 * Dedicated WebSocket controller for video frame processing
 * Integrates with predictive AI pipeline
 */
@Controller
public class VideoWebSocketController {
    
    @Autowired
    private EyeTrackingService eyeTrackingService;
    
    @Autowired
    private GazePredictionManager gazePredictionManager;
    
    @MessageMapping("/eye-tracking/frame")
    public void handleVideoFrame(VideoFrameMessage frameMessage) {
        try {
            // Enable webcam if not already enabled
            if (!eyeTrackingService.isWebcamEnabled()) {
                eyeTrackingService.enableWebcam(frameMessage.sessionId, true);
            }
            
            // Create gaze pattern from frame data
            GazePredictionManager.GazePattern gazePattern = new GazePredictionManager.GazePattern();
            gazePattern.setFocusedSquare("e4"); // Placeholder - would be extracted from frame
            
            // Trigger predictive AI pipeline
            gazePredictionManager.processGazeUpdate(gazePattern);
            
        } catch (Exception e) {
            System.err.println("[VIDEO] Error processing frame: " + e.getMessage());
        }
    }
    
    public static class VideoFrameMessage {
        public String sessionId;
        public String imageData;
        public long timestamp;
        public int width;
        public int height;
    }
}