# Deep Q-Network (DQN) AI Documentation

## Overview
Deep Q-Network AI combines deep learning with Q-learning for stable reinforcement learning. It features experience replay buffer, target network for stability, and integration with the Leela Chess Zero opening book. The system learns through self-play and experience sharing with other AI systems.

## How It Works in Chess

### Core Architecture
- **Q-Network**: Main neural network for Q-value estimation
- **Target Network**: Stable network for target Q-value calculation
- **Experience Replay**: Buffer storing past experiences for training
- **Opening Book Integration**: Uses Lc0 opening database for training diversity

### Key Features
1. **Experience Replay**: Learns from stored experiences to break correlation
2. **Target Network**: Separate network updated periodically for stability
3. **Epsilon-Greedy**: Balances exploration vs exploitation
4. **GPU Acceleration**: OpenCL support for faster training

## Code Implementation

### Main Class Structure
```java
public class DeepQNetworkAI {
    private MultiLayerNetwork qNetwork;
    private MultiLayerNetwork targetNetwork;
    private ExperienceReplay replayBuffer;
    private Random random = new Random();
    private LeelaChessZeroOpeningBook openingBook;
    
    private double epsilon = 0.1;
    private double gamma = 0.95;
    private int targetUpdateFreq = 1000;
    private int trainingSteps = 0;
    private AtomicBoolean isTraining = new AtomicBoolean(false);
}
```

### Network Architecture
```java
private void initializeNetworks() {
    MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
        .seed(123)
        .weightInit(WeightInit.XAVIER)
        .updater(new Adam(0.001))
        .list()
        .layer(0, new DenseLayer.Builder().nIn(64).nOut(256)
            .activation(Activation.RELU).build())
        .layer(1, new DenseLayer.Builder().nIn(256).nOut(128)
            .activation(Activation.RELU).build())
        .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
            .activation(Activation.IDENTITY).nIn(128).nOut(1).build())
        .build();
    
    qNetwork = new MultiLayerNetwork(conf);
    qNetwork.init();
    targetNetwork = qNetwork.clone();
}
```

### Move Selection with Epsilon-Greedy
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    
    if (random.nextDouble() < epsilon) {
        return validMoves.get(random.nextInt(validMoves.size()));
    }
    
    int[] bestMove = null;
    double bestQValue = Double.NEGATIVE_INFINITY;
    
    for (int[] move : validMoves) {
        double qValue = getQValue(board, move);
        if (qValue > bestQValue) {
            bestQValue = qValue;
            bestMove = move;
        }
    }
    
    return bestMove != null ? bestMove : validMoves.get(0);
}

private double getQValue(String[][] board, int[] move) {
    String[][] nextState = simulateMove(board, move);
    INDArray input = encodeBoardToVector(nextState);
    INDArray output = qNetwork.output(input);
    return output.getDouble(0);
}
```

## Chess Strategy

### Experience Storage
```java
public record QValue(int[] move, double value, double confidence) {}
public record TrainingStats(int steps, int experiences, double epsilon) {}
public record NetworkUpdate(int step, double loss, boolean targetUpdated) {}

public void storeExperience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
    replayBuffer.store(new Experience(state, action, reward, nextState, done));
}
```

### Training Step with Target Network
```java
public void trainStep() {
    if (replayBuffer.size() < 64) return;
    
    List<Experience> batch = replayBuffer.sample(32);
    List<INDArray> inputs = new ArrayList<>();
    List<INDArray> targets = new ArrayList<>();
    
    for (Experience exp : batch) {
        INDArray stateInput = encodeBoardToVector(exp.state);
        
        double targetQ;
        if (exp.done) {
            targetQ = exp.reward;
        } else {
            INDArray nextStateInput = encodeBoardToVector(exp.nextState);
            double nextMaxQ = targetNetwork.output(nextStateInput).getDouble(0);
            targetQ = exp.reward + gamma * nextMaxQ;
        }
        
        inputs.add(stateInput);
        targets.add(Nd4j.create(new double[]{targetQ}).reshape(1, 1));
    }
    
    if (!inputs.isEmpty()) {
        INDArray batchInput = Nd4j.vstack(inputs.toArray(new INDArray[0]));
        INDArray batchTarget = Nd4j.vstack(targets.toArray(new INDArray[0]));
        
        DataSet dataSet = new DataSet(batchInput, batchTarget);
        qNetwork.fit(dataSet);
        
        trainingSteps++;
        
        if (trainingSteps % targetUpdateFreq == 0) {
            updateTargetNetwork();
            saveModels();
        }
    }
}

private void updateTargetNetwork() {
    targetNetwork = qNetwork.clone();
}
```

### Board Encoding
```java
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
```

## Training Process

### Self-Play Training with Opening Book
```java
public void startTraining() {
    isTraining.set(true);
    Thread trainingThread = Thread.ofVirtual().name("DQN-Training").start(() -> {
        logger.info("*** DQN: Training started with Lc0 opening book ***");
        
        // Generate training games using opening book
        for (int game = 0; game < 1000 && isTraining.get(); game++) {
            VirtualChessBoard virtualBoard = new VirtualChessBoard();
            String[][] board = virtualBoard.getBoard();
            boolean whiteTurn = virtualBoard.isWhiteTurn();
            
            for (int move = 0; move < 50 && isTraining.get(); move++) {
                List<int[]> validMoves = generateValidMoves(board, whiteTurn);
                if (validMoves.isEmpty()) break;
                
                int[] selectedMove;
                
                // Use Lc0 opening book for early moves during training
                if (move < 15 && openingBook != null) {
                    LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = 
                        openingBook.getOpeningMove(board, validMoves);
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
            
            // Train on experiences
            trainStep();
            
            if (game % 100 == 0) {
                logger.info("DQN: Completed {} training games", game);
            }
        }
    });
}
```

### Experience Replay Buffer
```java
public static class Experience implements Serializable {
    private static final long serialVersionUID = 1L;
    public String[][] state;
    public int[] action;
    public double reward;
    public String[][] nextState;
    public boolean done;
    
    public Experience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.nextState = nextState;
        this.done = done;
    }
}

private static class ExperienceReplay {
    private List<Experience> buffer;
    private int maxSize;
    private Random random = new Random();
    
    public ExperienceReplay(int maxSize) {
        this.maxSize = maxSize;
        this.buffer = new ArrayList<>();
    }
    
    public void store(Experience experience) {
        if (buffer.size() >= maxSize) {
            buffer.remove(0);
        }
        buffer.add(experience);
    }
    
    public List<Experience> sample(int batchSize) {
        List<Experience> batch = new ArrayList<>();
        for (int i = 0; i < Math.min(batchSize, buffer.size()); i++) {
            batch.add(buffer.get(random.nextInt(buffer.size())));
        }
        return batch;
    }
}
```

## Performance Characteristics

### Strengths
- **Stable Learning**: Target network prevents oscillations
- **Experience Reuse**: Learns from past experiences multiple times
- **Exploration**: Epsilon-greedy ensures diverse experience
- **Opening Knowledge**: Benefits from professional opening database

### Considerations
- **Sample Efficiency**: Requires many experiences for good performance
- **Memory Usage**: Experience buffer consumes significant memory
- **Hyperparameter Sensitivity**: Performance depends on epsilon, gamma, etc.
- **Training Time**: Needs extensive training for convergence

## Integration Features

### Experience Sharing
```java
public void addTrainingExperience(String[][] state, int[] action, double reward, String[][] nextState, boolean done) {
    storeExperience(state, action, reward, nextState, done);
}
```

### Model Persistence
```java
public void saveModels() {
    try {
        ModelSerializer.writeModel(qNetwork, new File(DQN_MODEL_FILE), true);
        ModelSerializer.writeModel(targetNetwork, new File(DQN_TARGET_MODEL_FILE), true);
        
        File qModelFile = new File(DQN_MODEL_FILE);
        File targetModelFile = new File(DQN_TARGET_MODEL_FILE);
        System.out.println("DQN models saved (" + qModelFile.length() + " + " + targetModelFile.length() + " bytes)");
    } catch (Exception e) {
        System.err.println("DQN: Failed to save models - " + e.getMessage());
    }
}

public void saveExperiences() {
    try {
        List<Experience> experiences = replayBuffer.getAllExperiences();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DQN_EXPERIENCE_FILE))) {
            oos.writeObject(experiences);
        }
        File expFile = new File(DQN_EXPERIENCE_FILE);
        System.out.println("DQN experiences saved (" + experiences.size() + " entries, " + expFile.length() + " bytes)");
    } catch (Exception e) {
        System.err.println("DQN: Failed to save experiences - " + e.getMessage());
    }
}
```

## Configuration

### Hyperparameters
```java
private double epsilon = 0.1;           // Exploration rate
private double gamma = 0.95;            // Discount factor
private int targetUpdateFreq = 1000;    // Target network update frequency
private static final int BATCH_SIZE = 32; // Training batch size
private static final int BUFFER_SIZE = 10000; // Experience buffer size
```

### Training Parameters
- **Learning Rate**: 0.001 (Adam optimizer)
- **Network Architecture**: 64→256→128→1
- **Experience Buffer**: 10,000 experiences maximum
- **Target Update**: Every 1,000 training steps

## Usage Examples

### Basic Setup
```java
DeepQNetworkAI dqn = new DeepQNetworkAI(true);
```

### Training
```java
dqn.startTraining();
boolean isTraining = dqn.isTraining();
int steps = dqn.getTrainingSteps();
```

### Experience Integration
```java
dqn.addTrainingExperience(state, action, reward, nextState, gameOver);
```

### Parameter Access
```java
double epsilon = dqn.getEpsilon();
dqn.setEpsilon(0.05); // Reduce exploration over time
int experiences = dqn.getExperienceCount();
```

## Technical Details

### Records for Data Structures
```java
public record QValue(int[] move, double value, double confidence) {}
public record TrainingStats(int steps, int experiences, double epsilon) {}
public record NetworkUpdate(int step, double loss, boolean targetUpdated) {}
```

### GPU Support
- **OpenCL Detection**: Automatic AMD GPU detection and configuration
- **Memory Management**: Efficient GPU memory usage
- **Fallback**: CPU backend when GPU unavailable

### Error Handling
- **Model Loading**: Graceful handling of corrupted models
- **Experience Persistence**: Safe saving/loading of experience buffer
- **Training Interruption**: Clean shutdown with data preservation
- **Memory Management**: Automatic cleanup of old experiences

### Reward Calculation
```java
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
```

This DQN implementation provides stable deep reinforcement learning for chess, combining the power of neural networks with the proven Q-learning algorithm and enhanced by professional opening knowledge.