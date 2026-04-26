package com.example.chess.mcp.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight game state for MCP sessions
 * Contains only the board state and game logic, no AI systems
 */
public class MCPGameState {
    
    private String[][] board = new String[8][8];
    private boolean whiteTurn = true;
    private boolean gameOver = false;
    // Move history removed
    
    // Castling rights
    private boolean whiteKingMoved = false;
    private boolean blackKingMoved = false;
    private boolean whiteRookKingSideMoved = false;
    private boolean whiteRookQueenSideMoved = false;
    private boolean blackRookKingSideMoved = false;
    private boolean blackRookQueenSideMoved = false;
    
    public MCPGameState() {
        initializeBoard();
    }
    
    private void initializeBoard() {
        // Clear middle squares
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = "";
            }
        }
        
        // White pieces (bottom)
        board[7] = new String[]{"♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"};
        board[6] = new String[]{"♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"};
        
        // Black pieces (top)
        board[0] = new String[]{"♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"};
        board[1] = new String[]{"♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"};
    }
    
    /**
     * Make a move on the board
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        String piece = board[fromRow][fromCol];
        String capturedPiece = board[toRow][toCol];
        
        // Check for King capture - game over
        if ("♔".equals(capturedPiece)) {
            gameOver = true;
        } else if ("♚".equals(capturedPiece)) {
            gameOver = true;
        }
        
        // Handle castling
        if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(toCol - fromCol) == 2) {
            if ("♔".equals(piece)) {
                if (toCol == 6) {
                    board[7][5] = board[7][7];
                    board[7][7] = "";
                } else if (toCol == 2) {
                    board[7][3] = board[7][0];
                    board[7][0] = "";
                }
            } else {
                if (toCol == 6) {
                    board[0][5] = board[0][7];
                    board[0][7] = "";
                } else if (toCol == 2) {
                    board[0][3] = board[0][0];
                    board[0][0] = "";
                }
            }
        }
        
        // Handle pawn promotion
        if (isPawnPromotion(piece, toRow)) {
            if ("♙".equals(piece)) {
                board[toRow][toCol] = "♕"; // White pawn promotes to Queen
            } else if ("♟".equals(piece)) {
                board[toRow][toCol] = "♛"; // Black pawn promotes to Queen
            }
        } else {
            board[toRow][toCol] = piece;
        }
        board[fromRow][fromCol] = "";
        
        // Move history tracking removed
        
        // Update castling rights
        updateCastlingRights(piece, fromRow, fromCol);
        
        // Switch turns
        whiteTurn = !whiteTurn;
        
        return true;
    }
    
    private boolean isPawnPromotion(String piece, int toRow) {
        return ("♙".equals(piece) && toRow == 0) || ("♟".equals(piece) && toRow == 7);
    }
    
    private void updateCastlingRights(String piece, int fromRow, int fromCol) {
        if ("♔".equals(piece)) whiteKingMoved = true;
        if ("♚".equals(piece)) blackKingMoved = true;
        if ("♖".equals(piece)) {
            if (fromRow == 7 && fromCol == 0) whiteRookQueenSideMoved = true;
            if (fromRow == 7 && fromCol == 7) whiteRookKingSideMoved = true;
        }
        if ("♜".equals(piece)) {
            if (fromRow == 0 && fromCol == 0) blackRookQueenSideMoved = true;
            if (fromRow == 0 && fromCol == 7) blackRookKingSideMoved = true;
        }
    }
    
    /**
     * Get current position in FEN notation
     */
    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        
        // Board position
        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                String piece = board[row][col];
                if (piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(unicodeToFEN(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < 7) fen.append("/");
        }
        
        // Active color
        fen.append(" ").append(whiteTurn ? "w" : "b");
        
        // Castling rights
        fen.append(" ");
        StringBuilder castling = new StringBuilder();
        if (!whiteKingMoved && !whiteRookKingSideMoved) castling.append("K");
        if (!whiteKingMoved && !whiteRookQueenSideMoved) castling.append("Q");
        if (!blackKingMoved && !blackRookKingSideMoved) castling.append("k");
        if (!blackKingMoved && !blackRookQueenSideMoved) castling.append("q");
        fen.append(castling.length() > 0 ? castling.toString() : "-");
        
        // En passant and move counters (simplified)
        fen.append(" - 0 1");
        
        return fen.toString();
    }
    
    private String unicodeToFEN(String piece) {
        switch (piece) {
            case "♔": return "K";
            case "♕": return "Q";
            case "♖": return "R";
            case "♗": return "B";
            case "♘": return "N";
            case "♙": return "P";
            case "♚": return "k";
            case "♛": return "q";
            case "♜": return "r";
            case "♝": return "b";
            case "♞": return "n";
            case "♟": return "p";
            default: return "";
        }
    }
    
    // Getters
    public String[][] getBoard() { return board; }
    public boolean isWhiteTurn() { return whiteTurn; }
    public boolean isGameOver() { return gameOver; }
    public List<String> getMoveHistory() { return new ArrayList<>(); }
    public String getCurrentTurn() { return whiteTurn ? "white" : "black"; }
    public String getGameStatus() {
        if (gameOver) {
            return whiteTurn ? "black_wins" : "white_wins";
        }
        return "active";
    }
}