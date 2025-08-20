package com.example.chess;

/**
 * Training data cleanup and validation utility.
 * Resets corrupted AI training data for fresh start.
 */
public class TrainingDataReset {
    
    public static void resetAllTrainingData(ChessGame chessGame) {
        System.out.println("*** RESETTING ALL CORRUPTED TRAINING DATA ***");
        
        // Delete Q-Learning data
        boolean qDeleted = chessGame.getQLearningAI().deleteQTable();
        System.out.println("Q-Learning data deleted: " + qDeleted);
        
        // Delete Deep Learning data  
        boolean dlDeleted = chessGame.getDeepLearningAI().deleteModel();
        System.out.println("Deep Learning data deleted: " + dlDeleted);
        
        // Delete DQN data
        boolean dqnDeleted = chessGame.deleteAllTrainingData();
        System.out.println("DQN data deleted: " + dqnDeleted);
        
        if (qDeleted && dlDeleted && dqnDeleted) {
            System.out.println("*** ALL CORRUPTED TRAINING DATA SUCCESSFULLY DELETED ***");
            System.out.println("*** READY FOR FRESH TRAINING WITH CORRECT CHESS RULES ***");
        } else {
            System.err.println("*** WARNING: Some training data may not have been deleted ***");
        }
    }
    
    public static void validateFreshStart(ChessGame chessGame) {
        // Verify all systems are reset
        int qTableSize = chessGame.getQLearningAI().getQTableSize();
        boolean dlModelExists = chessGame.getDeepLearningAI().modelFileExists();
        
        System.out.println("Post-reset validation:");
        System.out.println("- Q-Learning entries: " + qTableSize + " (should be 0)");
        System.out.println("- Deep Learning model exists: " + dlModelExists + " (should be false)");
        
        if (qTableSize == 0 && !dlModelExists) {
            System.out.println("*** FRESH START VALIDATED - READY FOR CORRECT TRAINING ***");
        } else {
            System.err.println("*** WARNING: Reset may be incomplete ***");
        }
    }
}