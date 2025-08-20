# CNN Deep Learning AI Documentation

## Overview
CNN Deep Learning AI uses Convolutional Neural Networks for spatial pattern recognition in chess. It treats the chess board as an 8x8x12 image with piece-type channels, enabling better understanding of positional patterns and tactical motifs through convolutional layers.

## How It Works in Chess

### Core Architecture
- **Convolutional Layers**: 3 conv layers for pattern detection
- **Piece Channels**: 12 channels representing different piece types
- **Spatial Recognition**: Understands piece relationships and formations
- **GPU Acceleration**: OpenCL/CUDA support for training

### Key Features
1. **8x8x12 Tensor Input**: 12 channels for piece types (6 white + 6 black)
2. **Convolutional Pattern Recognition**: Detects tactical and positional patterns
3. **Spatial Understanding**: Recognizes piece formations and structures
4. **Game Data Learning**: Learns from AI vs User game outcomes

## Code Implementation

### Main Class Structure
```java
public class DeepLearningCNNAI {
    private MultiLayerNetwork network;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
    private QLearningAI qLearningAI;
    private LeelaChessZeroOpeningBook openingBook;
    private ChessController controller;
    
    // CNN input dimensions: 8x8 board with 12 piece type channels
    private static final int BOARD_SIZE = 8;
    private static final int PIECE_CHANNELS = 12;
    private static final int BATCH_SIZE = 64;
}
```

### CNN Architecture
```java
private void initializeNetwork() {
    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
        .seed(123)
        .weightInit(WeightInit.XAVIER)
        .updater(new Adam(0.001))
        .list()
        // First convolutional layer - detect basic piece patterns
        .layer(0, new ConvolutionLayer.Builder(3, 3)
            .nIn(PIECE_CHANNELS)
            .nOut(32)
            .stride(1, 1)
            .padding(1, 1)
            .activation(Activation.RELU)
            .build())
        // Second convolutional layer - detect tactical patterns
        .layer(1, new ConvolutionLayer.Builder(3, 3)
            .nOut(64)
            .stride(1, 1)
            .padding(1, 1)
            .activation(Activation.RELU)
            .build())
        // Third convolutional layer - detect complex formations
        .layer(2, new ConvolutionLayer.Builder(3, 3)
            .nOut(128)
            .stride(1, 1)
            .padding(1, 1)
            .activation(Activation.RELU)
            .build())
        // Pooling layer - reduce spatial dimensions
        .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
            .kernelSize(2, 2)
            .stride(2, 2)
            .build())
        // Dense layer for position evaluation
        .layer(4, new DenseLayer.Builder()
            .nOut(256)
            .activation(Activation.RELU)
            .build())
        // Output layer - position score
        .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
            .activation(Activation.IDENTITY)
            .nOut(1)
            .build())
        .setInputType(InputType.convolutional(BOARD_SIZE, BOARD_SIZE, PIECE_CHANNELS))
        .build();
    
    network = new MultiLayerNetwork(conf);
    network.init();
}
```

### Board Encoding to CNN Tensor
```java
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
```

## Chess Strategy

### Spatial Pattern Recognition
The CNN architecture is specifically designed to recognize chess patterns:

1. **First Conv Layer (3x3, 32 filters)**: Detects basic piece relationships
   - Piece attacks and defenses
   - Adjacent piece formations
   - Basic tactical motifs

2. **Second Conv Layer (3x3, 64 filters)**: Identifies tactical patterns
   - Forks, pins, skewers
   - Piece coordination
   - Weak squares and holes

3. **Third Conv Layer (3x3, 128 filters)**: Recognizes complex formations
   - Pawn structures
   - King safety patterns
   - Piece activity and mobility

### Move Evaluation
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    
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
    INDArray input = encodeBoardToCNN(board);
    INDArray output = network.output(input);
    return output.getDouble(0);
}
```

## Training Process

### Continuous Training with Multiple Data Sources
```java
public void startTraining() {
    trainingThread = Thread.ofVirtual().name("CNN-Training-Thread").start(() -> {
        isTraining.set(true);
        
        while (isTraining.get()) {
            inputBatch.clear();
            targetBatch.clear();
            
            for (int i = 0; i < BATCH_SIZE && isTraining.get(); i++) {
                INDArray input;
                INDArray target;
                
                // Use game data if available (30% probability)
                if (!trainingData.isEmpty() && Math.random() < 0.3) {
                    int randomIndex = (int)(Math.random() * trainingData.size());
                    TrainingData data = trainingData.stream().skip(randomIndex).findFirst().orElse(null);
                    if (data != null) {
                        input = encodeBoardToCNNOptimized(data.position());
                        target = createTargetOptimized(data.result());
                    }
                } else if (qLearningAI != null) {
                    // Learn from Q-Learning AI (70% probability)
                    VirtualChessBoard virtualBoard = new VirtualChessBoard();
                    String[][] board = virtualBoard.getBoard();
                    List<int[]> validMoves = generateValidMoves(board);
                    
                    if (!validMoves.isEmpty()) {
                        int[] bestMove = qLearningAI.selectMove(board, validMoves, true);
                        if (bestMove != null) {
                            input = encodeBoardToCNNOptimized(board);
                            double moveScore = evaluateWithQLearning(board, bestMove);
                            target = createTargetOptimized(moveScore);
                        }
                    }
                }
                
                inputBatch.add(input);
                targetBatch.add(target);
            }
            
            // Train on batch
            if (!inputBatch.isEmpty()) {
                INDArray batchInput = Nd4j.vstack(inputBatch.toArray(new INDArray[0]));
                INDArray batchTarget = Nd4j.vstack(targetBatch.toArray(new INDArray[0]));
                
                DataSet dataSet = new DataSet(batchInput, batchTarget);
                network.fit(dataSet);
                
                trainingIterations += BATCH_SIZE;
            }
            
            if (trainingIterations % 1000 == 0) {
                String method = (qLearningAI != null) ? "Q-Learning + CNN" : "CNN only";
                logger.info("*** CNN AI: {} iterations completed ({}) ***", trainingIterations, method);
            }
        }
    });
}
```

### AI vs User Game Learning
```java
public record TrainingData(String[][] position, double result, long timestamp) {}

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
```

## Performance Characteristics

### Strengths
- **Spatial Understanding**: Excellent at recognizing positional patterns
- **Pattern Recognition**: Identifies tactical motifs effectively
- **Memory Efficiency**: Compact representation of chess knowledge
- **GPU Acceleration**: Fast training with proper hardware

### Considerations
- **Training Data**: Requires diverse positions for good generalization
- **Computational Cost**: More expensive than simple neural networks
- **Memory Usage**: Larger model size due to convolutional layers
- **Overfitting Risk**: May memorize specific positions

## GPU Support and Optimization

### GPU Configuration
```java
private void configureGPU() {
    try {
        OpenCLDetector.detectAndConfigureOpenCL();
        String backendName = Nd4j.getBackend().getClass().getSimpleName();
        
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
```

### Memory Optimization
```java
// Reusable arrays for memory optimization
private INDArray reusableInput = Nd4j.zeros(1, PIECE_CHANNELS, BOARD_SIZE, BOARD_SIZE);
private INDArray reusableTarget = Nd4j.zeros(1, 1);

private INDArray encodeBoardToCNNOptimized(String[][] board) {
    reusableInput.assign(0);
    
    for (int row = 0; row < 8; row++) {
        for (int col = 0; col < 8; col++) {
            String piece = board[row][col];
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
```

## Integration Features

### WebSocket Integration
```java
public void setController(ChessController controller) {
    this.controller = controller;
    logger.info("CNN AI: Connected to WebSocket controller");
}

private void broadcastTrainingProgress() {
    try {
        if (controller != null) {
            String progressMessage = String.format("CNN Training: %d iterations completed", trainingIterations);
            // Integration with existing WebSocket system
        }
    } catch (Exception e) {
        logger.debug("CNN AI: Error broadcasting progress - {}", e.getMessage());
    }
}
```

### Model Persistence
- **Automatic Saving**: Saves model every 5000 iterations
- **Corruption Handling**: Backup and recovery for corrupted models
- **Atomic Writes**: Safe model saving with temporary files

## Configuration

### Network Architecture
- **Input**: 8x8x12 (board size × piece channels)
- **Conv Layers**: 3 layers with 32, 64, 128 filters
- **Kernel Size**: 3x3 for all convolutional layers
- **Pooling**: 2x2 max pooling after convolutions
- **Dense**: 256-unit dense layer before output

### Training Parameters
- **Batch Size**: 64 (optimized for memory/performance)
- **Learning Rate**: 0.001 (Adam optimizer)
- **Data Sources**: Game data (30%) + Q-Learning (70%)
- **Save Frequency**: Every 5000 iterations

## Usage Examples

### Basic Setup
```java
DeepLearningCNNAI cnnAI = new DeepLearningCNNAI(true);
cnnAI.setQLearningAI(qLearningAI);
cnnAI.setController(webSocketController);
```

### Training
```java
cnnAI.startTraining();
String status = cnnAI.getTrainingStatus();
int iterations = cnnAI.getTrainingIterations();
```

### Game Data Integration
```java
// After each game
cnnAI.addGameData(finalPosition, gameResult);
```

## Technical Details

### Records for Data Structures
```java
public record ChessPosition(int row, int col) {}
public record ChessMove(ChessPosition from, ChessPosition to, String piece, String captured) {}
public record TrainingData(String[][] position, double result, long timestamp) {}
```

### Error Handling
- **Model Corruption**: Automatic backup and recovery
- **GPU Failures**: Graceful fallback to CPU
- **Memory Issues**: Automatic garbage collection and cleanup
- **Training Interruption**: Safe shutdown with state preservation

This CNN implementation provides advanced spatial pattern recognition for chess, leveraging convolutional neural networks to understand positional concepts and tactical patterns more effectively than traditional neural networks.