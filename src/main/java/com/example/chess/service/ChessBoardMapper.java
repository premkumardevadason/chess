package com.example.chess.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ChessBoardMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(ChessBoardMapper.class);
    
    private Rectangle chessBoardBounds = new Rectangle(50, 50, 800, 800); // Larger bounds for better accuracy
    private Rectangle[][] squareBounds = new Rectangle[8][8];
    private String currentHighlightedSquare = null;
    private long highlightStartTime = 0;
    private static final long HIGHLIGHT_DURATION = 3000; // 3 seconds
    
    @Autowired
    private com.example.chess.WebSocketController webSocketController;
    
    public void initializeSquareBounds() {
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
        if (squareBounds[0][0] == null) {
            initializeSquareBounds();
        }
        
        // Scale from 640x480 video coordinates to 800x800 chess board coordinates
        Point2D scaledPoint = scaleVideoToChessBoard(gazePoint);
        
        // Direct mapping
        String exactSquare = getExactSquare(scaledPoint);
        if (exactSquare != null) {
            handleSquareHighlight(exactSquare);
            return exactSquare;
        }
        
        // Tolerance mapping (15% expansion)
        String tolerantSquare = getSquareWithTolerance(scaledPoint, 0.15);
        if (tolerantSquare != null) {
            handleSquareHighlight(tolerantSquare);
            return tolerantSquare;
        }
        
        return null;
    }
    
    private Point2D scaleVideoToChessBoard(Point2D videoPoint) {
        // Direct mapping - board bounds are updated from frontend
        return new Point2D.Double(videoPoint.getX(), videoPoint.getY());
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
    
    private String getSquareWithTolerance(Point2D gazePoint, double expansionFactor) {
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
    
    private String getSquareName(int row, int col) {
        char file = (char)('a' + col);
        int rank = 8 - row; // Fix coordinate system: row 0 = rank 8, row 7 = rank 1
        return "" + file + rank;
    }
    
    private void handleSquareHighlight(String square) {
        long currentTime = System.currentTimeMillis();
        
        if (!square.equals(currentHighlightedSquare)) {
            highlightSquare(square);
            currentHighlightedSquare = square;
            highlightStartTime = currentTime;
        }
    }
    
    /**
     * Get chess board bounds for frontend coordinate mapping
     */
    public Rectangle getChessBoardBounds() {
        return chessBoardBounds;
    }
    
    /**
     * Get square bounds for a specific chess square
     */
    public Rectangle getSquareBounds(String square) {
        if (square == null || square.length() != 2) return null;
        
        char file = square.charAt(0);
        int rank = Character.getNumericValue(square.charAt(1));
        
        if (file < 'a' || file > 'h' || rank < 1 || rank > 8) return null;
        
        int col = file - 'a';
        int row = 8 - rank; // Convert to array index
        
        if (squareBounds[0][0] == null) {
            initializeSquareBounds();
        }
        
        return squareBounds[row][col];
    }
    
    private void highlightSquare(String square) {
        try {
            Map<String, Object> highlightData = new HashMap<>();
            highlightData.put("square", square);
            highlightData.put("type", "redDot");
            highlightData.put("duration", HIGHLIGHT_DURATION);
            
            webSocketController.sendToAll("/topic/squareHighlight", highlightData);
            logger.debug("Showing red dot on square {}", square);
        } catch (Exception e) {
            logger.warn("Error showing red dot on square: {}", e.getMessage());
        }
    }
    
    public void updateBoardBounds(Rectangle newBounds) {
        this.chessBoardBounds = newBounds;
        initializeSquareBounds();
        logger.debug("Updated chess board bounds to: x={}, y={}, width={}, height={}", 
            newBounds.x, newBounds.y, newBounds.width, newBounds.height);
    }
    
    /**
     * Update board bounds from frontend coordinates (handles page scrolling)
     */
    public void updateBoardBoundsFromFrontend(double left, double top, double width, double height) {
        Rectangle newBounds = new Rectangle(
            (int)Math.round(left), 
            (int)Math.round(top), 
            (int)Math.round(width), 
            (int)Math.round(height)
        );
        updateBoardBounds(newBounds);
    }
}