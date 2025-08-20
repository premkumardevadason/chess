# AlphaZero AI Documentation

## Overview
AlphaZero AI is a self-play neural network implementation that learns chess from scratch without human game knowledge. It combines deep neural networks with Monte Carlo Tree Search (MCTS) to achieve superhuman performance through pure self-play training.

## How It Works in Chess

### Core Architecture
- **Neural Network**: Evaluates board positions and predicts move probabilities
- **MCTS Engine**: Guides search using neural network evaluations
- **Self-Play Training**: Learns by playing millions of games against itself

### Key Features
1. **Zero Human Knowledge**: Learns chess rules and strategy purely from self-play
2. **Neural Network Guidance**: Uses deep learning to evaluate positions
3. **MCTS Search**: Combines neural evaluations with tree search
4. **Continuous Learning**: Improves through ongoing self-play episodes

## Code Implementation

### Main Class Structure
```java
public class AlphaZeroAI {
    private final AlphaZeroInterfaces.NeuralNetwork neuralNetwork;
    private final AlphaZeroInterfaces.MCTSEngine mctsEngine;
    private ExecutorService executorService;
    private volatile boolean isThinking = false;
}
```

### Move Selection Process
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    if (validMoves.size() == 1) return validMoves.get(0);
    
    return selectMoveAsync(board, validMoves);
}

private int[] selectMoveSync(String[][] board, List<int[]> validMoves) {
    logger.info("*** AlphaZero: Starting neural network + MCTS search ***");
    long startTime = System.currentTimeMillis();
    
    // Use MCTS guided by neural network
    int[] move = mctsEngine.selectBestMove(board, validMoves);
    
    long totalTime = System.currentTimeMillis() - startTime;
    logger.info("*** AlphaZero: Completed in " + totalTime + "ms ***");
    
    return move;
}
```

### Self-Play Training
```java
public void startSelfPlayTraining(int games) {
    logger.info("*** AlphaZero: Starting self-play training with " + games + " games ***");
    
    if (trainingService == null) {
        trainingService = AlphaZeroFactory.createTrainingService(logger.isDebugEnabled());
    }
    
    trainingThread = Thread.ofVirtual().name("AlphaZero-Training").start(() -> {
        try {
            trainingService.runSelfPlayTraining(games);
            logger.info("*** AlphaZero: Training completed ***");
        } catch (Exception e) {
            logger.error("*** AlphaZero: Training error - {} ***", e.getMessage());
        }
    });
}
```

## Chess Strategy

### Opening Phase
- **No Opening Book**: Learns openings through self-play
- **Pattern Recognition**: Develops opening principles naturally
- **Flexible Approach**: Adapts to opponent's style

### Middle Game
- **Deep Calculation**: Uses MCTS for tactical analysis
- **Positional Understanding**: Neural network evaluates positions
- **Strategic Planning**: Balances short-term tactics with long-term strategy

### Endgame
- **Precise Calculation**: Excels in complex endgame positions
- **Pattern Mastery**: Learns endgame patterns through training
- **Optimal Play**: Finds best moves in simplified positions

## Training Process

### Episode-Based Learning
```java
public record TrainingEpisode(String[][] board, int[] move, double reward) {}
```

### Self-Play Games
1. **Game Generation**: Plays games against itself
2. **Position Evaluation**: Neural network evaluates each position
3. **Move Selection**: MCTS selects moves based on evaluations
4. **Result Backpropagation**: Updates neural network based on game outcomes

### Neural Network Updates
- **Policy Network**: Learns to predict good moves
- **Value Network**: Learns to evaluate positions
- **Combined Training**: Both networks trained simultaneously

## Performance Characteristics

### Strengths
- **Deep Calculation**: Excellent tactical vision
- **Pattern Recognition**: Learns complex positional patterns
- **Adaptability**: Improves continuously through training
- **No Bias**: Free from human preconceptions

### Considerations
- **Training Time**: Requires extensive self-play for optimal performance
- **Computational Cost**: Needs significant processing power
- **Memory Usage**: Large neural networks require substantial memory

## Integration with Chess System

### Dependency Injection
```java
public AlphaZeroAI(AlphaZeroInterfaces.NeuralNetwork neuralNetwork, 
                   AlphaZeroInterfaces.MCTSEngine mctsEngine) {
    this.neuralNetwork = neuralNetwork;
    this.mctsEngine = mctsEngine;
}
```

### Thread Management
- **Asynchronous Processing**: Move selection runs in separate threads
- **Timeout Protection**: 60-second timeout for move selection
- **Graceful Shutdown**: Proper cleanup of resources

### Training Integration
- **WebSocket Updates**: Real-time training progress
- **Model Persistence**: Automatic saving of neural network weights
- **Human Game Learning**: Can incorporate human game data

## Configuration

### Training Parameters
- **Episodes**: Number of self-play games (default: 10,000)
- **MCTS Simulations**: Tree search depth per move
- **Learning Rate**: Neural network training speed
- **Batch Size**: Training batch size for efficiency

### Performance Tuning
- **Thread Pool**: Configurable thread pool for parallel processing
- **Memory Management**: Efficient memory usage for large networks
- **GPU Support**: Optional GPU acceleration for training

## Usage Examples

### Basic Move Selection
```java
AlphaZeroAI ai = new AlphaZeroAI(neuralNetwork, mctsEngine);
int[] move = ai.selectMove(board, validMoves);
```

### Training Session
```java
ai.startSelfPlayTraining(10000);
String status = ai.getTrainingStatus();
```

### Human Game Integration
```java
ai.addHumanGameData(finalBoard, moveHistory, blackWon);
```

## Technical Details

### Records for Data Structures
```java
public record SearchResult(int[] move, double confidence, long timeMs) {}
public record GameData(SequencedCollection<String> moveHistory, boolean blackWon, int totalMoves) {}
```

### Virtual Thread Usage
- **Modern Concurrency**: Uses Java 21 virtual threads
- **Scalable**: Handles many concurrent operations efficiently
- **Resource Efficient**: Lower memory overhead than platform threads

### Error Handling
- **Timeout Management**: Graceful handling of long calculations
- **Exception Recovery**: Fallback to safe moves on errors
- **Resource Cleanup**: Proper shutdown procedures

This AlphaZero implementation represents the cutting edge of chess AI, combining deep learning with tree search to achieve superhuman performance through pure self-play learning.