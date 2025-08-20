# Q-Learning AI Documentation

## Overview
Q-Learning AI is a reinforcement learning system that learns chess through trial and error. It builds a Q-table mapping board states and moves to quality values, gradually improving its play through experience and reward-based learning.

## How It Works in Chess

### Core Concept
- **Q-Table**: Maps (state, action) pairs to quality values
- **Exploration vs Exploitation**: Balances trying new moves vs using known good moves
- **Reward Learning**: Updates move values based on game outcomes
- **Experience Replay**: Learns from past games and positions

### Key Features
1. **Comprehensive Chess Evaluation**: Advanced position analysis with king safety
2. **Balanced Strategy**: Combines attack and defense priorities
3. **Experience Sharing**: Integrates with DQN for knowledge transfer
4. **Real-time Training**: Continuous learning during gameplay

## Code Implementation

### Main Class Structure
```java
public class QLearningAI {
    private Map<String, Double> qTable = new ConcurrentHashMap<>();
    private double learningRate = 0.1;
    private double discountFactor = 0.9;
    private double epsilon = 0.3;
    private DeepQNetworkAI dqnAI; // Reference for experience sharing
    private LeelaChessZeroOpeningBook openingBook;
}
```

### Move Selection Process
```java
public int[] selectMove(String[][] board, List<int[]> validMoves, boolean isTraining) {
    if (validMoves.isEmpty()) return null;
    
    String boardState = encodeBoardState(board);
    Map<String, Double> activeQTable = (!isTraining && trainingInProgress) ? qTableSnapshot : qTable;
    
    if (isTraining && random.nextDouble() < epsilon) {
        return filteredMoves.get(random.nextInt(filteredMoves.size()));
    }
    
    // Select best move based on Q-values and chess evaluation
    int[] bestMove = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    
    for (int[] move : filteredMoves) {
        String stateAction = boardState + ":" + Arrays.toString(move);
        double qValue = activeQTable.getOrDefault(stateAction, 0.0);
        double chessScore = evaluateMove(board, move, !isTraining);
        double totalScore = qValue + chessScore;
        
        if (totalScore > bestScore) {
            bestScore = totalScore;
            bestMove = move;
        }
    }
    
    return bestMove != null ? bestMove : filteredMoves.get(0);
}
```

### Q-Value Update
```java
public void updateQValue(String prevState, int[] action, double reward, String newState, List<int[]> nextMoves) {
    String stateAction = prevState + ":" + Arrays.toString(action);
    double currentQ = qTable.getOrDefault(stateAction, 0.0);
    
    double maxNextQ = 0.0;
    if (nextMoves != null && !nextMoves.isEmpty()) {
        for (int[] nextMove : nextMoves) {
            String nextStateAction = newState + ":" + Arrays.toString(nextMove);
            maxNextQ = Math.max(maxNextQ, qTable.getOrDefault(nextStateAction, 0.0));
        }
    }
    
    double effectiveLearningRate = gameplayMode ? learningRate * 0.1 : learningRate;
    double newQ = currentQ + effectiveLearningRate * (reward + discountFactor * maxNextQ - currentQ);
    
    qTable.put(stateAction, newQ);
}
```

## Chess Strategy

### Balanced Evaluation System
```java
private double evaluateMove(String[][] board, int[] move, boolean useChessLogic) {
    double score = 0.0;
    String piece = board[move[0]][move[1]];
    String captured = board[move[2]][move[3]];
    boolean isWhite = "♔♕♖♗♘♙".contains(piece);
    
    // DEFENSIVE PRIORITIES (King Safety First)
    if (isWhite && isKingInCheck(board, false)) {
        score -= 5000.0; // Massive penalty for exposing own king
    }
    
    // ATTACK GOALS (Balanced with Defense)
    if (isCheckmate(board, !isWhite)) {
        score += 5000.0; // Reward for checkmate
    } else if (isKingInCheck(board, !isWhite)) {
        score += 500.0; // Reward for check
    }
    
    // BALANCED CAPTURES
    if (!captured.isEmpty()) {
        double captureValue = getPieceValue(captured);
        score += captureValue * 50.0; // Standard capture bonus
    }
    
    return score;
}
```

### Opening Integration
- **Leela Chess Zero Opening Book**: Uses professional opening database
- **Training Diversity**: Random opening selection for varied experience
- **Early Game Learning**: Focuses on opening principles during training

### Middle Game Strategy
- **Piece Coordination**: Rewards moves that improve piece cooperation
- **King Safety**: Prioritizes defensive moves to protect the king
- **Tactical Awareness**: Recognizes forks, pins, and tactical patterns

### Endgame Approach
- **Material Evaluation**: Focuses on piece values and exchanges
- **King Activity**: Encourages active king play in endgames
- **Pawn Promotion**: Prioritizes pawn advancement and promotion

## Training Process

### Self-Play Training
```java
public void trainAgainstSelfWithProgress(int games) {
    System.out.println("*** STARTING Q-LEARNING TRAINING WITH " + games + " GAMES ***");
    
    for (int game = 0; game < games; game++) {
        playTrainingGame();
        gamesCompleted++;
        
        if (gamesCompleted % 10 == 0) {
            saveQTable();
            if (controller != null) {
                controller.broadcastTrainingProgress();
            }
        }
    }
}
```

### Game Step Recording
```java
public record GameStep(String boardState, int[] move, boolean wasWhiteTurn, double reward) {}
```

### Training Game Flow
1. **Virtual Board Setup**: Creates isolated training environment
2. **Opening Book Integration**: Uses Lc0 openings for diversity
3. **Move Generation**: Validates moves using ChessGame rules
4. **Reward Calculation**: Evaluates moves based on outcomes
5. **Q-Table Updates**: Updates values for all game positions

## Performance Characteristics

### Strengths
- **Adaptive Learning**: Improves through experience
- **Balanced Strategy**: Combines attack and defense effectively
- **Memory Efficiency**: Stores only visited positions
- **Real-time Updates**: Learns during gameplay

### Considerations
- **State Space**: Large number of possible chess positions
- **Exploration Time**: Needs extensive training for optimal performance
- **Memory Usage**: Q-table grows with experience
- **Convergence**: May require many games to stabilize

## Integration Features

### DQN Experience Sharing
```java
public void setDQNAI(DeepQNetworkAI dqnAI) {
    this.dqnAI = dqnAI;
    logger.info("Q-Learning: Connected to DQN for experience sharing");
}
```

### WebSocket Integration
- **Real-time Progress**: Live training updates via WebSocket
- **Training Board Visualization**: Shows current training position
- **Status Broadcasting**: Communicates training state to frontend

### Concurrent Training
- **Thread Safety**: Uses ConcurrentHashMap for thread-safe operations
- **Snapshot System**: Maintains stable Q-table for gameplay during training
- **Graceful Shutdown**: Saves progress on interruption

## Configuration Parameters

### Learning Parameters
```java
private double learningRate = 0.1;      // How fast to learn from new experiences
private double discountFactor = 0.9;    // Importance of future rewards
private double epsilon = 0.3;           // Exploration vs exploitation balance
```

### Training Settings
- **Games per Session**: Configurable training duration
- **Save Frequency**: Automatic Q-table persistence
- **Batch Processing**: Efficient update processing
- **Memory Management**: Automatic cleanup of old entries

## Usage Examples

### Basic Training
```java
QLearningAI ai = new QLearningAI(true);
ai.trainAgainstSelf(1000);
```

### Move Selection
```java
int[] move = ai.selectMove(board, validMoves, false);
```

### Integration with Other AIs
```java
ai.setDQNAI(dqnAI);
ai.setController(webSocketController);
```

## Technical Details

### Board State Encoding
```java
private String encodeBoardState(String[][] board) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            String piece = board[i][j];
            sb.append(switch (piece) {
                case "♔" -> "K"; case "♕" -> "Q"; case "♖" -> "R";
                case "♗" -> "B"; case "♘" -> "N"; case "♙" -> "P";
                case "♚" -> "k"; case "♛" -> "q"; case "♜" -> "r";
                case "♝" -> "b"; case "♞" -> "n"; case "♟" -> "p";
                default -> ".";
            });
        }
    }
    return sb.toString();
}
```

### Persistence System
- **File-based Storage**: Saves Q-table to disk
- **Incremental Updates**: Tracks changes since last save
- **Recovery**: Loads existing Q-table on startup
- **Backup**: Creates backups during critical operations

### Error Handling
- **Validation**: Ensures moves are legal before processing
- **Fallback**: Safe move selection on errors
- **Logging**: Comprehensive debug information
- **Recovery**: Graceful handling of corrupted data

This Q-Learning implementation provides a solid foundation for reinforcement learning in chess, combining traditional RL techniques with modern chess evaluation methods.