# Deep Learning AI Documentation

## Overview
Deep Learning AI uses neural networks to evaluate chess positions and select moves. It features GPU acceleration support, batch training optimization, and real-time progress monitoring. The system learns from Q-Learning AI and random positions to develop chess understanding.

## How It Works in Chess

### Core Architecture
- **Neural Network**: 3-layer dense network for position evaluation
- **GPU Acceleration**: OpenCL/CUDA support for faster training
- **Batch Processing**: Efficient training with configurable batch sizes
- **Knowledge Transfer**: Learns from Q-Learning AI's expertise

### Key Features
1. **Position Evaluation**: Converts 8x8 board to 64-dimensional vector
2. **GPU Support**: Automatic detection and configuration of AMD/NVIDIA GPUs
3. **Continuous Training**: Real-time learning with progress monitoring
4. **Memory Optimization**: Efficient array reuse and batch processing

## Code Implementation

### Main Class Structure
```java
public class DeepLearningAI {
    private MultiLayerNetwork network;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
    private Thread trainingThread;
    private QLearningAI qLearningAI; // Reference for knowledge transfer
    private LeelaChessZeroOpeningBook openingBook;
    
    // Batch training optimization
    private static final int BATCH_SIZE = 128;
    private List<INDArray> inputBatch = new ArrayList<>();
    private List<INDArray> targetBatch = new ArrayList<>();
}
```

### Neural Network Architecture
```java
private void initializeNetwork() {
    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
        .seed(123)
        .weightInit(WeightInit.XAVIER)
        .updater(new Adam(0.01)) // Increased learning rate
        .list()
        .layer(0, new DenseLayer.Builder().nIn(64).nOut(128)
            .activation(Activation.RELU).build())
        .layer(1, new DenseLayer.Builder().nIn(128).nOut(64)
            .activation(Activation.RELU).build())
        .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
            .activation(Activation.IDENTITY).nIn(64).nOut(1).build())
        .build();
    
    network = new MultiLayerNetwork(conf);
    network.init();
}
```

### Move Selection Process
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
    INDArray input = encodeBoardToVector(board);
    INDArray output = network.output(input);
    return output.getDouble(0);
}
```

## Chess Strategy

### Position Encoding
```java
private INDArray encodeBoardToVector(String[][] board) {
    double[] vector = new double[64];
    int index = 0;
    
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            String piece = board[i][j];
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
            index++;
        }
    }
    
    return Nd4j.create(vector).reshape(1, 64);
}
```

### Learning from Q-Learning AI
```java
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
            value += switch (captured) {
                case "♕", "♛" -> 0.9; // Queen
                case "♘", "♞" -> 0.7; // Knight
                case "♗", "♝" -> 0.5; // Bishop
                case "♖", "♜" -> 0.3; // Rook
                case "♙", "♟" -> 0.1; // Pawn
                default -> 0.0;
            };
        }
    }
    
    return Math.tanh(value);
}
```

## Training Process

### Continuous Training Loop
```java
public void startTraining() {
    if (isTraining.get()) return;
    
    trainingThread = Thread.ofVirtual().name("DeepLearning-Training").start(() -> {
        isTraining.set(true);
        
        while (isTraining.get()) {
            // Build batch
            inputBatch.clear();
            targetBatch.clear();
            
            for (int i = 0; i < BATCH_SIZE && isTraining.get(); i++) {
                INDArray input;
                INDArray target;
                
                if (qLearningAI != null) {
                    // Generate real chess position
                    String[][] board = generateRandomChessPosition();
                    List<int[]> validMoves = generateValidMoves(board);
                    
                    if (!validMoves.isEmpty()) {
                        int[] bestMove = qLearningAI.selectMove(board, validMoves, true);
                        if (bestMove != null) {
                            input = encodeBoardToVectorOptimized(board);
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
        }
    });
}
```

### GPU Configuration
```java
private void configureGPU() {
    try {
        // Detect and configure OpenCL for AMD GPU
        OpenCLDetector.detectAndConfigureOpenCL();
        
        String backendName = Nd4j.getBackend().getClass().getSimpleName();
        logger.info("Deep Learning: Current backend: {}", backendName);
        
        if (OpenCLDetector.isOpenCLAvailable()) {
            logger.info("Deep Learning: AMD GPU (OpenCL) detected - enabling GPU acceleration");
        } else if (isCudaAvailable()) {
            logger.info("Deep Learning: NVIDIA GPU (CUDA) detected - enabling GPU acceleration");
        } else {
            logger.info("Deep Learning: No GPU detected - using CPU backend");
        }
        
    } catch (Exception e) {
        logger.error("Deep Learning: GPU configuration failed - {}", e.getMessage());
        logger.info("Deep Learning: Falling back to CPU backend");
    }
}
```

## Performance Characteristics

### Strengths
- **Fast Evaluation**: Quick position assessment once trained
- **GPU Acceleration**: Significant speedup with proper hardware
- **Continuous Learning**: Improves during gameplay
- **Memory Efficient**: Optimized array reuse

### Considerations
- **Training Time**: Requires extensive training for good performance
- **GPU Dependency**: Best performance requires GPU acceleration
- **Overfitting Risk**: May memorize positions rather than learn patterns
- **Limited Tactical Depth**: Single evaluation vs tree search

## GPU Support

### OpenCL (AMD GPUs)
- **Automatic Detection**: Detects AMD GPUs and configures OpenCL
- **Memory Management**: Efficient GPU memory usage
- **Fallback**: Graceful fallback to CPU if GPU unavailable

### CUDA (NVIDIA GPUs)
- **CUDA Support**: Automatic NVIDIA GPU detection
- **Performance**: Optimized for NVIDIA hardware
- **Configuration**: Automatic setup and configuration

### CPU Fallback
- **Compatibility**: Works on any system without GPU
- **Optimization**: Uses available CPU cores efficiently
- **Memory**: Optimized for system RAM usage

## Integration Features

### Knowledge Transfer
```java
public void setQLearningAI(QLearningAI qLearningAI) {
    this.qLearningAI = qLearningAI;
    logger.info("Deep Learning: Connected to Q-Learning for knowledge transfer");
}
```

### Model Persistence
```java
public void saveModel() {
    try {
        File tempFile = new File(MODEL_FILE + ".tmp");
        ModelSerializer.writeModel(network, tempFile, true);
        
        File originalFile = new File(MODEL_FILE);
        if (originalFile.exists()) {
            originalFile.delete();
        }
        
        if (tempFile.renameTo(originalFile)) {
            System.out.println("Deep Learning model saved (" + originalFile.length() + " bytes)");
        }
    } catch (Exception e) {
        System.err.println("Failed to save Deep Learning model: " + e.getMessage());
    }
}
```

## Configuration

### Training Parameters
- **Batch Size**: 128 (configurable for memory/performance balance)
- **Learning Rate**: 0.01 (increased for faster learning)
- **Architecture**: 64→128→64→1 (input→hidden→hidden→output)
- **Activation**: ReLU for hidden layers, Identity for output

### Performance Tuning
- **Memory Optimization**: Reusable arrays for batch processing
- **Save Frequency**: Automatic model saving every 10,000 iterations
- **Thread Management**: Virtual threads for efficient concurrency

## Usage Examples

### Basic Setup
```java
DeepLearningAI ai = new DeepLearningAI(true);
ai.setQLearningAI(qLearningAI);
```

### Training
```java
ai.startTraining();
String status = ai.getTrainingStatus();
int iterations = ai.getTrainingIterations();
```

### Move Selection
```java
int[] move = ai.selectMove(board, validMoves);
```

## Technical Details

### Records for Data Structures
```java
public record TrainingBatch(SequencedCollection<INDArray> inputs, SequencedCollection<INDArray> targets, int size) {}
public record PositionEvaluation(double materialScore, double kingSafety, double mobility) {}
public record GPUInfo(String backend, boolean isGPU, String type) {}
```

### Memory Optimization
- **Reusable Arrays**: Pre-allocated arrays for common operations
- **Batch Processing**: Efficient batch training to reduce overhead
- **Garbage Collection**: Minimal object creation during training

### Error Handling
- **Model Corruption**: Automatic backup and recovery
- **GPU Failures**: Graceful fallback to CPU
- **Training Interruption**: Safe shutdown with model saving

This Deep Learning AI provides a modern neural network approach to chess, leveraging GPU acceleration and knowledge transfer for effective learning and play.