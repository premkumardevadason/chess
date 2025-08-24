package com.example.chess;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import com.example.chess.async.TrainingDataIOWrapper;

/**
 * Asynchronous Advantage Actor-Critic (A3C) AI for Chess
 * 
 * Features:
 * - Multiple asynchronous worker threads
 * - Shared global network with local worker networks
 * - Actor-Critic architecture with advantage estimation
 * - Experience replay and n-step returns
 * - NIO.2 async I/O integration
 * - Chess-specific state representation and action space
 */
public class AsynchronousAdvantageActorCriticAI {
    private static final Logger logger = LogManager.getLogger(AsynchronousAdvantageActorCriticAI.class);
    
    // Network architecture
    private MultiLayerNetwork globalActorNetwork;
    private MultiLayerNetwork globalCriticNetwork;
    private final List<A3CWorker> workers = new ArrayList<>();
    private final int numWorkers = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    
    // Training parameters
    private final double learningRate = 0.001;
    private final double gamma = 0.99; // Discount factor
    private final int nSteps = 5; // N-step returns
    private final double entropyCoeff = 0.01; // Entropy regularization
    private final double valueCoeff = 0.5; // Value loss coefficient
    
    // State management
    private volatile boolean isTraining = false;
    private volatile boolean stopRequested = false;
    private final AtomicInteger globalSteps = new AtomicInteger(0);
    private final AtomicInteger episodesCompleted = new AtomicInteger(0);
    private final AtomicReference<String> trainingStatus = new AtomicReference<>("Initialized");
    
    // Chess-specific components
    private final ChessRuleValidator ruleValidator = new ChessRuleValidator();
    private final LeelaChessZeroOpeningBook openingBook;
    private final AIMoveTranslator moveTranslator = new AIMoveTranslator();
    private final ChessTacticalDefense tacticalDefense = new ChessTacticalDefense();
    
    // Async I/O
    private final TrainingDataIOWrapper ioWrapper;
    private static final String ACTOR_MODEL_FILE = "a3c_actor_model.zip";
    private static final String CRITIC_MODEL_FILE = "a3c_critic_model.zip";
    private static final String STATE_FILE = "a3c_state.dat";
    
    // Performance tracking
    private final Map<String, Double> performanceMetrics = new ConcurrentHashMap<>();
    private final List<Double> recentRewards = Collections.synchronizedList(new ArrayList<>());
    
    public AsynchronousAdvantageActorCriticAI() {
        this.ioWrapper = new TrainingDataIOWrapper();
        this.openingBook = new LeelaChessZeroOpeningBook(false);
        initializeNetworks();
        loadModels();
        logger.info("*** A3C AI: Initialized with {} workers ***", numWorkers);
    }
    
    private void initializeNetworks() {
        // Actor network (policy) - outputs action probabilities
        MultiLayerConfiguration actorConf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
            .updater(new Adam(learningRate))
            .weightInit(WeightInit.XAVIER)
            .list()
            .layer(0, new DenseLayer.Builder()
                .nIn(768) // 8x8x12 chess board representation
                .nOut(512)
                .activation(Activation.RELU)
                .build())
            .layer(1, new DenseLayer.Builder()
                .nIn(512)
                .nOut(256)
                .activation(Activation.RELU)
                .build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .nIn(256)
                .nOut(4096) // 64x64 possible moves (from-to squares)
                .activation(Activation.SOFTMAX)
                .build())
            .build();
        
        globalActorNetwork = new MultiLayerNetwork(actorConf);
        globalActorNetwork.init();
        globalActorNetwork.setListeners(new ScoreIterationListener(100));
        
        // Critic network (value function) - outputs state value
        MultiLayerConfiguration criticConf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
            .updater(new Adam(learningRate))
            .weightInit(WeightInit.XAVIER)
            .list()
            .layer(0, new DenseLayer.Builder()
                .nIn(768)
                .nOut(512)
                .activation(Activation.RELU)
                .build())
            .layer(1, new DenseLayer.Builder()
                .nIn(512)
                .nOut(256)
                .activation(Activation.RELU)
                .build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .nIn(256)
                .nOut(1)
                .activation(Activation.IDENTITY)
                .build())
            .build();
        
        globalCriticNetwork = new MultiLayerNetwork(criticConf);
        globalCriticNetwork.init();
        globalCriticNetwork.setListeners(new ScoreIterationListener(100));
        
        logger.info("*** A3C AI: Networks initialized - Actor: {} params, Critic: {} params ***", 
            globalActorNetwork.numParams(), globalCriticNetwork.numParams());
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves, boolean isTraining) {
        if (validMoves.isEmpty()) return null;
        
        // Tactical defense integration
        if (!isTraining) {
            int[] defensiveMove = ChessTacticalDefense.findBestDefensiveMove(board, validMoves, "A3C");
            if (defensiveMove != null) {
                logger.debug("*** A3C AI: Using tactical defense move ***");
                return defensiveMove;
            }
        }
        
        // Convert board to network input
        INDArray boardInput = boardToInput(board);
        
        // Get action probabilities from actor network
        INDArray actionProbs = globalActorNetwork.output(boardInput);
        
        // Filter probabilities for valid moves only
        Map<Integer, int[]> validMoveMap = new HashMap<>();
        double[] filteredProbs = new double[validMoves.size()];
        
        for (int i = 0; i < validMoves.size(); i++) {
            int[] move = validMoves.get(i);
            int actionIndex = moveToActionIndex(move);
            validMoveMap.put(i, move);
            filteredProbs[i] = actionProbs.getDouble(actionIndex);
        }
        
        // Normalize probabilities
        double sum = Arrays.stream(filteredProbs).sum();
        if (sum > 0) {
            for (int i = 0; i < filteredProbs.length; i++) {
                filteredProbs[i] /= sum;
            }
        } else {
            // Uniform distribution if all probabilities are zero
            Arrays.fill(filteredProbs, 1.0 / filteredProbs.length);
        }
        
        // Sample action based on probabilities (exploration during training)
        int selectedIndex;
        if (isTraining && Math.random() < 0.1) { // 10% exploration
            selectedIndex = new Random().nextInt(validMoves.size());
        } else {
            selectedIndex = sampleFromDistribution(filteredProbs);
        }
        
        return validMoveMap.get(selectedIndex);
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
        return probs.length - 1; // Fallback
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
        
        // Create and start worker threads
        ExecutorService workerExecutor = Executors.newFixedThreadPool(numWorkers);
        List<CompletableFuture<Void>> workerFutures = new ArrayList<>();
        
        for (int i = 0; i < numWorkers; i++) {
            A3CWorker worker = new A3CWorker(i, maxEpisodes / numWorkers);
            workers.add(worker);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(worker, workerExecutor);
            workerFutures.add(future);
        }
        
        // Start monitoring thread
        Thread.ofVirtual().name("A3C-Monitor").start(this::monitorTraining);
        
        // Wait for all workers to complete
        CompletableFuture.allOf(workerFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                isTraining = false;
                workerExecutor.shutdown();
                saveModels();
                logger.info("*** A3C AI: Training completed - {} episodes, {} steps ***", 
                    episodesCompleted.get(), globalSteps.get());
            });
    }
    
    public void stopTraining() {
        logger.info("*** A3C AI: Stop training requested ***");
        stopRequested = true;
        isTraining = false;
        
        // Stop all workers
        workers.forEach(A3CWorker::stop);
        workers.clear();
        
        // Save current state
        saveModels();
        logger.info("*** A3C AI: Training stopped ***");
    }
    
    private void monitorTraining() {
        while (isTraining && !stopRequested) {
            try {
                Thread.sleep(10000); // Monitor every 10 seconds
                
                int episodes = episodesCompleted.get();
                int steps = globalSteps.get();
                double avgReward = getAverageReward();
                
                trainingStatus.set(String.format("Episodes: %d, Steps: %d, Avg Reward: %.2f", 
                    episodes, steps, avgReward));
                
                logger.info("*** A3C AI: {} ***", trainingStatus.get());
                
                // Periodic save every 1000 episodes
                if (episodes > 0 && episodes % 1000 == 0) {
                    saveModels();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public double getAverageReward() {
        synchronized (recentRewards) {
            if (recentRewards.isEmpty()) return 0.0;
            return recentRewards.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
    
    private INDArray boardToInput(String[][] board) {
        // Convert 8x8 chess board to 768-dimensional input (8x8x12 channels for piece types)
        double[] input = new double[768];
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String piece = board[row][col];
                if (!piece.isEmpty()) {
                    int pieceType = getPieceTypeIndex(piece);
                    if (pieceType >= 0) {
                        int index = (row * 8 + col) * 12 + pieceType;
                        input[index] = 1.0;
                    }
                }
            }
        }
        
        return Nd4j.create(input).reshape(1, 768);
    }
    
    private int getPieceTypeIndex(String piece) {
        return switch (piece) {
            case "♔" -> 0; case "♕" -> 1; case "♖" -> 2;
            case "♗" -> 3; case "♘" -> 4; case "♙" -> 5;
            case "♚" -> 6; case "♛" -> 7; case "♜" -> 8;
            case "♝" -> 9; case "♞" -> 10; case "♟" -> 11;
            default -> -1;
        };
    }
    
    private int moveToActionIndex(int[] move) {
        // Convert move [fromRow, fromCol, toRow, toCol] to action index
        return move[0] * 512 + move[1] * 64 + move[2] * 8 + move[3];
    }
    
    private int[] actionIndexToMove(int actionIndex) {
        // Convert action index back to move coordinates
        int fromRow = actionIndex / 512;
        int fromCol = (actionIndex % 512) / 64;
        int toRow = (actionIndex % 64) / 8;
        int toCol = actionIndex % 8;
        return new int[]{fromRow, fromCol, toRow, toCol};
    }
    
    public void saveModels() {
        if (ioWrapper.isAsyncEnabled()) {
            ioWrapper.saveAIData("A3C-Actor", globalActorNetwork, ACTOR_MODEL_FILE);
            ioWrapper.saveAIData("A3C-Critic", globalCriticNetwork, CRITIC_MODEL_FILE);
            
            // Save training state
            Map<String, Object> state = new HashMap<>();
            state.put("episodes", episodesCompleted.get());
            state.put("steps", globalSteps.get());
            state.put("metrics", performanceMetrics);
            ioWrapper.saveAIData("A3C-State", state, STATE_FILE);
        }
        logger.info("*** A3C AI: Models saved ***");
    }
    
    private void loadModels() {
        if (ioWrapper.isAsyncEnabled()) {
            try {
                Object actorModel = ioWrapper.loadAIData("A3C-Actor", ACTOR_MODEL_FILE);
                if (actorModel instanceof MultiLayerNetwork) {
                    globalActorNetwork = (MultiLayerNetwork) actorModel;
                    logger.info("*** A3C AI: Actor model loaded ***");
                }
                
                Object criticModel = ioWrapper.loadAIData("A3C-Critic", CRITIC_MODEL_FILE);
                if (criticModel instanceof MultiLayerNetwork) {
                    globalCriticNetwork = (MultiLayerNetwork) criticModel;
                    logger.info("*** A3C AI: Critic model loaded ***");
                }
                
                Object stateData = ioWrapper.loadAIData("A3C-State", STATE_FILE);
                if (stateData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> state = (Map<String, Object>) stateData;
                    episodesCompleted.set((Integer) state.getOrDefault("episodes", 0));
                    globalSteps.set((Integer) state.getOrDefault("steps", 0));
                    logger.info("*** A3C AI: Training state loaded ***");
                }
            } catch (Exception e) {
                logger.warn("*** A3C AI: Could not load models - starting fresh: {} ***", e.getMessage());
            }
        }
    }
    
    // A3C Worker class
    private class A3CWorker implements Runnable {
        private final int workerId;
        private final int maxEpisodes;
        private final MultiLayerNetwork localActorNetwork;
        private final MultiLayerNetwork localCriticNetwork;
        private volatile boolean stopped = false;
        
        public A3CWorker(int workerId, int maxEpisodes) {
            this.workerId = workerId;
            this.maxEpisodes = maxEpisodes;
            
            // Create local networks (copies of global networks)
            this.localActorNetwork = globalActorNetwork.clone();
            this.localCriticNetwork = globalCriticNetwork.clone();
        }
        
        @Override
        public void run() {
            logger.info("*** A3C Worker {}: Starting training ***", workerId);
            
            int episodes = 0;
            while (episodes < maxEpisodes && !stopped && !stopRequested) {
                try {
                    runEpisode();
                    episodes++;
                    episodesCompleted.incrementAndGet();
                    
                    if (episodes % 100 == 0) {
                        logger.debug("*** A3C Worker {}: Completed {} episodes ***", workerId, episodes);
                    }
                    
                } catch (Exception e) {
                    logger.error("*** A3C Worker {}: Episode error - {} ***", workerId, e.getMessage());
                }
            }
            
            logger.info("*** A3C Worker {}: Training completed - {} episodes ***", workerId, episodes);
        }
        
        private void runEpisode() {
            // Create virtual chess board with opening book
            VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
            String[][] board = virtualBoard.getBoard();
            boolean whiteTurn = virtualBoard.isWhiteTurn();
            
            List<Experience> experiences = new ArrayList<>();
            double totalReward = 0.0;
            int steps = 0;
            
            while (steps < 200 && !stopped && !stopRequested) {
                // Get current state
                INDArray stateInput = boardToInput(board);
                
                // Get valid moves
                List<int[]> validMoves = ruleValidator.getAllValidMoves(board, whiteTurn, whiteTurn);
                if (validMoves.isEmpty()) break;
                
                // Select action using local actor network
                int[] selectedMove = selectMoveWithLocalNetwork(board, validMoves);
                if (selectedMove == null) break;
                
                // Get state value from critic
                double stateValue = localCriticNetwork.output(stateInput).getDouble(0);
                
                // Execute move
                String piece = board[selectedMove[0]][selectedMove[1]];
                String captured = board[selectedMove[2]][selectedMove[3]];
                board[selectedMove[2]][selectedMove[3]] = piece;
                board[selectedMove[0]][selectedMove[1]] = "";
                
                // Calculate reward
                double reward = calculateReward(piece, captured, board, whiteTurn);
                totalReward += reward;
                
                // Store experience
                experiences.add(new Experience(stateInput, selectedMove, reward, stateValue));
                
                // Check for game end
                whiteTurn = !whiteTurn;
                if (ruleValidator.isCheckmate(board, whiteTurn)) {
                    reward += whiteTurn ? -100.0 : 100.0; // Bonus/penalty for checkmate
                    break;
                }
                
                steps++;
                globalSteps.incrementAndGet();
            }
            
            // Update networks with collected experiences
            updateNetworks(experiences, totalReward);
            
            // Track performance
            synchronized (recentRewards) {
                recentRewards.add(totalReward);
                if (recentRewards.size() > 100) {
                    recentRewards.remove(0);
                }
            }
        }
        
        private int[] selectMoveWithLocalNetwork(String[][] board, List<int[]> validMoves) {
            INDArray boardInput = boardToInput(board);
            INDArray actionProbs = localActorNetwork.output(boardInput);
            
            // Filter for valid moves
            double[] filteredProbs = new double[validMoves.size()];
            for (int i = 0; i < validMoves.size(); i++) {
                int actionIndex = moveToActionIndex(validMoves.get(i));
                filteredProbs[i] = actionProbs.getDouble(actionIndex);
            }
            
            // Normalize
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
        
        private double calculateReward(String piece, String captured, String[][] board, boolean isWhite) {
            double reward = 0.0;
            
            // Capture rewards
            if (!captured.isEmpty()) {
                reward += getPieceValue(captured) * 10.0;
            }
            
            // Check/checkmate rewards
            if (ruleValidator.isKingInDanger(board, !isWhite)) {
                reward += ruleValidator.isCheckmate(board, !isWhite) ? 100.0 : 20.0;
            }
            
            // Penalty for exposing own king
            if (ruleValidator.isKingInDanger(board, isWhite)) {
                reward -= 50.0;
            }
            
            // Small reward for piece development
            reward += 1.0;
            
            return reward;
        }
        
        private double getPieceValue(String piece) {
            return switch (piece) {
                case "♙", "♟" -> 1.0;
                case "♘", "♞", "♗", "♝" -> 3.0;
                case "♖", "♜" -> 5.0;
                case "♕", "♛" -> 9.0;
                case "♔", "♚" -> 100.0;
                default -> 0.0;
            };
        }
        
        private void updateNetworks(List<Experience> experiences, double totalReward) {
            if (experiences.isEmpty()) return;
            
            // Calculate n-step returns and advantages
            List<Double> returns = calculateNStepReturns(experiences);
            List<Double> advantages = calculateAdvantages(experiences, returns);
            
            // Prepare training data
            int batchSize = experiences.size();
            INDArray statesBatch = Nd4j.zeros(batchSize, 768);
            INDArray actionsBatch = Nd4j.zeros(batchSize, 4096);
            INDArray advantagesBatch = Nd4j.zeros(batchSize, 1);
            INDArray returnsBatch = Nd4j.zeros(batchSize, 1);
            
            for (int i = 0; i < batchSize; i++) {
                Experience exp = experiences.get(i);
                statesBatch.putRow(i, exp.state);
                
                int actionIndex = moveToActionIndex(exp.action);
                actionsBatch.putScalar(i, actionIndex, 1.0);
                
                advantagesBatch.putScalar(i, 0, advantages.get(i));
                returnsBatch.putScalar(i, 0, returns.get(i));
            }
            
            // Update local networks
            synchronized (globalActorNetwork) {
                // Copy global weights to local
                localActorNetwork.setParams(globalActorNetwork.params());
                localCriticNetwork.setParams(globalCriticNetwork.params());
                
                // Train local networks
                localActorNetwork.fit(statesBatch, actionsBatch);
                localCriticNetwork.fit(statesBatch, returnsBatch);
                
                // Update global networks with local gradients
                globalActorNetwork.setParams(localActorNetwork.params());
                globalCriticNetwork.setParams(localCriticNetwork.params());
            }
        }
        
        private List<Double> calculateNStepReturns(List<Experience> experiences) {
            List<Double> returns = new ArrayList<>();
            
            for (int i = 0; i < experiences.size(); i++) {
                double ret = 0.0;
                double discount = 1.0;
                
                for (int j = i; j < Math.min(i + nSteps, experiences.size()); j++) {
                    ret += discount * experiences.get(j).reward;
                    discount *= gamma;
                }
                
                // Add bootstrapped value if not terminal
                if (i + nSteps < experiences.size()) {
                    ret += discount * experiences.get(i + nSteps).stateValue;
                }
                
                returns.add(ret);
            }
            
            return returns;
        }
        
        private List<Double> calculateAdvantages(List<Experience> experiences, List<Double> returns) {
            List<Double> advantages = new ArrayList<>();
            
            for (int i = 0; i < experiences.size(); i++) {
                double advantage = returns.get(i) - experiences.get(i).stateValue;
                advantages.add(advantage);
            }
            
            return advantages;
        }
        
        public void stop() {
            stopped = true;
        }
    }
    
    // Experience storage class
    private static class Experience {
        final INDArray state;
        final int[] action;
        final double reward;
        final double stateValue;
        
        Experience(INDArray state, int[] action, double reward, double stateValue) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.stateValue = stateValue;
        }
    }
    
    // Public interface methods for integration
    public boolean isTraining() { return isTraining; }
    public String getTrainingStatus() { return trainingStatus.get(); }
    public int getEpisodesCompleted() { return episodesCompleted.get(); }
    public int getGlobalSteps() { return globalSteps.get(); }

    
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        // A3C can learn from human games by treating them as demonstration episodes
        logger.debug("*** A3C AI: Processing human game data - {} moves ***", moveHistory.size());
        
        try {
            double gameReward = blackWon ? -50.0 : 50.0;
            
            // Process recent moves with outcome bias
            for (int i = Math.max(0, moveHistory.size() - 10); i < moveHistory.size(); i++) {
                boolean isWhiteMove = (i % 2 == 0);
                double moveReward = isWhiteMove ? gameReward : -gameReward;
                
                // Store in recent rewards for performance tracking
                synchronized (recentRewards) {
                    recentRewards.add(moveReward);
                    if (recentRewards.size() > 100) {
                        recentRewards.remove(0);
                    }
                }
            }
            
            logger.debug("*** A3C AI: Human game data processed ***");
            
        } catch (Exception e) {
            logger.error("*** A3C AI: Error processing human game data - {} ***", e.getMessage());
        }
    }
    
    public void shutdown() {
        logger.info("*** A3C AI: Initiating shutdown ***");
        stopTraining();
        saveModels();
        logger.info("*** A3C AI: Shutdown complete ***");
    }
    
    // Training data quality metrics for TrainingManager
    public int getTrainingEpisodes() { return episodesCompleted.get(); }
    public String getBackendInfo() { return "DeepLearning4J + A3C"; }
    public Map<String, Double> getPerformanceMetrics() { return new HashMap<>(performanceMetrics); }
}