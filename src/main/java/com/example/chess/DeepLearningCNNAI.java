package com.example.chess;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.TrainingDataIOWrapper;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * CNN-based Chess AI using Convolutional Neural Networks for spatial pattern recognition.
 * Treats chess board as 8x8x12 image with piece-type channels for better positional understanding.
 */
public class DeepLearningCNNAI {
    
    // Records for better data structures
    public record ChessPosition(int row, int col) {}
    public record ChessMove(ChessPosition from, ChessPosition to, String piece, String captured) {}
    public record TrainingData(String[][] position, double result, long timestamp) {}
    private static final Logger logger = LogManager.getLogger(DeepLearningCNNAI.class);
    private MultiLayerNetwork network;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
    private volatile Thread trainingThread;
    private volatile int trainingIterations = 0;
    private volatile String trainingStatus = "Not training";
    private static final String MODEL_FILE = "state/chess_cnn_model.zip";
    private QLearningAI qLearningAI;
    private LeelaChessZeroOpeningBook openingBook;
    private ChessController controller;
    private final Object saveLock = new Object();
    private volatile boolean isSaving = false;
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    private static final int BATCH_SIZE = 64;
    private List<INDArray> inputBatch = new ArrayList<>();
    private List<INDArray> targetBatch = new ArrayList<>();
    
    // CNN input dimensions: 8x8 board with 12 piece type channels
    private static final int BOARD_SIZE = 8;
    private static final int PIECE_CHANNELS = 12;
    
    private INDArray reusableInput = Nd4j.zeros(org.nd4j.linalg.api.buffer.DataType.FLOAT, 1, PIECE_CHANNELS, BOARD_SIZE, BOARD_SIZE);
    private INDArray reusableTarget = Nd4j.zeros(org.nd4j.linalg.api.buffer.DataType.FLOAT, 1, 1);
    private long lastSaveTime = System.currentTimeMillis();
    
    // Training data for AI vs User games using records
    private SequencedCollection<TrainingData> trainingData = new ArrayList<>();
    
    public DeepLearningCNNAI() {
        this(false);
    }
    
    public DeepLearningCNNAI(boolean debugEnabled) {
        configureGPU();
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        this.ioWrapper = new TrainingDataIOWrapper();
        
        if (!loadModel()) {
            initializeNetwork();
            logger.info("CNN AI: New convolutional neural network initialized");
        } else {
            logger.info("CNN AI: CNN model loaded from disk");
        }
        
        // Always load training iterations regardless of model loading
        loadTrainingIterations();
    }
    
    private void configureGPU() {
        try {
            OpenCLDetector.detectAndConfigureOpenCL();
            String backendName = Nd4j.getBackend().getClass().getSimpleName();
            logger.info("CNN AI: Current backend: {}", backendName);
            
            if (OpenCLDetector.isOpenCLAvailable()) {
                logger.info("CNN AI: AMD GPU (OpenCL) detected - enabling GPU acceleration");
            } else if (isCudaAvailable()) {
                logger.info("CNN AI: NVIDIA GPU (CUDA) detected - enabling GPU acceleration");
            } else {
                logger.info("CNN AI: No GPU detected - using CPU backend");
            }
        } catch (Exception e) {
            logger.error("CNN AI: GPU configuration failed - {}", e.getMessage());
        }
    }
    
    private boolean isCudaAvailable() {
        try {
            String backendName = Nd4j.getBackend().getClass().getSimpleName();
            return backendName.toLowerCase().contains("cuda") || backendName.toLowerCase().contains("gpu");
        } catch (Exception e) {
            return false;
        }
    }
    
    public void setQLearningAI(QLearningAI qLearningAI) {
        this.qLearningAI = qLearningAI;
        logger.info("CNN AI: Connected to Q-Learning for knowledge transfer");
    }
    
    public void setController(ChessController controller) {
        this.controller = controller;
    }
    
    /**
     * Add human game data for learning from user vs AI games
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.info("CNN AI: Processing human game data with {} moves", moveHistory.size());
        
        try {
            int positionsProcessed = 0;
            int batchesTrained = 0;
            int sizeBefore = trainingData.size();
            
            // Use VirtualChessBoard to reconstruct game sequence
            VirtualChessBoard virtualBoard = new VirtualChessBoard();
            String[][] board = virtualBoard.getBoard();
            boolean isWhiteTurn = true;
            
            // Process each move in sequence to learn from actual game flow
            for (String moveStr : moveHistory) {
                String[] parts = moveStr.split(",");
                if (parts.length == 4) {
                    try {
                        int[] move = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 
                                     Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
                        
                        // Validate move coordinates
                        if (isValidMove(move)) {
                            // Store position in persistent training data
                            double result = blackWon ? (isWhiteTurn ? -1.0 : 1.0) : (isWhiteTurn ? 1.0 : -1.0);
                            addGameData(copyBoard(board), result);
                            
                            // Create training example from this position
                            INDArray input = encodeBoardToCNNOptimized(board);
                            INDArray target = createTargetOptimized(result);
                            
                            // Add to training batch
                            inputBatch.add(input);
                            targetBatch.add(target);
                            positionsProcessed++;
                            
                            // Apply move to virtual board
                            if (virtualBoard.makeMove(move[0], move[1], move[2], move[3])) {
                                board = virtualBoard.getBoard();
                                isWhiteTurn = !isWhiteTurn;
                            }
                        }
                        
                    } catch (NumberFormatException e) {
                        logger.debug("Invalid move format: {}", moveStr);
                    }
                }
            }
            
            // Train on accumulated batch if we have enough data
            if (inputBatch.size() >= BATCH_SIZE) {
                try {
                    trainOnBatch();
                    batchesTrained++;
                } catch (Exception e) {
                    logger.error("CNN AI: Batch training error - {}", e.getMessage());
                    inputBatch.clear();
                    targetBatch.clear();
                }
            }
            
            // Force save model after human game data
            saveModel();
            
            int sizeAfter = trainingData.size();
            int newDataPoints = sizeAfter - sizeBefore;
            
            logger.info("CNN AI: Processed {} positions from human game ({} new data points, {} batches trained)", 
                positionsProcessed, newDataPoints, batchesTrained);
            
            // Verify data persistence
            verifyDataPersistence(moveHistory.size(), positionsProcessed);
            
        } catch (Exception e) {
            logger.error("CNN AI: Error processing human game data - {}", e.getMessage());
        }
    }
    
    private boolean isValidMove(int[] move) {
        return move.length == 4 && 
               move[0] >= 0 && move[0] < 8 && move[1] >= 0 && move[1] < 8 &&
               move[2] >= 0 && move[2] < 8 && move[3] >= 0 && move[3] < 8;
    }
    
    private void verifyDataPersistence(int expectedMoves, int actualProcessed) {
        try {
            // Verify training data was actually stored
            int currentDataSize = trainingData.size();
            logger.info("CNN AI: Data persistence verification - Training data size: {}", currentDataSize);
            
            if (currentDataSize == 0) {
                logger.warn("CNN AI: No training data persisted - this indicates data collection failure");
            } else {
                // Verify recent data quality
                TrainingData lastEntry = trainingData.stream().reduce((first, second) -> second).orElse(null);
                if (lastEntry != null) {
                    logger.debug("CNN AI: Last training entry - Result: {}, Timestamp: {}", 
                        String.format("%.3f", lastEntry.result()), new java.util.Date(lastEntry.timestamp()));
                }
            }
            
            // Verify model file exists and is recent
            java.io.File modelFile = new java.io.File(MODEL_FILE);
            if (modelFile.exists()) {
                long lastModified = modelFile.lastModified();
                long ageMinutes = (System.currentTimeMillis() - lastModified) / (1000 * 60);
                logger.info("CNN AI: Model file verified - Size: {} bytes, Age: {} minutes", 
                    modelFile.length(), ageMinutes);
            } else {
                logger.warn("CNN AI: Model file not found - persistence may have failed");
            }
            
        } catch (Exception e) {
            logger.error("CNN AI: Data persistence verification failed - {}", e.getMessage());
        }
    }
    
    private void initializeNetwork() {
        try {
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(0.0005))
                .list()
                // Multi-scale feature extraction - 1x1 convolutions
                .layer(0, new ConvolutionLayer.Builder(1, 1)
                    .nIn(PIECE_CHANNELS)
                    .nOut(64)
                    .stride(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Multi-scale feature extraction - 3x3 convolutions
                .layer(1, new ConvolutionLayer.Builder(3, 3)
                    .nOut(128)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Multi-scale feature extraction - 5x5 convolutions
                .layer(2, new ConvolutionLayer.Builder(5, 5)
                    .nOut(256)
                    .stride(1, 1)
                    .padding(2, 2)
                    .activation(Activation.RELU)
                    .build())
                // Depthwise separable convolution simulation - depthwise
                .layer(3, new ConvolutionLayer.Builder(3, 3)
                    .nOut(512)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Depthwise separable convolution simulation - pointwise
                .layer(4, new ConvolutionLayer.Builder(1, 1)
                    .nOut(256)
                    .stride(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Spatial attention mechanism
                .layer(5, new ConvolutionLayer.Builder(1, 1)
                    .nOut(1)
                    .stride(1, 1)
                    .activation(Activation.SIGMOID)
                    .build())
                // Feature combination after attention
                .layer(6, new ConvolutionLayer.Builder(3, 3)
                    .nOut(512)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Deep feature extraction
                .layer(7, new ConvolutionLayer.Builder(3, 3)
                    .nOut(1024)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Advanced pattern recognition
                .layer(8, new ConvolutionLayer.Builder(3, 3)
                    .nOut(512)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Pooling for dimensionality reduction
                .layer(9, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                    .kernelSize(2, 2)
                    .stride(2, 2)
                    .build())
                // Post-pooling convolutions
                .layer(10, new ConvolutionLayer.Builder(3, 3)
                    .nOut(1024)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                .layer(11, new ConvolutionLayer.Builder(3, 3)
                    .nOut(2048)
                    .stride(1, 1)
                    .padding(1, 1)
                    .activation(Activation.RELU)
                    .build())
                // Global average pooling to transition to dense layers
                .layer(12, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.AVG)
                    .kernelSize(4, 4)
                    .stride(1, 1)
                    .build())
                // Squeeze-and-Excitation simulation - squeeze
                .layer(13, new DenseLayer.Builder()
                    .nOut(128)
                    .activation(Activation.RELU)
                    .build())
                // Squeeze-and-Excitation simulation - excitation
                .layer(14, new DenseLayer.Builder()
                    .nOut(512)
                    .activation(Activation.SIGMOID)
                    .build())
                // Dense layers for position evaluation
                .layer(15, new DenseLayer.Builder()
                    .nOut(1024)
                    .activation(Activation.RELU)
                    .dropOut(0.3)
                    .build())
                .layer(16, new DenseLayer.Builder()
                    .nOut(512)
                    .activation(Activation.RELU)
                    .dropOut(0.2)
                    .build())
                .layer(17, new DenseLayer.Builder()
                    .nOut(256)
                    .activation(Activation.RELU)
                    .build())
                .layer(18, new DenseLayer.Builder()
                    .nOut(128)
                    .activation(Activation.RELU)
                    .build())
                // Output layer - position score
                .layer(19, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                    .activation(Activation.TANH)
                    .nOut(1)
                    .build())
                .setInputType(InputType.convolutional(BOARD_SIZE, BOARD_SIZE, PIECE_CHANNELS))
                .build();
            
            network = new MultiLayerNetwork(conf);
            network.init();
            
            logger.info("CNN AI: Advanced CNN initialized - {} parameters (Multi-scale + SE + Depthwise)", network.numParams());
            
        } catch (Exception e) {
            logger.error("CNN AI: Network initialization failed - {}", e.getMessage());
            throw new RuntimeException("Failed to initialize CNN", e);
        }
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        
        try {
            // CRITICAL: Use centralized tactical defense system
            // Tactical defense now handled centrally in ChessGame.findBestMove()
            
            int[] bestMove = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            
            // Limit evaluation to prevent timeout
            int maxMoves = Math.min(validMoves.size(), 20);
            
            for (int i = 0; i < maxMoves; i++) {
                int[] move = validMoves.get(i);
                double score = evaluateMove(board, move);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
            
            return bestMove != null ? bestMove : validMoves.get(0);
        } catch (Exception e) {
            logger.debug("CNN AI: Move selection error - {}", e.getMessage());
            return validMoves.get(0); // Fallback to first valid move
        }
    }
    
    private double evaluateMove(String[][] board, int[] move) {
        INDArray input = encodeBoardToCNN(board);
        INDArray output = network.output(input);
        return output.getDouble(0);
    }
    
    private INDArray encodeBoardToCNN(String[][] board) {
        // Create 8x8x12 tensor: 12 channels for different piece types
        double[][][][] tensor = new double[1][PIECE_CHANNELS][BOARD_SIZE][BOARD_SIZE];
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String piece = board[row][col];
                if (!piece.isEmpty()) {
                    int channel = getPieceChannel(piece);
                    if (channel >= 0) {
                        tensor[0][channel][row][col] = 1.0;
                    }
                }
            }
        }
        
        return Nd4j.create(tensor);
    }
    
    private int getPieceChannel(String piece) {
        // Map pieces to channels: 0-5 white pieces, 6-11 black pieces
        return switch (piece) {
            case "♔" -> 0;  // White King
            case "♕" -> 1;  // White Queen
            case "♖" -> 2;  // White Rook
            case "♗" -> 3;  // White Bishop
            case "♘" -> 4;  // White Knight
            case "♙" -> 5;  // White Pawn
            case "♚" -> 6;  // Black King
            case "♛" -> 7;  // Black Queen
            case "♜" -> 8;  // Black Rook
            case "♝" -> 9;  // Black Bishop
            case "♞" -> 10; // Black Knight
            case "♟" -> 11; // Black Pawn
            default -> -1;
        };
    }
    
    public void startTraining() {
        if (isTraining.get()) {
            logger.warn("CNN AI: Training already in progress");
            return;
        }
        
        this.trainingThread = Thread.ofVirtual().name("CNN-Training-Thread").start(() -> {
            isTraining.set(true);
            logger.info("*** CNN AI: Training started (continuous), batch size: {} ***", BATCH_SIZE);
            
            while (isTraining.get()) {
                try {
                    
                    inputBatch.clear();
                    targetBatch.clear();
                    
                    // Use virtual board for training
                    for (int i = 0; i < BATCH_SIZE && isTraining.get(); i++) {
                        // Check stop flag every 5 iterations for faster response
                        if (i % 5 == 0 && !isTraining.get()) {
                            logger.info("*** CNN AI: STOP DETECTED during batch preparation - Exiting training loop ***");
                            break;
                        }
                        
                        INDArray input;
                        INDArray target;
                        
                        // Use game data if available
                        if (!trainingData.isEmpty() && Math.random() < 0.3) {
                            int randomIndex = (int)(Math.random() * trainingData.size());
                            TrainingData data = trainingData.stream().skip(randomIndex).findFirst().orElse(null);
                            if (data != null) {
                                input = encodeBoardToCNNOptimized(data.position());
                                target = createTargetOptimized(data.result());
                            } else {
                                VirtualChessBoard fallbackBoard = new VirtualChessBoard();
                                input = encodeBoardToCNNOptimized(fallbackBoard.getBoard());
                                target = createTargetOptimized(0.0);
                            }
                        } else if (qLearningAI != null) {
                            VirtualChessBoard virtualBoard = new VirtualChessBoard();
                            String[][] board = virtualBoard.getBoard();
                            List<int[]> validMoves = generateValidMoves(board);
                            
                            if (!validMoves.isEmpty()) {
                                int[] bestMove = qLearningAI.selectMove(board, validMoves, true);
                                if (bestMove != null) {
                                    input = encodeBoardToCNNOptimized(board);
                                    double moveScore = evaluateWithQLearning(board, bestMove);
                                    target = createTargetOptimized(moveScore);
                                } else {
                                    input = encodeBoardToCNNOptimized(board);
                                    target = createTargetOptimized(0.0);
                                }
                            } else {
                                VirtualChessBoard fallbackBoard = new VirtualChessBoard();
                                input = encodeBoardToCNNOptimized(fallbackBoard.getBoard());
                                target = createTargetOptimized(0.0);
                            }
                        } else {
                            VirtualChessBoard virtualBoard = new VirtualChessBoard();
                            String[][] board = virtualBoard.getBoard();
                            input = encodeBoardToCNNOptimized(board);
                            target = createTargetOptimized(evaluateBasicPosition(board));
                        }
                        
                        inputBatch.add(input);
                        targetBatch.add(target);
                    }
                    
                    if (!inputBatch.isEmpty() && isTraining.get()) {
                        INDArray batchInput = Nd4j.vstack(inputBatch.toArray(new INDArray[0]));
                        INDArray batchTarget = Nd4j.vstack(targetBatch.toArray(new INDArray[0]));
                        
                        DataSet dataSet = new DataSet(batchInput, batchTarget);
                        
                        // Check stop flag before expensive DL4J operation
                        if (!isTraining.get()) {
                            logger.info("*** CNN AI: STOP DETECTED before network.fit() - Exiting training loop ***");
                            break;
                        }
                        
                        network.fit(dataSet);
                        
                        // Check stop flag immediately after expensive DL4J operation
                        if (!isTraining.get()) {
                            logger.info("*** CNN AI: STOP DETECTED after network.fit() - Exiting training loop ***");
                            break;
                        }
                        
                        trainingIterations += BATCH_SIZE;
                    }
                    
                    if (trainingIterations % 5000 == 0) {
                        String method = (qLearningAI != null) ? "Q-Learning + CNN" : "CNN only";
                        trainingStatus = "CNN AI: " + trainingIterations + " iterations (" + method + ")";
                        logger.info("*** CNN AI: {} iterations completed ({}) ***", trainingIterations, method);
                        
                        // CNN AI trains silently
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    if ((trainingIterations % 5000 == 0 || (currentTime - lastSaveTime) > 300000) && isTraining.get()) {
                        // Synchronized save to prevent concurrent saves
                        synchronized (saveLock) {
                            if (!isSaving) {
                                isSaving = true;
                                Thread.ofVirtual().start(() -> {
                                    // Check stop flag before expensive save operation
                                    if (!isTraining.get()) {
                                        logger.info("*** CNN AI: Save cancelled - training stopped ***");
                                        isSaving = false;
                                        return;
                                    }
                                    try {
                                        saveModel();
                                        lastSaveTime = currentTime;
                                    } finally {
                                        isSaving = false;
                                    }
                                });
                            }
                        }
                        
                        // Add 1 second pause after every 5000 iterations
                        if (trainingIterations % 5000 == 0) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    
                    // Check stop flag after each batch
                    if (!isTraining.get()) {
                        logger.info("*** CNN AI: STOP DETECTED after batch processing - Exiting training loop ***");
                        break;
                    }
                    
                } catch (Exception e) {
                    logger.error("CNN AI: Training error - {}", e.getMessage());
                    break;
                }
            }
            
            isTraining.set(false);
            logger.info("*** CNN AI: Training stopped - {} iterations ***", trainingIterations);
            
            // Wait for any pending saves to complete, then do final save
            synchronized (saveLock) {
                while (isSaving) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                }
                saveModel();
                logger.info("*** CNN AI: Model saved after training stop ***");
            }
        });
    }
    
    public void stopTraining() {
        logger.info("*** CNN AI: STOP REQUEST RECEIVED ***");
        isTraining.set(false);
        
        // DL4J-specific: Wait for current fit() to complete
        if (trainingThread != null && trainingThread.isAlive()) {
            try {
                trainingThread.join(30000); // Wait max 30 seconds
                logger.info("*** CNN AI: Training thread stopped gracefully ***");
            } catch (InterruptedException e) {
                trainingThread.interrupt();
                logger.warn("*** CNN AI: Training thread interrupted ***");
            }
        }
    }
    
    private INDArray encodeBoardToCNNOptimized(String[][] board) {
        reusableInput.assign(0);
        
        // Apply data augmentation during training (50% chance)
        String[][] processedBoard = board;
        if (isTraining.get() && Math.random() < 0.5) {
            processedBoard = applyDataAugmentation(board);
        }
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String piece = processedBoard[row][col];
                if (!piece.isEmpty()) {
                    int channel = getPieceChannel(piece);
                    if (channel >= 0) {
                        reusableInput.putScalar(0, channel, row, col, 1.0);
                    }
                }
            }
        }
        
        return reusableInput.dup();
    }
    
    private String[][] applyDataAugmentation(String[][] board) {
        double rand = Math.random();
        if (rand < 0.25) {
            return flipBoardHorizontally(board);
        } else if (rand < 0.5) {
            return rotateBoardClockwise(board);
        } else if (rand < 0.75) {
            return rotateBoardCounterClockwise(board);
        }
        return board; // No augmentation
    }
    
    private String[][] flipBoardHorizontally(String[][] board) {
        String[][] flipped = new String[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                flipped[row][7 - col] = board[row][col];
            }
        }
        return flipped;
    }
    
    private String[][] rotateBoardClockwise(String[][] board) {
        String[][] rotated = new String[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                rotated[col][7 - row] = board[row][col];
            }
        }
        return rotated;
    }
    
    private String[][] rotateBoardCounterClockwise(String[][] board) {
        String[][] rotated = new String[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                rotated[7 - col][row] = board[row][col];
            }
        }
        return rotated;
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
            
            return "CNN Backend: " + backendName + gpuType;
        } catch (Exception e) {
            return "CNN Backend: Unknown";
        }
    }
    
    public int getTrainingIterations() {
        return trainingIterations;
    }
    
    public void resetTrainingIterations() {
        trainingIterations = 0;
        trainingStatus = "Training reset";
    }
    
    private boolean loadModel() {
        // Phase 3: Dual-path implementation with enhanced error handling
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: CNN loading model using NIO.2 async LOAD path ***");
                Object data = ioWrapper.loadAIData("CNN", MODEL_FILE);
                if (data instanceof MultiLayerNetwork) {
                    MultiLayerNetwork loadedNetwork = (MultiLayerNetwork) data;
                    if (validateAdvancedCNNArchitecture(loadedNetwork)) {
                        network = loadedNetwork;
                        loadTrainingIterations();
                        logger.info("*** CNN AI: Model loaded using NIO.2 ({} params, {} iterations) ***", 
                            network.numParams(), trainingIterations);
                        return true;
                    } else {
                        logger.warn("*** CNN AI: Async loaded model incompatible with advanced architecture ***");
                    }
                } else if (data != null) {
                    logger.warn("*** CNN AI: Async loaded data is not MultiLayerNetwork: {} ***", data.getClass().getSimpleName());
                }
            } catch (Exception e) {
                logger.warn("*** CNN AI: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Enhanced synchronous loading with corruption detection
        try {
            File modelFile = new File(MODEL_FILE);
            if (modelFile.exists()) {
                // Check file size first
                if (modelFile.length() < 1000) {
                    logger.warn("CNN AI: Model file suspiciously small ({} bytes) - possible corruption", modelFile.length());
                    handleCorruptedModel();
                    return false;
                }
                
                logger.info("CNN AI: Loading model from file ({} bytes)", modelFile.length());
                MultiLayerNetwork loadedNetwork = ModelSerializer.restoreMultiLayerNetwork(modelFile);
                
                if (loadedNetwork == null) {
                    logger.error("CNN AI: Loaded network is null - file corrupted");
                    handleCorruptedModel();
                    return false;
                }
                
                if (loadedNetwork.numParams() == 0) {
                    logger.error("CNN AI: Loaded network has 0 parameters - file corrupted");
                    handleCorruptedModel();
                    return false;
                }
                
                if (validateAdvancedCNNArchitecture(loadedNetwork)) {
                    network = loadedNetwork;
                    loadTrainingIterations();
                    logger.info("CNN AI: Advanced CNN model loaded successfully ({} params, {} iterations)", 
                        network.numParams(), trainingIterations);
                    return true;
                } else {
                    logger.warn("CNN AI: Loaded model incompatible with advanced architecture - will recreate");
                    return false;
                }
            } else {
                logger.info("CNN AI: No existing model file found");
            }
        } catch (Exception e) {
            logger.error("CNN AI: Model loading failed - {}", e.getMessage());
            handleCorruptedModel();
        }
        return false;
    }
    
    private void handleCorruptedModel() {
        try {
            File modelFile = new File(MODEL_FILE);
            if (modelFile.exists()) {
                File backupFile = new File(MODEL_FILE + ".corrupted." + System.currentTimeMillis());
                if (modelFile.renameTo(backupFile)) {
                    logger.info("CNN AI: Corrupted model backed up to {}", backupFile.getName());
                } else {
                    modelFile.delete();
                    logger.info("CNN AI: Corrupted model file deleted");
                }
            }
        } catch (Exception e) {
            logger.error("CNN AI: Failed to handle corrupted model: {}", e.getMessage());
        }
    }
    
    public void saveModel() {
        synchronized (saveLock) {
            try {
                // Check memory before save
                Runtime runtime = Runtime.getRuntime();
                long freeMemory = runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                long usedMemory = runtime.totalMemory() - freeMemory;
                
                if ((maxMemory - usedMemory) < (150 * 1024 * 1024)) { // 150MB threshold
                    logger.warn("CNN AI: Low memory before save - forcing GC");
                    System.gc();
                    Thread.sleep(500);
                }
                
                // Phase 3: Dual-path implementation with enhanced error handling
                boolean asyncSaveSuccessful = false;
                if (ioWrapper.isAsyncEnabled()) {
                    try {
                        logger.debug("CNN AI: Using async save path");
                        ioWrapper.saveAIData("CNN", network, MODEL_FILE);
                        saveTrainingIterations();
                        asyncSaveSuccessful = true;
                        logger.info("CNN AI: Model saved via async I/O");
                    } catch (Exception asyncEx) {
                        logger.warn("CNN AI: Async save failed, falling back to sync - {}", asyncEx.getMessage());
                    }
                }
                
                if (!asyncSaveSuccessful) {
                    // Enhanced synchronous save with atomic operations
                    logger.debug("CNN AI: Using synchronous save path");
                    String tempFile = MODEL_FILE + ".tmp";
                    
                    try {
                        // Save to temporary file first
                        ModelSerializer.writeModel(network, tempFile, true);
                        
                        // Verify saved file
                        File temp = new File(tempFile);
                        if (temp.length() < 1000) {
                            throw new RuntimeException("Saved model file too small: " + temp.length() + " bytes");
                        }
                        
                        // Quick verification load
                        try {
                            MultiLayerNetwork testLoad = ModelSerializer.restoreMultiLayerNetwork(temp);
                            if (testLoad == null || testLoad.numParams() == 0) {
                                throw new RuntimeException("Saved model verification failed");
                            }
                        } catch (Exception verifyEx) {
                            throw new RuntimeException("Model verification failed: " + verifyEx.getMessage());
                        }
                        
                        // Atomic move to final location
                        File finalFile = new File(MODEL_FILE);
                        if (finalFile.exists()) {
                            finalFile.delete();
                        }
                        if (!temp.renameTo(finalFile)) {
                            throw new RuntimeException("Failed to move temporary file to final location");
                        }
                        
                        saveTrainingIterations();
                        logger.info("CNN AI: Model saved with verification ({} bytes, {} iterations)", 
                            finalFile.length(), trainingIterations);
                        
                    } catch (Exception saveEx) {
                        // Clean up temporary file
                        new File(tempFile).delete();
                        throw saveEx;
                    }
                }
                
            } catch (Exception e) {
                logger.error("CNN AI: Failed to save model - {}", e.getMessage());
                
                // If this is during training, don't let save failures stop training
                if (isTraining.get()) {
                    logger.warn("CNN AI: Continuing training despite save failure");
                } else {
                    // If not training, this is a critical error
                    throw new RuntimeException("CNN model save failed", e);
                }
            }
        }
    }
    
    public void stopThinking() {
        // CNN doesn't have async thinking threads like MCTS/AlphaZero
        // This is a no-op for compatibility
    }
    
    public void shutdown() {
        logger.info("CNN AI: Initiating graceful shutdown...");
        
        if (isTraining.get()) {
            stopTraining();
        }
        
        saveModel();
        logger.info("CNN AI: Shutdown complete - Model saved");
    }
    
    public boolean deleteModel() {
        logger.info("CNN AI: Attempting to delete model file: {}", MODEL_FILE);
        
        try {
            if (isTraining.get()) {
                stopTraining();
            }
            
            File modelFile = new File(MODEL_FILE);
            
            if (modelFile.exists()) {
                boolean deleted = modelFile.delete();
                
                if (deleted) {
                    trainingIterations = 0;
                    trainingStatus = "CNN model deleted - ready for fresh training";
                    trainingData.clear();
                    initializeNetwork();
                    logger.info("CNN AI: Model file deleted and network reinitialized");
                    return true;
                } else {
                    logger.error("CNN AI: Failed to delete model file - file may be locked");
                    return false;
                }
            } else {
                trainingIterations = 0;
                trainingStatus = "No CNN model file found - ready for fresh training";
                trainingData.clear();
                initializeNetwork();
                return true;
            }
        } catch (Exception e) {
            logger.error("CNN AI: Error deleting model - {}", e.getMessage());
            return false;
        }
    }
    
    // AI vs User game learning
    public void addGameData(String[][] position, double result) {
        synchronized (trainingData) {
            trainingData.add(new TrainingData(copyBoard(position), result, System.currentTimeMillis()));
            
            // Keep only last 1000 positions to prevent memory issues
            if (trainingData.size() > 1000) {
                trainingData.removeFirst();
            }
        }
        logger.debug("CNN AI: Added game position, total: {}", trainingData.size());
    }
    
    private String[][] copyBoard(String[][] original) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    

    
    // Helper methods from original DeepLearningAI
    private String[][] generateRandomChessPosition() {
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        String[][] board = virtualBoard.getBoard();
        
        if (openingBook != null) {
            try {
                List<int[]> validMoves = generateValidMoves(board);
                if (!validMoves.isEmpty()) {
                    LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), true);
                    if (openingResult != null) {
                        int[] move = openingResult.move;
                        String piece = board[move[0]][move[1]];
                        board[move[2]][move[3]] = piece;
                        board[move[0]][move[1]] = "";
                    }
                }
            } catch (Exception e) {
                // Fallback to basic position
            }
        }
        
        return board;
    }
    
    private List<int[]> generateValidMoves(String[][] board) {
        // AI vs User: Use ChessGame's ChessRuleValidator
        // AI vs AI Training: Use VirtualChessBoard's ChessRuleValidator
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
        return virtualBoard.getAllValidMoves(virtualBoard.isWhiteTurn());
    }
    
    private double evaluateWithQLearning(String[][] board, int[] move) {
        // Enhanced: Use actual Q-table lookups when available
        if (qLearningAI != null) {
            try {
                String boardState = qLearningAI.encodeBoardStatePublic(board);
                String stateAction = boardState + ":" + Arrays.toString(move);
                Double qValue = qLearningAI.getQValue(stateAction);
                if (qValue != null) {
                    return qValue; // Use actual Q-value
                }
            } catch (Exception e) {
                // Fallback to basic evaluation
            }
        }
        
        // Fallback: Basic chess evaluation
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        boolean isWhite = "♔♕♖♗♘♙".contains(piece);
        
        double value = 0.0;
        if (wouldExposeKing(board, move, isWhite)) value -= 2.0;
        if (!captured.isEmpty()) {
            boolean capturingOpponent = isWhite ? "♚♛♜♝♞♟".contains(captured) : "♔♕♖♗♘♙".contains(captured);
            if (capturingOpponent) {
                value += switch (captured) {
                    case "♕", "♛" -> 0.9;
                    case "♘", "♞" -> 0.7;
                    case "♗", "♝" -> 0.5;
                    case "♖", "♜" -> 0.3;
                    case "♙", "♟" -> 0.1;
                    default -> 0.0;
                };
            }
        }
        
        return Math.tanh(value);
    }
    
    private double evaluateBasicPosition(String[][] board) {
        double score = 0.0;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    score += switch (piece) {
                        case "♔" -> 10.0;  // White King
                        case "♕" -> 9.0;   // White Queen
                        case "♖" -> 5.0;   // White Rook
                        case "♗", "♘" -> 3.0;   // White Bishop, Knight
                        case "♙" -> 1.0;   // White Pawn
                        case "♚" -> -10.0; // Black King
                        case "♛" -> -9.0;  // Black Queen
                        case "♜" -> -5.0;  // Black Rook
                        case "♝", "♞" -> -3.0;  // Black Bishop, Knight
                        case "♟" -> -1.0;  // Black Pawn
                        default -> 0.0;
                    };
                }
            }
        }
        
        return Math.tanh(score / 50.0);
    }
    
    private boolean wouldExposeKing(String[][] board, int[] move, boolean isWhite) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        // Create temporary board to simulate move
        String[][] tempBoard = copyBoard(board);
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = "";
        
        boolean exposed = isKingInCheck(tempBoard, isWhite ? "♔" : "♚", isWhite);
        
        return exposed;
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
    
    // Additional methods for integration
    public String getModelFilePath() {
        return new File(MODEL_FILE).getAbsolutePath();
    }
    
    public boolean modelFileExists() {
        return new File(MODEL_FILE).exists();
    }
    
    public void saveModelNow() {
        saveModel();
    }
    
    public int getGameDataSize() {
        return trainingData.size();
    }
    
    private void saveTrainingIterations() {
        try {
            java.io.File iterFile = new java.io.File("state/chess_cnn_iterations.dat");
            try (java.io.FileWriter writer = new java.io.FileWriter(iterFile)) {
                writer.write(String.valueOf(trainingIterations));
            }
        } catch (Exception e) {
            logger.debug("CNN AI: Failed to save training iterations - {}", e.getMessage());
        }
    }
    
    private void loadTrainingIterations() {
        try {
            java.io.File iterFile = new java.io.File("state/chess_cnn_iterations.dat");
            if (iterFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(iterFile))) {
                    String line = reader.readLine();
                    if (line != null) {
                        trainingIterations = Integer.parseInt(line.trim());
                        logger.info("CNN AI: Loaded training iterations: {}", trainingIterations);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("CNN AI: Failed to load training iterations - {}", e.getMessage());
            trainingIterations = 0;
        }
    }
    
    private boolean validateAdvancedCNNArchitecture(MultiLayerNetwork loadedNetwork) {
        try {
            int numLayers = loadedNetwork.getnLayers();
            long numParams = loadedNetwork.numParams();
            
            // Advanced CNN should have 20 layers and significantly more parameters
            if (numLayers < 20 || numParams < 1000000) {
                logger.warn("CNN AI: Basic model architecture (layers: {}, params: {}), creating advanced CNN", numLayers, numParams);
                return false;
            }
            
            // Validate CNN input type
            if (loadedNetwork.getLayer(0).conf().getLayer() instanceof org.deeplearning4j.nn.conf.layers.ConvolutionLayer) {
                org.deeplearning4j.nn.conf.layers.ConvolutionLayer firstLayer = 
                    (org.deeplearning4j.nn.conf.layers.ConvolutionLayer) loadedNetwork.getLayer(0).conf().getLayer();
                long inputChannels = firstLayer.getNIn();
                if (inputChannels != PIECE_CHANNELS) {
                    logger.warn("CNN AI: Incompatible model (channels: {}), creating new network", inputChannels);
                    return false;
                }
            }
            
            logger.info("CNN AI: Advanced CNN architecture validated - {} layers, {} parameters", numLayers, numParams);
            return true;
        } catch (Exception e) {
            logger.warn("CNN AI: Cannot validate advanced CNN architecture, creating new network");
            return false;
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
    
    private INDArray boardToTensor(String[][] board) {
        return encodeBoardToCNN(board);
    }
    
    private void trainOnBatch() {
        if (inputBatch.isEmpty() || targetBatch.isEmpty()) return;
        
        try {
            // Ensure all inputs have consistent data type (FLOAT)
            List<INDArray> consistentInputs = new ArrayList<>();
            List<INDArray> consistentTargets = new ArrayList<>();
            
            for (INDArray input : inputBatch) {
                consistentInputs.add(input.castTo(org.nd4j.linalg.api.buffer.DataType.FLOAT));
            }
            
            for (INDArray target : targetBatch) {
                consistentTargets.add(target.castTo(org.nd4j.linalg.api.buffer.DataType.FLOAT));
            }
            
            INDArray batchInput = Nd4j.vstack(consistentInputs.toArray(new INDArray[0]));
            INDArray batchTarget = Nd4j.vstack(consistentTargets.toArray(new INDArray[0]));
            
            DataSet dataSet = new DataSet(batchInput, batchTarget);
            network.fit(dataSet);
            
            trainingIterations += inputBatch.size();
            
            inputBatch.clear();
            targetBatch.clear();
            
            logger.debug("CNN AI: Trained on batch, total iterations: {}", trainingIterations);
        } catch (Exception e) {
            logger.error("CNN AI: Batch training error - {}", e.getMessage());
            inputBatch.clear();
            targetBatch.clear();
        }
    }
}