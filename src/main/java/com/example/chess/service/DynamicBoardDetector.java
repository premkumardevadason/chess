package com.example.chess.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.awt.Rectangle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class DynamicBoardDetector {
    
    private Rectangle currentBoardBounds = null;
    private long lastDetectionTime = 0;
    private static final long DETECTION_INTERVAL = 500;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @Autowired
    private com.example.chess.service.ChessBoardMapper chessBoardMapper;
    
    public void startDynamicDetection() {
        scheduler.scheduleAtFixedRate(this::detectChessBoard, 0, DETECTION_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void detectChessBoard() {
        try {
            Rectangle templateResult = detectByTemplate();
            if (templateResult != null && isValidBoardSize(templateResult)) {
                updateBoardBounds(templateResult, "template");
                return;
            }
            
            if (currentBoardBounds == null) {
                currentBoardBounds = getDefaultBoardBounds();
                updateBoardBounds(currentBoardBounds, "default");
            }
            
        } catch (Exception e) {
            System.err.println("Error in dynamic board detection: " + e.getMessage());
        }
    }
    
    private Rectangle detectByTemplate() {
        Rectangle[] commonPositions = {
            new Rectangle(100, 100, 640, 640),
            new Rectangle(300, 150, 600, 600),
            new Rectangle(500, 200, 560, 560),
            new Rectangle(200, 250, 580, 580)
        };
        
        for (Rectangle pos : commonPositions) {
            if (isLikelyChessBoard(pos)) {
                return pos;
            }
        }
        
        return null;
    }
    
    private boolean isValidBoardSize(Rectangle bounds) {
        int minSize = 400;
        int maxSize = 800;
        
        return bounds.width >= minSize && bounds.width <= maxSize &&
               bounds.height >= minSize && bounds.height <= maxSize &&
               Math.abs(bounds.width - bounds.height) <= 50;
    }
    
    private boolean isLikelyChessBoard(Rectangle bounds) {
        return bounds.x >= 50 && bounds.y >= 50 &&
               bounds.x + bounds.width <= 1800 &&
               bounds.y + bounds.height <= 1000;
    }
    
    private Rectangle getDefaultBoardBounds() {
        return new Rectangle(200, 150, 640, 640);
    }
    
    private void updateBoardBounds(Rectangle newBounds, String method) {
        if (!newBounds.equals(currentBoardBounds)) {
            currentBoardBounds = newBounds;
            lastDetectionTime = System.currentTimeMillis();
            
            if (chessBoardMapper != null) {
                chessBoardMapper.updateBoardBounds(newBounds);
            }
            
            System.out.println("Board detected via " + method + ": " + 
                "x=" + newBounds.x + ", y=" + newBounds.y + 
                ", w=" + newBounds.width + ", h=" + newBounds.height);
        }
    }
    
    public Rectangle getCurrentBoardBounds() {
        return currentBoardBounds;
    }
    
    public boolean isBoardDetected() {
        return currentBoardBounds != null;
    }
    
    public long getLastDetectionTime() {
        return lastDetectionTime;
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}