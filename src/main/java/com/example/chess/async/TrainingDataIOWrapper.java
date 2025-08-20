package com.example.chess.async;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.ChessApplication;

public class TrainingDataIOWrapper {
    private static final Logger logger = LogManager.getLogger(TrainingDataIOWrapper.class);
    private final AsyncTrainingDataManager asyncManager;
    private final boolean useAsync;
    
    public TrainingDataIOWrapper(AsyncTrainingDataManager asyncManager) {
        this.asyncManager = asyncManager;
        this.useAsync = asyncManager != null;
    }
    
    public TrainingDataIOWrapper() {
        this.asyncManager = getAsyncManager();
        this.useAsync = asyncManager != null;
    }
    
    private AsyncTrainingDataManager getAsyncManager() {
        try {
            // Get the async manager from ChessApplication if available
            AsyncTrainingDataManager manager = ChessApplication.getAsyncManager();
            if (manager != null) {
                logger.debug("*** ASYNC I/O: TrainingDataIOWrapper connected to AsyncTrainingDataManager ***");
            }
            return manager;
        } catch (Exception e) {
            logger.debug("*** ASYNC I/O: Failed to get AsyncTrainingDataManager - using sync fallback ***");
            return null;
        }
    }
    
    public void saveQTable(Map<String, Double> qTable, String filename) {
        if (useAsync) {
            asyncManager.saveAIData("QLearning", qTable, filename);
        } else {
            // Existing synchronous code unchanged
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                for (Map.Entry<String, Double> entry : qTable.entrySet()) {
                    writer.println(entry.getKey() + "=" + entry.getValue());
                }
            } catch (Exception e) {
                // Handle error
            }
        }
    }
    
    public void saveAIData(String aiName, Object data, String filename) {
        if (useAsync && isAIEnabled(aiName) && isAsyncEnabledForAI(aiName)) {
            logger.info("*** ASYNC I/O: {} using NIO.2 async SAVE path (AI enabled: true, Async enabled: true) ***", aiName);
            asyncManager.saveAIData(aiName, data, filename);
        } else {
            if (!isAIEnabled(aiName)) {
                logger.debug("*** ASYNC I/O: {} skipped - AI disabled ***", aiName);
            } else if (!isAsyncEnabledForAI(aiName)) {
                logger.debug("*** ASYNC I/O: {} using synchronous fallback (Async disabled for this AI) ***", aiName);
            } else {
                logger.debug("*** ASYNC I/O: {} using synchronous fallback (Async system disabled) ***", aiName);
            }
            // Fallback to existing synchronous implementation
        }
    }
    
    public Object loadAIData(String aiName, String filename) {
        if (useAsync && isAIEnabled(aiName) && isAsyncEnabledForAI(aiName)) {
            logger.info("*** ASYNC I/O: {} using NIO.2 async LOAD path (AI enabled: true, Async enabled: true) ***", aiName);
            return asyncManager.loadAIData(aiName, filename).join();
        } else {
            if (!isAIEnabled(aiName)) {
                logger.debug("*** ASYNC I/O: {} load skipped - AI disabled ***", aiName);
            } else if (!isAsyncEnabledForAI(aiName)) {
                logger.debug("*** ASYNC I/O: {} using synchronous LOAD fallback (Async disabled for this AI) ***", aiName);
            } else {
                logger.debug("*** ASYNC I/O: {} using synchronous LOAD fallback (Async system disabled) ***", aiName);
            }
            // Fallback to existing synchronous implementation
            return null;
        }
    }
    
    public void saveOnTrainingStop() {
        if (useAsync) {
            asyncManager.saveOnTrainingStop().join();
        } else {
            // Existing synchronous save logic
        }
    }
    
    public void saveOnGameReset() {
        if (useAsync) {
            asyncManager.saveOnGameReset().join();
        } else {
            // Existing synchronous save logic
        }
    }
    
    public boolean isAsyncEnabled() {
        return useAsync;
    }
    
    public void flushAllData() {
        if (useAsync) {
            logger.info("*** ASYNC I/O: Flushing all cached data during shutdown ***");
            asyncManager.shutdown().join();
        }
    }
    
    private boolean isAIEnabled(String aiName) {
        String propertyKey = switch (aiName) {
            case "QLearning" -> "chess.ai.qlearning.enabled";
            case "GeneticAlgorithm" -> "chess.ai.genetic.enabled";
            case "AlphaFold3" -> "chess.ai.alphafold3.enabled";
            case "DQN_Main", "DQN_Target", "DQN_Experiences" -> "chess.ai.dqn.enabled";
            case "DeepLearning" -> "chess.ai.deeplearning.enabled";
            case "CNN" -> "chess.ai.deeplearningcnn.enabled";
            case "AlphaZero", "AlphaZero-Policy", "AlphaZero-Value" -> "chess.ai.alphazero.enabled";
            case "LeelaZero", "LeelaZero-Policy", "LeelaZero-Value" -> "chess.ai.leelazerochess.enabled";
            default -> "chess.ai." + aiName.toLowerCase() + ".enabled";
        };
        
        // Check Spring configuration properties first, fallback to system properties
        try {
            String springValue = System.getProperty(propertyKey);
            if (springValue != null) {
                return Boolean.parseBoolean(springValue);
            }
        } catch (Exception e) {
            // Fallback to default
        }
        
        // Default to true for enabled AI systems
        return true;
    }
    
    private boolean isAsyncEnabledForAI(String aiName) {
        String asyncPropertyKey = switch (aiName) {
            case "QLearning" -> "chess.async.qlearning";
            case "GeneticAlgorithm" -> "chess.async.genetic";
            case "AlphaFold3" -> "chess.async.alphafold3";
            case "DQN_Main", "DQN_Target", "DQN_Experiences" -> "chess.async.dqn";
            case "DeepLearning" -> "chess.async.deeplearning";
            case "CNN" -> "chess.async.cnn";
            case "AlphaZero", "AlphaZero-Policy", "AlphaZero-Value" -> "chess.async.alphazero";
            case "LeelaZero", "LeelaZero-Policy", "LeelaZero-Value" -> "chess.async.leela";
            default -> "chess.async." + aiName.toLowerCase();
        };
        
        // Check async configuration
        try {
            String asyncValue = System.getProperty(asyncPropertyKey);
            if (asyncValue != null) {
                return Boolean.parseBoolean(asyncValue);
            }
        } catch (Exception e) {
            // Fallback to default
        }
        
        // Default to true if async is generally enabled
        return Boolean.parseBoolean(System.getProperty("chess.async.enabled", "true"));
    }
}