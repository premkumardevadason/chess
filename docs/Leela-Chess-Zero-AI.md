# Leela Chess Zero AI Documentation

## Overview
Leela Chess Zero AI is based on the open-source Leela Chess Zero project, featuring human game knowledge and transformer architecture. It combines neural networks with enhanced MCTS for chess-specific optimizations, learning from human grandmaster games rather than pure self-play.

## How It Works in Chess

### Core Architecture
- **Neural Network**: Transformer-based architecture for position evaluation
- **Enhanced MCTS**: Chess-optimized Monte Carlo Tree Search
- **Human Knowledge**: Trained on human grandmaster games
- **No Opening Book**: Relies purely on neural network evaluation

### Key Features
1. **Human Game Learning**: Incorporates knowledge from human chess games
2. **Transformer Architecture**: Advanced neural network design
3. **Enhanced MCTS**: Chess-specific search optimizations
4. **Move Evaluation**: Provides confidence scores for AI comparison

## Code Implementation

### Main Class Structure
```java
public class LeelaChessZeroAI {
    private LeelaChessZeroNetwork neuralNetwork;
    private LeelaChessZeroMCTS mcts;
    private ExecutorService executorService;
    
    // Threading support
    private volatile int[] selectedMove = null;
    private volatile boolean isThinking = false;
    private volatile Thread trainingThread = null;
    
    // AI weighting for combined evaluation
    private double aiWeight = 1.0;
    private boolean convergenceEnabled = true;
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
    logger.info("*** LeelaZero: Starting pure neural MCTS search ***");
    long startTime = System.currentTimeMillis();
    
    // Pure neural network approach - no opening book
    int[] move = mcts.selectBestMove(board, validMoves);
    
    long totalTime = System.currentTimeMillis() - startTime;
    logger.debug("*** LeelaZero: Completed in {}ms ({:.1f}s) ***", totalTime, totalTime/1000.0);
    
    return move;
}

private int[] selectMoveAsync(String[][] board, List<int[]> validMoves) {
    selectedMove = null;
    isThinking = true;
    
    // Reinitialize ExecutorService if terminated
    if (executorService.isTerminated() || executorService.isShutdown()) {
        logger.debug("*** LeelaZero: Reinitializing terminated ExecutorService ***");
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LeelaZero-Thread");
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
        selectedMove = future.get(60, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        logger.info("*** LeelaZero: TIMEOUT - Cancelling ***");
        future.cancel(true);
        isThinking = false;
    } catch (Exception e) {
        logger.error("*** LeelaZero: ERROR - {} ***", e.getMessage());
        isThinking = false;
    }
    
    return selectedMove != null ? selectedMove : validMoves.get(0);
}
```

## Chess Strategy

### Neural Network Evaluation
Unlike AlphaZero, Leela Chess Zero incorporates human chess knowledge:

1. **Human Game Training**: Learns from millions of human games
2. **Pattern Recognition**: Recognizes human-like chess patterns
3. **Strategic Understanding**: Incorporates human strategic concepts
4. **Positional Evaluation**: Evaluates positions using human-derived knowledge

### Move Evaluation for AI Comparison
```java
public Map<int[], Double> evaluateAllMoves(String[][] board, List<int[]> validMoves) {
    if (!convergenceEnabled) return new HashMap<>();
    
    Map<int[], Double> evaluations = new HashMap<>();
    
    try {
        // Get evaluations from neural network
        for (int[] move : validMoves) {
            double evaluation = neuralNetwork.evaluateMove(board, move) * aiWeight;
            evaluations.put(move, evaluation);
        }
        
        logger.debug("*** LeelaZero: Provided {} move evaluations (weight: {}) ***", evaluations.size(), aiWeight);
        
    } catch (Exception e) {
        logger.error("*** LeelaZero: Error in move evaluation - {} ***", e.getMessage());
    }
    
    return evaluations;
}
```

### Enhanced MCTS Integration
The system uses LeelaChessZeroMCTS which provides:
- **Chess-specific optimizations**: Tailored for chess positions
- **Neural network guidance**: MCTS guided by neural evaluations
- **Efficient search**: Optimized tree search algorithms
- **Position caching**: Efficient position evaluation caching

## Training Process

### Self-Play Training with Human Knowledge
```java
public void startSelfPlayTraining(int games) {
    logger.info("*** STARTING LEELAZEERO TRAINING WITH {} GAMES ***", games);
    
    if (neuralNetwork == null) {
        logger.error("*** LeelaZero: ERROR - Neural network is NULL ***");
        return;
    }
    
    trainingThread = Thread.ofVirtual().name("LeelaZero-Training").start(() -> {
        try {
            logger.debug("*** LeelaZero: Training thread STARTED ***");
            LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(logger.isDebugEnabled());
            LeelaChessZeroTrainer trainer = new LeelaChessZeroTrainer(neuralNetwork, mcts, openingBook, logger.isDebugEnabled());
            trainer.runSelfPlayTraining(games);
            logger.debug("*** LeelaZero: TRAINING THREAD COMPLETED ***");
        } catch (Exception e) {
            if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                logger.info("*** LeelaZero: Training interrupted - saving progress before shutdown ***");
                saveState();
                Thread.currentThread().interrupt();
                return;
            }
            logger.error("*** LeelaZero: TRAINING ERROR: {} ***", e.getMessage());
        } finally {
            logger.info("*** LeelaZero: Training thread ending - saving final state ***");
            saveState();
        }
    });
}
```

### Human Game Data Integration
```java
public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
    if (neuralNetwork == null) return;
    
    logger.debug("*** LeelaZero: Adding human game data ({} moves) ***", moveHistory.size());
    
    try {
        LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(logger.isDebugEnabled());
        LeelaChessZeroTrainer trainer = new LeelaChessZeroTrainer(neuralNetwork, mcts, openingBook, logger.isDebugEnabled());
        trainer.addHumanGameExperience(finalBoard, moveHistory, blackWon);
    } catch (Exception e) {
        logger.error("*** LeelaZero: Failed to add human game data - {} ***", e.getMessage());
    }
}
```

## Performance Characteristics

### Strengths
- **Human-like Play**: Incorporates human chess understanding
- **Strategic Depth**: Excellent positional understanding
- **Pattern Recognition**: Recognizes complex chess patterns
- **Balanced Approach**: Combines tactical and positional play

### Considerations
- **Training Complexity**: Requires sophisticated training process
- **Computational Cost**: Neural network evaluation is expensive
- **Memory Usage**: Large neural networks require significant memory
- **No Opening Book**: Relies entirely on neural network knowledge

## Integration Features

### AI Weighting System
```java
public void setAiWeight(double weight) {
    this.aiWeight = Math.max(0.0, Math.min(1.0, weight));
    logger.debug("*** LeelaZero: AI weight set to {} ***", this.aiWeight);
}

public void setConvergenceEnabled(boolean enabled) {
    this.convergenceEnabled = enabled;
    logger.debug("*** LeelaZero: Convergence {} ***", enabled ? "enabled" : "disabled");
}
```

### State Management
```java
public void saveState() {
    try {
        if (neuralNetwork != null) {
            neuralNetwork.saveModel();
            logger.info("*** LeelaZero: Neural network saved ***");
        }
        logger.info("*** LeelaZero: All training data saved successfully ***");
    } catch (Exception e) {
        logger.error("*** LeelaZero: Error saving training data - {} ***", e.getMessage());
    }
}

public void resetState() {
    if (neuralNetwork != null) {
        neuralNetwork.resetModel();
    }
    logger.debug("*** LeelaZero: State reset ***");
}
```

### Thread Management
- **Asynchronous Processing**: Move selection in separate threads
- **Timeout Protection**: 60-second timeout for move selection
- **Graceful Shutdown**: Proper cleanup of resources
- **Training Interruption**: Safe interruption with state saving

## Configuration

### Neural Network Parameters
- **Architecture**: Transformer-based neural network
- **Training Data**: Human grandmaster games
- **Evaluation**: Position and policy evaluation
- **Optimization**: Advanced neural network optimizations

### MCTS Parameters
- **Simulations**: Configurable number of MCTS simulations
- **Neural Guidance**: Neural network guides tree search
- **Chess Optimizations**: Chess-specific search enhancements
- **Caching**: Efficient position evaluation caching

## Usage Examples

### Basic Setup
```java
LeelaChessZeroAI leelaAI = new LeelaChessZeroAI(true);
```

### Move Selection
```java
int[] move = leelaAI.selectMove(board, validMoves);
boolean thinking = leelaAI.isThinking();
```

### Training
```java
leelaAI.startSelfPlayTraining(10000);
String status = leelaAI.getTrainingStatus();
```

### AI Comparison Integration
```java
Map<int[], Double> evaluations = leelaAI.evaluateAllMoves(board, validMoves);
leelaAI.setAiWeight(0.8);
leelaAI.setConvergenceEnabled(true);
```

### Human Game Learning
```java
leelaAI.addHumanGameData(finalBoard, moveHistory, blackWon);
```

## Technical Details

### Records for Data Structures
```java
public record MoveEvaluation(int[] move, double value, double policy, double confidence) {}
public record TrainingGame(SequencedCollection<String> moves, double result, int length) {}
public record NetworkState(double confidence, String status, int trainingGames) {}
```

### Virtual Thread Usage
- **Modern Concurrency**: Uses Java 21 virtual threads
- **Training Threads**: Separate threads for training processes
- **Resource Efficient**: Lower memory overhead than platform threads
- **Scalable**: Handles concurrent operations efficiently

### Error Handling
- **Network Failures**: Graceful handling of neural network errors
- **Training Interruption**: Safe shutdown with state preservation
- **Timeout Management**: Proper timeout handling for move selection
- **Resource Cleanup**: Automatic cleanup of resources

### Integration with Other Components
- **LeelaChessZeroNetwork**: Neural network implementation
- **LeelaChessZeroMCTS**: Enhanced MCTS implementation
- **LeelaChessZeroTrainer**: Training system for self-play and human games
- **LeelaChessZeroOpeningBook**: Professional opening database

### Confidence Scoring
```java
public double getConfidenceScore() {
    if (neuralNetwork == null) return 0.0;
    return neuralNetwork.getConfidenceScore();
}
```

This Leela Chess Zero implementation provides a sophisticated neural network-based chess AI that incorporates human chess knowledge while maintaining the power of modern deep learning techniques.