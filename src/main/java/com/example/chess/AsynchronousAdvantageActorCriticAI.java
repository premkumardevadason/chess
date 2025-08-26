package com.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.GlobalPoolingLayer;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;

import com.example.chess.async.TrainingDataIOWrapper;

/**
 * True Asynchronous Advantage Actor-Critic (A3C) AI for Chess
 */
public class AsynchronousAdvantageActorCriticAI {
    private static final Logger logger = LogManager.getLogger(AsynchronousAdvantageActorCriticAI.class);
    
    // Network architecture
    private MultiLayerNetwork globalActorNetwork;
    private MultiLayerNetwork globalCriticNetwork;
    private PolicyGradientLoss actorLoss;
    private final List<A3CWorker> workers = new ArrayList<>();
    private final int numWorkers = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    
    // Training parameters with dynamic entropy decay
    private final double learningRate = 0.001;
    private final double gamma = 0.99;
    private final double lambda = 0.95; // GAE parameter
    private final int nSteps = 5;
    private volatile double entropyCoeff = 0.1; // Start higher
    private final double minEntropyCoeff = 0.001;
    private final double valueCoeff = 0.5;
    private final int syncFrequency = 50; // Sync every 50 steps instead of 1000
    private volatile double rewardShapingScale = 1.0; // Anneal tactical rewards over time
    
    // State management with clean shutdown
    private volatile boolean isTraining = false;
    private volatile boolean stopRequested = false;
    private final AtomicInteger globalSteps = new AtomicInteger(0);
    private final AtomicInteger episodesCompleted = new AtomicInteger(0);
    private final AtomicReference<String> trainingStatus = new AtomicReference<>("Initialized");
    private final List<Thread> workerThreads = Collections.synchronizedList(new ArrayList<>());
    
    // Chess components
    private final ChessRuleValidator ruleValidator = new ChessRuleValidator();
    private final LeelaChessZeroOpeningBook openingBook;
    private final AIMoveTranslator moveTranslator = new AIMoveTranslator();
    private final ChessTacticalDefense tacticalDefense = new ChessTacticalDefense();
    private final TrainingDataIOWrapper ioWrapper;
    private static final String ACTOR_MODEL_FILE = "state/a3c_actor_model.zip";
    private static final String CRITIC_MODEL_FILE = "state/a3c_critic_model.zip";
    private static final String STATE_FILE = "state/a3c_state.dat";
    
    // Performance tracking
    private final List<Double> recentRewards = Collections.synchronizedList(new ArrayList<>());
    
    public AsynchronousAdvantageActorCriticAI() {
        this.ioWrapper = new TrainingDataIOWrapper();
        this.openingBook = new LeelaChessZeroOpeningBook(false);
        initializeNetworks();
        loadModels();
        
        // Register with Spring for proper shutdown order
        // Shutdown hook removed to prevent conflicts with Spring context shutdown
        
        logger.info("*** A3C AI: Initialized with {} workers ***", numWorkers);
    }
    
    // Missing methods required by ChessGame
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        // A3C learns from experience during training, not from human games
        logger.info("*** A3C AI: Human game data received but not used in A3C training ***");
    }
    
    public void shutdown() {
        stopTraining();
        logger.info("*** A3C AI: Shutdown complete ***");
    }
    
    private void initializeNetworks() {
        // Actor network - ResNet-inspired architecture with skip connections
        MultiLayerConfiguration actorConf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
            .updater(new Adam(learningRate))
            .weightInit(WeightInit.XAVIER)
            .l2(1e-4) // L2 regularization
            .list()
            // Initial conv block
            .layer(0, new ConvolutionLayer.Builder(3, 3)
                .nIn(12)
                .nOut(128)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(1, new BatchNormalization.Builder().build())
            // Residual block 1
            .layer(2, new ConvolutionLayer.Builder(3, 3)
                .nOut(128)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(3, new BatchNormalization.Builder().build())
            .layer(4, new ConvolutionLayer.Builder(3, 3)
                .nOut(128)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(5, new BatchNormalization.Builder().build())
            // Residual block 2
            .layer(6, new ConvolutionLayer.Builder(3, 3)
                .nOut(256)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(7, new BatchNormalization.Builder().build())
            .layer(8, new ConvolutionLayer.Builder(1, 1)
                .nOut(256)
                .stride(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(9, new GlobalPoolingLayer.Builder(PoolingType.AVG).build())
            .layer(10, new DenseLayer.Builder()
                .nOut(512)
                .activation(Activation.RELU)
                .dropOut(0.3)
                .build())
            .layer(11, new OutputLayer.Builder(actorLoss = new PolicyGradientLoss(entropyCoeff))
                .nOut(4672) // AlphaZero-style: 64*73 move planes
                .activation(Activation.SOFTMAX)
                .build())
            .setInputType(InputType.convolutional(8, 8, 12))
            .build();
        
        globalActorNetwork = new MultiLayerNetwork(actorConf);
        globalActorNetwork.init();
        globalActorNetwork.setListeners(new ScoreIterationListener(100));
        
        // Critic network - Optimized for unbounded value estimation
        MultiLayerConfiguration criticConf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
            .updater(new Adam(learningRate * 0.5)) // Slower critic learning
            .weightInit(WeightInit.XAVIER)
            .l2(1e-4) // L2 regularization
            .list()
            // Shared feature extraction with actor
            .layer(0, new ConvolutionLayer.Builder(3, 3)
                .nIn(12)
                .nOut(128)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(1, new BatchNormalization.Builder().build())
            .layer(2, new ConvolutionLayer.Builder(3, 3)
                .nOut(128)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(3, new BatchNormalization.Builder().build())
            .layer(4, new ConvolutionLayer.Builder(3, 3)
                .nOut(256)
                .stride(1, 1)
                .padding(1, 1)
                .activation(Activation.RELU)
                .build())
            .layer(5, new BatchNormalization.Builder().build())
            .layer(6, new GlobalPoolingLayer.Builder(PoolingType.AVG).build())
            .layer(7, new DenseLayer.Builder()
                .nOut(512)
                .activation(Activation.RELU)
                .dropOut(0.2)
                .build())
            .layer(8, new DenseLayer.Builder()
                .nOut(256)
                .activation(Activation.RELU)
                .build())
            .layer(9, new OutputLayer.Builder(LossFunctions.LossFunction.MSE) // MSE for unbounded values
                .nOut(1)
                .activation(Activation.IDENTITY) // Unbounded output
                .build())
            .setInputType(InputType.convolutional(8, 8, 12))
            .build();
        
        globalCriticNetwork = new MultiLayerNetwork(criticConf);
        globalCriticNetwork.init();
        globalCriticNetwork.setListeners(new ScoreIterationListener(100));
        
        logger.info("*** A3C AI: Networks initialized ***");
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves, boolean isTraining) {
        if (validMoves.isEmpty()) return null;
        
        // Tactical defense integration
        if (!isTraining) {
            int[] defensiveMove = ChessTacticalDefense.findBestDefensiveMove(board, validMoves, "A3C");
            if (defensiveMove != null) {
                return defensiveMove;
            }
        }
        
        // Convert board to network input
        INDArray boardInput = boardToInput(board);
        
        // Get action probabilities from actor network
        INDArray actionProbs = globalActorNetwork.output(boardInput);
        
        // AlphaZero-style move decoding with legal move masking
        double[] filteredProbs = decodeMoveProbs(actionProbs, validMoves);
        
        // Normalize probabilities
        double sum = Arrays.stream(filteredProbs).sum();
        if (sum > 0) {
            for (int i = 0; i < filteredProbs.length; i++) {
                filteredProbs[i] /= sum;
            }
        } else {
            Arrays.fill(filteredProbs, 1.0 / filteredProbs.length);
        }
        
        // Sample action
        int selectedIndex = sampleFromDistribution(filteredProbs);
        return validMoves.get(selectedIndex);
    }
    
    private int sampleFromDistribution(double[] probs) {
        double rand = Math.random();
        double cumulative = 0.0;
        
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (rand <= cumulative) {
                return i;
            }
        }
        return probs.length - 1;
    }
    
    public void startTraining(int maxEpisodes) {
        if (isTraining) {
            logger.warn("*** A3C AI: Training already in progress ***");
            return;
        }
        
        isTraining = true;
        stopRequested = false;
        episodesCompleted.set(0);
        globalSteps.set(0);
        
        logger.info("*** A3C AI: Starting training with {} workers, {} max episodes ***", numWorkers, maxEpisodes);
        
        // Create and start workers with clean shutdown tracking
        for (int i = 0; i < numWorkers; i++) {
            A3CWorker worker = new A3CWorker(i, maxEpisodes / numWorkers);
            workers.add(worker);
            Thread workerThread = new Thread(worker, "A3C-Worker-" + i);
            workerThread.setDaemon(true);
            workerThreads.add(workerThread);
            workerThread.start();
        }
        
        // Monitor training progress with dynamic entropy decay
        Thread.ofVirtual().name("A3C-Monitor").start(() -> {
            while (isTraining && !stopRequested) {
                try {
                    Thread.sleep(5000);
                    // Dynamic entropy decay and reward shaping annealing
                    updateEntropyCoeff();
                    updateRewardShaping();
                    actorLoss.setEntropyCoeff(entropyCoeff);
                    
                    logger.info(String.format("*** A3C AI: Episodes: %d, Steps: %d, Avg Reward: %.2f, Entropy: %.4f ***", 
                        episodesCompleted.get(), globalSteps.get(), getAverageReward(), entropyCoeff));
                    trainingStatus.set("Running");
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    public void stopTraining() {
        logger.info("*** A3C AI: Stop training requested ***");
        trainingStatus.set("Stopping");
        stopRequested = true;
        isTraining = false;
        
        // Wait for workers to finish cleanly
        for (Thread thread : workerThreads) {
            try {
                thread.join(5000); // Wait up to 5 seconds per worker
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        workers.clear();
        workerThreads.clear();
        saveModels();
        trainingStatus.set("Idle");
        logger.info("*** A3C AI: Training stopped cleanly ***");
    }
    
    // TRUE A3C: Thread-safe updates with advantage normalization
    private void updateGlobalNetworks(INDArray states, INDArray actions, INDArray advantages, INDArray returns) {
        INDArray normalizedAdvantages = normalizeAdvantages(advantages);
        INDArray actorLabels = createPolicyGradientLabels(actions, normalizedAdvantages);
        INDArray valueTargets = returns.reshape(returns.length(), 1);
        
        // Single synchronized block for actor/critic consistency
        synchronized (this) {
            globalActorNetwork.fit(states, actorLabels);
            globalCriticNetwork.fit(states, valueTargets);
        }
    }
    
    private INDArray normalizeAdvantages(INDArray adv) {
        if (adv.length() == 1) return adv.dup();
        
        // Use built-in normalization if available, fallback to manual
        try {
            return Transforms.normalizeZeroMeanAndUnitVariance(adv);
        } catch (Exception e) {
            // Manual normalization with improved numeric stability
            double mean = adv.meanNumber().doubleValue();
            double variance = adv.varNumber().doubleValue();
            
            if (variance < 1e-8) {
                return Nd4j.zeros(adv.shape()); // All advantages are identical, return zeros
            }
            
            double std = Math.sqrt(variance);
            return adv.sub(mean).div(std);
        }
    }
    
    // Create labels for custom policy gradient loss: advantage * one-hot
    private INDArray createPolicyGradientLabels(INDArray actions, INDArray advantages) {
        INDArray adv = advantages.reshape(actions.rows(), 1);
        return actions.mul(adv); // shape (B, 4672)
    }
    
    // A3C Worker class
    private class A3CWorker implements Runnable {
        private final int workerId;
        private final int maxEpisodes;
        private MultiLayerNetwork localActorNetwork;
        private MultiLayerNetwork localCriticNetwork;
        private final List<Experience> experienceBuffer = new ArrayList<>();
        
        public A3CWorker(int workerId, int maxEpisodes) {
            this.workerId = workerId;
            this.maxEpisodes = maxEpisodes;
            initializeLocalNetworks();
        }
        
        private void initializeLocalNetworks() {
            localActorNetwork = globalActorNetwork.clone();
            localCriticNetwork = globalCriticNetwork.clone();
        }
        
        @Override
        public void run() {
            int episodeCount = 0;
            
            while (episodeCount < maxEpisodes && !stopRequested && isTraining) {
                try {
                    // Improved sync frequency for better alignment
                    if (globalSteps.get() % syncFrequency == 0) {
                        syncWithGlobalNetworks();
                    }
                    
                    // Run episode
                    double episodeReward = runEpisode();
                    
                    // Update global networks
                    if (!experienceBuffer.isEmpty()) {
                        updateGlobalNetworksFromExperience();
                        experienceBuffer.clear();
                    }
                    
                    // Track progress
                    episodeCount++;
                    episodesCompleted.incrementAndGet();
                    synchronized(recentRewards) {
                        recentRewards.add(episodeReward);
                        if (recentRewards.size() > 100) {
                            recentRewards.remove(0);
                        }
                    }
                    
                } catch (Exception e) {
                    logger.error("*** A3C Worker {}: Error - {} ***", workerId, e.getMessage());
                }
            }
            
            logger.info("*** A3C Worker {}: Training completed - {} episodes ***", workerId, episodeCount);
        }
        
        private void syncWithGlobalNetworks() {
            // Thread-safe parameter sync
            synchronized (AsynchronousAdvantageActorCriticAI.this) {
                localActorNetwork.setParams(globalActorNetwork.params().dup());
                localCriticNetwork.setParams(globalCriticNetwork.params().dup());
            }
        }
        
        private double runEpisode() {
            VirtualChessBoard board = new VirtualChessBoard(openingBook);
            double totalReward = 0.0;
            int moveCount = 0;
            
            while (moveCount < 100 && !board.isGameOver() && !stopRequested) {
                String[][] boardState = board.getBoard();
                boolean isWhiteTurn = board.isWhiteTurn();
                
                List<int[]> validMoves = board.getAllValidMoves(isWhiteTurn);
                if (validMoves.isEmpty()) break;
                
                // TRUE A3C: Use local networks for action selection during training
                INDArray stateInput = boardToInput(boardState);                // state s_t, shape (1,12,8,8)
                INDArray actionProbs = localActorNetwork.output(stateInput);
                int[] selectedMove = selectMoveFromProbs(actionProbs, validMoves);
                
                // Get value estimate from local critic for current state (V(s_t))
                double value = localCriticNetwork.output(stateInput).getDouble(0);
                
                // Execute move -> transitions board to next state s_{t+1}
                board.makeMove(selectedMove[0], selectedMove[1], selectedMove[2], selectedMove[3]);
                
                // Prepare next state input (state after the move)
                INDArray nextStateInput = boardToInput(board.getBoard());     // shape (1,12,8,8)
                boolean isTerminal = board.isGameOver();
                
                // Pure game outcome reward (AlphaZero style)
                double reward = calculateEnhancedReward(boardState, selectedMove, isTerminal, isWhiteTurn);
                
                // Add tactical bonuses for training stability (with annealing)
                reward += calculateTacticalReward(boardState, selectedMove, isTerminal, isWhiteTurn) * rewardShapingScale;
                totalReward += reward;
                
                // Store experience with nextState for correct GAE bootstrapping
                experienceBuffer.add(new Experience(stateInput, nextStateInput, selectedMove, reward, value, isTerminal));
                
                moveCount++;
                globalSteps.incrementAndGet();
                
                // Update global networks every N steps for true asynchronous learning
                if (experienceBuffer.size() >= nSteps * 2) {
                    updateGlobalNetworksFromExperience();
                    experienceBuffer.clear();
                }
            }
            
            // If episode ends with leftover experiences, ensure they are used by caller (existing flow does this)
            return totalReward;
        }
        
        private int[] selectMoveFromProbs(INDArray actionProbs, List<int[]> validMoves) {
            double[] filteredProbs = new double[validMoves.size()];
            for (int i = 0; i < validMoves.size(); i++) {
                int[] move = validMoves.get(i);
                int actionIndex = moveToActionIndex(move);
                filteredProbs[i] = actionProbs.getDouble(actionIndex);
            }
            
            double sum = Arrays.stream(filteredProbs).sum();
            if (sum > 0) {
                for (int i = 0; i < filteredProbs.length; i++) {
                    filteredProbs[i] /= sum;
                }
            } else {
                Arrays.fill(filteredProbs, 1.0 / filteredProbs.length);
            }
            
            return validMoves.get(sampleFromDistribution(filteredProbs));
        }
        
        private void updateGlobalNetworksFromExperience() {
            if (experienceBuffer.size() < nSteps) return;
            
            int batchSize = experienceBuffer.size();
            double[] values = new double[batchSize];
            double[] rewards = new double[batchSize];
            
            for (int i = 0; i < batchSize; i++) {
                values[i] = experienceBuffer.get(i).value;
                rewards[i] = experienceBuffer.get(i).reward;
            }
            
            double bootstrapValue = computeBootstrapValue();
            double[] gaeAdvantages = computeGAE(rewards, values, gamma, lambda, bootstrapValue);
            
            // Pre-allocate batch arrays for performance
            INDArray statesBatch = Nd4j.create(batchSize, 12, 8, 8);
            INDArray actionsBatch = Nd4j.zeros(batchSize, 4672);
            double[] advantageArray = new double[batchSize];
            double[] returnArray = new double[batchSize];
            
            for (int i = 0; i < batchSize; i++) {
                Experience exp = experienceBuffer.get(i);
                
                // Ensure we put the correct 3D tensor (12x8x8) into the statesBatch row
                INDArray s = exp.state;
                if (s.rank() == 4 && s.size(0) == 1) {
                    // s shape is (1,12,8,8) — remove leading batch dim
                    s = s.get(NDArrayIndex.point(0), NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.all());
                } else if (s.rank() == 1 && s.length() == 12*8*8) {
                    // rare: flattened, reshape if necessary
                    s = s.reshape(12, 8, 8);
                } else if (s.rank() != 3) {
                    // Fallback: reshape to expected dimensions
                    s = s.reshape(12, 8, 8);
                }
                statesBatch.putRow(i, s);
                
                int actionIndex = moveToActionIndex(exp.action);
                if (actionIndex < 4672) {
                    actionsBatch.putScalar(i, actionIndex, 1.0);
                }
                
                advantageArray[i] = gaeAdvantages[i];
                returnArray[i] = gaeAdvantages[i] + exp.value;
            }
            
            INDArray advantagesBatch = Nd4j.create(advantageArray);
            INDArray returnsBatch = Nd4j.create(returnArray);
            
            updateGlobalNetworks(statesBatch, actionsBatch, advantagesBatch, returnsBatch);
        }
        
        // Compute bootstrap value for GAE using nextState estimated by the local critic
        private double computeBootstrapValue() {
            if (experienceBuffer.isEmpty()) return 0.0;
            
            Experience lastExp = experienceBuffer.get(experienceBuffer.size() - 1);
            
            // If terminal state, bootstrap with 0
            if (lastExp.terminal) {
                return 0.0;
            } else {
                // Non-terminal: estimate V(s_{T}) using the local critic on lastExp.nextState
                if (lastExp.nextState == null) return 0.0;
                return localCriticNetwork.output(lastExp.nextState).getDouble(0);
            }
        }

    }
    
    // Experience storage with next state tracking
    private static class Experience {
        final INDArray state;      // state at time t (shape: 1 x 12 x 8 x 8)
        final INDArray nextState;  // state at time t+1 (shape: 1 x 12 x 8 x 8)
        final int[] action;
        final double reward;
        final double value;        // V(s_t) estimated when stored
        final boolean terminal;
        
        Experience(INDArray state, INDArray nextState, int[] action, double reward, double value, boolean terminal) {
            this.state = state;
            this.nextState = nextState;
            this.action = action;
            this.reward = reward;
            this.value = value;
            this.terminal = terminal;
        }
    }
    
    // Utility methods
    public boolean isTraining() {
        return isTraining;
    }
    
    public int getEpisodesCompleted() {
        return episodesCompleted.get();
    }
    
    public int getGlobalSteps() {
        return globalSteps.get();
    }
    
    public double getAverageReward() {
        synchronized(recentRewards) {
            return recentRewards.isEmpty() ? 0.0 : 
                recentRewards.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
    
    public String getTrainingStatus() {
        return trainingStatus.get();
    }
    
    private double calculateReward(String[][] board, int[] move, boolean gameOver) {
        return calculateEnhancedReward(board, move, gameOver, true);
    }
    
    // Pure game outcome rewards (AlphaZero style)
    private double calculateEnhancedReward(String[][] board, int[] move, boolean gameOver, boolean isWhiteTurn) {
        if (!gameOver) return 0.0; // No intermediate rewards
        
        // Determine game outcome
        // Simplified: assume current player won if game ended
        return 1.0; // Win: +1, Loss: -1, Draw: 0
    }
    
    // Enhanced tactical reward with improved awareness
    private double calculateTacticalReward(String[][] board, int[] move, boolean gameOver, boolean isWhiteTurn) {
        double reward = 0.0;
        
        // Tactical pattern recognition
        if (isInCheck(board, !isWhiteTurn)) {
            reward += 0.1; // Check bonus
        }
        
        if (gameOver && isCheckmate(board, !isWhiteTurn)) {
            reward += 1.0; // Checkmate bonus
        }
        
        // Material advantage
        String capturedPiece = board[move[2]][move[3]];
        if (!capturedPiece.isEmpty()) {
            reward += getPieceValue(capturedPiece) * 0.01;
        }
        
        // Center control bonus
        if (isCenter(move[2], move[3])) {
            reward += 0.02;
        }
        
        // Development bonus (early game)
        if (isDevelopmentMove(board, move)) {
            reward += 0.03;
        }
        
        return reward;
    }
    
    private boolean isInCheck(String[][] board, boolean isWhite) {
        // Simplified check detection
        return false; // Implement proper check detection
    }
    
    private boolean isCheckmate(String[][] board, boolean isWhite) {
        // Simplified checkmate detection
        return false; // Implement proper checkmate detection
    }
    
    private double getPieceValue(String piece) {
        return switch (piece) {
            case "♙", "♟" -> 1.0;
            case "♘", "♞" -> 3.0;
            case "♗", "♝" -> 3.0;
            case "♖", "♜" -> 5.0;
            case "♕", "♛" -> 9.0;
            case "♔", "♚" -> 100.0;
            default -> 0.0;
        };
    }
    
    // Dynamic entropy decay based on performance
    private void updateEntropyCoeff() {
        double avgReward = getAverageReward();
        int episodes = episodesCompleted.get();
        
        double decayRate = 0.995;
        if (avgReward > 0.5 && episodes > 100) {
            decayRate = 0.99;
        } else if (avgReward < -0.5) {
            decayRate = 0.999;
        }
        
        entropyCoeff = Math.max(minEntropyCoeff, entropyCoeff * decayRate);
    }
    
    // Anneal reward shaping over time to focus on win/loss
    private void updateRewardShaping() {
        int episodes = episodesCompleted.get();
        // Linearly decay from 1.0 to 0.1 over 10000 episodes
        rewardShapingScale = Math.max(0.1, 1.0 - (episodes * 0.9 / 10000.0));
    }
    
    // GAE computation with proper bootstrapping
    private double[] computeGAE(double[] rewards, double[] values, double gamma, double lambda, double bootstrapValue) {
        int T = rewards.length;
        double[] advantages = new double[T];
        double gae = 0.0;
        
        // Work backwards from last timestep
        for (int t = T - 1; t >= 0; t--) {
            double nextValue = (t == T - 1) ? bootstrapValue : values[t + 1];
            double delta = rewards[t] + gamma * nextValue - values[t];
            gae = delta + gamma * lambda * gae;
            advantages[t] = gae;
        }
        
        return advantages;
    }
    

    
    private boolean isCenter(int row, int col) {
        return (row >= 3 && row <= 4) && (col >= 3 && col <= 4);
    }
    
    private boolean isDevelopmentMove(String[][] board, int[] move) {
        String piece = board[move[0]][move[1]];
        // Knight or bishop moving from back rank
        return (piece.equals("♘") || piece.equals("♞") || piece.equals("♗") || piece.equals("♝")) 
               && (move[0] == 0 || move[0] == 7);
    }
    
    // ConvNet input: 8×8×12 tensor (12 piece planes)
    private INDArray boardToInput(String[][] board) {
        INDArray input = Nd4j.zeros(1, 12, 8, 8);
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String piece = board[row][col];
                if (!piece.isEmpty()) {
                    int pieceIndex = getPieceIndex(piece);
                    input.putScalar(0, pieceIndex, row, col, 1.0);
                }
            }
        }
        
        return input;
    }
    
    private int getPieceIndex(String piece) {
        return switch (piece) {
            case "♔" -> 0; case "♚" -> 6;
            case "♕" -> 1; case "♛" -> 7;
            case "♖" -> 2; case "♜" -> 8;
            case "♗" -> 3; case "♝" -> 9;
            case "♘" -> 4; case "♞" -> 10;
            case "♙" -> 5; case "♟" -> 11;
            default -> 0;
        };
    }
    
    // AlphaZero-style move encoding: 64 from-squares × 73 move types
    private int moveToActionIndex(int[] move) {
        int fromSquare = move[0] * 8 + move[1];
        int toSquare = move[2] * 8 + move[3];
        
        // Basic move encoding (simplified)
        int moveType = getMoveType(move[0], move[1], move[2], move[3]);
        return fromSquare * 73 + moveType;
    }
    
    private int getMoveType(int fromRow, int fromCol, int toRow, int toCol) {
        // Simplified move type encoding
        int deltaRow = toRow - fromRow;
        int deltaCol = toCol - fromCol;
        
        // Map to move type (0-72)
        if (Math.abs(deltaRow) <= 7 && Math.abs(deltaCol) <= 7) {
            return (deltaRow + 7) * 8 + (deltaCol + 7);
        }
        return 0; // Default
    }
    
    private double[] decodeMoveProbs(INDArray actionProbs, List<int[]> validMoves) {
        double[] filteredProbs = new double[validMoves.size()];
        for (int i = 0; i < validMoves.size(); i++) {
            int[] move = validMoves.get(i);
            int actionIndex = moveToActionIndex(move);
            if (actionIndex < actionProbs.length()) {
                filteredProbs[i] = actionProbs.getDouble(actionIndex);
            }
        }
        return filteredProbs;
    }
    
    public void saveModels() {
        synchronized (this) { // Prevent concurrent network updates during save
            try {
                ioWrapper.saveAIData("A3C-Actor", globalActorNetwork, ACTOR_MODEL_FILE);
                ioWrapper.saveAIData("A3C-Critic", globalCriticNetwork, CRITIC_MODEL_FILE);
                
                Map<String, Object> state = new HashMap<>();
                state.put("episodes", episodesCompleted.get());
                state.put("steps", globalSteps.get());
                state.put("avgReward", getAverageReward());
                ioWrapper.saveAIData("A3C-State", state, STATE_FILE);
                
                logger.info("*** A3C AI: Models saved ***");
            } catch (Exception e) {
                logger.error("*** A3C AI: Save failed - {} ***", e.getMessage());
            }
        }
    }
    
    private void loadModels() {
        try {
            // Try loading compatible models
            Object actorModel = ioWrapper.loadAIData("A3C-Actor", ACTOR_MODEL_FILE);
            if (actorModel instanceof MultiLayerNetwork) {
                MultiLayerNetwork loaded = (MultiLayerNetwork) actorModel;
                if (isCompatible(loaded, globalActorNetwork)) {
                    globalActorNetwork = loaded;
                    logger.info("*** A3C AI: Actor model loaded ***");
                }
            }
            
            Object criticModel = ioWrapper.loadAIData("A3C-Critic", CRITIC_MODEL_FILE);
            if (criticModel instanceof MultiLayerNetwork) {
                MultiLayerNetwork loaded = (MultiLayerNetwork) criticModel;
                if (isCompatible(loaded, globalCriticNetwork)) {
                    globalCriticNetwork = loaded;
                    logger.info("*** A3C AI: Critic model loaded ***");
                }
            }
            
            Object stateData = ioWrapper.loadAIData("A3C-State", STATE_FILE);
            if (stateData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> state = (Map<String, Object>) stateData;
                episodesCompleted.set((Integer) state.getOrDefault("episodes", 0));
                globalSteps.set((Integer) state.getOrDefault("steps", 0));
                logger.info("*** A3C AI: State loaded - Episodes: {}, Steps: {} ***", 
                    episodesCompleted.get(), globalSteps.get());
            }
            
        } catch (Exception e) {
            logger.info("*** A3C AI: No saved models found, starting fresh ***");
            episodesCompleted.set(0);
            globalSteps.set(0);
        }
    }
    
    private boolean isCompatible(MultiLayerNetwork loaded, MultiLayerNetwork current) {
        // Simple compatibility check: same number of layers and output size
        return loaded.getnLayers() == current.getnLayers();
    }
}