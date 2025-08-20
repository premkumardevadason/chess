package com.example.chess;

import java.util.*;

/**
 * Centralized chess rule validation system.
 * Contains all chess rules extracted from ChessGame for reuse across AI systems.
 */
public class ChessRuleValidator {
    
    public boolean isValidMove(String[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean whiteTurn) {
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
            toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) return false;
        
        String piece = board[fromRow][fromCol];
        if (piece == null || piece.isEmpty()) return false;
        
        boolean isWhitePiece = "♔♕♖♗♘♙".contains(piece);
        if (isWhitePiece != whiteTurn) return false;
        
        String targetPiece = board[toRow][toCol];
        if (targetPiece != null && !targetPiece.isEmpty()) {
            boolean isTargetWhite = "♔♕♖♗♘♙".contains(targetPiece);
            if (isTargetWhite == isWhitePiece) return false;
        }
        
        // Check for castling move first
        if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(toCol - fromCol) == 2) {
            return isCastlingValid(board, fromRow, fromCol, toRow, toCol, piece);
        }
        
        // Special pawn validation with board context
        if ("♙♟".contains(piece)) {
            if (!isValidPawnMove(board, piece, fromRow, fromCol, toRow, toCol)) return false;
        } else {
            if (!isValidPieceMove(piece, fromRow, fromCol, toRow, toCol)) return false;
            if (!"♘♞".contains(piece) && isPathBlocked(board, fromRow, fromCol, toRow, toCol)) return false;
        }
        
        // King safety validation
        String originalTarget = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = "";
        
        String kingPiece = isWhitePiece ? "♔" : "♚";
        int kingRow = -1, kingCol = -1;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (kingPiece.equals(board[i][j])) {
                    kingRow = i;
                    kingCol = j;
                    break;
                }
            }
        }
        
        boolean wouldLeaveKingInCheck = false;
        if (kingRow != -1) {
            wouldLeaveKingInCheck = isSquareUnderAttack(board, kingRow, kingCol, !isWhitePiece);
        }
        
        // Restore board
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = originalTarget;
        
        return !wouldLeaveKingInCheck;
    }
    
    private boolean isValidPawnMove(String[][] board, String piece, int fromRow, int fromCol, int toRow, int toCol) {
        int colDiff = Math.abs(toCol - fromCol);
        String targetPiece = board[toRow][toCol];
        boolean targetEmpty = (targetPiece == null || targetPiece.isEmpty());
        
        if ("♙".equals(piece)) { // White pawn
            if (colDiff == 0) { // Straight move
                if (!targetEmpty) return false; // Can't move forward to occupied square
                if (fromRow == 6 && toRow == 4) { // 2-square initial move
                    return board[5][fromCol].isEmpty(); // Path must be clear
                }
                return fromRow - toRow == 1; // 1-square forward
            } else if (colDiff == 1) { // Diagonal capture
                return fromRow - toRow == 1 && !targetEmpty; // Must capture enemy piece
            }
        } else if ("♟".equals(piece)) { // Black pawn
            if (colDiff == 0) { // Straight move
                if (!targetEmpty) return false; // Can't move forward to occupied square
                if (fromRow == 1 && toRow == 3) { // 2-square initial move
                    return board[2][fromCol].isEmpty(); // Path must be clear
                }
                return toRow - fromRow == 1; // 1-square forward
            } else if (colDiff == 1) { // Diagonal capture
                return toRow - fromRow == 1 && !targetEmpty; // Must capture enemy piece
            }
        }
        return false;
    }
    
    public boolean isValidPieceMove(String piece, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙":
                if (colDiff == 0) {
                    return (fromRow == 6 && toRow == 4) || (fromRow - toRow == 1);
                }
                return fromRow - toRow == 1 && colDiff == 1;
            case "♟":
                if (colDiff == 0) {
                    return (fromRow == 1 && toRow == 3) || (toRow - fromRow == 1);
                }
                return toRow - fromRow == 1 && colDiff == 1;
            case "♖": case "♜":
                return (rowDiff == 0 && colDiff > 0) || (colDiff == 0 && rowDiff > 0);
            case "♗": case "♝":
                return rowDiff == colDiff && rowDiff > 0;
            case "♕": case "♛":
                return ((rowDiff == 0 && colDiff > 0) || (colDiff == 0 && rowDiff > 0) || (rowDiff == colDiff && rowDiff > 0));
            case "♘": case "♞":
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚":
                return rowDiff <= 1 && colDiff <= 1 && (rowDiff > 0 || colDiff > 0);
        }
        return false;
    }
    
    public boolean isPathBlocked(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (board[currentRow][currentCol] != null && !board[currentRow][currentCol].isEmpty()) return true;
            currentRow += rowDir;
            currentCol += colDir;
        }
        
        return false;
    }
    
    public boolean isSquareUnderAttack(String[][] board, int row, int col, boolean byWhite) {
        String enemyPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece != null && !piece.isEmpty() && enemyPieces.contains(piece)) {
                    if (canDirectlyAttack(board, i, j, row, col, piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public boolean canDirectlyAttack(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": return fromRow - toRow == 1 && colDiff == 1;
            case "♟": return toRow - fromRow == 1 && colDiff == 1;
            case "♖": case "♜":
                return (rowDiff == 0 || colDiff == 0) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♗": case "♝":
                return rowDiff == colDiff && rowDiff > 0 && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♕": case "♛":
                return (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♘": case "♞":
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚":
                return rowDiff <= 1 && colDiff <= 1 && !(rowDiff == 0 && colDiff == 0);
        }
        return false;
    }
    
    public boolean isPathClear(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (board[currentRow][currentCol] != null && !board[currentRow][currentCol].isEmpty()) {
                return false;
            }
            currentRow += rowDir;
            currentCol += colDir;
        }
        return true;
    }
    
    public boolean isKingInDanger(String[][] board, boolean forWhite) {
        String king = forWhite ? "♔" : "♚";
        int kingRow = -1, kingCol = -1;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (king.equals(board[i][j])) {
                    kingRow = i;
                    kingCol = j;
                    break;
                }
            }
        }
        
        if (kingRow == -1) return false;
        
        return isSquareUnderAttack(board, kingRow, kingCol, !forWhite);
    }
    
    public List<int[]> getAllValidMoves(String[][] board, boolean forWhite, boolean whiteTurn) {
        List<int[]> moves = new ArrayList<>();
        String pieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece != null && !piece.isEmpty() && pieces.contains(piece)) {
                    for (int r = 0; r < 8; r++) {
                        for (int c = 0; c < 8; c++) {
                            if (i == r && j == c) continue;
                            
                            String target = board[r][c];
                            if ("♔".equals(target) || "♚".equals(target)) {
                                continue; // Skip king captures
                            }
                            
                            if (isValidMove(board, i, j, r, c, whiteTurn)) {
                                moves.add(new int[]{i, j, r, c});
                            }
                        }
                    }
                }
            }
        }
        
        return moves;
    }
    
    public double getChessPieceValue(String piece) {
        switch (piece) {
            case "♙": case "♟": return 100;
            case "♗": case "♝": return 300;
            case "♘": case "♞": return 320;
            case "♖": case "♜": return 500;
            case "♕": case "♛": return 900;
            case "♔": case "♚": return 10000;
            default: return 0;
        }
    }
    
    public boolean isSquareDirectlyUnderAttack(String[][] board, int row, int col, boolean byWhite) {
        return isSquareUnderAttack(board, row, col, byWhite);
    }
    
    private boolean isCastlingValid(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        // White king castling
        if ("♔".equals(piece) && fromRow == 7 && fromCol == 4) {
            if (toRow == 7 && toCol == 6) { // King side
                return board[7][5].isEmpty() && board[7][6].isEmpty() &&
                       "♖".equals(board[7][7]) &&
                       !isSquareUnderAttack(board, 7, 4, false) && 
                       !isSquareUnderAttack(board, 7, 5, false) && 
                       !isSquareUnderAttack(board, 7, 6, false);
            }
            if (toRow == 7 && toCol == 2) { // Queen side
                return board[7][1].isEmpty() && board[7][2].isEmpty() && board[7][3].isEmpty() &&
                       "♖".equals(board[7][0]) &&
                       !isSquareUnderAttack(board, 7, 4, false) && 
                       !isSquareUnderAttack(board, 7, 3, false) && 
                       !isSquareUnderAttack(board, 7, 2, false);
            }
        }
        
        // Black king castling
        if ("♚".equals(piece) && fromRow == 0 && fromCol == 4) {
            if (toRow == 0 && toCol == 6) { // King side
                return board[0][5].isEmpty() && board[0][6].isEmpty() &&
                       "♜".equals(board[0][7]) &&
                       !isSquareUnderAttack(board, 0, 4, true) && 
                       !isSquareUnderAttack(board, 0, 5, true) && 
                       !isSquareUnderAttack(board, 0, 6, true);
            }
            if (toRow == 0 && toCol == 2) { // Queen side
                return board[0][1].isEmpty() && board[0][2].isEmpty() && board[0][3].isEmpty() &&
                       "♜".equals(board[0][0]) &&
                       !isSquareUnderAttack(board, 0, 4, true) && 
                       !isSquareUnderAttack(board, 0, 3, true) && 
                       !isSquareUnderAttack(board, 0, 2, true);
            }
        }
        
        return false;
    }
    
    public boolean isCastlingMove(String[][] board, String piece, int fromRow, int fromCol, int toRow, int toCol,
                                 boolean whiteKingMoved, boolean blackKingMoved, 
                                 boolean whiteRookKingSideMoved, boolean whiteRookQueenSideMoved,
                                 boolean blackRookKingSideMoved, boolean blackRookQueenSideMoved) {
        // White king castling
        if ("♔".equals(piece) && fromRow == 7 && fromCol == 4 && !whiteKingMoved) {
            if (toRow == 7 && toCol == 6) { // King side
                return !whiteRookKingSideMoved && 
                       board[7][5].isEmpty() && board[7][6].isEmpty() &&
                       "♖".equals(board[7][7]) &&
                       !isSquareUnderAttack(board, 7, 4, false) && 
                       !isSquareUnderAttack(board, 7, 5, false) && 
                       !isSquareUnderAttack(board, 7, 6, false);
            }
            if (toRow == 7 && toCol == 2) { // Queen side
                return !whiteRookQueenSideMoved && 
                       board[7][1].isEmpty() && board[7][2].isEmpty() && board[7][3].isEmpty() &&
                       "♖".equals(board[7][0]) &&
                       !isSquareUnderAttack(board, 7, 4, false) && 
                       !isSquareUnderAttack(board, 7, 3, false) && 
                       !isSquareUnderAttack(board, 7, 2, false);
            }
        }
        
        // Black king castling
        if ("♚".equals(piece) && fromRow == 0 && fromCol == 4 && !blackKingMoved) {
            if (toRow == 0 && toCol == 6) { // King side
                return !blackRookKingSideMoved && 
                       board[0][5].isEmpty() && board[0][6].isEmpty() &&
                       "♜".equals(board[0][7]) &&
                       !isSquareUnderAttack(board, 0, 4, true) && 
                       !isSquareUnderAttack(board, 0, 5, true) && 
                       !isSquareUnderAttack(board, 0, 6, true);
            }
            if (toRow == 0 && toCol == 2) { // Queen side
                return !blackRookQueenSideMoved && 
                       board[0][1].isEmpty() && board[0][2].isEmpty() && board[0][3].isEmpty() &&
                       "♜".equals(board[0][0]) &&
                       !isSquareUnderAttack(board, 0, 4, true) && 
                       !isSquareUnderAttack(board, 0, 3, true) && 
                       !isSquareUnderAttack(board, 0, 2, true);
            }
        }
        
        return false;
    }
    
    public boolean isCheckmate(String[][] board, boolean forWhite) {
        if (!isKingInDanger(board, forWhite)) return false;
        
        List<int[]> moves = getAllValidMoves(board, forWhite, forWhite);
        for (int[] move : moves) {
            String movingPiece = board[move[0]][move[1]];
            String capturedPiece = board[move[2]][move[3]];
            
            board[move[2]][move[3]] = movingPiece;
            board[move[0]][move[1]] = "";
            
            boolean stillInCheck = isKingInDanger(board, forWhite);
            
            board[move[0]][move[1]] = movingPiece;
            board[move[2]][move[3]] = capturedPiece;
            
            if (!stillInCheck) return false;
        }
        return true;
    }
    
    public boolean isPawnPromotion(String piece, int toRow) {
        return ("♙".equals(piece) && toRow == 0) || ("♟".equals(piece) && toRow == 7);
    }
    
    public boolean isPiecePinned(String[][] board, int pieceRow, int pieceCol) {
        String piece = board[pieceRow][pieceCol];
        if (piece == null || piece.isEmpty()) return false;
        
        boolean isWhite = "♔♕♖♗♘♙".contains(piece);
        
        // Temporarily remove the piece
        board[pieceRow][pieceCol] = "";
        
        // Check if King would be in check without this piece
        boolean kingInDanger = isKingInDanger(board, isWhite);
        
        // Restore the piece
        board[pieceRow][pieceCol] = piece;
        
        return kingInDanger;
    }
}