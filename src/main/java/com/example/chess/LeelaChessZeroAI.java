package com.example.chess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.chess.async.TrainingDataIOWrapper;

/**
 * Leela Chess Zero AI with human game knowledge and transformer architecture.
 * Features enhanced MCTS with chess-specific optimizations.
 */
public class LeelaChessZeroAI {
    
    // Records for better data structures
    public record MoveEvaluation(int[] move, double value, double policy, double confidence) {}
    public record TrainingGame(SequencedCollection<String> moves, double result, int length) {}
    public record NetworkState(double confidence, String status, int trainingGames) {}
    private static final Logger logger = LogManager.getLogger(LeelaChessZeroAI.class);
    private LeelaChessZeroNetwork neuralNetwork;
    private LeelaChessZeroMCTS mcts;
    private ExecutorService executorService;
    private volatile LeelaChessZeroTrainer currentTrainer = null;
    
    // Threading support
    private volatile int[] selectedMove = null;
    private volatile boolean isThinking = false;
    private volatile Thread trainingThread = null;
    private volatile boolean trainingStopRequested = false;
    private volatile boolean trainingInProgress = false;
    
    // AI weighting for combined evaluation
    private double aiWeight = 1.0;
    private boolean convergenceEnabled = true;
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    public LeelaChessZeroAI(boolean debugEnabled) {
        this.neuralNetwork = new LeelaChessZeroNetwork(logger.isDebugEnabled());
        this.mcts = new LeelaChessZeroMCTS(neuralNetwork, logger.isDebugEnabled());
        this.ioWrapper = new TrainingDataIOWrapper();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LeelaZero-Thread");
            t.setDaemon(true);
            return t;
        });
        
        loadState();
        loadTrainingGames();
        logger.debug("*** LeelaZero: Initialized with Lc0-aligned architecture (no opening book) ***");
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        return selectMoveAsync(board, validMoves);
    }
    
    private int[] selectMoveAsync(String[][] board, List<int[]> validMoves) {
        selectedMove = null;
        isThinking = true;
        
        // Reinitialize ExecutorService if terminated
        if (executorService.isTerminated() || executorService.isShutdown()) {
            logger.debug("*** LeelaZero: Reinitializing terminated ExecutorService ***");
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LeelaZero-Thread");
                t.setDaemon(true);
                return t;
            });
        }
        
        Future<int[]> future = executorService.submit(() -> {
            try {
                return selectMoveSync(board, validMoves);
            } finally {
                isThinking = false;
            }
        });
        
        try {
            selectedMove = future.get(12, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.info("*** LeelaZero: TIMEOUT - Cancelling ***");
            future.cancel(true);
            isThinking = false;
        } catch (Exception e) {
            logger.error("*** LeelaZero: ERROR - {} ***", e.getMessage());
            isThinking = false;
        }
        
        return selectedMove != null ? selectedMove : validMoves.get(0);
    }
    
    private int[] selectMoveSync(String[][] board, List<int[]> validMoves) {
        logger.debug("*** LeelaZero: Starting enhanced MCTS with tactical defense ***");
        long startTime = System.currentTimeMillis();
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Enhanced neural network approach
        int[] move = mcts.selectBestMove(board, validMoves);
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.debug("*** LeelaZero: Completed in {}ms ({:.1f}s) ***", totalTime, totalTime/1000.0);
        
        return move;
    }
    
    /**
     * Get move evaluation for combined AI assessment
     */
    public Map<int[], Double> evaluateAllMoves(String[][] board, List<int[]> validMoves) {
        if (!convergenceEnabled) return new HashMap<>();
        
        Map<int[], Double> evaluations = new HashMap<>();
        
        try {
            // Get evaluations from neural network
            for (int[] move : validMoves) {
                double evaluation = neuralNetwork.evaluateMove(board, move) * aiWeight;
                evaluations.put(move, evaluation);
            }
            
            logger.debug("*** LeelaZero: Provided {} move evaluations (weight: {}) ***", evaluations.size(), aiWeight);
            
        } catch (Exception e) {
            logger.error("*** LeelaZero: Error in move evaluation - {} ***", e.getMessage());
        }
        
        return evaluations;
    }
    
    public boolean isThinking() {
        return isThinking;
    }
    
    public void stopThinking() {
        if (executorService != null) {
            logger.info("*** LeelaZero: Stopping thread ***");
            executorService.shutdownNow();
        }
    }
    
    public void stopTraining() {
        logger.info("*** LeelaZero AI: STOP REQUEST RECEIVED - Setting training flags ***");
        trainingStopRequested = true;
        trainingInProgress = false;
        
        // Stop MCTS simulations immediately
        if (mcts != null) {
            mcts.stopSimulations();
        }
        
        // Stop trainer immediately (with null check)
        if (currentTrainer != null) {
            currentTrainer.requestStop();
            logger.info("*** LeelaZero: Stop request sent to trainer ***");
        } else {
            logger.info("*** LeelaZero: No trainer to stop (may be starting up) ***");
        }
        
        logger.info("*** LeelaZero AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    public void shutdown() {
        logger.info("*** LeelaZero: Starting shutdown ***");
        stopThinking();
        stopTraining();
        
        // Wait for training to actually stop
        if (trainingThread != null && trainingThread.isAlive()) {
            try {
                trainingThread.join(3000); // Wait up to 3 seconds
                if (trainingThread.isAlive()) {
                    logger.warn("*** LeelaZero: Training thread still alive after shutdown ***");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (neuralNetwork != null) {
            neuralNetwork.shutdown();
        }
        logger.info("*** LeelaZero: Shutdown complete ***");
    }
    
    public synchronized void startSelfPlayTraining(int games) {
        // Check if training is already active - immediate rejection
        if (trainingInProgress) {
            logger.info("*** LeelaZero: Training already in progress - skipping ***");
            return;
        }
        
        // Mark training as in progress immediately to prevent race conditions
        trainingInProgress = true;
        trainingStopRequested = false;
        
        logger.info("*** STARTING LEELAZEERO TRAINING WITH {} GAMES ***", games);
        
        if (neuralNetwork == null) {
            logger.error("*** LeelaZero: ERROR - Neural network is NULL ***");
            return;
        }
        
        // Reset MCTS simulations
        if (mcts != null) {
            mcts.resetSimulations();
        }
        
        trainingThread = Thread.ofVirtual().name("LeelaZero-Training").start(() -> {
            // Virtual threads are daemon by default
            try {
                logger.debug("*** LeelaZero: Training thread STARTED ***");
                LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(logger.isDebugEnabled());
                currentTrainer = new LeelaChessZeroTrainer(neuralNetwork, mcts, openingBook, logger.isDebugEnabled());
                
                // CRITICAL: Check stop flag immediately after trainer creation
                if (trainingStopRequested) {
                    logger.info("*** LeelaZero AI: STOP DETECTED after trainer creation - Exiting training ***");
                    return;
                }
                
                currentTrainer.runSelfPlayTraining(games);
                
                // CRITICAL FIX: Save neural network models after training with verification
                logger.info("*** LeelaZero: Training completed - saving neural network models ***");
                saveStateWithVerification();
                
                logger.debug("*** LeelaZero: TRAINING THREAD COMPLETED ***");
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    logger.info("*** LeelaZero: Training interrupted - saving progress before shutdown ***");
                    // Save training progress before shutting down
                    saveStateWithVerification();
                    Thread.currentThread().interrupt();
                    return;
                }
                logger.error("*** LeelaZero: TRAINING ERROR: {} ***", e.getMessage());
                if (logger.isDebugEnabled()) e.printStackTrace();
            } finally {
                // Always save state when training thread ends with verification
                logger.info("*** LeelaZero: Training thread ending - saving final state ***");
                saveStateWithVerification();
                trainingInProgress = false; // Clear flag when training ends
            }
        });
    }
    
    public String getTrainingStatus() {
        return neuralNetwork.getTrainingStatus();
    }
    
    public void saveState() {
        try {
            if (neuralNetwork != null) {
                // Phase 3: Dual-path implementation
                if (ioWrapper.isAsyncEnabled()) {
                    // LeelaZero has policyNetwork and valueNetwork - need to save both via NIO.2
                    ioWrapper.saveAIData("LeelaZero-Policy", neuralNetwork.getPolicyNetwork(), "leela_policy.zip");
                    ioWrapper.saveAIData("LeelaZero-Value", neuralNetwork.getValueNetwork(), "leela_value.zip");
                } else {
                    neuralNetwork.saveModel();
                }
                logger.info("*** LeelaZero: Neural network models saved ***");
            }
            logger.info("*** LeelaZero: All training data saved successfully ***");
        } catch (Exception e) {
            logger.error("*** LeelaZero: Error saving training data - {} ***", e.getMessage());
        }
    }
    
    public void saveStateWithVerification() {
        try {
            if (neuralNetwork != null) {
                // Get training stats before save
                int trainingGames = neuralNetwork.getTrainingGames();
                String status = neuralNetwork.getTrainingStatus();
                
                // Phase 3: Dual-path implementation
                if (ioWrapper.isAsyncEnabled()) {
                    // LeelaZero has policyNetwork and valueNetwork - need to save both via NIO.2
                    ioWrapper.saveAIData("LeelaZero-Policy", neuralNetwork.getPolicyNetwork(), "leela_models/lc0_policy.zip");
                    ioWrapper.saveAIData("LeelaZero-Value", neuralNetwork.getValueNetwork(), "leela_models/lc0_value.zip");
                } else {
                    // Create backup of existing models
                    java.io.File policyFile = new java.io.File("leela_policy.zip");
                    java.io.File valueFile = new java.io.File("leela_value.zip");
                    
                    if (policyFile.exists()) {
                        java.io.File policyBackup = new java.io.File("leela_policy.zip.backup");
                        policyFile.renameTo(policyBackup);
                    }
                    if (valueFile.exists()) {
                        java.io.File valueBackup = new java.io.File("leela_value.zip.backup");
                        valueFile.renameTo(valueBackup);
                    }
                    
                    // Save new models
                    neuralNetwork.saveModel();
                    
                    // Verify save was successful
                    if (policyFile.exists() && valueFile.exists()) {
                        logger.info("*** LeelaZero: Neural network models saved and verified ***");
                        logger.info("Policy model: {} bytes, Value model: {} bytes", 
                            policyFile.length(), valueFile.length());
                        
                        // Clean up backups on successful save
                        java.io.File policyBackup = new java.io.File("leela_policy.zip.backup");
                        java.io.File valueBackup = new java.io.File("leela_value.zip.backup");
                        if (policyBackup.exists()) policyBackup.delete();
                        if (valueBackup.exists()) valueBackup.delete();
                    } else {
                        logger.error("*** LeelaZero: Model save verification failed - restoring backups ***");
                        // Restore backups
                        java.io.File policyBackup = new java.io.File("leela_policy.zip.backup");
                        java.io.File valueBackup = new java.io.File("leela_value.zip.backup");
                        if (policyBackup.exists()) policyBackup.renameTo(policyFile);
                        if (valueBackup.exists()) valueBackup.renameTo(valueFile);
                    }
                }
                
                logger.info("*** LeelaZero: Training data saved - Games: {}, Status: {} ***", trainingGames, status);
            }
        } catch (Exception e) {
            logger.error("*** LeelaZero: Error saving training data with verification - {} ***", e.getMessage());
            // Attempt basic save as fallback
            saveState();
        }
    }
    
    public void loadState() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: LeelaZero loading neural networks using NIO.2 async LOAD path ***");
                Object policyData = ioWrapper.loadAIData("LeelaZero-Policy", "leela_policy.zip");
                Object valueData = ioWrapper.loadAIData("LeelaZero-Value", "leela_value.zip");
                
                if (policyData instanceof org.deeplearning4j.nn.multilayer.MultiLayerNetwork &&
                    valueData instanceof org.deeplearning4j.nn.multilayer.MultiLayerNetwork) {
                    neuralNetwork.setPolicyNetwork((org.deeplearning4j.nn.multilayer.MultiLayerNetwork) policyData);
                    neuralNetwork.setValueNetwork((org.deeplearning4j.nn.multilayer.MultiLayerNetwork) valueData);
                    logger.info("*** LeelaZero: Dual networks loaded using NIO.2 stream bridge ***");
                    return;
                }
            } catch (Exception e) {
                logger.warn("*** LeelaZero: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Neural network loading handled by initialization
        logger.debug("*** LeelaZero: Neural network loading handled by initialization ***");
    }
    
    public void resetState() {
        if (neuralNetwork != null) {
            neuralNetwork.resetModel();
        }
        logger.debug("*** LeelaZero: State reset ***");
    }
    
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        if (neuralNetwork == null) return;
        
        logger.debug("*** LeelaZero: Adding human game data ({} moves) ***", moveHistory.size());
        
        try {
            LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(logger.isDebugEnabled());
            LeelaChessZeroTrainer trainer = new LeelaChessZeroTrainer(neuralNetwork, mcts, openingBook, logger.isDebugEnabled());
            trainer.addHumanGameExperience(finalBoard, moveHistory, blackWon);
            
            // Save neural network after human game data with verification
            saveStateWithVerification();
            
            logger.debug("*** LeelaZero: Added human game data and saved model ***");
        } catch (Exception e) {
            logger.error("*** LeelaZero: Failed to add human game data - {} ***", e.getMessage());
        }
    }
    

    
    // AI weighting and convergence methods
    public void setAiWeight(double weight) {
        this.aiWeight = Math.max(0.0, Math.min(1.0, weight));
        logger.debug("*** LeelaZero: AI weight set to {} ***", this.aiWeight);
    }
    
    public double getAiWeight() {
        return aiWeight;
    }
    
    public void setConvergenceEnabled(boolean enabled) {
        this.convergenceEnabled = enabled;
        logger.debug("*** LeelaZero: Convergence {} ***", enabled ? "enabled" : "disabled");
    }
    
    public boolean isConvergenceEnabled() {
        return convergenceEnabled;
    }
    
    public boolean isTraining() {
        return trainingInProgress && !trainingStopRequested;
    }
    
    /**
     * Get confidence score for the AI's current state
     */
    public double getConfidenceScore() {
        if (neuralNetwork == null) return 0.0;
        return neuralNetwork.getConfidenceScore();
    }
    
    public int getTrainingGames() {
        if (neuralNetwork == null) return 0;
        return neuralNetwork.getTrainingGames();
    }
    
    private void loadTrainingGames() {
        try {
            java.io.File gamesFile = new java.io.File("leela_training_games.dat");
            if (gamesFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(gamesFile))) {
                    String line = reader.readLine();
                    if (line != null && neuralNetwork != null) {
                        int games = Integer.parseInt(line.trim());
                        logger.info("LeelaZero: Loaded training games: {}", games);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("LeelaZero: Failed to load training games - {}", e.getMessage());
        }
    }
}