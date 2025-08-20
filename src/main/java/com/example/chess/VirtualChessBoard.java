package com.example.chess;

/**
 * Isolated chess board for AI training with Leela Chess Zero opening book integration.
 * Provides training diversity through professional opening sequences.
 */
public class VirtualChessBoard {
    private String[][] board = new String[8][8];
    private boolean whiteTurn = true;
    private ChessRuleValidator ruleValidator = new ChessRuleValidator();
    
    public VirtualChessBoard() {
        initializeStandardBoard();
    }
    
    public VirtualChessBoard(LeelaChessZeroOpeningBook openingBook) {
        initializeWithRandomOpening(openingBook);
    }
    
    public VirtualChessBoard(String[][] sourceBoard, boolean whiteTurn) {
        copyBoard(sourceBoard);
        this.whiteTurn = whiteTurn;
    }
    

    
    private void initializeStandardBoard() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = "";
            }
        }
        
        board[0] = new String[]{"♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"};
        board[1] = new String[]{"♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"};
        board[6] = new String[]{"♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"};
        board[7] = new String[]{"♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"};
        
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = "";
            }
        }
    }
    

    
    private void copyBoard(String[][] sourceBoard) {
        for (int i = 0; i < 8; i++) {
            System.arraycopy(sourceBoard[i], 0, board[i], 0, 8);
        }
    }
    
    public String[][] getBoard() {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidPosition(fromRow, fromCol) || !isValidPosition(toRow, toCol)) {
            return false;
        }
        
        String piece = board[fromRow][fromCol];
        if (piece.isEmpty()) return false;
        
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = "";
        whiteTurn = !whiteTurn;
        return true;
    }
    
    public void generateRandomPosition() {
        // Initialize with standard position for training
        initializeStandardBoard();
    }
    
    private void initializeWithRandomOpening(LeelaChessZeroOpeningBook openingBook) {
        initializeStandardBoard();
        if (openingBook != null) {
            try {
                String[] randomOpening = openingBook.getRandomOpeningSequenceForTraining();
                if (randomOpening != null && randomOpening.length > 0) {
                    applyOpeningMoves(java.util.Arrays.asList(randomOpening));
                }
            } catch (Exception e) {
                // Handle potential exceptions from opening book
                initializeStandardBoard(); // Fallback to standard board
            }
        }
    }
    
    private void applyOpeningMoves(java.util.List<String> moves) {
        for (String move : moves) {
            int[] coords = parseAlgebraicMove(move);
            if (coords != null) {
                makeMove(coords[0], coords[1], coords[2], coords[3]);
            }
        }
    }
    
    private int[] parseAlgebraicMove(String move) {
        if (move.length() < 4) return null;
        try {
            int fromCol = move.charAt(0) - 'a';
            int fromRow = 8 - (move.charAt(1) - '0');
            int toCol = move.charAt(2) - 'a';
            int toRow = 8 - (move.charAt(3) - '0');
            if (isValidPosition(fromRow, fromCol) && isValidPosition(toRow, toCol)) {
                return new int[]{fromRow, fromCol, toRow, toCol};
            }
        } catch (Exception e) {
            // Invalid move format
        }
        return null;
    }
    
    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
    
    public boolean isWhiteTurn() {
        return whiteTurn;
    }
    
    public void setWhiteTurn(boolean whiteTurn) {
        this.whiteTurn = whiteTurn;
    }
    
    public void setBoard(String[][] newBoard) {
        copyBoard(newBoard);
    }
    
    public boolean isGameOver() {
        // Simple game over detection for training
        return false; // Simplified for training purposes
    }
    
    public int getMoveCount() {
        // Simplified move count for training
        return 0;
    }
    
    public java.util.List<int[]> getAllValidMoves(boolean forWhite) {
        return ruleValidator.getAllValidMoves(board, forWhite, whiteTurn);
    }
    
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return ruleValidator.isValidMove(board, fromRow, fromCol, toRow, toCol, whiteTurn);
    }
    
    public ChessRuleValidator getRuleValidator() {
        return ruleValidator;
    }
}