package com.example.chess;

import java.util.List;

/**
 * Leela Chess Zero interfaces following SOLID principles.
 * Aligned with AlphaZero architecture for consistency.
 */
public interface LeelaChessZeroInterfaces {
    
    interface NeuralNetwork {
        PolicyValueResult evaluate(String[][] board, int[] move);
        double evaluateMove(String[][] board, int[] move);
        void trainOnGameData(List<float[]> inputs, List<float[]> policies, List<Float> values);
        String getTrainingStatus();
        double getConfidenceScore();
        void saveModel();
        void resetModel();
        void shutdown();
    }
    
    interface MCTSEngine {
        int[] selectBestMove(String[][] board, List<int[]> validMoves);
    }
    
    interface ChessRules {
        List<int[]> generateValidMoves(String[][] board, boolean isWhiteTurn);
        boolean isGameOver(String[][] board);
        String[][] applyMove(String[][] board, int[] move);
    }
    
    class PolicyValueResult {
        public final double[] policy;
        public final double value;
        
        public PolicyValueResult(double[] policy, double value) {
            this.policy = policy;
            this.value = value;
        }
    }
}