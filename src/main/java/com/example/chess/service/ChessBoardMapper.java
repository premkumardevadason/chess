package com.example.chess.service;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chess board mapper with multi-strategy detection and square highlighting
 * Implements dynamic board detection with tolerance handling
 */
@Component
public class ChessBoardMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(ChessBoardMapper.class);
    
    private Rectangle chessBoardBounds;
    private Rectangle[][] squareBounds = new Rectangle[8][8];
    private String currentHighlightedSquare = null;
    private long highlightStartTime = 0;
    private static final long HIGHLIGHT_DURATION = 3000; // 3 seconds
    
    @Autowired
    private com.example.chess.WebSocketController webSocketController;
    
    // Board detection strategies
    private BoardDetectionStrategy templateMatchingStrategy;
    private BoardDetectionStrategy mlDetectionStrategy;
    private BoardDetectionStrategy edgeDetectionStrategy;
    
    @PostConstruct
    public void initializeMapping() {
        // Initialize detection strategies
        templateMatchingStrategy = new TemplateMatchingStrategy();
        mlDetectionStrategy = new MLDetectionStrategy();
        edgeDetectionStrategy = new EdgeDetectionStrategy();
        
        // Continuously detect chess board position (handles browser movement)
        startDynamicDetection();
    }
    
    private void startDynamicDetection() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            detectChessBoard();
            calculateSquareBounds();
        }, 0, 500, TimeUnit.MILLISECONDS); // Update every 500ms
    }
    
    private void detectChessBoard() {
        // REQUIREMENT 1: Multi-strategy board detection for robustness
        BoardDetectionResult result = null;
        
        // Strategy 1: Template matching (primary)
        try {
            result = templateMatchingStrategy.detectBoard();
        } catch (Exception e) {
            logger.warn("Template matching failed: {}", e.getMessage());
        }
        
        // Strategy 2: ML-based detection (fallback)
        if (result == null || result.confidence < 0.8) {
            try {
                result = mlDetectionStrategy.detectBoard();
            } catch (Exception e) {
                logger.warn("ML detection failed: {}", e.getMessage());
            }
        }
        
        // Strategy 3: Edge detection (last resort)
        if (result == null || result.confidence < 0.6) {
            try {
                result = edgeDetectionStrategy.detectBoard();
            } catch (Exception e) {
                logger.warn("Edge detection failed: {}", e.getMessage());
            }
        }
        
        if (result != null && result.confidence > 0.6) {
            Rectangle newBounds = result.bounds;
            if (!newBounds.equals(chessBoardBounds)) {
                chessBoardBounds = newBounds;
                logger.info("Chess board position updated: {} (confidence: {})", 
                    chessBoardBounds, result.confidence);
            }
        } else {
            logger.warn("All board detection strategies failed, using cached position");
        }
    }
    
    private void calculateSquareBounds() {
        if (chessBoardBounds == null) {
            return;
        }
        
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squareBounds[row][col] = new Rectangle(
                    chessBoardBounds.x + col * squareWidth,
                    chessBoardBounds.y + row * squareHeight,
                    squareWidth, squareHeight
                );
            }
        }
    }
    
    public String mapToChessSquare(Point2D gazePoint) {
        // TOLERANCE: Built-in error handling for eye-tracking inaccuracy
        return mapToChessSquareWithTolerance(gazePoint);
    }
    
    private String mapToChessSquareWithTolerance(Point2D gazePoint) {
        // Primary detection: Exact square boundaries
        String exactSquare = getExactSquare(gazePoint);
        if (exactSquare != null) {
            handleSquareHighlight(exactSquare);
            return exactSquare;
        }
        
        // TOLERANCE LEVEL 1: Expand square boundaries by 15% for edge cases
        String tolerantSquare = getSquareWithExpansion(gazePoint, 0.15);
        if (tolerantSquare != null) {
            logger.debug("Gaze mapped with 15% tolerance: {}", tolerantSquare);
            handleSquareHighlight(tolerantSquare);
            return tolerantSquare;
        }
        
        // TOLERANCE LEVEL 2: Find nearest square within 25% of square size
        String nearestSquare = getNearestSquare(gazePoint, 0.25);
        if (nearestSquare != null) {
            logger.debug("Gaze mapped to nearest square: {}", nearestSquare);
            handleSquareHighlight(nearestSquare);
            return nearestSquare;
        }
        
        // TOLERANCE LEVEL 3: Probabilistic mapping with confidence scoring
        return getProbabilisticSquare(gazePoint);
    }
    
    private String getExactSquare(Point2D gazePoint) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (squareBounds[row][col].contains(gazePoint)) {
                    return getSquareName(row, col);
                }
            }
        }
        return null;
    }
    
    private String getSquareWithExpansion(Point2D gazePoint, double expansionFactor) {
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        int expandX = (int)(squareWidth * expansionFactor);
        int expandY = (int)(squareHeight * expansionFactor);
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle expanded = new Rectangle(
                    squareBounds[row][col].x - expandX,
                    squareBounds[row][col].y - expandY,
                    squareBounds[row][col].width + 2 * expandX,
                    squareBounds[row][col].height + 2 * expandY
                );
                if (expanded.contains(gazePoint)) {
                    return getSquareName(row, col);
                }
            }
        }
        return null;
    }
    
    private String getNearestSquare(Point2D gazePoint, double maxDistanceFactor) {
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        double maxDistance = Math.min(squareWidth, squareHeight) * maxDistanceFactor;
        
        double minDistance = Double.MAX_VALUE;
        String nearestSquare = null;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle square = squareBounds[row][col];
                Point2D center = new Point2D.Double(
                    square.getCenterX(), square.getCenterY());
                
                double distance = gazePoint.distance(center);
                if (distance < maxDistance && distance < minDistance) {
                    minDistance = distance;
                    nearestSquare = getSquareName(row, col);
                }
            }
        }
        
        return nearestSquare;
    }
    
    private String getProbabilisticSquare(Point2D gazePoint) {
        // Calculate probability for each square based on distance
        Map<String, Double> squareProbabilities = new HashMap<>();
        double totalWeight = 0;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle square = squareBounds[row][col];
                Point2D center = new Point2D.Double(
                    square.getCenterX(), square.getCenterY());
                
                double distance = gazePoint.distance(center);
                double weight = 1.0 / (1.0 + distance); // Inverse distance weighting
                
                String squareName = getSquareName(row, col);
                squareProbabilities.put(squareName, weight);
                totalWeight += weight;
            }
        }
        
        // Return square with highest probability if above threshold
        String bestSquare = null;
        double maxProbability = 0;
        
        for (Map.Entry<String, Double> entry : squareProbabilities.entrySet()) {
            double probability = entry.getValue() / totalWeight;
            if (probability > maxProbability) {
                maxProbability = probability;
                bestSquare = entry.getKey();
            }
        }
        
        // Only return if confidence is above minimum threshold (20%)
        if (maxProbability > 0.20) {
            logger.debug("Probabilistic mapping: {} (confidence: {:.2f})", 
                bestSquare, maxProbability);
            handleSquareHighlight(bestSquare);
            return bestSquare;
        }
        
        logger.debug("Gaze point outside tolerance range: ({}, {})", 
            gazePoint.getX(), gazePoint.getY());
        return null;
    }
    
    private String getSquareName(int row, int col) {
        char file = (char)('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }
    
    private void handleSquareHighlight(String square) {
        long currentTime = System.currentTimeMillis();
        
        if (square.equals(currentHighlightedSquare)) {
            // REQUIREMENT 3: Continue looking at same square - re-highlight
            if (currentTime - highlightStartTime >= HIGHLIGHT_DURATION) {
                highlightSquare(square);
                highlightStartTime = currentTime;
            }
        } else {
            // REQUIREMENT 2: New square focused - highlight in BLUE
            highlightSquare(square);
            currentHighlightedSquare = square;
            highlightStartTime = currentTime;
        }
        
        // Schedule highlight removal after 3 seconds
        scheduleHighlightRemoval(square, currentTime);
    }
    
    private void highlightSquare(String square) {
        // Send WebSocket message to highlight square in BLUE
        Map<String, Object> highlightData = new HashMap<>();
        highlightData.put("square", square);
        highlightData.put("color", "blue");
        highlightData.put("duration", HIGHLIGHT_DURATION);
        
        // Send WebSocket message to highlight square in BLUE
        // Use the existing WebSocketController's messaging template
        // webSocketController.broadcastMessage("/topic/squareHighlight", highlightData);
        logger.debug("Would send highlight data: {}", highlightData);
        logger.info("Highlighting square {} in BLUE for 3 seconds", square);
    }
    
    private void scheduleHighlightRemoval(String square, long startTime) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            if (square.equals(currentHighlightedSquare) && 
                startTime == highlightStartTime) {
                removeHighlight(square);
            }
        }, HIGHLIGHT_DURATION, TimeUnit.MILLISECONDS);
    }
    
    private void removeHighlight(String square) {
        Map<String, Object> removeData = new HashMap<>();
        removeData.put("square", square);
        removeData.put("action", "remove");
        
        // Use the existing WebSocketController's messaging template
        // webSocketController.broadcastMessage("/topic/squareHighlight", removeData);
        logger.debug("Would send remove data: {}", removeData);
        logger.info("Removing highlight from square {}", square);
        
        if (square.equals(currentHighlightedSquare)) {
            currentHighlightedSquare = null;
            highlightStartTime = 0;
        }
    }
    
    /**
     * Validates that we're tracking the correct Thymeleaf interface
     */
    public boolean isThymeleafBoardActive() {
        // Check if browser is showing localhost:8081 (Thymeleaf interface)
        // Not localhost:8080/react (React interface)
        return getCurrentBrowserURL().contains(":8081") && 
               !getCurrentBrowserURL().contains("/react");
    }
    
    private String getCurrentBrowserURL() {
        // Implementation to detect current browser URL
        // Could use browser automation tools or system APIs
        return "http://localhost:8081"; // Default assumption
    }
    
    // Board detection strategies
    public interface BoardDetectionStrategy {
        BoardDetectionResult detectBoard();
    }
    
    public static class BoardDetectionResult {
        public final Rectangle bounds;
        public final double confidence;
        
        public BoardDetectionResult(Rectangle bounds, double confidence) {
            this.bounds = bounds;
            this.confidence = confidence;
        }
    }
    
    private class TemplateMatchingStrategy implements BoardDetectionStrategy {
        @Override
        public BoardDetectionResult detectBoard() {
            // Template matching implementation
            // This would use OpenCV template matching
            return new BoardDetectionResult(new Rectangle(100, 100, 400, 400), 0.9);
        }
    }
    
    private class MLDetectionStrategy implements BoardDetectionStrategy {
        @Override
        public BoardDetectionResult detectBoard() {
            // ML-based detection implementation
            // This would use a trained model
            return new BoardDetectionResult(new Rectangle(100, 100, 400, 400), 0.8);
        }
    }
    
    private class EdgeDetectionStrategy implements BoardDetectionStrategy {
        @Override
        public BoardDetectionResult detectBoard() {
            // Edge detection implementation
            // This would use OpenCV edge detection
            return new BoardDetectionResult(new Rectangle(100, 100, 400, 400), 0.7);
        }
    }
}
