package com.example.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates structured training scenarios for check/checkmate situations
 */
public class CheckTrainingScenarios {
    
    /**
     * Generate training positions where pieces can capture attacking pieces
     */
    public static List<TrainingScenario> generateCaptureAttackerScenarios() {
        List<TrainingScenario> scenarios = new ArrayList<>();
        
        // Scenario 1: Queen can capture attacking pawn
        String[][] board1 = createEmptyBoard();
        board1[0][4] = "♚"; // Black King
        board1[1][3] = "♙"; // White Pawn giving check
        board1[0][3] = "♛"; // Black Queen can capture
        scenarios.add(new TrainingScenario(board1, "Queen captures checking pawn", 
            new int[]{0, 3, 1, 3}, 500.0));
        
        // Scenario 2: Bishop can capture attacking piece
        String[][] board2 = createEmptyBoard();
        board2[0][4] = "♚"; // Black King
        board2[2][2] = "♖"; // White Rook giving check
        board2[1][1] = "♝"; // Black Bishop can capture
        scenarios.add(new TrainingScenario(board2, "Bishop captures checking rook", 
            new int[]{1, 1, 2, 2}, 300.0));
        
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
        
        public TrainingScenario(String[][] board, String description, int[] correctMove, double reward) {
            this.board = board;
            this.description = description;
            this.correctMove = correctMove;
            this.reward = reward;
        }
    }
}