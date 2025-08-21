package com.example.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates structured training scenarios for check/checkmate situations
 */
public class CheckTrainingScenarios {
    
    /**
     * Generate training positions where pieces can capture attacking pieces
     * Creates scenarios for both WHITE and BLACK perspectives
     */
    public static List<TrainingScenario> generateCaptureAttackerScenarios() {
        List<TrainingScenario> scenarios = new ArrayList<>();
        
        // BLACK perspective: Queen captures attacking pawn
        String[][] board1 = createEmptyBoard();
        board1[0][4] = "♚"; // Black King
        board1[1][3] = "♙"; // White Pawn giving check
        board1[0][3] = "♛"; // Black Queen can capture
        scenarios.add(new TrainingScenario(board1, "BLACK: Queen captures checking pawn", 
            new int[]{0, 3, 1, 3}, 500.0, false));
        
        // WHITE perspective: Queen captures attacking pawn (flipped)
        String[][] board1w = createEmptyBoard();
        board1w[7][4] = "♔"; // White King
        board1w[6][3] = "♟"; // Black Pawn giving check
        board1w[7][3] = "♕"; // White Queen can capture
        scenarios.add(new TrainingScenario(board1w, "WHITE: Queen captures checking pawn", 
            new int[]{7, 3, 6, 3}, 500.0, true));
        
        // BLACK perspective: Bishop captures attacking rook
        String[][] board2 = createEmptyBoard();
        board2[0][4] = "♚"; // Black King
        board2[2][2] = "♖"; // White Rook giving check
        board2[1][1] = "♝"; // Black Bishop can capture
        scenarios.add(new TrainingScenario(board2, "BLACK: Bishop captures checking rook", 
            new int[]{1, 1, 2, 2}, 300.0, false));
        
        // WHITE perspective: Bishop captures attacking rook (flipped)
        String[][] board2w = createEmptyBoard();
        board2w[7][4] = "♔"; // White King
        board2w[5][2] = "♜"; // Black Rook giving check
        board2w[6][1] = "♗"; // White Bishop can capture
        scenarios.add(new TrainingScenario(board2w, "WHITE: Bishop captures checking rook", 
            new int[]{6, 1, 5, 2}, 300.0, true));
        
        return scenarios;
    }
    
    /**
     * Generate training positions where pieces can block attacks
     */
    public static List<TrainingScenario> generateBlockingScenarios() {
        List<TrainingScenario> scenarios = new ArrayList<>();
        
        // Scenario: Knight can block queen attack
        String[][] board = createEmptyBoard();
        board[0][4] = "♚"; // Black King
        board[3][4] = "♕"; // White Queen giving check
        board[1][2] = "♞"; // Black Knight can block
        scenarios.add(new TrainingScenario(board, "Knight blocks queen check", 
            new int[]{1, 2, 2, 4}, 200.0));
        
        return scenarios;
    }
    
    /**
     * Generate positions where King must move to escape
     */
    public static List<TrainingScenario> generateKingEscapeScenarios() {
        List<TrainingScenario> scenarios = new ArrayList<>();
        
        // King escape scenario
        String[][] board = createEmptyBoard();
        board[0][4] = "♚"; // Black King
        board[1][4] = "♙"; // White Pawn giving check
        scenarios.add(new TrainingScenario(board, "King escapes check", 
            new int[]{0, 4, 0, 3}, 100.0));
        
        return scenarios;
    }
    
    private static String[][] createEmptyBoard() {
        String[][] board = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = "";
            }
        }
        return board;
    }
    
    public static class TrainingScenario {
        public String[][] board;
        public String description;
        public int[] correctMove;
        public double reward;
        public boolean isWhitePerspective;
        
        public TrainingScenario(String[][] board, String description, int[] correctMove, double reward, boolean isWhitePerspective) {
            this.board = board;
            this.description = description;
            this.correctMove = correctMove;
            this.reward = reward;
            this.isWhitePerspective = isWhitePerspective;
        }
        
        // Backward compatibility constructor
        public TrainingScenario(String[][] board, String description, int[] correctMove, double reward) {
            this(board, description, correctMove, reward, false); // Default to BLACK perspective
        }
    }
}