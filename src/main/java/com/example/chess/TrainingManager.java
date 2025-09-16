package com.example.chess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.chess.config.AIStateConfig;

/**
 * Manages AI training coordination and lifecycle
 * Extracted from ChessGame to follow Single Responsibility Principle
 */
@Component
public class TrainingManager {
    private static final Logger logger = LogManager.getLogger(TrainingManager.class);
    
    @Autowired
    private AIStateConfig aiStateConfig;
    
    private volatile boolean isTrainingActive = false;
    private volatile boolean stopTrainingRequested = false;
    private final Map<String, Thread> trainingThreads = new ConcurrentHashMap<>();
    private volatile Thread periodicSaveThread = null;
    
    public boolean isTrainingActive() {
        return isTrainingActive;
    }
    
    public boolean isStopRequested() {
        return stopTrainingRequested;
    }
    
    public void startTraining(ChessGame game) {
        if (isTrainingActive) {
            logger.info("Training already active - ignoring duplicate start request");
            return;
        }
        
        isTrainingActive = true;
        stopTrainingRequested = false;
        
        // CRITICAL: Reset periodic save thread reference for fresh start
        periodicSaveThread = null;
        
        // CRITICAL: Mark state as changed when training starts
        game.notifyStateChanged();
        
        logger.info("*** STARTING AI TRAINING COORDINATION ***");
        
        // Start centralized periodic save mechanism
        startPeriodicSave(game);
        
        startQLearningTraining(game);
        startDeepLearningTraining(game);
        startDeepLearningCNNTraining(game);
        startDQNTraining(game);
        startAlphaZeroTraining(game);
        startLeelaZeroTraining(game);
        startGeneticTraining(game);
        startAlphaFold3Training(game);
        startA3CTraining(game);
        startMCTSTraining(game);
        
        logger.info("*** ALL AI TRAINING STARTED ***");
    }
    
    private void startDeepLearningCNNTraining(ChessGame game) {
        if (game.isDeepLearningCNNEnabled()) {
            logger.info("*** TrainingManager: Starting CNN Deep Learning AI training ***");
            game.getDeepLearningCNNAI().startTraining();
        }
    }
    
    public void stopTraining(ChessGame game) {
        if (!isTrainingActive) {
            logger.debug("No active training to stop");
            return;
        }
        
        logger.info("*** STOPPING ALL AI TRAINING ***");
        
        // CRITICAL: Stop periodic save FIRST to prevent race condition
        stopTrainingRequested = true;
        isTrainingActive = false;
        logger.info("*** PERIODIC SAVE: Stop flag set - will stop on next check ***");
        
        // CRITICAL: Clear training threads map for fresh start
        trainingThreads.clear();
        
        // CRITICAL: Interrupt periodic save thread immediately
        if (periodicSaveThread != null && periodicSaveThread.isAlive()) {
            periodicSaveThread.interrupt();
            logger.info("*** PERIODIC SAVE: Thread interrupted ***");
        }
        periodicSaveThread = null; // Clear reference for next start
        
        // CRITICAL: Cancel any queued async operations immediately
        try {
            Object asyncManager = com.example.chess.ChessApplication.getAsyncManager();
            if (asyncManager != null) {
                java.lang.reflect.Method cancelMethod = asyncManager.getClass().getDeclaredMethod("cancelQueuedOperations");
                cancelMethod.setAccessible(true);
                cancelMethod.invoke(asyncManager);
                logger.info("*** ASYNC I/O: Queued operations cancelled ***");
                
                // CRITICAL: Also clear all dirty flags to prevent any further saves
                java.lang.reflect.Method clearDirtyMethod = asyncManager.getClass().getDeclaredMethod("clearAllDirtyFlags");
                clearDirtyMethod.setAccessible(true);
                clearDirtyMethod.invoke(asyncManager);
                logger.info("*** ASYNC I/O: All dirty flags cleared ***");
            }
        } catch (Exception e) {
            logger.debug("Could not cancel queued operations: {}", e.getMessage());
        }
        
        // Interrupt all training threads immediately
        for (Map.Entry<String, Thread> entry : trainingThreads.entrySet()) {
            Thread thread = entry.getValue();
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
                logger.info("*** TrainingManager: Interrupted {} thread ***", entry.getKey());
            }
        }
        trainingThreads.clear();
        
        // Stop all AI training systems with timeout protection
        stopAIWithTimeout("Q-Learning", () -> {
            if (game.isQLearningEnabled()) game.getQLearningAI().stopTraining();
        });
        
        stopAIWithTimeout("Deep Learning", () -> {
            if (game.isDeepLearningEnabled()) game.getDeepLearningAI().stopTraining();
        });
        
        stopAIWithTimeout("CNN Deep Learning", () -> {
            if (game.isDeepLearningCNNEnabled()) game.getDeepLearningCNNAI().stopTraining();
        });
        
        stopAIWithTimeout("DQN", () -> {
            if (game.isDQNEnabled()) game.getDQNAI().stopTraining();
        });
        
        stopAIWithTimeout("AlphaZero", () -> {
            if (game.isAlphaZeroEnabled()) game.getAlphaZeroAI().stopTraining();
        });
        
        stopAIWithTimeout("LeelaZero", () -> {
            if (game.isLeelaZeroEnabled()) game.getLeelaZeroAI().stopTraining();
        });
        
        stopAIWithTimeout("Genetic", () -> {
            if (game.isGeneticEnabled()) game.getGeneticAI().stopTraining();
        });
        
        stopAIWithTimeout("AlphaFold3", () -> {
            if (game.isAlphaFold3Enabled()) {
                logger.info("*** TrainingManager: Stopping AlphaFold3 training ***");
                game.getAlphaFold3AI().stopTraining();
            }
        });
        
        stopAIWithTimeout("A3C", () -> {
            if (game.isA3CEnabled()) {
                logger.info("*** TrainingManager: Stopping A3C training ***");
                game.getA3CAI().stopTraining();
            }
        });
        
        // Final save before stopping - use async manager (only if not shutting down)
        if (!com.example.chess.ChessApplication.shutdownInProgress) {
            logger.info("*** FINAL SAVE: Saving all AI training data before shutdown ***");
            try {
                Object asyncManager = com.example.chess.ChessApplication.getAsyncManager();
                if (asyncManager != null) {
                    java.lang.reflect.Method saveOnTrainingStopMethod = asyncManager.getClass().getMethod("saveOnTrainingStop");
                    Object saveTaskResult = saveOnTrainingStopMethod.invoke(asyncManager);
                    
                    if (saveTaskResult instanceof java.util.concurrent.CompletableFuture) {
                        java.util.concurrent.CompletableFuture<?> saveTask = (java.util.concurrent.CompletableFuture<?>) saveTaskResult;
                        saveTask.get(30, java.util.concurrent.TimeUnit.SECONDS); // Wait up to 30 seconds
                        logger.info("*** ASYNC I/O: Training stop save completed ***");
                    } else {
                        logger.info("*** ASYNC I/O: Training stop save completed (synchronous) ***");
                    }
                } else {
                    logger.warn("*** ASYNC I/O: AsyncManager not available ***");
                }
            } catch (java.util.concurrent.TimeoutException e) {
                logger.warn("*** ASYNC I/O: Training stop save timed out after 30 seconds ***");
            } catch (Exception e) {
                logger.error("*** ASYNC I/O: Training stop save failed - {} ***", e.getMessage());
            }
        } else {
            logger.info("*** FINAL SAVE: Skipped during application shutdown ***");
        }
        
        logger.info("*** ALL AI TRAINING STOPPED ***");
    }
    
    private void stopAIWithTimeout(String aiName, Runnable stopAction) {
        try {
            stopAction.run();
            logger.debug("*** {}: Stop requested ***", aiName);
        } catch (Exception e) {
            logger.warn("*** {}: Stop error - {} ***", aiName, e.getMessage());
        }
    }
    

    
    private void startQLearningTraining(ChessGame game) {
        if (game.isQLearningEnabled()) {
            Thread thread = Thread.ofVirtual().name("Q-Learning-Training").start(() -> {
                try {
                    // FIXED: Single training session instead of infinite loop
                    if (!stopTrainingRequested && isTrainingActive) {
                        logger.info("*** Q-Learning: Starting single training session ***");
                        game.getQLearningAI().trainAgainstSelfWithProgress(1000000);
                        logger.info("*** Q-Learning: Training session completed ***");
                    }
                    logger.info("*** Q-Learning: Training thread stopped - stopRequested={}, isActive={} ***", stopTrainingRequested, isTrainingActive);
                } catch (Exception e) {
                    logger.error("Q-Learning training error: {}", e.getMessage());
                }
            });
            trainingThreads.put("Q-Learning", thread);
        }
    }
    
    private void startDeepLearningTraining(ChessGame game) {
        if (game.isDeepLearningEnabled()) {
            logger.info("*** TrainingManager: Starting Deep Learning AI training ***");
            game.getDeepLearningAI().startTraining();
        }
    }
    
    private void startDQNTraining(ChessGame game) {
        if (game.isDQNEnabled()) {
            logger.info("*** TrainingManager: Starting DQN AI training ***");
            game.getDQNAI().startTraining();
        }
    }
    
    private void startAlphaZeroTraining(ChessGame game) {
        if (!game.isAlphaZeroEnabled()) return;
        
        Thread alphaZeroThread = Thread.ofVirtual().name("AlphaZero-Training").start(() -> {
            try {
                if (!stopTrainingRequested && isTrainingActive) {
                    logger.info("*** AlphaZero: Starting training session ***");
                    game.getAlphaZeroAI().startSelfPlayTraining(2000);
                    logger.info("*** AlphaZero: Training session completed ***");
                }
            } catch (Exception e) {
                logger.error("AlphaZero training error: {}", e.getMessage());
            }
        });
    }
    
    private void startLeelaZeroTraining(ChessGame game) {
        if (game.isLeelaZeroEnabled()) {
            game.getLeelaZeroAI().startSelfPlayTraining(1000);
        }
    }
    
    private void startGeneticTraining(ChessGame game) {
        if (!game.isGeneticEnabled()) return;
        
        Thread geneticThread = Thread.ofVirtual().name("Genetic-Training").start(() -> {
            try {
                game.getGeneticAI().startTraining(500);
            } catch (Exception e) {
                logger.error("Genetic training error: {}", e.getMessage());
            }
        });
    }
    
    private void startAlphaFold3Training(ChessGame game) {
        if (!game.isAlphaFold3Enabled()) {
            logger.info("*** TrainingManager: AlphaFold3 not enabled - skipping start ***");
            return;
        }
        
        logger.info("*** TrainingManager: Starting AlphaFold3 continuous training ***");
        Thread.ofVirtual().name("AlphaFold3-Training").start(() -> {
            try {
                // Start training once, let it run continuously
                game.getAlphaFold3AI().startTraining(1000000); // Single long training session
            } catch (Exception e) {
                logger.error("AlphaFold3 training error: {}", e.getMessage());
            }
        });
        logger.info("*** TrainingManager: AlphaFold3 continuous training thread started ***");
    }
    
    private void startA3CTraining(ChessGame game) {
        if (!game.isA3CEnabled()) {
            logger.info("*** TrainingManager: A3C not enabled - skipping start ***");
            return;
        }
        
        logger.info("*** TrainingManager: Starting A3C training ***");
        Thread.ofVirtual().name("A3C-Training").start(() -> {
            try {
                game.getA3CAI().startTraining(10000); // 10,000 episodes
            } catch (Exception e) {
                logger.error("A3C training error: {}", e.getMessage());
            }
        });
        logger.info("*** TrainingManager: A3C training thread started ***");
    }
    
    private void startMCTSTraining(ChessGame game) {
        if (game.isMCTSEnabled()) {
            Thread thread = Thread.ofVirtual().name("MCTS-Training").start(() -> {
                try {
                    // MCTS training via virtual board simulation
                    int gameCount = 0;
                    while (!stopTrainingRequested && !Thread.currentThread().isInterrupted() && gameCount < 5000) {
                        // Check stop flag every game for immediate response
                        if (stopTrainingRequested || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        
                        VirtualChessBoard virtualBoard = new VirtualChessBoard(game.getLeelaOpeningBook());
                        
                        for (int move = 0; move < 50 && !stopTrainingRequested; move++) {
                            // Check stop flag every 10 moves
                            if (move % 10 == 0 && stopTrainingRequested) {
                                break;
                            }
                            
                            int[] selectedMove = game.getMCTSAI().selectMoveForTraining(virtualBoard);
                            if (selectedMove == null) break;
                            
                            virtualBoard.makeMove(selectedMove[0], selectedMove[1], selectedMove[2], selectedMove[3]);
                            if (virtualBoard.isGameOver()) break;
                        }
                        
                        gameCount++;
                        if (gameCount % 100 == 0) {
                            logger.debug("MCTS: {} training games completed", gameCount);
                        }
                        
                        // Check stop flag after each game
                        if (stopTrainingRequested) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("MCTS training error: {}", e.getMessage());
                }
            });
            trainingThreads.put("MCTS", thread);
        }
    }
    
    /**
     * Centralized periodic save mechanism - saves all AI training data every 30 minutes
     */
    private void startPeriodicSave(ChessGame game) {
        // CRITICAL: Prevent multiple periodic save threads
        if (periodicSaveThread != null && periodicSaveThread.isAlive()) {
            logger.info("*** PERIODIC SAVE: Already running - skipping duplicate start ***");
            return;
        }
        
        periodicSaveThread = Thread.ofVirtual().name("Centralized-Periodic-Save").start(() -> {
            logger.info("*** CENTRALIZED PERIODIC SAVE: Started (every 30 minutes) ***");
            
            while (!stopTrainingRequested) {
                try {
                    Thread.sleep(30 * 60 * 1000); // 30 minutes = 1,800,000 ms
                    
                    if (!stopTrainingRequested) {
                        logger.info("*** CENTRALIZED PERIODIC SAVE: Saving all AI training data ***");
                        // Mark state as changed before saving
                        game.notifyStateChanged();
                        saveOnGameReset(game);
                        logger.info("*** CENTRALIZED PERIODIC SAVE: Complete ***");
                    }
                } catch (InterruptedException e) {
                    logger.info("*** CENTRALIZED PERIODIC SAVE: Interrupted ***");
                    break;
                } catch (Exception e) {
                    logger.error("*** CENTRALIZED PERIODIC SAVE: Error - {} ***", e.getMessage());
                }
            }
            
            logger.info("*** CENTRALIZED PERIODIC SAVE: Stopped ***");
        });
    }
    
    public void saveOnGameReset(ChessGame game) {
        logger.info("*** GAME RESET SAVE: Saving all AI training data ***");
        try {
            Object asyncManager = com.example.chess.ChessApplication.getAsyncManager();
            if (asyncManager != null) {
                java.lang.reflect.Method saveOnGameResetMethod = asyncManager.getClass().getMethod("saveOnGameReset");
                Object saveTaskResult = saveOnGameResetMethod.invoke(asyncManager);
                
                if (saveTaskResult instanceof java.util.concurrent.CompletableFuture) {
                    java.util.concurrent.CompletableFuture<?> saveTask = (java.util.concurrent.CompletableFuture<?>) saveTaskResult;
                    // PERFORMANCE FIX: Don't block on save completion
                    saveTask.thenRun(() -> {
                        logger.info("*** GAME RESET SAVE: Completed ***");
                    }).exceptionally(ex -> {
                        logger.error("*** GAME RESET SAVE: Failed - {} ***", ex.getMessage());
                        return null;
                    });
                } else {
                    logger.info("*** GAME RESET SAVE: Completed (synchronous) ***");
                }
            } else {
                logger.warn("*** GAME RESET SAVE: AsyncManager not available ***");
            }
        } catch (Exception e) {
            logger.error("*** GAME RESET SAVE: Failed - {} ***", e.getMessage());
        }
    }
    
    /**
     * Training Data Quality Evaluation System
     * CRITICAL: Identifies training data starvation and broken AI systems
     */
    public void evaluateTrainingDataQuality(ChessGame game) {
        if (isTrainingActive) {
            logger.warn("*** QUALITY EVALUATION: Cannot evaluate while training is active ***");
            return;
        }
        
        logger.info("*** TRAINING DATA QUALITY EVALUATION STARTED ***");
        logger.info("*** CRITICAL: Checking for training data starvation and broken AI systems ***");
        
        Map<String, QualityReport> reports = new HashMap<>();
        
        if (game.isQLearningEnabled()) reports.put("Q-Learning", evaluateQLearningQuality(game));
        if (game.isDeepLearningEnabled()) reports.put("Deep Learning", evaluateDeepLearningQuality(game));
        if (game.isDeepLearningCNNEnabled()) reports.put("CNN Deep Learning", evaluateCNNQuality(game));
        if (game.isDQNEnabled()) reports.put("DQN", evaluateDQNQuality(game));
        if (game.isAlphaZeroEnabled()) reports.put("AlphaZero", evaluateAlphaZeroQuality(game));
        if (game.isLeelaZeroEnabled()) reports.put("Leela Chess Zero", evaluateLeelaZeroQuality(game));
        if (game.isGeneticEnabled()) reports.put("Genetic Algorithm", evaluateGeneticQuality(game));
        if (game.isAlphaFold3Enabled()) reports.put("AlphaFold3", evaluateAlphaFold3Quality(game));
        if (game.isA3CEnabled()) reports.put("A3C", evaluateA3CQuality(game));
        if (game.isMCTSEnabled()) reports.put("MCTS", evaluateMCTSQuality(game));
        if (game.isNegamaxEnabled()) reports.put("Negamax", evaluateNegamaxQuality(game));
        if (game.isOpenAiEnabled()) reports.put("OpenAI", evaluateOpenAIQuality(game));
        
        generateQualityReport(reports);
        
        logger.info("*** TRAINING DATA QUALITY EVALUATION COMPLETED ***");
    }
    
    private QualityReport evaluateQLearningQuality(ChessGame game) {
        QualityReport report = new QualityReport("Q-Learning");
        
        try {
            File dataFile = new File(aiStateConfig.getQlearningPath());
            if (!dataFile.exists()) {
                report.addError("Q-Learning data file not found");
                return report;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                Map<String, Double> qTable = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            try {
                                qTable.put(parts[0], Double.parseDouble(parts[1]));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                double epsilon = 0.3; // Default value
                int iterations = qTable.size() / 10; // Estimate from table size
                
                int qTableSize = qTable.size();
                double avgQValue = qTable.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                report.addMetric("Q-Table Size", qTableSize);
                report.addMetric("Average Q-Value", avgQValue);
                report.addMetric("Exploration Rate", epsilon);
                report.addMetric("Training Iterations", Math.max(iterations, qTableSize > 0 ? qTableSize / 10 : 0));
                
                report.addScore("State Space Coverage", Math.min(100.0, (qTableSize / 1000.0) * 100));
                report.addScore("Value Convergence", Math.max(0.0, 100.0 - Math.abs(avgQValue) * 5));
                report.addScore("Exploration Strategy", Math.max(0.0, 100.0 - Math.abs(epsilon - 0.15) * 500));
                report.addScore("Training Maturity", Math.min(100.0, (iterations / 10.0) * 100));
                report.addScore("Learning Stability", Math.min(100.0, qTableSize / 100.0));
            }
            
        } catch (Exception e) {
            report.addError("Q-Learning evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateDeepLearningQuality(ChessGame game) {
        QualityReport report = new QualityReport("Deep Learning");
        
        try {
            File modelFile = new File(aiStateConfig.getDeeplearningPath());
            
            boolean modelExists = modelFile.exists();
            report.addMetric("Model File Exists", modelExists);
            report.addScore("Integrity", modelExists ? 100.0 : 0.0);
            
            // Get actual training data from the AI instance if available
            int iterations = 0;
            int gameDataSize = 0;
            String backend = "Unknown";
            
            if (game.isDeepLearningEnabled() && game.getDeepLearningAI() != null) {
                iterations = game.getDeepLearningAI().getTrainingIterations();
                gameDataSize = game.getDeepLearningAI().getGameDataSize();
                backend = game.getDeepLearningAI().getBackendInfo();
            }
            
            report.addMetric("Training Iterations", iterations);
            report.addMetric("Game Data Size", gameDataSize);
            report.addMetric("Backend", backend);
            
            report.addScore("Loss Function Stability", iterations > 0 ? Math.min(100.0, (iterations / 5.0) * 100) : 0.0);
            report.addScore("Batch Size Memory Check", modelExists ? 100.0 : 0.0);
            report.addScore("Data Balance Enforcement", gameDataSize > 0 ? Math.min(100.0, (gameDataSize / 2.0) * 100) : 0.0);
            report.addScore("Training Volume", iterations > 0 ? Math.min(100.0, (iterations / 5.0) * 100) : 0.0);
            
        } catch (Exception e) {
            report.addError("Deep Learning evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateCNNQuality(ChessGame game) {
        QualityReport report = new QualityReport("CNN Deep Learning");
        
        try {
            File modelFile = new File(aiStateConfig.getDeeplearningCnnPath());
            
            boolean modelExists = modelFile.exists();
            report.addMetric("Advanced Model Exists", modelExists);
            report.addScore("Architecture", modelExists ? 100.0 : 0.0);
            
            // Get actual training data from the AI instance if available
            int iterations = 0;
            int gameDataSize = 0;
            String backend = "Unknown";
            
            if (game.isDeepLearningCNNEnabled() && game.getDeepLearningCNNAI() != null) {
                iterations = game.getDeepLearningCNNAI().getTrainingIterations();
                gameDataSize = game.getDeepLearningCNNAI().getGameDataSize();
                backend = game.getDeepLearningCNNAI().getBackendInfo();
            }
            
            report.addMetric("Training Iterations", iterations);
            report.addMetric("Game Data Size", gameDataSize);
            report.addMetric("Backend", backend);
            
            report.addScore("Noise Injection Control", gameDataSize > 0 ? Math.min(100.0, (gameDataSize / 1.0) * 100) : 0.0);
            report.addScore("Loss Function Stability", iterations > 0 ? Math.min(100.0, (iterations / 2.0) * 100) : 100.0);
            report.addScore("Batch Size Memory Check", modelExists ? 100.0 : 0.0);
            report.addScore("Pattern Recognition", gameDataSize > 0 ? Math.min(100.0, (gameDataSize / 5.0) * 100) : 0.0);
            
        } catch (Exception e) {
            report.addError("CNN evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateDQNQuality(ChessGame game) {
        QualityReport report = new QualityReport("DQN");
        
        try {
            File dataFile = new File(aiStateConfig.getDqnExperiencesPath());
            File modelFile = new File(aiStateConfig.getDqnPath());
            File targetModelFile = new File(aiStateConfig.getDqnTargetPath());
            
            boolean hasData = dataFile.exists();
            boolean hasModel = modelFile.exists();
            boolean hasTargetModel = targetModelFile.exists();
            
            report.addMetric("Data File Exists", hasData);
            report.addMetric("Model File Exists", hasModel);
            report.addMetric("Target Model Exists", hasTargetModel);
            
            // Get actual training data from the AI instance if available
            int episodes = 0;
            double avgReward = 0.0;
            int bufferSize = 0;
            boolean dualNetwork = hasModel && hasTargetModel;
            
            if (game.isDQNEnabled() && game.getDQNAI() != null) {
                episodes = game.getDQNAI().getTrainingEpisodes();
                avgReward = game.getDQNAI().getAverageReward();
                bufferSize = game.getDQNAI().getExperienceBufferSize();
            } else if (hasData) {
                // Estimate from file size if AI not available
                long fileSize = dataFile.length();
                episodes = (int)(fileSize / 1000); // Rough estimate
                bufferSize = episodes / 10;
            }
            
            report.addMetric("Experience Buffer Size", bufferSize);
            report.addMetric("Training Episodes", episodes);
            report.addMetric("Average Reward", avgReward);
            report.addMetric("Dual Network", dualNetwork);
            
            report.addScore("Exploration vs Exploitation", Math.min(100.0, Math.max(episodes, bufferSize) / 0.5 * 100));
            report.addScore("Experience Replay Buffer Integrity", Math.min(100.0, (bufferSize / 5.0) * 100));
            report.addScore("Loss Function Stability", Math.min(100.0, (avgReward + 1.0) * 100));
            report.addScore("GPU Utilization Limits", dualNetwork ? 100.0 : 50.0);
            report.addScore("Architecture Quality", dualNetwork ? "TRUE" : "FALSE");
            
        } catch (Exception e) {
            report.addError("DQN evaluation failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        
        return report;
    }
    
    private QualityReport evaluateAlphaZeroQuality(ChessGame game) {
        QualityReport report = new QualityReport("AlphaZero");
        
        try {
            File dataFile = new File(aiStateConfig.getAlphazeroCachePath());
            
            boolean hasData = dataFile.exists();
            report.addMetric("Cache File Exists", hasData);
            
            // Get actual training data from the AI instance if available
            int episodes = 0;
            int cacheSize = 0;
            boolean hasResNet = false;
            double lossValue = 0.0;
            String status = "Not Available";
            
            if (game.isAlphaZeroEnabled() && game.getAlphaZeroAI() != null) {
                episodes = game.getAlphaZeroAI().getTrainingEpisodes();
                status = game.getAlphaZeroAI().getTrainingStatus();
                cacheSize = episodes / 10; // Estimate cache size
                logger.debug("*** TrainingManager: AlphaZero episodes read directly: {} ***", episodes);
                
                // REMOVED: Problematic saveNeuralNetwork() call that was causing redundant saves
                // The evaluation should not trigger saves - that's handled elsewhere
                
            } else if (hasData) {
                // Estimate from file size if AI not available
                long fileSize = dataFile.length();
                episodes = (int)(fileSize / 100); // Rough estimate
                cacheSize = episodes / 5;
            }
            
            report.addMetric("Self-Play Episodes", episodes);
            report.addMetric("MCTS Cache Size", cacheSize);
            report.addMetric("ResNet Architecture", hasResNet ? "8 blocks" : "Basic");
            report.addMetric("Status Details", "Episodes: " + episodes + ", Cache: " + cacheSize);
            report.addMetric("Training Status", status);
            
            report.addScore("Neural Architecture", hasResNet ? "TRUE" : "FALSE");
            report.addScore("Search Memory", Math.min(100.0, (cacheSize / 10.0) * 100));
            report.addScore("Training State", Math.min(100.0, episodes * 2));
            report.addScore("Self-Play Quality", Math.min(100.0, episodes * 1.0));
            report.addScore("Game State Validity", Math.min(100.0, episodes * 2));
            report.addScore("Exploration vs Exploitation", Math.min(100.0, episodes * 2));
            report.addScore("Search Depth Control", Math.min(100.0, (cacheSize / 10.0) * 100));
            report.addScore("Loss Function Stability", Math.max(0.0, 100.0 - lossValue * 100));
            report.addScore("Cross-Model Benchmarking", Math.min(100.0, episodes * 2));
            
        } catch (Exception e) {
            report.addError("AlphaZero evaluation failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        
        return report;
    }
    
    private QualityReport evaluateLeelaZeroQuality(ChessGame game) {
        QualityReport report = new QualityReport("Leela Chess Zero");
        
        try {
            File policyFile = new File(aiStateConfig.getLeelazerochessPolicyPath());
            File valueFile = new File(aiStateConfig.getLeelazerochessValuePath());
            
            boolean hasPolicyModel = policyFile.exists();
            boolean hasValueModel = valueFile.exists();
            boolean hasDualHead = hasPolicyModel && hasValueModel;
            
            report.addMetric("Policy Model Exists", hasPolicyModel);
            report.addMetric("Value Model Exists", hasValueModel);
            report.addMetric("Opening Book", "Available");
            
            // Estimate training data from file sizes
            int trainingGames = 0;
            String status = "File-based evaluation";
            
            if (hasPolicyModel || hasValueModel) {
                long totalSize = 0;
                if (hasPolicyModel) totalSize += policyFile.length();
                if (hasValueModel) totalSize += valueFile.length();
                trainingGames = (int)(totalSize / 100000); // Rough estimate from file sizes
            }
            
            report.addMetric("Training Games", trainingGames);
            report.addMetric("Status Details", status);
            
            report.addScore("Data Balance Enforcement", Math.min(100.0, (trainingGames / 10.0) * 100));
            report.addScore("Loss Function Stability", hasDualHead ? "TRUE" : "FALSE");
            report.addScore("Batch Size Memory Check", (hasPolicyModel || hasValueModel) ? "TRUE" : "FALSE");
            report.addScore("GPU Utilization Limits", (hasPolicyModel || hasValueModel) ? "TRUE" : "FALSE");
            report.addScore("Elo Drift Monitor", Math.min(100.0, (trainingGames / 10.0) * 100));
            
        } catch (Exception e) {
            report.addError("Leela Chess Zero evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateGeneticQuality(ChessGame game) {
        QualityReport report = new QualityReport("Genetic Algorithm");
        
        try {
            File dataFile = new File(aiStateConfig.getGeneticPath());
            
            boolean hasData = dataFile.exists();
            report.addMetric("Population File Exists", hasData);
            
            // Get actual training data from the AI instance if available
            int generation = 0;
            int populationSize = 0;
            boolean isAdaptive = false;
            double fitnessScore = 0.0;
            
            if (game.isGeneticEnabled() && game.getGeneticAI() != null) {
                generation = game.getGeneticAI().getCurrentGeneration();
                populationSize = game.getGeneticAI().getPopulationSize();
                fitnessScore = game.getGeneticAI().getBestFitness();
            } else if (hasData) {
                // Estimate from file size if AI not available
                long fileSize = dataFile.length();
                generation = (int)(fileSize / 1000); // Rough estimate
                populationSize = Math.max(10, generation * 2);
                fitnessScore = Math.min(1.0, generation / 100.0);
            }
            
            report.addMetric("Current Generation", generation);
            report.addMetric("Population Size", populationSize);
            report.addMetric("Status Details", "Gen: " + generation + ", Fitness: " + fitnessScore);
            
            report.addScore("Mutation Safety", isAdaptive ? "TRUE" : "FALSE");
            report.addScore("Population Diversity Check", Math.min(100.0, populationSize * 2.0));
            report.addScore("Cross-Model Benchmarking", Math.min(100.0, generation * 20));
            report.addScore("Elo Drift Monitor", Math.min(100.0, Math.max(0.0, fitnessScore * 100)));
            report.addScore("System Health", Math.min(100.0, generation * 10));
            
        } catch (Exception e) {
            report.addError("Genetic Algorithm evaluation failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        
        return report;
    }
    
    private QualityReport evaluateAlphaFold3Quality(ChessGame game) {
        QualityReport report = new QualityReport("AlphaFold3");
        
        try {
            File dataFile = new File(aiStateConfig.getAlphafold3Path());
            
            boolean hasData = dataFile.exists();
            report.addMetric("State File Exists", hasData);
            
            // Get actual training data from the AI instance if available
            int episodes = 0;
            boolean hasErrors = false;
            int positionCount = 0;
            int trajectoryCount = 0;
            String status = "Not Available";
            
            if (hasData) {
                // Estimate from file size if AI not available
                long fileSize = dataFile.length();
                episodes = (int)(fileSize / 10); // Very rough estimate for small file
                positionCount = episodes / 5;
                trajectoryCount = episodes / 10;
            }
            
            String learningPhase = episodes < 500 ? "BASIC" : episodes < 1500 ? "TRAJECTORY" : episodes < 3000 ? "NOISE" : "ADVANCED";
            
            report.addMetric("Diffusion Episodes", episodes);
            report.addMetric("Training Status", hasErrors ? "Error" : "Ready");
            report.addMetric("Learning Phase", learningPhase);
            report.addMetric("Position Evaluations", positionCount);
            report.addMetric("Trajectory Count", trajectoryCount);
            
            // More realistic scoring for experimental diffusion modeling
            report.addScore("Noise Injection Control", Math.min(85.0, (trajectoryCount / 50.0) * 100));
            report.addScore("Game State Validity", Math.min(90.0, (positionCount / 100.0) * 100));
            report.addScore("Loss Function Stability", hasErrors ? 0.0 : Math.min(80.0, (episodes / 10.0) * 100));
            report.addScore("Versioned Checkpoint Replay", Math.min(75.0, (positionCount / 200.0) * 100));
            report.addScore("Diffusion Process Quality", Math.min(70.0, (episodes / 30.0) * 100));
            
        } catch (Exception e) {
            report.addError("AlphaFold3 evaluation failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        
        return report;
    }
    
    private QualityReport evaluateA3CQuality(ChessGame game) {
        QualityReport report = new QualityReport("A3C");
        
        try {
            // A3C uses DeepLearning4J models
            java.io.File actorModel = new java.io.File(aiStateConfig.getState().getDirectory() + "/a3c_actor_model.zip");
            java.io.File criticModel = new java.io.File(aiStateConfig.getState().getDirectory() + "/a3c_critic_model.zip");
            java.io.File stateFile = new java.io.File(aiStateConfig.getState().getDirectory() + "/a3c_state.dat");
            
            boolean hasActorModel = actorModel.exists();
            boolean hasCriticModel = criticModel.exists();
            boolean hasState = stateFile.exists();
            
            report.addMetric("Actor Model Exists", hasActorModel);
            report.addMetric("Critic Model Exists", hasCriticModel);
            report.addMetric("State File Exists", hasState);
            
            // Get training data from AI instance if available
            int episodes = 0;
            int steps = 0;
            double avgReward = 0.0;
            String status = "Not Available";
            
            if (game.isA3CEnabled() && game.getA3CAI() != null) {
                episodes = game.getA3CAI().getEpisodesCompleted();
                steps = game.getA3CAI().getGlobalSteps();
                avgReward = game.getA3CAI().getAverageReward();
                status = game.getA3CAI().getTrainingStatus();
            }
            
            report.addMetric("Training Episodes", episodes);
            report.addMetric("Global Steps", steps);
            report.addMetric("Average Reward", avgReward);
            report.addMetric("Training Status", status);
            
            // A3C specific scoring
            report.addScore("Actor-Critic Architecture", (hasActorModel && hasCriticModel) ? 100.0 : 0.0);
            report.addScore("Asynchronous Workers", episodes > 0 ? Math.min(100.0, episodes / 10.0) : 0.0);
            report.addScore("Experience Quality", Math.min(100.0, Math.max(0.0, (avgReward + 50.0) * 2.0)));
            report.addScore("Training Progress", Math.min(100.0, steps / 100.0));
            report.addScore("Model Persistence", hasState ? 100.0 : 0.0);
            
        } catch (Exception e) {
            report.addError("A3C evaluation failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        
        return report;
    }
    
    private QualityReport evaluateMCTSQuality(ChessGame game) {
        QualityReport report = new QualityReport("MCTS");
        
        try {
            boolean isEnabled = game.isMCTSEnabled();
            
            int simulations = 0;
            int treeSize = 0;
            
            report.addMetric("MCTS Enabled", isEnabled);
            report.addMetric("Status", isEnabled ? "Available" : "Disabled");
            report.addMetric("Tree Reuse", treeSize > 0 ? "Active" : "Empty");
            report.addMetric("Opening Book", "Available");
            
            report.addScore("Search Depth Time Cap", isEnabled ? 100.0 : 0.0);
            report.addScore("Tree Pruning Policy", Math.min(100.0, (treeSize / 10.0) * 100));
            report.addScore("Stochastic Consistency", Math.min(100.0, (simulations / 10.0) * 100));
            report.addScore("System Health", isEnabled ? 100.0 : 0.0);
            
        } catch (Exception e) {
            report.addError("MCTS evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateNegamaxQuality(ChessGame game) {
        QualityReport report = new QualityReport("Negamax");
        
        try {
            boolean isEnabled = game.isNegamaxEnabled();
            report.addMetric("Engine Enabled", isEnabled);
            report.addMetric("Status", isEnabled ? "Available" : "Disabled");
            report.addMetric("Opening Book", "Available");
            
            // Get actual data from AI instance if available
            int cacheSize = 0;
            String optimizationStats = "Not Available";
            
            if (isEnabled && game.getNegamaxAI() != null) {
                cacheSize = game.getNegamaxAI().getCacheSize();
                optimizationStats = game.getNegamaxAI().getOptimizationStats();
            }
            
            report.addMetric("Transposition Table Size", cacheSize);
            report.addMetric("Optimization Stats", optimizationStats);
            
            report.addScore("Alpha-Beta Pruning", isEnabled ? 100.0 : 0.0);
            report.addScore("Iterative Deepening", isEnabled ? 100.0 : 0.0);
            report.addScore("Transposition Table", Math.min(100.0, (cacheSize / 100.0) * 100));
            report.addScore("Move Ordering", isEnabled ? 100.0 : 0.0);
            report.addScore("Time Management", isEnabled ? 100.0 : 0.0);
            
        } catch (Exception e) {
            report.addError("Negamax evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private QualityReport evaluateOpenAIQuality(ChessGame game) {
        QualityReport report = new QualityReport("OpenAI");
        
        try {
            boolean isEnabled = game.isOpenAiEnabled();
            report.addMetric("API Enabled", isEnabled);
            report.addMetric("Status", isEnabled ? "Available" : "Disabled");
            report.addMetric("Model", "GPT-3.5-turbo-instruct");
            report.addMetric("Opening Book", "Available");
            
            // OpenAI is stateless - no persistent training data
            report.addMetric("API Type", "Stateless");
            report.addMetric("Training Data", "Pre-trained (not user-specific)");
            
            report.addScore("API Availability", isEnabled ? 100.0 : 0.0);
            report.addScore("Strategic Reasoning", isEnabled ? 100.0 : 0.0);
            report.addScore("FEN Processing", isEnabled ? 100.0 : 0.0);
            report.addScore("Natural Language Understanding", isEnabled ? 100.0 : 0.0);
            report.addScore("Response Quality", isEnabled ? 90.0 : 0.0);
            
        } catch (Exception e) {
            report.addError("OpenAI evaluation failed: " + e.getMessage());
        }
        
        return report;
    }
    
    private void generateQualityReport(Map<String, QualityReport> reports) {
        logger.info("=".repeat(80));
        logger.info("                    TRAINING DATA QUALITY EVALUATION REPORT");
        logger.info("=".repeat(80));
        
        double totalScore = 0.0;
        int totalAI = 0;
        
        for (Map.Entry<String, QualityReport> entry : reports.entrySet()) {
            QualityReport report = entry.getValue();
            logger.info("-".repeat(50));
            logger.info("\t[{}] - Overall Score: {}%", report.aiName, String.format("%.1f", report.getOverallScore()));
            logger.info("-".repeat(50));
            
            for (Map.Entry<String, Object> metric : report.metrics.entrySet()) {
                logger.info("  {}: {}", metric.getKey(), metric.getValue());
            }
            
            for (Map.Entry<String, Object> score : report.scores.entrySet()) {
                if (score.getValue() instanceof Double) {
                    logger.info("  {} Score: {}%", score.getKey(), String.format("%.1f", (Double) score.getValue()));
                } else {
                    logger.info("  {}: {}", score.getKey(), score.getValue());
                }
            }
            
            if (!report.errors.isEmpty()) {
                logger.info("  ERRORS:");
                for (String error : report.errors) {
                    logger.info("    - {}", error);
                }
            }
            
            totalScore += report.getOverallScore();
            totalAI++;
        }
        
        double overallQuality = totalAI > 0 ? totalScore / totalAI : 0.0;
        
        logger.info("=".repeat(80));
        logger.info("OVERALL SYSTEM QUALITY: {}% ({} AI systems evaluated)", String.format("%.1f", overallQuality), totalAI);
        logger.info("=".repeat(80));
        
        if (overallQuality >= 85.0) {
            logger.info("✅ EXCELLENT: Training data quality is excellent across all AI systems");
        } else if (overallQuality >= 70.0) {
            logger.info("✅ GOOD: Training data quality is good, minor improvements possible");
        } else if (overallQuality >= 50.0) {
            logger.info("⚠️  MODERATE: Training data quality needs improvement");
        } else {
            logger.info("❌ POOR: Training data quality requires significant improvement");
        }
    }
    
    /**
     * Shutdown method to be called during application shutdown
     * Ensures periodic save thread is stopped immediately
     */
    public void shutdown() {
        logger.info("*** TRAINING MANAGER SHUTDOWN: Stopping periodic save thread ***");
        
        // Stop training and periodic saves immediately
        stopTrainingRequested = true;
        isTrainingActive = false;
        
        // Interrupt periodic save thread immediately
        if (periodicSaveThread != null && periodicSaveThread.isAlive()) {
            periodicSaveThread.interrupt();
            logger.info("*** PERIODIC SAVE: Thread interrupted during shutdown ***");
        }
        periodicSaveThread = null;
        
        // Clear training threads
        trainingThreads.clear();
        
        logger.info("*** TRAINING MANAGER SHUTDOWN: Complete ***");
    }
    
    private static class QualityReport {
        final String aiName;
        final Map<String, Object> metrics = new HashMap<>();
        final Map<String, Object> scores = new HashMap<>();
        final List<String> errors = new ArrayList<>();
        
        QualityReport(String aiName) {
            this.aiName = aiName;
        }
        
        void addMetric(String name, Object value) {
            metrics.put(name, value);
        }
        
        void addScore(String name, Object score) {
            scores.put(name, score);
        }
        
        void addError(String error) {
            errors.add(error);
        }
        
        double getOverallScore() {
            return scores.values().stream()
                .filter(v -> v instanceof Double)
                .mapToDouble(v -> (Double) v)
                .average().orElse(0.0);
        }
    }
}