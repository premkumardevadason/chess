# Monte Carlo Tree Search AI Documentation

## Overview
Monte Carlo Tree Search AI uses pure MCTS algorithm with tree reuse optimization and integration with other AI systems. It performs random simulations to evaluate positions and uses UCB1 formula for node selection. The system features adaptive tree reuse based on performance and AI-guided simulations.

## How It Works in Chess

### Core Algorithm
- **Selection**: Navigate tree using UCB1 formula
- **Expansion**: Add new nodes to the tree
- **Simulation**: Random playout to terminal position
- **Backpropagation**: Update node statistics with results

### Key Features
1. **Tree Reuse**: AlphaZero-style tree preservation between moves
2. **AI Integration**: Uses other AIs for guided simulations
3. **Adaptive Performance**: Enables/disables tree reuse based on success
4. **Opening Book**: Leela Chess Zero opening database integration

## Code Implementation

### Main Class Structure
```java
public class MonteCarloTreeSearchAI {
    // Mutable class for tree structure (records not suitable for mutable tree nodes)
    public static class MCTSNode {
        public MCTSNode parent;
        public final int[] move;
        public final String[][] board;
        public final boolean isWhiteTurn;
        public final List<MCTSNode> children;
        public int visits;
        public double wins;
    }
    
    private Random random = new Random();
    private int simulationsPerMove = 1000;
    private double explorationConstant = Math.sqrt(2);
    
    // Reference to other AIs for enhanced evaluation
    private QLearningAI qLearningAI;
    private DeepLearningAI deepLearningAI;
    private DeepQNetworkAI dqnAI;
    private LeelaChessZeroOpeningBook openingBook;
    
    // Tree reuse for AlphaZero-style optimization
    private MCTSNode rootNode = null;
    private String lastBoardState = null;
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
    System.out.println("*** MCTS: Starting " + simulationsPerMove + " simulations (Tree reuse: " + 
        (enableTreeReuse ? "ON" : "OFF") + ") ***");
    long startTime = System.currentTimeMillis();
    
    // Try to reuse tree from previous move (AlphaZero optimization)
    MCTSNode root = findReusableSubtree(board, validMoves);
    if (root == null) {
        root = new MCTSNode(null, null, copyBoard(board), false);
        System.out.println("*** MCTS: Created new tree - no reusable subtree found ***");
    } else {
        System.out.println("*** MCTS: TREE REUSED - Starting with " + root.visits + " existing visits ***");
    }
    
    // Pre-expand root with all valid moves
    for (int[] move : validMoves) {
        String[][] newBoard = makeMove(board, move);
        MCTSNode child = new MCTSNode(root, move, newBoard, true);
        root.children.add(child);
    }
    
    // Run MCTS simulations
    for (int i = 0; i < simulationsPerMove; i++) {
        if (Thread.currentThread().isInterrupted()) {
            System.out.println("*** MCTS: THREAD INTERRUPTED - Aborting at simulation " + i + " ***");
            break;
        }
        
        try {
            MCTSNode selectedNode = select(root, validMoves);
            MCTSNode expandedNode = expand(selectedNode, validMoves);
            double result = simulate(expandedNode);
            backpropagate(expandedNode, result);
        } catch (Exception e) {
            System.err.println("*** MCTS: CRITICAL ERROR in simulation " + i + ": " + e.getMessage() + " ***");
            break;
        }
    }
    
    // Select best move from root's children
    MCTSNode bestChild = null;
    int maxVisits = 0;
    
    for (MCTSNode child : root.children) {
        if (child.visits > maxVisits) {
            maxVisits = child.visits;
            bestChild = child;
        }
    }
    
    long totalTime = System.currentTimeMillis() - startTime;
    System.out.println("*** MCTS: Completed in " + totalTime + "ms ***");
    
    if (bestChild != null) {
        System.out.println("*** MCTS: SELECTED MOVE - " + bestChild.visits + " visits, win rate: " + 
            String.format("%.1f%%", (bestChild.wins / bestChild.visits) * 100) + " ***");
        
        // Store tree for next move reuse
        if (enableTreeReuse) {
            rootNode = bestChild;
            rootNode.parent = null;
            lastBoardState = encodeBoardState(bestChild.board);
        }
    }
    
    return bestChild != null ? bestChild.move : validMoves.get(0);
}
```

## Chess Strategy

### UCB1 Selection Formula
```java
private double calculateUCB1(MCTSNode node, int parentVisits) {
    if (node.visits == 0) return Double.POSITIVE_INFINITY;
    
    double exploitation = node.wins / node.visits;
    double exploration = explorationConstant * Math.sqrt(Math.log(parentVisits) / node.visits);
    
    return exploitation + exploration;
}

private MCTSNode selectBestChild(MCTSNode node) {
    MCTSNode bestChild = null;
    double bestValue = Double.NEGATIVE_INFINITY;
    
    for (MCTSNode child : node.children) {
        double ucb1Value = calculateUCB1(child, node.visits);
        if (ucb1Value > bestValue) {
            bestValue = ucb1Value;
            bestChild = child;
        }
    }
    
    return bestChild != null ? bestChild : node;
}
```

### AI-Guided Simulations
```java
private int[] selectSimulationMove(String[][] board, List<int[]> moves, boolean isWhite) {
    if (moves.isEmpty()) return null;
    
    // Use Lc0 opening book for early moves in simulation
    if (openingBook != null) {
        LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, moves);
        if (openingResult != null) {
            return openingResult.move;
        }
    }
    
    // Use hybrid approach: 70% AI-guided, 30% random
    if (random.nextDouble() < 0.7 && hasAIReference()) {
        return selectAIGuidedMove(board, moves);
    } else {
        return moves.get(random.nextInt(moves.size()));
    }
}

private int[] selectAIGuidedMove(String[][] board, List<int[]> moves) {
    try {
        // Try DQN first, then Q-Learning, then random
        if (dqnAI != null) {
            int[] dqnMove = dqnAI.selectMove(board, moves);
            if (dqnMove != null) return dqnMove;
        }
        
        if (qLearningAI != null) {
            int[] qMove = qLearningAI.selectMove(board, moves, false);
            if (qMove != null) return qMove;
        }
    } catch (Exception e) {
        // Fall back to random if AI fails
    }
    
    return moves.get(random.nextInt(moves.size()));
}
```

### Tree Reuse Optimization
```java
private MCTSNode findReusableSubtree(String[][] currentBoard, List<int[]> validMoves) {
    if (!enableTreeReuse || rootNode == null || lastBoardState == null) {
        return null;
    }
    
    String currentBoardState = encodeBoardState(currentBoard);
    System.out.println("*** MCTS: Searching for subtree match in " + rootNode.children.size() + " children ***");
    
    // Check if current position matches any child of the stored root
    for (int i = 0; i < rootNode.children.size(); i++) {
        MCTSNode child = rootNode.children.get(i);
        String childBoardState = encodeBoardState(child.board);
        
        if (currentBoardState.equals(childBoardState)) {
            System.out.println("*** MCTS: SUBTREE MATCH FOUND at child " + i + " - Reusing " + child.visits + " visits ***");
            
            // Detach and return this subtree as new root
            child.parent = null;
            return child;
        }
    }
    
    System.out.println("*** MCTS: No subtree match found - Board state changed too much ***");
    return null;
}
```

## Training and Adaptation

### Adaptive Tree Reuse
```java
public void reportMoveResult(boolean mctsWon) {
    totalMoves++;
    
    if (mctsWon) {
        mctsWinStreak++;
        System.out.println("*** MCTS: WON AI COMPARISON (Win streak: " + mctsWinStreak + ") ***");
    } else {
        mctsWinStreak = 0;
        System.out.println("*** MCTS: LOST AI COMPARISON - Tree cleared to save memory ***");
        rootNode = null;
        lastBoardState = null;
    }
    
    // Adaptive tree reuse: disable if MCTS is losing too often
    if (totalMoves >= 10) {
        double winRate = (double) mctsWinStreak / Math.min(totalMoves, 10);
        boolean shouldEnableTreeReuse = winRate > 0.3; // Enable if >30% recent win rate
        
        if (shouldEnableTreeReuse != enableTreeReuse) {
            enableTreeReuse = shouldEnableTreeReuse;
            System.out.println("*** MCTS: ADAPTIVE MODE - Tree reuse " + (enableTreeReuse ? "ENABLED" : "DISABLED") + 
                " (Recent win rate: " + String.format("%.1f%%", winRate * 100) + ") ***");
            
            if (!enableTreeReuse) {
                rootNode = null;
                lastBoardState = null;
            }
        }
    }
}
```

### Position Evaluation
```java
private double evaluatePosition(String[][] board, boolean forWhite) {
    return evaluateBasicPosition(board, forWhite);
}

private double evaluateBasicPosition(String[][] board, boolean forWhite) {
    double whiteScore = 0, blackScore = 0;
    
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            String piece = board[i][j];
            if (!piece.isEmpty()) {
                double value = getPieceValue(piece);
                if ("♔♕♖♗♘♙".contains(piece)) {
                    whiteScore += value;
                } else {
                    blackScore += value;
                }
            }
        }
    }
    
    // Check for checkmate/stalemate
    if (isCheckmate(board, true)) return forWhite ? 0.0 : 1.0;
    if (isCheckmate(board, false)) return forWhite ? 1.0 : 0.0;
    if (isStalemate(board, true) || isStalemate(board, false)) return 0.5;
    
    double totalScore = whiteScore + blackScore;
    if (totalScore == 0) return 0.5;
    
    double whiteAdvantage = whiteScore / totalScore;
    return forWhite ? whiteAdvantage : (1.0 - whiteAdvantage);
}
```

## Performance Characteristics

### Strengths
- **No Domain Knowledge**: Works with minimal chess knowledge
- **Scalable**: Performance improves with more simulations
- **Tree Reuse**: Efficient memory usage through tree preservation
- **AI Integration**: Benefits from other AI systems' knowledge

### Considerations
- **Simulation Time**: Requires many simulations for good performance
- **Memory Usage**: Tree structures can consume significant memory
- **Tactical Blindness**: May miss deep tactical sequences
- **Random Variance**: Results can vary between runs

## Integration Features

### AI System Integration
```java
public void setAIReferences(QLearningAI qLearning, DeepLearningAI deepLearning, DeepQNetworkAI dqn) {
    this.qLearningAI = qLearning;
    this.deepLearningAI = deepLearning;
    this.dqnAI = dqn;
    logger.info("MCTS: Connected to other AI systems for enhanced evaluation");
}
```

### Threading Support
```java
private int[] selectMoveAsync(String[][] board, List<int[]> validMoves) {
    selectedMove = null;
    isThinking = true;
    
    // Create and start MCTS virtual thread
    mctsThread = Thread.ofVirtual().name("MCTS-Thread").start(() -> {
        selectedMove = selectMoveSync(board, validMoves);
        isThinking = false;
    });
    
    // Wait for result with timeout
    try {
        mctsThread.join(35000); // 35 second timeout
        if (isThinking) {
            System.out.println("*** MCTS: THREAD TIMEOUT - Interrupting ***");
            mctsThread.interrupt();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    
    return selectedMove != null ? selectedMove : validMoves.get(0);
}
```

## Configuration

### MCTS Parameters
```java
private int simulationsPerMove = 1000;      // Number of simulations per move
private double explorationConstant = Math.sqrt(2); // UCB1 exploration parameter
private boolean enableTreeReuse = true;     // Enable/disable tree reuse
private int maxMoves = 50;                  // Maximum moves per simulation
```

### Performance Tuning
- **Simulation Count**: Adjustable based on time constraints
- **Tree Depth**: Limited to prevent excessive memory usage
- **Timeout Protection**: 30-second timeout for move selection
- **Memory Management**: Automatic tree cleanup on poor performance

## Usage Examples

### Basic Setup
```java
MonteCarloTreeSearchAI mcts = new MonteCarloTreeSearchAI(true);
mcts.setAIReferences(qLearningAI, deepLearningAI, dqnAI);
```

### Move Selection
```java
int[] move = mcts.selectMove(board, validMoves);
boolean thinking = mcts.isThinking();
```

### Performance Reporting
```java
mcts.reportMoveResult(mctsWon);
mcts.setSimulationsPerMove(2000);
```

### Tree Management
```java
mcts.clearTree(); // Clear tree for new game
```

## Technical Details

### Records for Data Structures
```java
public record SimulationResult(double score, int moveCount, boolean terminated) {}
public record SearchStats(int simulations, long timeMs, double bestWinRate) {}
```

### Board State Encoding
```java
private String encodeBoardState(String[][] board) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            sb.append(board[i][j] == null || board[i][j].isEmpty() ? "." : board[i][j]);
        }
    }
    return sb.toString();
}
```

### Error Handling
- **Simulation Errors**: Graceful handling of invalid positions
- **Thread Interruption**: Clean shutdown on timeout
- **Memory Management**: Automatic cleanup of large trees
- **Move Validation**: Integration with ChessGame for legal moves

### Virtual Thread Usage
- **Modern Concurrency**: Uses Java 21 virtual threads
- **Timeout Management**: 35-second timeout for move selection
- **Resource Efficient**: Lower overhead than platform threads

This MCTS implementation provides a robust tree search algorithm that can be enhanced through integration with other AI systems while maintaining the core principles of Monte Carlo Tree Search.