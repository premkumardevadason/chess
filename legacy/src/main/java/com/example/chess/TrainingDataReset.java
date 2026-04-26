package com.example.chess;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Training data cleanup and validation utility.
 * Resets corrupted AI training data for fresh start.
 */
public class TrainingDataReset {
    
    private static final Logger logger = LogManager.getLogger(TrainingDataReset.class);
    
    public static void resetAllTrainingData(ChessGame chessGame) {
        logger.info("*** RESETTING ALL CORRUPTED TRAINING DATA ***");
        
        // Delete Q-Learning data
        boolean qDeleted = chessGame.getQLearningAI().deleteQTable();
        logger.info("Q-Learning data deleted: " + qDeleted);
        
        // Delete Deep Learning data  
        boolean dlDeleted = chessGame.getDeepLearningAI().deleteModel();
        logger.info("Deep Learning data deleted: " + dlDeleted);
        
        // Delete DQN data
        boolean dqnDeleted = chessGame.deleteAllTrainingData();
        logger.info("DQN data deleted: " + dqnDeleted);
        
        if (qDeleted && dlDeleted && dqnDeleted) {
            logger.info("*** ALL CORRUPTED TRAINING DATA SUCCESSFULLY DELETED ***");
            logger.info("*** READY FOR FRESH TRAINING WITH CORRECT CHESS RULES ***");
        } else {
            logger.error("*** WARNING: Some training data may not have been deleted ***");
        }
    }
    
    public static void validateFreshStart(ChessGame chessGame) {
        // Verify all systems are reset
        int qTableSize = chessGame.getQLearningAI().getQTableSize();
        boolean dlModelExists = chessGame.getDeepLearningAI().modelFileExists();
        
        logger.info("Post-reset validation:");
        logger.info("- Q-Learning entries: " + qTableSize + " (should be 0)");
        logger.info("- Deep Learning model exists: " + dlModelExists + " (should be false)");
        
        if (qTableSize == 0 && !dlModelExists) {
            logger.info("*** FRESH START VALIDATED - READY FOR CORRECT TRAINING ***");
        } else {
            logger.error("*** WARNING: Reset may be incomplete ***");
        }
    }
}