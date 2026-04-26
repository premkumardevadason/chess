package com.example.chess;

import java.util.List;

/**
 * Centralized move translation utility for all AI systems.
 * Allows AIs to use WHITE training knowledge when playing as BLACK.
 */
public class AIMoveTranslator {
    
    /**
     * Translate a WHITE move to equivalent BLACK move with similar strategic intent
     */
    public static int[] translateWhiteMoveToBlack(int[] whiteMove, String[][] board, List<int[]> blackMoves) {
        String whitePiece = board[whiteMove[0]][whiteMove[1]];
        
        // Map WHITE pieces to BLACK equivalents
        String targetBlackPiece = switch (whitePiece) {
            case "♔" -> "♚"; // King
            case "♕" -> "♛"; // Queen  
            case "♖" -> "♜"; // Rook
            case "♗" -> "♝"; // Bishop
            case "♘" -> "♞"; // Knight
            case "♙" -> "♟"; // Pawn
            default -> null;
        };
        
        if (targetBlackPiece == null) return null;
        
        // Find BLACK piece of same type with similar strategic intent
        for (int[] blackMove : blackMoves) {
            String blackPiece = board[blackMove[0]][blackMove[1]];
            if (targetBlackPiece.equals(blackPiece)) {
                if (hasSimilarStrategicIntent(whiteMove, blackMove, board)) {
                    return blackMove;
                }
            }
        }
        
        // Fallback: find any BLACK piece of same type
        for (int[] blackMove : blackMoves) {
            String blackPiece = board[blackMove[0]][blackMove[1]];
            if (targetBlackPiece.equals(blackPiece)) {
                return blackMove;
            }
        }
        
        // Final fallback: return any valid BLACK move to prevent null
        if (!blackMoves.isEmpty()) {
            return blackMoves.get(0);
        }
        
        return null;
    }
    
    private static boolean hasSimilarStrategicIntent(int[] whiteMove, int[] blackMove, String[][] board) {
        // Check if both moves are captures
        boolean whiteCapturesEnemy = !"".equals(board[whiteMove[2]][whiteMove[3]]) && 
            "♚♛♜♝♞♟".contains(board[whiteMove[2]][whiteMove[3]]);
        boolean blackCapturesEnemy = !"".equals(board[blackMove[2]][blackMove[3]]) && 
            "♔♕♖♗♘♙".contains(board[blackMove[2]][blackMove[3]]);
        
        if (whiteCapturesEnemy && blackCapturesEnemy) {
            double whiteCapValue = getChessPieceValue(board[whiteMove[2]][whiteMove[3]]);
            double blackCapValue = getChessPieceValue(board[blackMove[2]][blackMove[3]]);
            return blackCapValue >= whiteCapValue * 0.8; // Similar or better capture
        }
        
        // Check if both moves advance toward center
        boolean whiteAdvancesCenter = (whiteMove[2] >= 3 && whiteMove[2] <= 4) && (whiteMove[3] >= 3 && whiteMove[3] <= 4);
        boolean blackAdvancesCenter = (blackMove[2] >= 3 && blackMove[2] <= 4) && (blackMove[3] >= 3 && blackMove[3] <= 4);
        
        if (whiteAdvancesCenter && blackAdvancesCenter) {
            return true; // Both control center
        }
        
        // Check if both moves are development moves
        boolean whiteDevelops = whiteMove[0] == 7; // Moving from back rank
        boolean blackDevelops = blackMove[0] == 0; // Moving from back rank
        
        return whiteDevelops && blackDevelops;
    }
    
    private static double getChessPieceValue(String piece) {
        return switch (piece) {
            case "♙", "♟" -> 100.0; // Pawn
            case "♘", "♞" -> 320.0; // Knight
            case "♗", "♝" -> 330.0; // Bishop
            case "♖", "♜" -> 500.0; // Rook
            case "♕", "♛" -> 900.0; // Queen
            case "♔", "♚" -> 20000.0; // King
            default -> 0.0;
        };
    }
    
    /**
     * Check if a move is for a WHITE piece (needs translation when AI plays BLACK)
     */
    public static boolean isWhitePieceMove(int[] move, String[][] board) {
        if (move == null || move.length != 4) return false;
        String piece = board[move[0]][move[1]];
        return "♔♕♖♗♘♙".contains(piece);
    }
    
    /**
     * Check if a move is for a BLACK piece (correct when AI plays BLACK)
     */
    public static boolean isBlackPieceMove(int[] move, String[][] board) {
        if (move == null || move.length != 4) return false;
        String piece = board[move[0]][move[1]];
        return "♚♛♜♝♞♟".contains(piece);
    }
}