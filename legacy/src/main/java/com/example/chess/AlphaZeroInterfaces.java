package com.example.chess;

import java.util.List;

/**
 * Core interfaces for AlphaZero architecture following SOLID principles.
 */
public interface AlphaZeroInterfaces {
    
    interface NeuralNetwork {
        PolicyValue predict(String[][] board);
        void train(List<TrainingExample> examples);
        String getStatus();
        void shutdown();
        String getTrainingStatus();
        void saveModel();
        int getTrainingEpisodes();
    }
    
    interface MCTSEngine {
        int[] selectBestMove(String[][] board, List<int[]> validMoves);
    }
    
    interface ChessRules {
        List<int[]> getValidMoves(String[][] board, boolean forWhite);
        boolean isGameOver(String[][] board);
        String[][] makeMove(String[][] board, int[] move);
    }
    
    class PolicyValue implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final double[] policy;
        public final double value;
        
        public PolicyValue(double[] policy, double value) {
            this.policy = policy;
            this.value = value;
        }
    }
    
    class TrainingExample {
        public final String[][] board;
        public final double[] policy;
        public final double value;
        
        public TrainingExample(String[][] board, double[] policy, double value) {
            this.board = board;
            this.policy = policy;
            this.value = value;
        }
    }
}