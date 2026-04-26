package com.example.chess;

import java.util.List;

/**
 * Chess rules implementation for AlphaZero MCTS.
 */
public class AlphaZeroChessRules implements AlphaZeroInterfaces.ChessRules {
    
    @Override
    public List<int[]> getValidMoves(String[][] board, boolean forWhite) {
        // Use ChessGame's proper move validation
        ChessGame tempGame = new ChessGame();
        tempGame.setBoard(board);
        tempGame.setWhiteTurn(forWhite);
        List<int[]> validMoves = tempGame.getAllValidMoves(forWhite);
        
        // Limit moves for AlphaZero training efficiency
        return validMoves.size() > 30 ? validMoves.subList(0, 30) : validMoves;
    }
    
    @Override
    public boolean isGameOver(String[][] board) {
        return getValidMoves(board, true).isEmpty() || getValidMoves(board, false).isEmpty();
    }
    
    @Override
    public String[][] makeMove(String[][] board, int[] move) {
        String[][] newBoard = copyBoard(board);
        String piece = newBoard[move[0]][move[1]];
        newBoard[move[2]][move[3]] = piece;
        newBoard[move[0]][move[1]] = "";
        return newBoard;
    }
    
    private void addPieceMoves(String[][] board, int row, int col, String piece, List<int[]> moves) {
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (r == row && c == col) continue;
                
                String target = board[r][c];
                if (target.isEmpty()) {
                    moves.add(new int[]{row, col, r, c});
                } else {
                    boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
                    if (isPieceWhite != isTargetWhite) {
                        moves.add(new int[]{row, col, r, c});
                    }
                }
            }
        }
    }
    
    private boolean isBasicValidMove(String[][] board, int[] move, boolean forWhite) {
        String piece = board[move[0]][move[1]];
        String target = board[move[2]][move[3]];
        
        if (piece.isEmpty()) return false;
        
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        if (isPieceWhite != forWhite) return false;
        
        if (!target.isEmpty()) {
            boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
            if (isTargetWhite == isPieceWhite) return false;
        }
        
        return true;
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                copy[i][j] = board[i][j] == null ? "" : board[i][j];
            }
        }
        return copy;
    }
}