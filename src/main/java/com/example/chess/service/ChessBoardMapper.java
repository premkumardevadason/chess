package com.example.chess.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.HashMap;

@Component
public class ChessBoardMapper {
    
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
        // Video: 640x480, Chess board: 800x800 (positioned at 50,50)
        double scaleX = (double)chessBoardBounds.width / 640.0;
        double scaleY = (double)chessBoardBounds.height / 480.0;
        
        double scaledX = chessBoardBounds.x + (videoPoint.getX() * scaleX);
        double scaledY = chessBoardBounds.y + (videoPoint.getY() * scaleY);
        
        return new Point2D.Double(scaledX, scaledY);
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
    
    private void highlightSquare(String square) {
        try {
            Map<String, Object> highlightData = new HashMap<>();
            highlightData.put("square", square);
            highlightData.put("color", "blue");
            highlightData.put("duration", HIGHLIGHT_DURATION);
            
            webSocketController.sendToAll("/topic/squareHighlight", highlightData);
            // Square highlighting logging removed
        } catch (Exception e) {
            // Error highlighting logging removed
        }
    }
    
    public void updateBoardBounds(Rectangle newBounds) {
        this.chessBoardBounds = newBounds;
        initializeSquareBounds();
    }
}