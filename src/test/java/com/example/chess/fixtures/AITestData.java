package com.example.chess.fixtures;

import java.util.HashMap;
import java.util.Map;

/**
 * Test data fixtures for AI systems
 */
public class AITestData {
    
    /**
     * Sample Q-table entries for testing
     */
    public static Map<String, Double> getSampleQTable() {
        Map<String, Double> qTable = new HashMap<>();
        
        // Opening moves
        qTable.put("e2e4", 0.8);
        qTable.put("d2d4", 0.7);
        qTable.put("Ng1f3", 0.6);
        qTable.put("c2c4", 0.5);
        
        // Defensive moves
        qTable.put("e7e5", 0.7);
        qTable.put("d7d5", 0.6);
        qTable.put("Ng8f6", 0.5);
        qTable.put("c7c5", 0.4);
        
        // Tactical moves
        qTable.put("Qd1h5", 0.9); // Scholar's mate threat
        qTable.put("Bf1c4", 0.8); // Bishop development
        qTable.put("Nb1c3", 0.6); // Knight development
        
        return qTable;
    }
    
    /**
     * Sample neural network weights for testing
     */
    public static double[][] getSampleNeuralWeights() {
        return new double[][] {
            {0.1, 0.2, 0.3, 0.4},
            {0.5, 0.6, 0.7, 0.8},
            {0.9, 1.0, 1.1, 1.2},
            {1.3, 1.4, 1.5, 1.6}
        };
    }
    
    /**
     * Sample experience replay data for DQN
     */
    public static Object[] getSampleExperience() {
        String[][] state = {
            {"♜","♞","♝","♛","♚","♝","♞","♜"},
            {"♟","♟","♟","♟","♟","♟","♟","♟"},
            {"","","","","","","",""},
            {"","","","","","","",""},
            {"","","","","♙","","",""},
            {"","","","","","","",""},
            {"♙","♙","♙","♙","","♙","♙","♙"},
            {"♖","♘","♗","♕","♔","♗","♘","♖"}
        };
        
        int[] action = {6, 4, 4, 4}; // e2-e4
        double reward = 0.1;
        boolean done = false;
        
        return new Object[]{state, action, reward, state, done};
    }
    
    /**
     * Sample genetic algorithm chromosome
     */
    public static double[] getSampleChromosome() {
        return new double[]{
            0.8, 0.6, 0.4, 0.9, 0.3, 0.7, 0.5, 0.2,
            0.1, 0.8, 0.6, 0.4, 0.9, 0.3, 0.7, 0.5
        };
    }
    
    /**
     * Sample population for genetic algorithm
     */
    public static double[][] getSamplePopulation() {
        return new double[][] {
            {0.8, 0.6, 0.4, 0.9, 0.3, 0.7, 0.5, 0.2},
            {0.7, 0.5, 0.3, 0.8, 0.2, 0.6, 0.4, 0.1},
            {0.9, 0.7, 0.5, 1.0, 0.4, 0.8, 0.6, 0.3},
            {0.6, 0.4, 0.2, 0.7, 0.1, 0.5, 0.3, 0.0}
        };
    }
    
    /**
     * Sample MCTS tree node data
     */
    public static Map<String, Object> getSampleMCTSNode() {
        Map<String, Object> node = new HashMap<>();
        node.put("visits", 100);
        node.put("wins", 60);
        node.put("children", 5);
        node.put("ucb1", 0.85);
        return node;
    }
    
    /**
     * Sample opening book entries
     */
    public static Map<String, Integer> getSampleOpeningBook() {
        Map<String, Integer> openings = new HashMap<>();
        
        // Italian Game
        openings.put("e2e4_e7e5_Ng1f3_Nb8c6_Bf1c4", 1500);
        
        // Spanish Opening
        openings.put("e2e4_e7e5_Ng1f3_Nb8c6_Bb1b5", 2000);
        
        // Sicilian Defense
        openings.put("e2e4_c7c5_Ng1f3_d7d6", 1800);
        
        // French Defense
        openings.put("e2e4_e7e6_d2d4_d7d5", 1200);
        
        // King's Indian Defense
        openings.put("d2d4_Ng8f6_c2c4_g7g6", 1000);
        
        return openings;
    }
    
    /**
     * Sample AlphaZero training examples
     */
    public static Object[] getSampleTrainingExample() {
        String[][] position = {
            {"♜","♞","♝","♛","♚","♝","♞","♜"},
            {"♟","♟","♟","♟","♟","♟","♟","♟"},
            {"","","","","","","",""},
            {"","","","","","","",""},
            {"","","","","","","",""},
            {"","","","","","","",""},
            {"♙","♙","♙","♙","♙","♙","♙","♙"},
            {"♖","♘","♗","♕","♔","♗","♘","♖"}
        };
        
        double[] policy = new double[64 * 64]; // All possible moves
        policy[6*8+4 * 64 + 4*8+4] = 0.3; // e2-e4 probability
        policy[6*8+3 * 64 + 4*8+3] = 0.2; // d2-d4 probability
        
        double value = 0.1; // Slight advantage for white
        
        return new Object[]{position, policy, value};
    }
    
    /**
     * Sample A3C training data
     */
    public static Map<String, Object> getSampleA3CData() {
        Map<String, Object> data = new HashMap<>();
        
        data.put("states", new String[][][]{
            {{"♜","♞","♝","♛","♚","♝","♞","♜"},
             {"♟","♟","♟","♟","♟","♟","♟","♟"},
             {"","","","","","","",""},
             {"","","","","","","",""},
             {"","","","","","","",""},
             {"","","","","","","",""},
             {"♙","♙","♙","♙","♙","♙","♙","♙"},
             {"♖","♘","♗","♕","♔","♗","♘","♖"}}
        });
        
        data.put("actions", new int[][]{{6,4,4,4}});
        data.put("rewards", new double[]{0.1});
        data.put("values", new double[]{0.05});
        data.put("advantages", new double[]{0.05});
        
        return data;
    }
    
    /**
     * Sample diffusion trajectory for AlphaFold3
     */
    public static double[] getSampleDiffusionTrajectory() {
        return new double[]{
            0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9
        };
    }
    
    /**
     * Performance benchmarks for testing
     */
    public static Map<String, Double> getPerformanceBenchmarks() {
        Map<String, Double> benchmarks = new HashMap<>();
        
        benchmarks.put("qlearning_games_per_second", 25.0);
        benchmarks.put("dqn_steps_per_second", 15.0);
        benchmarks.put("move_validation_ms", 0.05);
        benchmarks.put("ai_move_selection_ms", 3000.0);
        benchmarks.put("board_update_ms", 0.3);
        benchmarks.put("startup_time_ms", 12000.0);
        
        return benchmarks;
    }
}