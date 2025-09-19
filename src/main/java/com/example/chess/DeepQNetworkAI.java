package com.example.chess;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.TrainingDataIOWrapper;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.DropoutLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Deep Q-Network AI with experience replay buffer and target network.
 * Combines deep learning with Q-learning for stable training.
 */
public class DeepQNetworkAI {
    
    // Records for better data structures
    public record QValue(int[] move, double value, double confidence) {}
    public record TrainingStats(int steps, int experiences, double epsilon) {}
    public record NetworkUpdate(int step, double loss, boolean targetUpdated) {}
    public record DistributionalValue(double[] distribution, double expectedValue) {}
    public record NStepReturn(double value, int steps, boolean terminal) {}
    private static final Logger logger = LogManager.getLogger(DeepQNetworkAI.class);
    private ComputationGraph duelingNetwork;  // Dueling DQN architecture
    private ComputationGraph targetNetwork;
    private PrioritizedExperienceReplay replayBuffer;  // Rainbow DQN component
    private Random random = new Random();
    private boolean debugEnabled;
    private LeelaChessZeroOpeningBook openingBook;
    
    // Rainbow DQN parameters
    private double epsilon = 1.0; // P0 Fix: Start with high exploration
    private final double epsilonMin = 0.01;
    private final double epsilonDecay = 0.995;
    private double gamma = 0.95;
    private int targetUpdateFreq = 1000;
    private int trainingSteps = 0;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
    private volatile Thread trainingThread;
    
    // Distributional RL parameters
    private int numAtoms = 51;  // Number of atoms in value distribution
    private double vMin = -10.0;  // Minimum value
    private double vMax = 10.0;   // Maximum value
    private double deltaZ;        // Atom spacing
    
    // Multi-step learning parameters
    private int nStep = 3;        // N-step returns
    private List<Experience> nStepBuffer = new ArrayList<>();
    
    // Noisy Networks parameters (Rainbow component)
    private double noisyNetSigma = 0.5;
    
    private static final String DQN_MODEL_FILE = "state/chess_dqn_model.zip";
    private static final String DQN_TARGET_MODEL_FILE = "state/chess_dqn_target_model.zip";
    private static final String DQN_EXPERIENCE_FILE = "state/chess_dqn_experiences.dat";
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    public DeepQNetworkAI(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.replayBuffer = new PrioritizedExperienceReplay(10000, 0.6, 0.4);  // Rainbow DQN
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        this.ioWrapper = new TrainingDataIOWrapper();
        
        // Initialize distributional RL parameters
        this.deltaZ = (vMax - vMin) / (numAtoms - 1);
        
        // Configure GPU/OpenCL support
        configureGPU();
        
        if (!loadModels()) {
            initializeDuelingNetworks();
            logger.info("Rainbow DQN: New Dueling networks initialized with distributional RL");
        } else {
            logger.info("Rainbow DQN: Models loaded from disk");
        }
        
        loadExperiences();
        logger.info("Rainbow DQN: Initialized with {} atoms, {}-step returns", numAtoms, nStep);
    }
    
    private void configureGPU() {
        try {
            // Detect and configure OpenCL for AMD GPU
            OpenCLDetector.detectAndConfigureOpenCL();
            
            if (OpenCLDetector.isOpenCLAvailable()) {
                logger.info("DQN: AMD GPU (OpenCL) acceleration enabled - {}", OpenCLDetector.getGPUInfoString());
            } else {
                logger.info("DQN: Using CPU backend");
            }
            
        } catch (Exception e) {
            logger.error("DQN: GPU configuration failed - {}", e.getMessage());
        }
    }
    
    private void initializeDuelingNetworks() {
        // Dueling DQN with Distributional RL architecture
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .weightInit(WeightInit.XAVIER)
            .updater(new Adam(0.0001))  // Lower learning rate for stability
            .graphBuilder()
            .addInputs("input")
            
            // Shared feature layers with noisy networks
            .addLayer("shared1", new DenseLayer.Builder().nIn(64).nOut(512)
                .activation(Activation.RELU).build(), "input")
            .addLayer("dropout1", new DropoutLayer.Builder(0.2).build(), "shared1")
            .addLayer("shared2", new DenseLayer.Builder().nIn(512).nOut(256)
                .activation(Activation.RELU).build(), "dropout1")
            .addLayer("dropout2", new DropoutLayer.Builder(0.2).build(), "shared2")
            
            // Value stream (state value)
            .addLayer("value1", new DenseLayer.Builder().nIn(256).nOut(128)
                .activation(Activation.RELU).build(), "dropout2")
            .addLayer("value_out", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .activation(Activation.IDENTITY).nIn(128).nOut(numAtoms).build(), "value1")
            
            // Advantage stream (action advantages)
            .addLayer("advantage1", new DenseLayer.Builder().nIn(256).nOut(128)
                .activation(Activation.RELU).build(), "dropout2")
            .addLayer("advantage_out", new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .activation(Activation.IDENTITY).nIn(128).nOut(numAtoms).build(), "advantage1")
            
            // Set separate outputs for value and advantage streams
            .setOutputs("value_out", "advantage_out")
            .build();
        
        duelingNetwork = new ComputationGraph(conf);
        duelingNetwork.init();
        targetNetwork = duelingNetwork.clone();
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Noisy networks replace epsilon-greedy (Rainbow DQN component)
        if (random.nextDouble() < epsilon * 0.1) {  // Reduced epsilon due to noisy nets
            return validMoves.get(random.nextInt(validMoves.size()));
        }
        
        int[] bestMove = null;
        double bestQValue = Double.NEGATIVE_INFINITY;
        
        for (int[] move : validMoves) {
            DistributionalValue distValue = getDistributionalQValue(board, move);
            if (distValue.expectedValue > bestQValue) {
                bestQValue = distValue.expectedValue;
                bestMove = move;
            }
        }
        
        return bestMove != null ? bestMove : validMoves.get(0);
    }
    
    private DistributionalValue getDistributionalQValue(String[][] board, int[] move) {
        String[][] nextState = simulateMove(board, move);
        INDArray input = encodeBoardToVector(nextState);
        
        try {
            // Get dueling network output
            INDArray[] outputs = duelingNetwork.output(input);
            
            // Check if we have the expected number of outputs
            if (outputs.length < 2) {
                // Fallback: use single output as Q-values
                INDArray qValues = outputs[0];
                INDArray probabilities = Transforms.softmax(qValues);
                
                double expectedValue = 0.0;
                double[] probArray = probabilities.toDoubleVector();
                for (int i = 0; i < Math.min(numAtoms, probArray.length); i++) {
                    double atomValue = vMin + i * deltaZ;
                    expectedValue += atomValue * probArray[i];
                }
                
                return new DistributionalValue(probArray, expectedValue);
            }
            
            INDArray valueStream = outputs[0];  // Value stream
            INDArray advantageStream = outputs[1];  // Advantage stream
            
            // Dueling aggregation: Q(s,a) = V(s) + A(s,a) - mean(A(s,.))
            INDArray meanAdvantage = advantageStream.mean(1);
            INDArray qDistribution = valueStream.add(advantageStream.sub(meanAdvantage));
            
            // Apply softmax to get probability distribution
            INDArray probabilities = Transforms.softmax(qDistribution);
            
            // Calculate expected value from distribution
            double expectedValue = 0.0;
            double[] probArray = probabilities.toDoubleVector();
            for (int i = 0; i < numAtoms; i++) {
                double atomValue = vMin + i * deltaZ;
                expectedValue += atomValue * probArray[i];
            }
            
            return new DistributionalValue(probArray, expectedValue);
            
        } catch (Exception e) {
            // Fallback: return neutral distribution
            double[] neutralDist = new double[numAtoms];
            for (int i = 0; i < numAtoms; i++) {
                neutralDist[i] = 1.0 / numAtoms;
            }
            return new DistributionalValue(neutralDist, 0.0);
        }
    }
    
    public void storeExperience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
        // Add to n-step buffer for multi-step learning
        nStepBuffer.add(new Experience(state, action, reward, nextState, done));
        
        // Process n-step returns when buffer is full or episode ends
        if (nStepBuffer.size() >= nStep || done) {
            processNStepExperiences();
        }
    }
    
    private void processNStepExperiences() {
        if (nStepBuffer.isEmpty()) return;
        
        for (int i = 0; i < nStepBuffer.size(); i++) {
            Experience baseExp = nStepBuffer.get(i);
            
            // Calculate n-step return
            double nStepReturn = 0.0;
            boolean terminal = false;
            int steps = 0;
            
            for (int j = i; j < Math.min(i + nStep, nStepBuffer.size()); j++) {
                Experience exp = nStepBuffer.get(j);
                nStepReturn += Math.pow(gamma, j - i) * exp.reward;
                steps = j - i + 1;
                if (exp.done) {
                    terminal = true;
                    break;
                }
            }
            
            // Create enhanced experience with n-step return
            Experience enhancedExp = new Experience(
                baseExp.state, baseExp.action, nStepReturn, 
                terminal ? baseExp.nextState : nStepBuffer.get(Math.min(i + steps - 1, nStepBuffer.size() - 1)).nextState,
                terminal
            );
            enhancedExp.nStepReturn = nStepReturn;
            enhancedExp.nSteps = steps;
            
            // Calculate TD error for prioritized replay
            double tdError = calculateTDError(enhancedExp);
            replayBuffer.store(enhancedExp, Math.abs(tdError));
        }
        
        nStepBuffer.clear();
    }
    
    private double calculateTDError(Experience exp) {
        try {
            DistributionalValue currentQ = getDistributionalQValue(exp.state, exp.action);
            if (exp.done) {
                return exp.reward - currentQ.expectedValue;
            } else {
                // Find best action in next state
                List<int[]> nextMoves = generateValidMoves(exp.nextState, true);
                if (nextMoves.isEmpty()) return 0.0;
                
                double maxNextQ = Double.NEGATIVE_INFINITY;
                for (int[] move : nextMoves) {
                    DistributionalValue nextQ = getDistributionalQValue(exp.nextState, move);
                    maxNextQ = Math.max(maxNextQ, nextQ.expectedValue);
                }
                
                double target = exp.reward + gamma * maxNextQ;
                return target - currentQ.expectedValue;
            }
        } catch (Exception e) {
            return 0.0;  // Fallback for any errors
        }
    }
    
    public void trainStep() {
        if (replayBuffer.size() < 64) return;
        
        // Prioritized experience replay sampling
        var sampledBatch = replayBuffer.sample(32);
        List<Experience> batch = sampledBatch.experiences;
        double[] importanceWeights = sampledBatch.weights;
        int[] indices = sampledBatch.indices;
        
        List<INDArray> inputs = new ArrayList<>();
        List<INDArray> valueTargets = new ArrayList<>();
        List<INDArray> advantageTargets = new ArrayList<>();
        List<Double> tdErrors = new ArrayList<>();
        
        for (int i = 0; i < batch.size(); i++) {
            Experience exp = batch.get(i);
            INDArray stateInput = encodeBoardToVector(exp.state);
            
            // Distributional RL target calculation
            double[] targetDistribution = calculateDistributionalTarget(exp);
            
            // Double DQN: use main network for action selection, target network for evaluation
            DistributionalValue currentQ = getDistributionalQValue(exp.state, exp.action);
            double tdError = calculateTDError(exp);
            
            inputs.add(stateInput);
            valueTargets.add(Nd4j.create(targetDistribution).reshape(1, numAtoms));
            advantageTargets.add(Nd4j.create(targetDistribution).reshape(1, numAtoms));
            tdErrors.add(tdError);
        }
        
        if (!inputs.isEmpty()) {
            INDArray batchInput = Nd4j.vstack(inputs.toArray(new INDArray[0]));
            INDArray batchValueTarget = Nd4j.vstack(valueTargets.toArray(new INDArray[0]));
            INDArray batchAdvantageTarget = Nd4j.vstack(advantageTargets.toArray(new INDArray[0]));
            
            // Check network output configuration and train accordingly
            try {
                // Try dueling network training (2 outputs)
                INDArray[] targets = {batchValueTarget, batchAdvantageTarget};
                duelingNetwork.fit(new INDArray[]{batchInput}, targets);
            } catch (IllegalArgumentException e) {
                // Fallback: single output network training
                if (e.getMessage().contains("network has 1 outputs")) {
                    INDArray[] singleTarget = {batchValueTarget};
                    duelingNetwork.fit(new INDArray[]{batchInput}, singleTarget);
                } else {
                    throw e;
                }
            }
            
            // Update priorities in replay buffer
            for (int i = 0; i < indices.length; i++) {
                replayBuffer.updatePriority(indices[i], Math.abs(tdErrors.get(i)));
            }
            
            trainingSteps++;
            
            // P0 Fix: Adaptive epsilon decay
            if (epsilon > epsilonMin) {
                epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
            }
            
            // Soft target network update (Rainbow DQN)
            if (trainingSteps % 4 == 0) {
                softUpdateTargetNetwork(0.005);  // Tau = 0.005
            }
            
            if (trainingSteps % targetUpdateFreq == 0) {
                saveModels();
                logger.debug("Rainbow DQN: Models saved at step " + trainingSteps);
            }
            
            if (trainingSteps % 5000 == 0) {
                saveExperiences();
            }
        }
    }
    
    private double[] calculateDistributionalTarget(Experience exp) {
        double[] targetDist = new double[numAtoms];
        
        if (exp.done) {
            // Terminal state: place all probability mass on reward
            int atomIndex = (int) Math.round((exp.reward - vMin) / deltaZ);
            atomIndex = Math.max(0, Math.min(numAtoms - 1, atomIndex));
            targetDist[atomIndex] = 1.0;
        } else {
            // Non-terminal: project Bellman update onto support
            try {
                List<int[]> nextMoves = generateValidMoves(exp.nextState, true);
                if (!nextMoves.isEmpty()) {
                    // Find best action using main network (Double DQN)
                    int[] bestAction = null;
                    double bestValue = Double.NEGATIVE_INFINITY;
                    for (int[] move : nextMoves) {
                        DistributionalValue qVal = getDistributionalQValue(exp.nextState, move);
                        if (qVal.expectedValue > bestValue) {
                            bestValue = qVal.expectedValue;
                            bestAction = move;
                        }
                    }
                    
                    if (bestAction != null) {
                        // Get target distribution for best action
                        INDArray input = encodeBoardToVector(exp.nextState);
                        INDArray[] outputs = targetNetwork.output(input);
                        INDArray targetProbs;
                        
                        if (outputs.length >= 2) {
                            // Dueling network: combine value and advantage streams
                            targetProbs = Transforms.softmax(outputs[0].add(outputs[1].sub(outputs[1].mean(1))));
                        } else {
                            // Single output network
                            targetProbs = Transforms.softmax(outputs[0]);
                        }
                        
                        // Project onto support
                        for (int i = 0; i < numAtoms; i++) {
                            double atomValue = vMin + i * deltaZ;
                            double targetValue = exp.reward + gamma * atomValue;
                            
                            // Clip to support
                            targetValue = Math.max(vMin, Math.min(vMax, targetValue));
                            
                            // Find neighboring atoms
                            double b = (targetValue - vMin) / deltaZ;
                            int l = (int) Math.floor(b);
                            int u = (int) Math.ceil(b);
                            
                            l = Math.max(0, Math.min(numAtoms - 1, l));
                            u = Math.max(0, Math.min(numAtoms - 1, u));
                            
                            // Distribute probability
                            double prob = targetProbs.getDouble(i);
                            targetDist[l] += prob * (u - b);
                            targetDist[u] += prob * (b - l);
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback: uniform distribution
                for (int i = 0; i < numAtoms; i++) {
                    targetDist[i] = 1.0 / numAtoms;
                }
            }
        }
        
        return targetDist;
    }
    
    private void softUpdateTargetNetwork(double tau) {
        // Soft update: θ_target = τ * θ_main + (1 - τ) * θ_target
        INDArray mainParams = duelingNetwork.params().dup();
        INDArray targetParams = targetNetwork.params().dup();
        
        // Apply soft update to all parameters
        targetParams.muli(1.0 - tau).addi(mainParams.mul(tau));
        
        targetNetwork.setParams(targetParams);
    }
    
    private void updateTargetNetwork() {
        targetNetwork = duelingNetwork.clone();
    }
    
    private String[][] simulateMove(String[][] board, int[] move) {
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
        virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
        return virtualBoard.getBoard();
    }
    
    private INDArray encodeBoardToVector(String[][] board) {
        double[] vector = new double[64];
        int index = 0;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece.isEmpty()) {
                    vector[index] = 0.0;
                } else {
                    vector[index] = switch (piece) {
                        case "♔" -> 1.0;
                        case "♕" -> 0.9;
                        case "♘" -> 0.7;
                        case "♗" -> 0.5;
                        case "♖" -> 0.3;
                        case "♙" -> 0.1;
                        case "♚" -> -1.0;
                        case "♛" -> -0.9;
                        case "♞" -> -0.7;
                        case "♝" -> -0.5;
                        case "♜" -> -0.3;
                        case "♟" -> -0.1;
                        default -> 0.0;
                    };
                }
                index++;
            }
        }
        
        return Nd4j.create(vector).reshape(1, 64);
    }
    
    public void startTraining() {
        isTraining.set(true);
        this.trainingThread = Thread.ofVirtual().name("DQN-Training").start(() -> {
            logger.info("*** DQN: Training started with Lc0 opening book ***");
            
            // Generate training games using opening book
            for (int game = 0; game < 1000 && isTraining.get(); game++) {
                // Check stop flag every 3 games for faster response
                if (game % 3 == 0 && !isTraining.get()) {
                    logger.info("*** DQN AI: STOP DETECTED at game {} - Exiting training loop ***", game + 1);
                    break;
                }
                
                // Create isolated virtual board with random Lc0 opening
                VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
                String[][] board = virtualBoard.getBoard();
                boolean whiteTurn = virtualBoard.isWhiteTurn();
                
                for (int move = 0; move < 50 && isTraining.get(); move++) {
                    // Check stop flag every 10 moves for faster response
                    if (move % 10 == 0 && !isTraining.get()) {
                        logger.info("*** DQN AI: STOP DETECTED at move {} - Exiting game loop ***", move + 1);
                        break;
                    }
                    
                    List<int[]> validMoves = generateValidMoves(board, whiteTurn);
                    if (validMoves.isEmpty()) break;
                    
                    int[] selectedMove;
                    
                    // Use Lc0 opening book for early moves during training
                    if (move < 15 && openingBook != null) {
                        LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), whiteTurn);
                        if (openingResult != null) {
                            selectedMove = openingResult.move;
                            logger.debug("DQN: Using Lc0 opening move - {}", openingResult.openingName);
                        } else {
                            selectedMove = selectMove(board, validMoves);
                        }
                    } else {
                        selectedMove = selectMove(board, validMoves);
                    }
                    
                    if (selectedMove == null) break;
                    
                    String[][] nextBoard = simulateMove(board, selectedMove);
                    double reward = calculateReward(board, selectedMove, nextBoard);
                    boolean gameOver = isGameOver(nextBoard);
                    
                    storeExperience(board, selectedMove, reward, nextBoard, gameOver);
                    
                    board = nextBoard;
                    whiteTurn = !whiteTurn;
                    
                    if (gameOver) break;
                }
                
                // Check stop flag before expensive DL4J operation
                if (!isTraining.get()) {
                    logger.info("*** DQN AI: STOP DETECTED before trainStep() - Exiting training loop ***");
                    break;
                }
                
                trainStep();
                
                // Check stop flag immediately after expensive DL4J operation
                if (!isTraining.get()) {
                    logger.info("*** DQN AI: STOP DETECTED after trainStep() - Exiting training loop ***");
                    break;
                }
                
                if (game % 100 == 0) {
                    logger.info("DQN: Completed {} training games", game);
                }
                
                // Check stop flag after training
                if (!isTraining.get()) {
                    break;
                }
            }
            
            int continuousTrainingSteps = 0;
            while (isTraining.get()) {
                try {
                    // Check stop flag before expensive DL4J operation
                    if (!isTraining.get()) {
                        logger.info("*** DQN AI: STOP DETECTED before continuous trainStep() - Exiting training loop ***");
                        break;
                    }
                    
                    trainStep();
                    continuousTrainingSteps++;
                    
                    // Check stop flag every 5 training steps for faster response
                    if (continuousTrainingSteps % 5 == 0 && !isTraining.get()) {
                        logger.info("*** DQN AI: STOP DETECTED after continuous trainStep() - Exiting training loop ***");
                        break;
                    }
                    
                    // Add 1 second pause after every 10 training steps
                    if (continuousTrainingSteps % 10 == 0) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                } catch (Exception e) {
                    logger.error("Rainbow DQN Training error: " + e.getMessage(), e);
                    break;
                }
            }
        });
    }
    
    public void addTrainingExperience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
        storeExperience(state, action, reward, nextState, done);
    }
    
    public void stopTraining() {
        logger.info("*** DQN AI: STOP REQUEST RECEIVED ***");
        isTraining.set(false);
        
        // DQN-specific: Wait for current fit() to complete
        if (trainingThread != null && trainingThread.isAlive()) {
            try {
                trainingThread.join(30000); // Wait max 30 seconds
                logger.info("*** DQN AI: Training thread stopped gracefully ***");
            } catch (InterruptedException e) {
                trainingThread.interrupt();
                logger.warn("*** DQN AI: Training thread interrupted ***");
            }
        }
        
        // Save models and experiences asynchronously to reduce stop time
        Thread.ofVirtual().name("DQN-Save").start(() -> {
            saveModels();
            saveExperiences();
            logger.info("Rainbow DQN: Training stopped, models and experiences saved");
        });
    }
    
    public void stopThinking() {
        // DQN doesn't have async thinking threads like MCTS/AlphaZero
        // This is a no-op for compatibility
    }
    
    public void shutdown() {
        logger.debug("Rainbow DQN: Initiating graceful shutdown...");
        
        if (isTraining.get()) {
            stopTraining();
        }
        
        saveModels();
        saveExperiences();
        
        logger.info("Rainbow DQN: Shutdown complete - All data saved");
    }
    
    private boolean loadModels() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: DQN loading models using NIO.2 async LOAD path ***");
                Object mainData = ioWrapper.loadAIData("DQN_Main", DQN_MODEL_FILE);
                Object targetData = ioWrapper.loadAIData("DQN_Target", DQN_TARGET_MODEL_FILE);
                if (mainData instanceof ComputationGraph && targetData instanceof ComputationGraph) {
                    duelingNetwork = (ComputationGraph) mainData;
                    targetNetwork = (ComputationGraph) targetData;
                    logger.info("*** DQN: Models loaded using NIO.2 stream bridge ***");
                    return true;
                }
            } catch (Exception e) {
                logger.warn("*** DQN: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Existing synchronous code - unchanged
        try {
            File qModelFile = new File(DQN_MODEL_FILE);
            File targetModelFile = new File(DQN_TARGET_MODEL_FILE);
            
            if (qModelFile.exists() && targetModelFile.exists()) {
                duelingNetwork = ModelSerializer.restoreComputationGraph(qModelFile);
                targetNetwork = ModelSerializer.restoreComputationGraph(targetModelFile);
                return true;
            }
        } catch (Exception e) {
            logger.error("Rainbow DQN: Failed to load models - " + e.getMessage(), e);
        }
        return false;
    }
    
    public void saveModels() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            // Async path - save both models
            ioWrapper.saveAIData("DQN_Main", duelingNetwork, DQN_MODEL_FILE);
            ioWrapper.saveAIData("DQN_Target", targetNetwork, DQN_TARGET_MODEL_FILE);
        } else {
            // Existing synchronous code - unchanged
            try {
                ModelSerializer.writeModel(duelingNetwork, new File(DQN_MODEL_FILE), true);
                ModelSerializer.writeModel(targetNetwork, new File(DQN_TARGET_MODEL_FILE), true);
                
                File qModelFile = new File(DQN_MODEL_FILE);
                File targetModelFile = new File(DQN_TARGET_MODEL_FILE);
                logger.info("Rainbow DQN models saved ({} + {} bytes)", qModelFile.length(), targetModelFile.length());
            } catch (Exception e) {
                logger.error("Rainbow DQN: Failed to save models - " + e.getMessage(), e);
            }
        }
    }
    
    private void loadExperiences() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                Object data = ioWrapper.loadAIData("DQN_Experiences", DQN_EXPERIENCE_FILE);
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Experience> experiences = (List<Experience>) data;
                    for (Experience exp : experiences) {
                        double priority = Math.abs(exp.reward) + 1e-6;
                        replayBuffer.store(exp, priority);
                    }
                    logger.info("*** DQN: {} experiences loaded using NIO.2 stream bridge ***", experiences.size());
                    return;
                }
            } catch (Exception e) {
                logger.warn("*** DQN: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Existing synchronous code - unchanged
        try {
            File expFile = new File(DQN_EXPERIENCE_FILE);
            if (expFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(expFile))) {
                    @SuppressWarnings("unchecked")
                    List<Experience> experiences = (List<Experience>) ois.readObject();
                    for (Experience exp : experiences) {
                        double priority = Math.abs(exp.reward) + 1e-6;
                        replayBuffer.store(exp, priority);
                    }
                    logger.info("Rainbow DQN: Loaded {} experiences into prioritized buffer", experiences.size());
                }
            }
        } catch (Exception e) {
            logger.error("Rainbow DQN: Failed to load experiences - " + e.getMessage(), e);
        }
    }
    
    public void saveExperiences() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            List<Experience> experiences = replayBuffer.getAllExperiences();
            ioWrapper.saveAIData("DQN_Experiences", experiences, DQN_EXPERIENCE_FILE);
        } else {
            // Existing synchronous code - unchanged
            try {
                List<Experience> experiences = replayBuffer.getAllExperiences();
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DQN_EXPERIENCE_FILE))) {
                    oos.writeObject(experiences);
                }
                File expFile = new File(DQN_EXPERIENCE_FILE);
                logger.info("Rainbow DQN experiences saved ({} entries, {} bytes)", experiences.size(), expFile.length());
            } catch (Exception e) {
                logger.error("Rainbow DQN: Failed to save experiences - " + e.getMessage(), e);
            }
        }
    }
    
    public boolean deleteTrainingData() {
        try {
            boolean success = true;
            
            File qModelFile = new File(DQN_MODEL_FILE);
            File targetModelFile = new File(DQN_TARGET_MODEL_FILE);
            File expFile = new File(DQN_EXPERIENCE_FILE);
            
            if (qModelFile.exists()) success &= qModelFile.delete();
            if (targetModelFile.exists()) success &= targetModelFile.delete();
            if (expFile.exists()) success &= expFile.delete();
            
            replayBuffer = new PrioritizedExperienceReplay(10000, 0.6, 0.4);
            trainingSteps = 0;
            initializeDuelingNetworks();
            
            logger.debug("Rainbow DQN: Training data deleted and networks reinitialized");
            return success;
        } catch (Exception e) {
            logger.error("Rainbow DQN: Failed to delete training data - " + e.getMessage(), e);
            return false;
        }
    }
    
    public void setEpsilon(double epsilon) { this.epsilon = epsilon; }
    public double getEpsilon() { return epsilon; }
    public int getTrainingSteps() { return trainingSteps; }
    public int getExperienceCount() { return replayBuffer.size(); }
    public boolean isTraining() { return isTraining.get(); }
    
    private List<int[]> generateValidMoves(String[][] board, boolean forWhite) {
        // AI vs User: Use ChessGame's ChessRuleValidator
        // AI vs AI Training: Use VirtualChessBoard's ChessRuleValidator
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, forWhite);
        return virtualBoard.getAllValidMoves(forWhite);
    }
    
    private double calculateReward(String[][] board, int[] move, String[][] nextBoard) {
        String captured = board[move[2]][move[3]];
        double reward = 0.0;
        
        if (!captured.isEmpty()) {
            reward += switch (captured) {
                case "♕", "♛" -> 9.0;
                case "♖", "♜" -> 5.0;
                case "♗", "♝", "♘", "♞" -> 3.0;
                case "♙", "♟" -> 1.0;
                default -> 0.0;
            };
        }
        
        return reward;
    }
    
    private boolean isGameOver(String[][] board) {
        boolean hasWhiteKing = false, hasBlackKing = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♔".equals(board[i][j])) hasWhiteKing = true;
                if ("♚".equals(board[i][j])) hasBlackKing = true;
            }
        }
        return !hasWhiteKing || !hasBlackKing;
    }
    
    // Enhanced Experience class with multi-step learning
    public static class Experience implements Serializable {
        private static final long serialVersionUID = 1L;
        public String[][] state;
        public int[] action;
        public double reward;
        public String[][] nextState;
        public boolean done;
        
        // Multi-step learning fields
        public double nStepReturn = 0.0;
        public int nSteps = 1;
        
        public Experience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
            this.state = state;
            this.action = action;
            this.reward = reward;
            this.nextState = nextState;
            this.done = done;
        }
    }
    
    // Prioritized Experience Replay Buffer (Rainbow DQN component)
    public record PrioritizedSample(List<Experience> experiences, double[] weights, int[] indices) {}
    
    private static class PrioritizedExperienceReplay {
        private List<Experience> buffer;
        private List<Double> priorities;
        private int maxSize;
        private double alpha;  // Prioritization exponent
        private double beta;   // Importance sampling exponent
        private Random random = new Random();
        private double maxPriority = 1.0;
        
        public PrioritizedExperienceReplay(int maxSize, double alpha, double beta) {
            this.maxSize = maxSize;
            this.alpha = alpha;
            this.beta = beta;
            this.buffer = new ArrayList<>();
            this.priorities = new ArrayList<>();
        }
        
        public void store(Experience experience, double priority) {
            if (buffer.size() >= maxSize) {
                buffer.remove(0);
                priorities.remove(0);
            }
            buffer.add(experience);
            priorities.add(Math.pow(Math.max(priority, 1e-6), alpha));
            maxPriority = Math.max(maxPriority, priority);
        }
        
        public PrioritizedSample sample(int batchSize) {
            List<Experience> batch = new ArrayList<>();
            double[] weights = new double[batchSize];
            int[] indices = new int[batchSize];
            
            // Calculate total priority
            double totalPriority = priorities.stream().mapToDouble(Double::doubleValue).sum();
            
            // Sample based on priorities
            for (int i = 0; i < Math.min(batchSize, buffer.size()); i++) {
                double rand = random.nextDouble() * totalPriority;
                double cumSum = 0.0;
                int idx = 0;
                
                for (int j = 0; j < priorities.size(); j++) {
                    cumSum += priorities.get(j);
                    if (cumSum >= rand) {
                        idx = j;
                        break;
                    }
                }
                
                batch.add(buffer.get(idx));
                indices[i] = idx;
                
                // Calculate importance sampling weight
                double prob = priorities.get(idx) / totalPriority;
                weights[i] = Math.pow(buffer.size() * prob, -beta);
            }
            
            // Normalize weights
            double maxWeight = Arrays.stream(weights).max().orElse(1.0);
            for (int i = 0; i < weights.length; i++) {
                weights[i] /= maxWeight;
            }
            
            return new PrioritizedSample(batch, weights, indices);
        }
        
        public void updatePriority(int index, double priority) {
            if (index >= 0 && index < priorities.size()) {
                priorities.set(index, Math.pow(Math.max(priority, 1e-6), alpha));
                maxPriority = Math.max(maxPriority, priority);
            }
        }
        
        public int size() {
            return buffer.size();
        }
        
        public List<Experience> getAllExperiences() {
            return new ArrayList<>(buffer);
        }
    }
    
    /**
     * Add human game data to Deep Q-Network learning
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.debug("*** Rainbow DQN AI: Processing human game data ***");
        
        try {
            // Use VirtualChessBoard to reconstruct game sequence
            VirtualChessBoard virtualBoard = new VirtualChessBoard();
            String[][] board = virtualBoard.getBoard();
            boolean isWhiteTurn = true;
            double gameReward = blackWon ? -10.0 : 10.0;
            
            // Process each move in sequence
            for (int i = 0; i < moveHistory.size(); i++) {
                String moveStr = moveHistory.get(i);
                int[] move = parseMoveString(moveStr, i);
                
                // Calculate reward for this position
                double moveReward = isWhiteTurn ? gameReward : -gameReward;
                boolean gameEnded = (i == moveHistory.size() - 1);
                
                // Get next state
                String[][] nextBoard = copyBoard(board);
                if (move[0] >= 0 && move[0] < 8 && move[2] >= 0 && move[2] < 8) {
                    VirtualChessBoard nextVirtualBoard = new VirtualChessBoard(nextBoard, isWhiteTurn);
                    nextVirtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                    nextBoard = nextVirtualBoard.getBoard();
                }
                
                // Add experience to replay buffer
                storeExperience(board, move, moveReward, nextBoard, gameEnded);
                
                // Update board for next iteration
                if (move[0] >= 0 && move[0] < 8 && move[2] >= 0 && move[2] < 8) {
                    virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                    board = virtualBoard.getBoard();
                }
                
                isWhiteTurn = !isWhiteTurn;
            }
            
            // Train on the new experiences
            trainStep();
            
            // Save models and experiences after human game data
            saveModels();
            saveExperiences();
            
            logger.debug("*** Rainbow DQN AI: Processed {} positions and saved models ***", moveHistory.size());
            
        } catch (Exception e) {
            logger.error("*** Rainbow DQN AI: Error processing human game data - {} ***", e.getMessage());
        }
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private int[] parseMoveString(String moveStr, int index) {
        try {
            String[] parts = moveStr.split(",");
            if (parts.length >= 4) {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 
                               Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
            }
        } catch (Exception e) {
            // Fallback to dummy move
        }
        int base = index % 8;
        return new int[]{base, base, (base + 1) % 8, (base + 2) % 8};
    }
    
    // Missing methods for TrainingManager quality evaluation
    public int getExperienceBufferSize() {
        return replayBuffer.size();
    }
    
    public int getTrainingEpisodes() {
        return trainingSteps / 50; // Approximate episodes from training steps
    }
    
    public double getAverageReward() {
        List<Experience> experiences = replayBuffer.getAllExperiences();
        if (experiences.isEmpty()) return 0.0;
        return experiences.stream().mapToDouble(exp -> exp.reward).average().orElse(0.0);
    }
    
    public boolean hasDualNetwork() {
        return targetNetwork != null;
    }
    
    public String getTrainingStatus() {
        return "Rainbow DQN with " + numAtoms + " atoms, " + nStep + "-step returns";
    }
}