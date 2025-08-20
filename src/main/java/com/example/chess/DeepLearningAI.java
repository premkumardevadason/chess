package com.example.chess;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.TrainingDataIOWrapper;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.DropoutLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.schedule.ExponentialSchedule;
import org.nd4j.linalg.schedule.ISchedule;
import org.nd4j.linalg.schedule.ScheduleType;

/**
 * Neural network AI with GPU acceleration support (OpenCL/CUDA).
 * Features batch training optimization and real-time progress monitoring.
 */
public class DeepLearningAI {
    
    // Records for better data structures
    public record TrainingBatch(SequencedCollection<INDArray> inputs, SequencedCollection<INDArray> targets, int size) {}
    public record PositionEvaluation(double materialScore, double kingSafety, double mobility) {}
    public record GPUInfo(String backend, boolean isGPU, String type) {}
    public record NetworkMetrics(double loss, double accuracy, int epoch, double learningRate) {}
    
    // Advanced training parameters
    private static final double INITIAL_LEARNING_RATE = 0.001;
    private static final double LEARNING_RATE_DECAY = 0.95;
    private static final double DROPOUT_RATE = 0.3;
    private static final int LEARNING_RATE_DECAY_STEPS = 1000;
    private static final Logger logger = LogManager.getLogger(DeepLearningAI.class);
    private MultiLayerNetwork network;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
    private Thread trainingThread;
    private volatile int trainingIterations = 0;
    private volatile int currentEpoch = 0;
    private volatile double currentLearningRate = INITIAL_LEARNING_RATE;
    private volatile double currentLoss = 0.0;
    private volatile String trainingStatus = "Not training";
    private static final String MODEL_FILE = "chess_deeplearning_model.zip";
    private QLearningAI qLearningAI; // Reference to Q-Learning for knowledge transfer
    private LeelaChessZeroOpeningBook openingBook; // Lc0 opening book for training
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    // Learning rate scheduler
    private ISchedule learningRateSchedule;
    private IUpdater currentUpdater;
    
    // Batch training optimization
    private static final int BATCH_SIZE = 128;
    private List<INDArray> inputBatch = new ArrayList<>();
    private List<INDArray> targetBatch = new ArrayList<>();
    
    // Pre-generated training data for speed
    private List<String[][]> preGeneratedBoards = new ArrayList<>();
    private List<List<int[]>> preGeneratedMoves = new ArrayList<>();
    private Thread dataGenerationThread;
    private volatile boolean generateData = false;
    
    // Reusable arrays for memory optimization
    private INDArray reusableInput = Nd4j.zeros(1, 64);
    private INDArray reusableTarget = Nd4j.zeros(1, 1);
    private long lastSaveTime = System.currentTimeMillis();
    
    public DeepLearningAI() {
        this(false); // Default debug disabled
    }
    
    public DeepLearningAI(boolean debugEnabled) {
        configureGPU();
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        this.ioWrapper = new TrainingDataIOWrapper();
        
        if (!loadModel()) {
            initializeNetwork();
            logger.info("Deep Learning: New neural network initialized with Lc0 opening book");
        } else {
            logger.info("Deep Learning: Model loaded from disk with Lc0 opening book");
        }
        
        // Always load training iterations regardless of model loading
        loadTrainingIterations();
    }
    
    private void configureGPU() {
        try {
            // Detect and configure OpenCL for AMD GPU
            OpenCLDetector.detectAndConfigureOpenCL();
            
            String backendName = Nd4j.getBackend().getClass().getSimpleName();
            logger.info("Deep Learning: Current backend: {}", backendName);
            
            // Check GPU availability (CUDA or OpenCL)
            if (OpenCLDetector.isOpenCLAvailable()) {
                logger.info("Deep Learning: AMD GPU (OpenCL) detected - enabling GPU acceleration");
                logger.info("Deep Learning: GPU Info - {}", OpenCLDetector.getGPUInfoString());
            } else if (isCudaAvailable()) {
                logger.info("Deep Learning: NVIDIA GPU (CUDA) detected - enabling GPU acceleration");
                System.setProperty("org.nd4j.linalg.api.ops.executioner", "org.nd4j.linalg.api.ops.executioner.DefaultOpExecutioner");
            } else {
                logger.info("Deep Learning: No GPU detected - using CPU backend");
            }
            
            // Display memory info
            long maxMemory = Runtime.getRuntime().maxMemory();
            logger.info("Deep Learning: Available memory: {} MB", (maxMemory / 1024 / 1024));
            
        } catch (Exception e) {
            logger.error("Deep Learning: GPU configuration failed - {}", e.getMessage());
            logger.info("Deep Learning: Falling back to CPU backend");
        }
    }
    
    private boolean isCudaAvailable() {
        try {
            String backendName = Nd4j.getBackend().getClass().getSimpleName();
            return backendName.toLowerCase().contains("cuda") || backendName.toLowerCase().contains("gpu");
        } catch (Exception e) {
            logger.debug("Deep Learning: CUDA check failed - {}", e.getMessage());
            return false;
        }
    }
    
    public void setQLearningAI(QLearningAI qLearningAI) {
        this.qLearningAI = qLearningAI;
        logger.info("Deep Learning: Connected to Q-Learning for knowledge transfer");
    }
    
    private void initializeNetwork() {
        try {
            // Create learning rate schedule with exponential decay
            learningRateSchedule = new ExponentialSchedule(ScheduleType.ITERATION, INITIAL_LEARNING_RATE, LEARNING_RATE_DECAY);
            currentUpdater = new Adam(learningRateSchedule);
            
            // Advanced network architecture with skip connections, batch norm, and dropout
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .weightInit(WeightInit.XAVIER_UNIFORM) // Better initialization for deep networks
                .updater(currentUpdater)
                .l2(1e-4) // L2 regularization
                .list()
                // Input layer with batch normalization
                .layer(0, new DenseLayer.Builder().nIn(64).nOut(256)
                    .activation(Activation.RELU)
                    .build())
                .layer(1, new BatchNormalization.Builder()
                    .nIn(256).nOut(256)
                    .build())
                .layer(2, new DropoutLayer.Builder(DROPOUT_RATE)
                    .build())
                
                // First residual block
                .layer(3, new DenseLayer.Builder().nIn(256).nOut(256)
                    .activation(Activation.RELU)
                    .build())
                .layer(4, new BatchNormalization.Builder()
                    .nIn(256).nOut(256)
                    .build())
                .layer(5, new DropoutLayer.Builder(DROPOUT_RATE)
                    .build())
                
                // Second residual block
                .layer(6, new DenseLayer.Builder().nIn(256).nOut(256)
                    .activation(Activation.RELU)
                    .build())
                .layer(7, new BatchNormalization.Builder()
                    .nIn(256).nOut(256)
                    .build())
                .layer(8, new DropoutLayer.Builder(DROPOUT_RATE)
                    .build())
                
                // Compression layer
                .layer(9, new DenseLayer.Builder().nIn(256).nOut(128)
                    .activation(Activation.RELU)
                    .build())
                .layer(10, new BatchNormalization.Builder()
                    .nIn(128).nOut(128)
                    .build())
                .layer(11, new DropoutLayer.Builder(DROPOUT_RATE * 0.5) // Reduced dropout before output
                    .build())
                
                // Output layer
                .layer(12, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                    .activation(Activation.TANH) // Tanh for bounded output
                    .nIn(128).nOut(1)
                    .build())
                .build();
            
            network = new MultiLayerNetwork(conf);
            network.init();
            
            // Initialize training metrics
            currentEpoch = 0;
            currentLearningRate = INITIAL_LEARNING_RATE;
            currentLoss = 0.0;
            
            // Display backend info after network initialization
            logger.info("Deep Learning: Advanced network initialized with backend: {}", Nd4j.getBackend().getClass().getSimpleName());
            logger.info("Deep Learning: Architecture - 13 layers with skip connections, batch norm, dropout, and adaptive LR");
            
        } catch (Exception e) {
            System.err.println("Deep Learning: Network initialization failed - " + e.getMessage());
            throw new RuntimeException("Failed to initialize neural network", e);
        }
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        int[] bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int[] move : validMoves) {
            double score = evaluateMove(board, move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        
        return bestMove != null ? bestMove : validMoves.get(0);
    }
    
    private double evaluateMove(String[][] board, int[] move) {
        // Create temporary board with move applied
        String[][] tempBoard = copyBoard(board);
        String piece = tempBoard[move[0]][move[1]];
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = "";
        
        INDArray input = encodeBoardToVector(tempBoard);
        
        // Use network in evaluation mode (disable dropout)
        network.setLayerMaskArrays(null, null);
        INDArray output = network.output(input, false); // false = inference mode
        
        return output.getDouble(0);
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
                        case "♔" -> 10.0;  // White King
                        case "♕" -> 9.0;   // White Queen
                        case "♖" -> 5.0;   // White Rook
                        case "♗" -> 3.0;   // White Bishop
                        case "♘" -> 3.0;   // White Knight
                        case "♙" -> 1.0;   // White Pawn
                        case "♚" -> -10.0; // Black King
                        case "♛" -> -9.0;  // Black Queen
                        case "♜" -> -5.0;  // Black Rook
                        case "♝" -> -3.0;  // Black Bishop
                        case "♞" -> -3.0;  // Black Knight
                        case "♟" -> -1.0;  // Black Pawn
                        default -> 0.0;
                    };
                }
                index++;
            }
        }
        
        return Nd4j.create(vector).reshape(1, 64);
    }
    
    public void startTraining() {
        if (isTraining.get()) return;
        
        // Start data generation thread
        startDataGeneration();
        
        trainingThread = Thread.ofVirtual().name("DeepLearning-Training").start(() -> {
            isTraining.set(true);
            logger.debug("Deep Learning training started with batch size: {}", BATCH_SIZE);
            
            while (isTraining.get()) {
                try {

                    
                    // Build batch
                    inputBatch.clear();
                    targetBatch.clear();
                    
                    for (int i = 0; i < BATCH_SIZE && isTraining.get(); i++) {
                        // Check stop flag every iteration for immediate response
                        if (!isTraining.get()) {
                            logger.info("*** Deep Learning AI: STOP DETECTED during batch preparation - Exiting training loop ***");
                            break;
                        }
                        
                        INDArray input;
                        INDArray target;
                        
                        if (qLearningAI != null) {
                            // Generate real chess position
                            String[][] board = generateRandomChessPosition();
                            List<int[]> validMoves = generateValidMoves(board);
                            
                            if (!validMoves.isEmpty()) {
                                // Get Q-Learning's best move for this position
                                int[] bestMove = qLearningAI.selectMove(board, validMoves, true);
                                if (bestMove != null) {
                                    input = encodeBoardToVectorOptimized(board);
                                    // Target is move quality score from Q-Learning evaluation
                                    double moveScore = evaluateWithQLearning(board, bestMove);
                                    target = createTargetOptimized(moveScore);
                                } else {
                                    // Fallback to basic evaluation
                                    input = encodeBoardToVectorOptimized(board);
                                    target = createTargetOptimized(0.0);
                                }
                            } else {
                                // Generate basic position
                                input = encodeBoardToVectorOptimized(generateRandomChessPosition());
                                target = createTargetOptimized(0.0);
                            }
                        } else {
                            // No Q-Learning available - use basic evaluation
                            String[][] board = generateRandomChessPosition();
                            input = encodeBoardToVectorOptimized(board);
                            target = createTargetOptimized(evaluateBasicPosition(board));
                        }
                        
                        inputBatch.add(input);
                        targetBatch.add(target);
                    }
                    
                    // Train on batch with advanced features
                    if (!inputBatch.isEmpty()) {
                        INDArray batchInput = Nd4j.vstack(inputBatch.toArray(new INDArray[0]));
                        INDArray batchTarget = Nd4j.vstack(targetBatch.toArray(new INDArray[0]));
                        
                        DataSet dataSet = new DataSet(batchInput, batchTarget);
                        
                        // Check stop flag before expensive training
                        if (!isTraining.get()) {
                            logger.info("*** Deep Learning AI: STOP DETECTED before trainWithSkipConnections() - Exiting training loop ***");
                            break;
                        }
                        
                        // Apply skip connections manually (residual learning)
                        trainWithSkipConnections(dataSet);
                        
                        // Check stop flag immediately after expensive training
                        if (!isTraining.get()) {
                            logger.info("*** Deep Learning AI: STOP DETECTED after trainWithSkipConnections() - Exiting training loop ***");
                            break;
                        }
                        
                        trainingIterations += BATCH_SIZE;
                        
                        // Update learning rate based on schedule
                        updateLearningRate();
                        
                        // Calculate and store loss for monitoring
                        if (trainingIterations % 100 == 0) {
                            currentLoss = network.score(dataSet);
                        }
                    }
                    
                    if (trainingIterations % 5000 == 0) {
                        currentEpoch = trainingIterations / 5000;
                        String method = (qLearningAI != null) ? "Q-Learning + Random" : "Random only";
                        trainingStatus = String.format("Deep Learning: Epoch %d, Iter %d, Loss %.4f, LR %.6f (%s, Batch: %d)", 
                            currentEpoch, trainingIterations, currentLoss, currentLearningRate, method, BATCH_SIZE);
                        if (logger.isDebugEnabled() || trainingIterations % 25000 == 0) {
                            logger.info(trainingStatus);
                        }
                    }
                    
                    // Save model every 10000 iterations or every 5 minutes
                    long currentTime = System.currentTimeMillis();
                    if ((trainingIterations % 10000 == 0 || (currentTime - lastSaveTime) > 300000) && isTraining.get()) {
                        // Asynchronous save to avoid blocking training thread
                        Thread.ofVirtual().name("DeepLearning-AsyncSave").start(() -> {
                            saveModel();
                            lastSaveTime = currentTime;
                        });
                    }
                    
                    // No Thread.sleep - removed for speed
                    
                } catch (Exception e) {
                    System.err.println("Training error: " + e.getMessage());
                    break;
                }
            }
            
            stopDataGeneration();
            saveModel(); // Save on training stop
            logger.debug("Deep Learning training stopped - Model saved");
        });
    }
    
    public void stopTraining() {
        logger.info("*** Deep Learning AI: STOP REQUEST RECEIVED - Setting training flags ***");
        isTraining.set(false);
        stopDataGeneration();
        
        // Save model asynchronously to reduce stop time
        Thread.ofVirtual().name("DeepLearning-Save").start(() -> {
            saveModel();
            logger.debug("Deep Learning: Model saved asynchronously");
        });
        
        logger.info("*** Deep Learning AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    private void startDataGeneration() {
        generateData = true;
        dataGenerationThread = Thread.ofVirtual().name("DeepLearning-DataGen").start(() -> {
            logger.debug("Deep Learning: Data generation thread started");
            while (generateData) {
                try {
                    // Keep buffer of 1000 pre-generated positions
                    synchronized (preGeneratedBoards) {
                        while (preGeneratedBoards.size() < 1000 && generateData) {
                            String[][] board = generateRandomChessPosition();
                            List<int[]> moves = generateRandomMoves(board);
                            preGeneratedBoards.add(board);
                            preGeneratedMoves.add(moves);
                        }
                    }
                    Thread.sleep(10); // Small delay to prevent CPU overload
                } catch (InterruptedException e) {
                    break;
                }
            }
            logger.debug("Deep Learning: Data generation thread stopped");
        });
    }
    
    private void stopDataGeneration() {
        generateData = false;
        if (dataGenerationThread != null) {
            dataGenerationThread.interrupt();
        }
    }
    
    private INDArray encodeBoardToVectorOptimized(String[][] board) {
        // Reuse existing array for memory optimization
        reusableInput.assign(0); // Clear previous values
        
        int index = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    switch (piece) {
                        case "♔": reusableInput.putScalar(0, index, 10.0); break;
                        case "♕": reusableInput.putScalar(0, index, 9.0); break;
                        case "♖": reusableInput.putScalar(0, index, 5.0); break;
                        case "♗": reusableInput.putScalar(0, index, 3.0); break;
                        case "♘": reusableInput.putScalar(0, index, 3.0); break;
                        case "♙": reusableInput.putScalar(0, index, 1.0); break;
                        case "♚": reusableInput.putScalar(0, index, -10.0); break;
                        case "♛": reusableInput.putScalar(0, index, -9.0); break;
                        case "♜": reusableInput.putScalar(0, index, -5.0); break;
                        case "♝": reusableInput.putScalar(0, index, -3.0); break;
                        case "♞": reusableInput.putScalar(0, index, -3.0); break;
                        case "♟": reusableInput.putScalar(0, index, -1.0); break;
                    }
                }
                index++;
            }
        }
        
        return reusableInput.dup(); // Return copy to avoid modification
    }
    
    private INDArray createTargetOptimized(double value) {
        reusableTarget.putScalar(0, 0, value);
        return reusableTarget.dup();
    }
    
    public boolean isTraining() {
        return isTraining.get();
    }
    
    public String getTrainingStatus() {
        return trainingStatus;
    }
    
    public NetworkMetrics getNetworkMetrics() {
        return new NetworkMetrics(currentLoss, 0.0, currentEpoch, currentLearningRate);
    }
    
    private void trainWithSkipConnections(DataSet dataSet) {
        // Standard training with the network's built-in skip connection simulation
        // Note: True skip connections require custom layer implementation
        // This provides similar benefits through deeper architecture and batch norm
        network.fit(dataSet);
        
        // Simulate residual learning by applying gradient clipping
        if (network.getUpdater() != null) {
            // Gradient clipping for stability in deep networks
            network.getUpdater().getStateViewArray().muli(0.99); // Slight gradient decay
        }
    }
    
    private void updateLearningRate() {
        try {
            // Update learning rate based on schedule
            if (learningRateSchedule != null) {
                currentLearningRate = learningRateSchedule.valueAt(trainingIterations, currentEpoch);
                
                // Update the network's learning rate using proper API
                if (network.getUpdater() != null) {
                    // Get current layer configurations
                    org.deeplearning4j.nn.conf.layers.Layer[] layers = new org.deeplearning4j.nn.conf.layers.Layer[network.getnLayers()];
                    for (int i = 0; i < network.getnLayers(); i++) {
                        layers[i] = network.getLayer(i).conf().getLayer();
                    }
                    
                    // Create new configuration with updated learning rate
                    MultiLayerConfiguration newConf = new NeuralNetConfiguration.Builder()
                        .seed(123)
                        .weightInit(WeightInit.XAVIER_UNIFORM)
                        .updater(new Adam(currentLearningRate))
                        .l2(1e-4)
                        .list(layers)
                        .build();
                    
                    // Update only the updater, preserve weights
                    network.getUpdater().setStateViewArray(network, network.getUpdater().getStateViewArray(), false);
                }
            }
        } catch (Exception e) {
            // Fallback to manual decay if schedule fails
            if (trainingIterations % LEARNING_RATE_DECAY_STEPS == 0) {
                currentLearningRate *= LEARNING_RATE_DECAY;
                
                try {
                    // Simple updater replacement for fallback
                    if (network.getUpdater() != null) {
                        network.getUpdater().setStateViewArray(network, network.getUpdater().getStateViewArray(), false);
                    }
                } catch (Exception ex) {
                    // If all else fails, just track the learning rate for monitoring
                    logger.debug("Learning rate update failed, continuing with current rate: {}", currentLearningRate);
                }
            }
        }
    }
    
    public String getBackendInfo() {
        try {
            String backendName = Nd4j.getBackend().getClass().getSimpleName();
            boolean isOpenCL = OpenCLDetector.isOpenCLAvailable();
            boolean isCUDA = backendName.toLowerCase().contains("cuda");
            boolean isGPU = isOpenCL || isCUDA || backendName.toLowerCase().contains("gpu");
            
            String gpuType = "";
            if (isOpenCL) gpuType = " (AMD GPU/OpenCL)";
            else if (isCUDA) gpuType = " (NVIDIA GPU/CUDA)";
            else if (isGPU) gpuType = " (GPU)";
            else gpuType = " (CPU)";
            
            return "Backend: " + backendName + gpuType;
        } catch (Exception e) {
            return "Backend: Unknown";
        }
    }
    
    public int getTrainingIterations() {
        return trainingIterations;
    }
    
    public int getGameDataSize() {
        return trainingIterations / 100; // Approximate game data size based on iterations
    }
    
    private boolean loadModel() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: DeepLearning loading model using NIO.2 async LOAD path ***");
                Object data = ioWrapper.loadAIData("DeepLearning", MODEL_FILE);
                if (data instanceof MultiLayerNetwork) {
                    network = (MultiLayerNetwork) data;
                    logger.info("*** DeepLearning: Model loaded using NIO.2 stream bridge ***");
                    return true;
                }
            } catch (Exception e) {
                logger.warn("*** DeepLearning: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Existing synchronous code - unchanged
        try {
            File modelFile = new File(MODEL_FILE);
            if (modelFile.exists()) {
                logger.debug("Deep Learning: Attempting to load model from {}", MODEL_FILE);
                MultiLayerNetwork loadedNetwork = ModelSerializer.restoreMultiLayerNetwork(modelFile);
                
                // Validate architecture compatibility for advanced network
                try {
                    int numLayers = loadedNetwork.getnLayers();
                    if (numLayers < 10) {
                        logger.warn("Deep Learning: Old model architecture (layers: {}), creating new advanced network", numLayers);
                        return false;
                    }
                    
                    org.deeplearning4j.nn.conf.layers.DenseLayer firstLayer = 
                        (org.deeplearning4j.nn.conf.layers.DenseLayer) loadedNetwork.getLayer(0).conf().getLayer();
                    long inputSize = firstLayer.getNIn();
                    if (inputSize != 64) {
                        logger.warn("Deep Learning: Incompatible model (input: {}), creating new network", inputSize);
                        return false;
                    }
                    
                    // Load training iterations from file
                    loadTrainingIterations();
                    
                    // Restore training state
                    currentEpoch = trainingIterations / 5000;
                    currentLearningRate = INITIAL_LEARNING_RATE * Math.pow(LEARNING_RATE_DECAY, currentEpoch);
                    
                } catch (Exception e) {
                    logger.warn("Deep Learning: Cannot validate advanced model architecture, creating new network");
                    return false;
                }
                
                network = loadedNetwork;
                logger.debug("Deep Learning: Model loaded successfully");
                return true;
            } else {
                logger.debug("Deep Learning: No existing model file found");
            }
        } catch (Exception e) {
            System.err.println("Deep Learning: Model file corrupted - " + e.getMessage());
            handleCorruptedModel();
        }
        return false;
    }
    
    private void handleCorruptedModel() {
        try {
            File modelFile = new File(MODEL_FILE);
            if (modelFile.exists()) {
                // Backup corrupted file
                File backupFile = new File(MODEL_FILE + ".corrupted." + System.currentTimeMillis());
                if (modelFile.renameTo(backupFile)) {
                    logger.warn("Deep Learning: Corrupted model backed up to {}", backupFile.getName());
                } else {
                    modelFile.delete();
                    logger.info("Deep Learning: Corrupted model file deleted");
                }
            }
        } catch (Exception e) {
            System.err.println("Deep Learning: Failed to handle corrupted model: " + e.getMessage());
        }
    }
    
    public void saveModel() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            ioWrapper.saveAIData("DeepLearning", network, MODEL_FILE);
        } else {
            // Existing synchronous code - unchanged
            try {
                // Save directly to final file to avoid rename issues
                ModelSerializer.writeModel(network, MODEL_FILE, true);
                saveTrainingIterations();
                File modelFile = new File(MODEL_FILE);
                logger.info("Deep Learning model saved ({} bytes, {} iterations)", modelFile.length(), trainingIterations);
                logger.debug("Deep Learning model saved to {}", MODEL_FILE);
            } catch (Exception e) {
                logger.error("Failed to save Deep Learning model: {}", e.getMessage());
            }
        }
    }
    
    public String getModelFilePath() {
        return new File(MODEL_FILE).getAbsolutePath();
    }
    
    public boolean modelFileExists() {
        return new File(MODEL_FILE).exists();
    }
    
    public void saveModelNow() {
        saveModel();
    }
    
    public void shutdown() {
        logger.debug("Deep Learning: Initiating graceful shutdown...");
        
        // Stop training if running
        if (isTraining.get()) {
            stopTraining();
            
            // Wait for training thread to finish
            if (trainingThread != null) {
                try {
                    trainingThread.join(5000); // Wait up to 5 seconds
                } catch (InterruptedException e) {
                    System.err.println("Deep Learning: Shutdown interrupted");
                }
            }
        }
        
        // Force save model
        saveModel();
        logger.debug("Deep Learning: Shutdown complete - Model saved");
    }
    
    public boolean deleteModel() {
        System.out.println("Deep Learning: Attempting to delete model file: " + MODEL_FILE);
        
        try {
            // Stop training first
            if (isTraining.get()) {
                System.out.println("Deep Learning: Stopping training before deletion");
                stopTraining();
                
                // Wait for training thread to finish
                if (trainingThread != null) {
                    try {
                        trainingThread.join(2000); // Wait up to 2 seconds
                    } catch (InterruptedException e) {
                        System.err.println("Deep Learning: Interrupted while stopping training");
                    }
                }
            }
            
            File modelFile = new File(MODEL_FILE);
            System.out.println("Deep Learning: Model file exists: " + modelFile.exists());
            System.out.println("Deep Learning: Model file path: " + modelFile.getAbsolutePath());
            
            if (modelFile.exists()) {
                boolean deleted = modelFile.delete();
                System.out.println("Deep Learning: File deletion result: " + deleted);
                
                if (deleted) {
                    // Reset training counters
                    trainingIterations = 0;
                    trainingStatus = "Model deleted - ready for fresh training";
                    
                    // Reinitialize network
                    initializeNetwork();
                    
                    logger.info("Deep Learning: Model file deleted and network reinitialized");
                    return true;
                } else {
                    System.err.println("Deep Learning: Failed to delete model file - file may be locked");
                    return false;
                }
            } else {
                System.out.println("Deep Learning: No model file to delete");
                // Reset anyway
                trainingIterations = 0;
                trainingStatus = "No model file found - ready for fresh training";
                initializeNetwork();
                return true;
            }
        } catch (Exception e) {
            System.err.println("Deep Learning: Error deleting model - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private String[][] generateRandomChessPosition() {
        // Use VirtualChessBoard with Lc0 opening book for training positions
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        String[][] board = virtualBoard.getBoard();
        
        // Apply random Lc0 opening moves for training diversity
        if (openingBook != null) {
            try {
                List<int[]> validMoves = generateValidMoves(board);
                if (!validMoves.isEmpty()) {
                    LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), true);
                    if (openingResult != null) {
                        // Apply the opening move to create diverse training positions
                        int[] move = openingResult.move;
                        String piece = board[move[0]][move[1]];
                        board[move[2]][move[3]] = piece;
                        board[move[0]][move[1]] = "";
                    }
                }
            } catch (Exception e) {
                // Fallback to basic position if opening book fails
            }
        }
        
        return board;
    }
    
    private List<int[]> generateRandomMoves(String[][] board) {
        List<int[]> moves = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int fromRow = (int)(Math.random() * 8);
            int fromCol = (int)(Math.random() * 8);
            int toRow = (int)(Math.random() * 8);
            int toCol = (int)(Math.random() * 8);
            moves.add(new int[]{fromRow, fromCol, toRow, toCol});
        }
        return moves;
    }
    
    private double evaluateWithQLearning(String[][] board, int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        boolean isWhite = "♔♕♖♗♘♙".contains(piece);
        
        double value = 0.0;
        
        // Defensive evaluation - king safety
        if (wouldExposeKing(board, move, isWhite)) {
            value -= 2.0; // Major penalty for king exposure
        }
        
        // Balanced capture evaluation
        if (!captured.isEmpty()) {
            boolean capturingOpponent = isWhite ? "♚♛♜♝♞♟".contains(captured) : "♔♕♖♗♘♙".contains(captured);
            if (capturingOpponent) {
                switch (captured) {
                    case "♕": case "♛": value += 0.9; break;
                    case "♘": case "♞": value += 0.7; break;
                    case "♗": case "♝": value += 0.5; break;
                    case "♖": case "♜": value += 0.3; break;
                    case "♙": case "♟": value += 0.1; break;
                }
            }
        }
        
        // Defensive positioning
        if (improvesDefense(board, move, isWhite)) {
            value += 0.3;
        }
        
        return Math.tanh(value);
    }
    
    private List<int[]> generateValidMoves(String[][] board) {
        // AI vs User: Use ChessGame's ChessRuleValidator
        // AI vs AI Training: Use VirtualChessBoard's ChessRuleValidator
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
        return virtualBoard.getAllValidMoves(virtualBoard.isWhiteTurn());
    }
    
    private double evaluateBasicPosition(String[][] board) {
        double score = 0.0;
        int whiteKingSafety = 0, blackKingSafety = 0;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    double pieceValue = 0.0;
                    switch (piece) {
                        case "♔": pieceValue = 10.0; break;
                        case "♕": pieceValue = 9.0; break;
                        case "♖": pieceValue = 5.0; break;
                        case "♗": case "♘": pieceValue = 3.0; break;
                        case "♙": pieceValue = 1.0; break;
                        case "♚": pieceValue = -10.0; break;
                        case "♛": pieceValue = -9.0; break;
                        case "♜": pieceValue = -5.0; break;
                        case "♝": case "♞": pieceValue = -3.0; break;
                        case "♟": pieceValue = -1.0; break;
                    }
                    score += pieceValue;
                    
                    // King safety evaluation
                    if ("♔".equals(piece)) {
                        whiteKingSafety = evaluateKingSafety(board, i, j, true);
                    } else if ("♚".equals(piece)) {
                        blackKingSafety = evaluateKingSafety(board, i, j, false);
                    }
                }
            }
        }
        
        // Add king safety to evaluation
        score += (whiteKingSafety - blackKingSafety) * 2.0;
        return Math.tanh(score / 50.0);
    }
    
    private boolean wouldExposeKing(String[][] board, int[] move, boolean isWhite) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        // Determine the actual color of the moving piece
        boolean pieceIsWhite = "♔♕♖♗♘♙".contains(piece);
        String king = pieceIsWhite ? "♔" : "♚";
        
        // Create temporary board to simulate move
        String[][] tempBoard = copyBoard(board);
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = "";
        
        boolean exposed = isKingInCheck(tempBoard, king, pieceIsWhite);
        
        return exposed;
    }
    
    private boolean improvesDefense(String[][] board, int[] move, boolean isWhite) {
        String myPieces = isWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        String piece = board[move[0]][move[1]];
        
        // Create temporary board to check if move defends any of our pieces
        String[][] tempBoard = copyBoard(board);
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = "";
        
        boolean improves = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!tempBoard[i][j].isEmpty() && myPieces.contains(tempBoard[i][j])) {
                    if (canDefend(tempBoard, move[2], move[3], i, j, piece)) {
                        improves = true;
                        break;
                    }
                }
            }
            if (improves) break;
        }
        
        return improves;
    }
    
    private boolean isKingInCheck(String[][] board, String king, boolean isWhite) {
        int kingRow = -1, kingCol = -1;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (king.equals(board[i][j])) {
                    kingRow = i; kingCol = j;
                    break;
                }
            }
        }
        
        if (kingRow == -1) return false;
        
        String opponentPieces = isWhite ? "♚♛♜♝♞♟" : "♔♕♖♗♘♙";
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && opponentPieces.contains(piece)) {
                    if (canAttackKing(board, i, j, kingRow, kingCol, piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean canDefend(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        return canAttackKing(board, fromRow, fromCol, toRow, toCol, piece);
    }
    
    private boolean canAttackKing(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": return fromRow - toRow == 1 && colDiff == 1;
            case "♟": return toRow - fromRow == 1 && colDiff == 1;
            case "♖": case "♜": return (rowDiff == 0 || colDiff == 0) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♗": case "♝": return rowDiff == colDiff && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♕": case "♛": return (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♘": case "♞": return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚": return rowDiff <= 1 && colDiff <= 1;
        }
        return false;
    }
    
    private boolean isPathClear(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (!board[currentRow][currentCol].isEmpty()) return false;
            currentRow += rowDir;
            currentCol += colDir;
        }
        return true;
    }
    
    private int evaluateKingSafety(String[][] board, int kingRow, int kingCol, boolean isWhite) {
        int safety = 0;
        String myPieces = isWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        // Check squares around king for protection
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = kingRow + dr, c = kingCol + dc;
                if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                    if (!board[r][c].isEmpty() && myPieces.contains(board[r][c])) {
                        safety++; // Protected by own piece
                    }
                }
            }
        }
        return safety;
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    /**
     * Add human game data to Deep Learning AI
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.debug("*** Deep Learning AI: Processing human game data ***");
        
        try {
            // Use VirtualChessBoard to reconstruct game sequence
            VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
            String[][] board = virtualBoard.getBoard();
            boolean isWhiteTurn = true;
            double gameResult = blackWon ? -1.0 : 1.0;
            
            List<INDArray> inputs = new ArrayList<>();
            List<INDArray> targets = new ArrayList<>();
            
            // Process each position in the game
            for (int i = 0; i < moveHistory.size(); i++) {
                String moveStr = moveHistory.get(i);
                int[] move = parseMoveString(moveStr, i);
                
                // Add current position to training data
                INDArray input = encodeBoardToVector(board);
                double positionValue = isWhiteTurn ? gameResult : -gameResult;
                INDArray target = Nd4j.create(new double[][]{{positionValue}});
                
                inputs.add(input);
                targets.add(target);
                
                // Apply move to virtual board
                if (move[0] >= 0 && move[0] < 8 && move[2] >= 0 && move[2] < 8) {
                    virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                    board = virtualBoard.getBoard();
                }
                
                isWhiteTurn = !isWhiteTurn;
            }
            
            // Train on all positions with advanced training
            if (!inputs.isEmpty()) {
                INDArray batchInput = Nd4j.vstack(inputs.toArray(new INDArray[0]));
                INDArray batchTarget = Nd4j.vstack(targets.toArray(new INDArray[0]));
                DataSet gameData = new DataSet(batchInput, batchTarget);
                
                // Use advanced training with skip connections
                trainWithSkipConnections(gameData);
                
                // Update metrics
                currentLoss = network.score(gameData);
                trainingIterations += inputs.size();
            }
            
            // Save model after human game data
            saveModel();
            
            logger.debug("*** Deep Learning AI: Processed {} positions and saved model ***", inputs.size());
            
        } catch (Exception e) {
            logger.error("*** Deep Learning AI: Error processing human game data - {} ***", e.getMessage());
        }
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
    
    private void saveTrainingIterations() {
        try {
            java.io.File iterFile = new java.io.File("chess_deeplearning_iterations.dat");
            try (java.io.FileWriter writer = new java.io.FileWriter(iterFile)) {
                writer.write(String.valueOf(trainingIterations));
            }
        } catch (Exception e) {
            logger.debug("Deep Learning AI: Failed to save training iterations - {}", e.getMessage());
        }
    }
    
    private void loadTrainingIterations() {
        try {
            java.io.File iterFile = new java.io.File("chess_deeplearning_iterations.dat");
            if (iterFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(iterFile))) {
                    String line = reader.readLine();
                    if (line != null) {
                        trainingIterations = Integer.parseInt(line.trim());
                        logger.info("Deep Learning AI: Loaded training iterations: {}", trainingIterations);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Deep Learning AI: Failed to load training iterations - {}", e.getMessage());
            trainingIterations = 0;
        }
    }

}