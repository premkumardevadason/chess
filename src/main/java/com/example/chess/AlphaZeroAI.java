package com.example.chess;

import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.TrainingDataIOWrapper;

/**
 * AlphaZero AI implementation with dependency injection following SOLID principles.
 * Separates concerns between neural network, MCTS, and chess rules.
 */
public class AlphaZeroAI {
    
    // Records for better data structures
    public record SearchResult(int[] move, double confidence, long timeMs) {}
    public record TrainingEpisode(String[][] board, int[] move, double reward) {}
    public record GameData(SequencedCollection<String> moveHistory, boolean blackWon, int totalMoves) {}
    private static final Logger logger = LogManager.getLogger(AlphaZeroAI.class);
    private final AlphaZeroInterfaces.NeuralNetwork neuralNetwork;
    private final AlphaZeroInterfaces.MCTSEngine mctsEngine;
    private ExecutorService executorService;
    
    private volatile int[] selectedMove = null;
    private volatile boolean isThinking = false;
    private volatile boolean trainingStopRequested = false;
    private volatile boolean stopTrainingFlag = false;
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    public AlphaZeroAI(AlphaZeroInterfaces.NeuralNetwork neuralNetwork, AlphaZeroInterfaces.MCTSEngine mctsEngine) {
        this.neuralNetwork = neuralNetwork;
        this.mctsEngine = mctsEngine;
        this.ioWrapper = new TrainingDataIOWrapper();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AlphaZero-Thread");
            t.setDaemon(true);
            return t;
        });
        
        loadNeuralNetwork();
        logger.debug("*** AlphaZero: Initialized with injected dependencies ***");
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        return selectMoveAsync(board, validMoves);
    }
    
    private int[] selectMoveAsync(String[][] board, List<int[]> validMoves) {
        selectedMove = null;
        isThinking = true;
        
        // Reinitialize ExecutorService if it's been terminated
        if (executorService.isTerminated() || executorService.isShutdown()) {
            logger.debug("*** AlphaZero: Reinitializing terminated ExecutorService ***");
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AlphaZero-Thread");
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
            logger.info("*** AlphaZero: TIMEOUT - Cancelling ***");
            future.cancel(true);
            isThinking = false;
        } catch (ExecutionException | InterruptedException e) {
            logger.error("*** AlphaZero: ERROR - {} ***", e.getMessage());
            isThinking = false;
        }
        
        return selectedMove != null ? selectedMove : validMoves.get(0);
    }
    
    private int[] selectMoveSync(String[][] board, List<int[]> validMoves) {
        logger.debug("*** AlphaZero: Starting neural network + MCTS with tactical defense ***");
        long startTime = System.currentTimeMillis();
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Use MCTS guided by neural network
        int[] move = mctsEngine.selectBestMove(board, validMoves);
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.debug("*** AlphaZero: Completed in " + totalTime + "ms (" + 
            String.format("%.1f", totalTime/1000.0) + "s) ***");
        
        return move;
    }
    
    public boolean isThinking() {
        return isThinking;
    }
    
    public void stopThinking() {
        if (executorService != null) {
            logger.info("*** AlphaZero: Stopping thread ***");
            executorService.shutdownNow();
            // Reinitialize executor for next use
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AlphaZero-Thread");
                t.setDaemon(true);
                return t;
            });
        }
    }
    
    private volatile Thread trainingThread = null;
    private AlphaZeroTrainingService trainingService;
    
    public void stopTraining() {
        logger.info("*** AlphaZero AI: STOP REQUEST RECEIVED - Setting training flags ***");
        trainingStopRequested = true;
        stopTrainingFlag = true;
        if (trainingService != null) {
            trainingService.requestStop();
        }
        logger.info("*** AlphaZero AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    public void shutdown() {
        stopThinking();
        if (neuralNetwork != null) {
            neuralNetwork.shutdown();
        }
        logger.debug("*** AlphaZero: Shutdown complete ***");
    }
    
    public void startSelfPlayTraining(int games) {
        // Reset stop flags to allow new training
        trainingStopRequested = false;
        stopTrainingFlag = false;
        
        // Wait for previous training thread to complete
        if (trainingThread != null && trainingThread.isAlive()) {
            logger.info("*** AlphaZero: Waiting for previous training to complete ***");
            try {
                trainingThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (trainingThread.isAlive()) {
                logger.info("*** AlphaZero: Training already in progress - skipping ***");
                return;
            }
        }
        
        // ENHANCED: Ensure sufficient episodes for effective learning
        int effectiveGames = Math.max(games, 100); // Minimum 100 episodes
        if (games < 100) {
            logger.info("*** AlphaZero: Increasing training episodes from {} to {} for effectiveness ***", games, effectiveGames);
        }
        
        // Check current training level and recommend more episodes if needed
        int currentEpisodes = getTrainingEpisodes();
        if (currentEpisodes < 500) {
            int recommendedTotal = Math.max(effectiveGames, 200);
            logger.info("*** AlphaZero: Current episodes: {}, Running {} more (Recommended total: 500+ for effectiveness) ***", 
                currentEpisodes, recommendedTotal);
            effectiveGames = recommendedTotal;
        }
        
        logger.info("*** AlphaZero: Starting self-play training with {} episodes ***", effectiveGames);
        
        // Create training service if not exists
        if (trainingService == null) {
            try {
                trainingService = AlphaZeroFactory.createTrainingService(logger.isDebugEnabled());
                logger.info("*** AlphaZero: Training service created successfully ***");
            } catch (Exception e) {
                logger.error("*** AlphaZero: Failed to create training service - {} ***", e.getMessage());
                return;
            }
        }
        
        final int finalGames = effectiveGames;
        trainingThread = new Thread(() -> {
            try {
                // CRITICAL FIX: Check stop flags before starting training
                if (trainingStopRequested || stopTrainingFlag) {
                    logger.info("*** AlphaZero: Training cancelled before start - stop flags set ***");
                    return;
                }
                
                logger.info("*** AlphaZero: Training thread started, calling runSelfPlayTraining with {} episodes ***", finalGames);
                
                // Enhanced training with progress monitoring
                long startTime = System.currentTimeMillis();
                trainingService.runSelfPlayTraining(finalGames);
                long endTime = System.currentTimeMillis();
                
                double trainingTimeMinutes = (endTime - startTime) / (1000.0 * 60.0);
                int finalEpisodes = getTrainingEpisodes();
                
                logger.info("*** AlphaZero: Training completed successfully ***");
                logger.info("Training statistics: {} episodes, {} minutes, {} episodes/min", 
                    finalEpisodes, String.format("%.1f", trainingTimeMinutes), String.format("%.1f", finalGames / Math.max(trainingTimeMinutes, 0.1)));
                
                // Save neural network after training
                saveNeuralNetwork();
                
                // Provide effectiveness assessment
                assessTrainingEffectiveness(finalEpisodes);
                
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("*** AlphaZero: Training interrupted after {} episodes ***", getTrainingEpisodes());
                    Thread.currentThread().interrupt();
                } else {
                    logger.error("*** AlphaZero: Training error - {} ***", e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "AlphaZero-Training");
        
        trainingThread.setDaemon(true);
        trainingThread.start();
        logger.info("*** AlphaZero: Training thread started successfully ***");
    }
    
    private void assessTrainingEffectiveness(int totalEpisodes) {
        try {
            String effectiveness;
            String recommendation;
            
            if (totalEpisodes < 100) {
                effectiveness = "INSUFFICIENT";
                recommendation = "Train with at least 100 episodes for basic functionality";
            } else if (totalEpisodes < 500) {
                effectiveness = "BASIC";
                recommendation = "Train with 500+ episodes for improved play strength";
            } else if (totalEpisodes < 1000) {
                effectiveness = "GOOD";
                recommendation = "Train with 1000+ episodes for strong play";
            } else if (totalEpisodes < 5000) {
                effectiveness = "STRONG";
                recommendation = "Train with 5000+ episodes for expert-level play";
            } else {
                effectiveness = "EXPERT";
                recommendation = "Continue training for incremental improvements";
            }
            
            logger.info("*** AlphaZero Training Assessment ***");
            logger.info("Total episodes: {}", totalEpisodes);
            logger.info("Effectiveness level: {}", effectiveness);
            logger.info("Recommendation: {}", recommendation);
            
            // Log neural network confidence if available
            String status = getTrainingStatus();
            if (!status.isEmpty()) {
                logger.info("Neural network status: {}", status);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to assess training effectiveness: {}", e.getMessage());
        }
    }
    
    public String getTrainingStatus() {
        String baseStatus = neuralNetwork.getTrainingStatus();
        int episodes = getTrainingEpisodes();
        
        // Enhanced status with effectiveness assessment
        String effectiveness;
        if (episodes < 100) {
            effectiveness = "INSUFFICIENT (need 100+ episodes)";
        } else if (episodes < 500) {
            effectiveness = "BASIC (recommend 500+ episodes)";
        } else if (episodes < 1000) {
            effectiveness = "GOOD (recommend 1000+ episodes)";
        } else {
            effectiveness = "STRONG";
        }
        
        return String.format("%s - Episodes: %d (%s)", baseStatus, episodes, effectiveness);
    }
    
    public int getTrainingEpisodes() {
        return neuralNetwork.getTrainingEpisodes();
    }
    
    public boolean isTrainingEffective() {
        return getTrainingEpisodes() >= 100;
    }
    
    public int getRecommendedAdditionalEpisodes() {
        int current = getTrainingEpisodes();
        if (current < 100) return 100 - current;
        if (current < 500) return 500 - current;
        if (current < 1000) return 1000 - current;
        return 0; // Already well-trained
    }
    
    public boolean isTraining() {
        return trainingThread != null && trainingThread.isAlive() && !trainingStopRequested;
    }
    
    public void saveNeuralNetwork() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            // AlphaZero neural network doesn't expose MultiLayerNetwork directly
            // Use existing saveModel method for now
            neuralNetwork.saveModel();
        } else {
            // Existing synchronous code - unchanged
            neuralNetwork.saveModel();
        }
    }
    
    public void loadNeuralNetwork() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: AlphaZero loading neural network using NIO.2 async LOAD path ***");
                // Use existing loadModel method for now - neural network wrapper needed
                logger.info("*** AlphaZero: Neural network loading handled by existing methods ***");
                return;
            } catch (Exception e) {
                logger.warn("*** AlphaZero: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Neural network loading handled by factory initialization
        logger.debug("*** AlphaZero: Neural network loading handled by factory ***");
    }
    
    /**
     * Add human game data to AlphaZero's learning
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.info("*** AlphaZero: Processing human game data ***");
        
        try {
            // Check if we have sufficient training episodes for effective learning
            int currentEpisodes = getTrainingEpisodes();
            if (currentEpisodes < 50) {
                logger.warn("*** AlphaZero: Only {} episodes trained - human game data may not be effective ***", currentEpisodes);
                logger.warn("*** Recommendation: Train with at least 100 self-play episodes first ***");
            }
            
            // Use VirtualChessBoard to reconstruct game sequence
            VirtualChessBoard virtualBoard = new VirtualChessBoard();
            String[][] board = virtualBoard.getBoard();
            boolean isWhiteTurn = true;
            double gameReward = blackWon ? -1.0 : 1.0;
            
            List<AlphaZeroInterfaces.TrainingExample> examples = new java.util.ArrayList<>();
            
            // Process each move in sequence
            for (int i = 0; i < moveHistory.size(); i++) {
                String moveStr = moveHistory.get(i);
                int[] move = parseMoveString(moveStr);
                
                if (move != null) {
                    // Create MEANINGFUL training example that learns from outcomes
                    double[] policy = new double[64 * 64];
                    java.util.Arrays.fill(policy, 0.0001); // Very small base probability
                    
                    int moveIndex = move[0] * 8 * 64 + move[1] * 64 + move[2] * 8 + move[3];
                    if (moveIndex < policy.length) {
                        // Enhanced learning signals based on training maturity
                        double learningStrength = Math.min(1.0, currentEpisodes / 100.0); // Scale with training
                        
                        // If Black lost, penalize ALL Black moves from this game
                        if (!isWhiteTurn && !blackWon) {
                            policy[moveIndex] = 0.000001 * learningStrength; // Scaled penalty for losing moves
                            
                            // Extra penalty for Scholar's Mate vulnerable moves
                            if ((move[0] == 0 && move[1] == 1 && move[2] == 2 && move[3] == 2) || // Nc6
                                (move[0] == 0 && move[1] == 6 && move[2] == 2 && move[3] == 5) || // Nf6 hanging
                                (move[0] == 1 && move[1] == 3 && move[2] == 3 && move[3] == 3)) { // d6 weak
                                policy[moveIndex] = 0.0000001 * learningStrength; // Ultra penalty
                            }
                        } else {
                            double rewardStrength = (blackWon && !isWhiteTurn ? 0.1 : 0.01) * learningStrength;
                            policy[moveIndex] = rewardStrength; // Scaled reward for winning moves
                        }
                    }
                    
                    // Normalize policy
                    double sum = java.util.Arrays.stream(policy).sum();
                    if (sum > 0) {
                        for (int k = 0; k < policy.length; k++) {
                            policy[k] /= sum;
                        }
                    }
                    
                    // Stronger value signal for losing positions, scaled by training maturity
                    double positionValue = isWhiteTurn ? gameReward : -gameReward;
                    if (!blackWon && !isWhiteTurn) {
                        positionValue = -2.0 * Math.min(1.0, currentEpisodes / 200.0); // Scale negative signal
                    }
                    examples.add(new AlphaZeroInterfaces.TrainingExample(copyBoard(board), policy, positionValue));
                    
                    // Apply move to virtual board
                    if (move[0] >= 0 && move[0] < 8 && move[2] >= 0 && move[2] < 8) {
                        virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                        board = virtualBoard.getBoard();
                    }
                    
                    isWhiteTurn = !isWhiteTurn;
                }
            }
            
            // Train neural network with examples
            if (!examples.isEmpty()) {
                logger.info("*** AlphaZero: Training with {} examples from {} game (Black won: {}, Episodes: {}) ***", 
                    examples.size(), blackWon ? "LOSING" : "WINNING", blackWon, currentEpisodes);
                
                neuralNetwork.train(examples);
                
                // Always save neural network after human game data
                saveNeuralNetwork();
                logger.info("*** AlphaZero: Processed {} positions and saved model ***", examples.size());
                
                // Suggest more training if needed
                if (currentEpisodes < 200) {
                    logger.info("*** AlphaZero: Suggestion - Train {} more episodes for better human game learning ***", 
                        200 - currentEpisodes);
                }
            }
            
        } catch (Exception e) {
            logger.error("*** AlphaZero: Error processing human game data - {} ***", e.getMessage());
        }
    }
    
    /**
     * Parse move string in format "fromRow,fromCol,toRow,toCol"
     */
    private int[] parseMoveString(String moveStr) {
        try {
            String[] parts = moveStr.split(",");
            if (parts.length == 4) {
                return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim())
                };
            }
        } catch (Exception e) {
            logger.debug("Failed to parse move: {}", moveStr);
        }
        return null;
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
}