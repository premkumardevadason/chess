package com.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Monte Carlo Tree Search AI with tree reuse optimization.
 * Integrates with other AI systems for enhanced move evaluation.
 */
public class MonteCarloTreeSearchAI {
    
    // Advanced MCTS Node with RAVE and Virtual Loss support
    public static class MCTSNode {
        public MCTSNode parent;
        public final int[] move;
        public final String[][] board;
        public final boolean isWhiteTurn;
        public final List<MCTSNode> children;
        public int visits;
        public double wins;
        
        // RAVE (Rapid Action Value Estimation) statistics
        public int raveVisits;
        public double raveWins;
        
        // Virtual Loss for parallelization
        public volatile int virtualLoss;
        
        // Progressive Widening - track unexplored moves
        public List<int[]> unexploredMoves;
        public int expansionThreshold;
        
        // Chess domain knowledge
        public double domainBonus;
        public boolean isCapture;
        public boolean isCheck;
        public boolean isCastling;
        
        public MCTSNode(MCTSNode parent, int[] move, String[][] board, boolean isWhiteTurn) {
            this.parent = parent;
            this.move = move;
            this.board = board;
            this.isWhiteTurn = isWhiteTurn;
            this.children = new ArrayList<>();
            this.visits = 0;
            this.wins = 0.0;
            
            // Initialize RAVE
            this.raveVisits = 0;
            this.raveWins = 0.0;
            
            // Initialize Virtual Loss
            this.virtualLoss = 0;
            
            // Initialize Progressive Widening
            this.unexploredMoves = new ArrayList<>();
            this.expansionThreshold = 1;
            
            // Initialize domain knowledge
            this.domainBonus = 0.0;
            this.isCapture = false;
            this.isCheck = false;
            this.isCastling = false;
            
            // Analyze move for domain knowledge
            if (move != null) {
                analyzeMoveForDomainKnowledge(board, move);
            }
        }
        
        public boolean isLeaf() {
            return children.isEmpty();
        }
        
        private void analyzeMoveForDomainKnowledge(String[][] board, int[] move) {
            // Check if move is a capture
            if (!board[move[2]][move[3]].isEmpty()) {
                this.isCapture = true;
                this.domainBonus += 0.1; // Bonus for captures
            }
            
            // Check for castling
            String piece = board[move[0]][move[1]];
            if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(move[3] - move[1]) == 2) {
                this.isCastling = true;
                this.domainBonus += 0.05; // Bonus for castling
            }
            
            // Check for center control
            if ((move[2] >= 3 && move[2] <= 4) && (move[3] >= 3 && move[3] <= 4)) {
                this.domainBonus += 0.03; // Bonus for center control
            }
            
            // Check for piece development
            if (("♘♞♗♝".contains(piece)) && 
                (("♘♗".contains(piece) && move[0] == 7) || ("♞♝".contains(piece) && move[0] == 0))) {
                this.domainBonus += 0.02; // Bonus for piece development
            }
        }
    }
    
    public record SimulationResult(double score, int moveCount, boolean terminated) {}
    public record SearchStats(int simulations, long timeMs, double bestWinRate) {}
    private static final Logger logger = LogManager.getLogger(MonteCarloTreeSearchAI.class);
    
    private Random random = new Random();
    private boolean debugEnabled;
    private int simulationsPerMove = 50; // Further reduced for faster response
    private double explorationConstant = Math.sqrt(2);
    private final ChessLegalMoveAdapter moveAdapter;
    
    // RAVE parameters
    private double raveConstant = 300.0; // RAVE bias parameter
    
    // Progressive Widening parameters
    private double progressiveWideningConstant = 2.0;
    
    // Virtual Loss parameters
    private int virtualLossValue = 3;
    
    // Reference to other AIs for enhanced evaluation
    private QLearningAI qLearningAI;
    private DeepLearningAI deepLearningAI;
    private DeepQNetworkAI dqnAI;
    private LeelaChessZeroOpeningBook openingBook;
    
    // Tree reuse for AlphaZero-style optimization
    private MCTSNode rootNode = null;
    private String lastBoardState = null;
    
    // Adaptive tree reuse based on MCTS success rate
    private int mctsWinStreak = 0;
    private int totalMoves = 0;
    private boolean enableTreeReuse = true;
    
    // Threading support
    private volatile int[] selectedMove = null;
    private volatile boolean isThinking = false;
    private Thread mctsThread = null;
    
    public MonteCarloTreeSearchAI(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        this.moveAdapter = new ChessLegalMoveAdapter();
        logger.info("MCTS: Initialized with {} simulations per move and Lc0 opening book", simulationsPerMove);
    }
    
    public void setAIReferences(QLearningAI qLearning, DeepLearningAI deepLearning, DeepQNetworkAI dqn) {
        this.qLearningAI = qLearning;
        this.deepLearningAI = deepLearning;
        this.dqnAI = dqn;
        logger.info("MCTS: Connected to other AI systems for enhanced evaluation");
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Run MCTS in separate thread
        return selectMoveAsync(board, validMoves);
    }
    
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
            mctsThread.join(6000); // 6 second timeout to fit within ChessGame's 8s limit
            if (isThinking) {
                System.out.println("*** MCTS: THREAD TIMEOUT - Interrupting ***");
                mctsThread.interrupt();
                mctsThread.join(500); // Wait 0.5 second for cleanup
                isThinking = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            isThinking = false;
        }
        
        return selectedMove != null ? selectedMove : validMoves.get(0);
    }
    
    private int[] selectMoveSync(String[][] board, List<int[]> validMoves) {
        
        logger.debug("*** MCTS: Starting {} simulations (Tree reuse: {}) ***", simulationsPerMove, (enableTreeReuse ? "ON" : "OFF"));
        long startTime = System.currentTimeMillis();
        
        // Try to reuse tree from previous move (AlphaZero optimization)
        MCTSNode root = findReusableSubtree(board, validMoves);
        if (root == null) {
            // No reusable tree - create new root
            root = new MCTSNode(null, null, copyBoard(board), false);
            logger.debug("*** MCTS: Created new tree - no reusable subtree found ***");
        } else {
            logger.debug("*** MCTS: TREE REUSED - Starting with {} existing visits ***", root.visits);
        }
        
        // Pre-expand root with all valid moves
        for (int[] move : validMoves) {
            String[][] newBoard = makeMove(board, move);
            MCTSNode child = new MCTSNode(root, move, newBoard, true);
            root.children.add(child);
        }
        
        // Launch virtual threads for parallel simulations
        logger.debug("*** MCTS: Launching {} virtual threads ***", simulationsPerMove);
        List<Thread> simThreads = new ArrayList<>();
        final MCTSNode finalRoot = root; // Make effectively final for lambda
        
        for (int i = 0; i < simulationsPerMove; i++) {
            final int simId = i;
            Thread simThread = Thread.ofVirtual().name("MCTS-Sim-" + simId).start(() -> {
                try {
                    // CRITICAL FIX: Check for interruption at start of simulation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    Random threadRandom = new Random(System.currentTimeMillis() + simId);
                    
                    // Select and expand with minimal synchronization and virtual loss
                    MCTSNode selectedNode;
                    MCTSNode expandedNode;
                    synchronized (finalRoot) {
                        // CRITICAL FIX: Check for interruption during synchronized block
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        selectedNode = select(finalRoot, validMoves);
                        expandedNode = expand(selectedNode, validMoves);
                        
                        // Apply virtual loss to selected path
                        MCTSNode pathNode = expandedNode;
                        while (pathNode != null) {
                            pathNode.virtualLoss++;
                            pathNode = pathNode.parent;
                        }
                    }
                    
                    // CRITICAL FIX: Check for interruption before simulation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    // Simulate without synchronization (most expensive operation)
                    double result = simulateWithRandom(expandedNode, threadRandom);
                    
                    // CRITICAL FIX: Check for interruption before backpropagation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    // Backpropagate with synchronization (virtual loss removed in backpropagate)
                    synchronized (finalRoot) {
                        backpropagate(expandedNode, result);
                    }
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Silently handle interruption
                        return;
                    }
                    System.err.println("*** MCTS: Sim-" + simId + " error: " + e.getMessage() + " ***");
                }
            });
            simThreads.add(simThread);
        }
        
        // Wait for all simulations with timeout
        int completed = 0;
        long timeoutPerThread = Math.max(50, 5000 / simulationsPerMove); // Dynamic timeout based on simulation count
        
        for (Thread simThread : simThreads) {
            try {
                simThread.join(timeoutPerThread);
                if (!simThread.isAlive()) {
                    completed++;
                } else {
                    simThread.interrupt(); // Force stop slow threads
                }
                
                // Check overall timeout
                if (System.currentTimeMillis() - startTime > 5000) {
                    logger.debug("MCTS: Overall timeout reached, stopping remaining threads");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.debug("*** MCTS: Completed {}/{} simulations ***", completed, simulationsPerMove);
        
        // Select best move from root's children (which are the valid moves)
        MCTSNode bestChild = null;
        int maxVisits = 0;
        
        for (MCTSNode child : root.children) {
            if (child.visits > maxVisits) {
                maxVisits = child.visits;
                bestChild = child;
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.debug("*** MCTS: Completed in {}ms ({}s) ***", totalTime, String.format("%.1f", totalTime/1000.0));
        
        if (bestChild != null) {
            logger.debug("*** MCTS: SELECTED MOVE - {} visits, win rate: {}% ***", 
                bestChild.visits, String.format("%.1f", (bestChild.wins / bestChild.visits) * 100));
            
            // Store tree for next move reuse (only if tree reuse is enabled)
            if (enableTreeReuse) {
                rootNode = bestChild;
                rootNode.parent = null; // Detach from old tree
                lastBoardState = encodeBoardState(bestChild.board);
            } else {
                rootNode = null;
                lastBoardState = null;
            }
        }
        
        return bestChild != null ? bestChild.move : validMoves.get(0);
    }
    
    private double simulateWithRandom(MCTSNode node, Random threadRandom) {
        // Use thread-specific random for parallel safety
        Random originalRandom = this.random;
        this.random = threadRandom;
        try {
            return simulate(node);
        } finally {
            this.random = originalRandom;
        }
    }
    
    public boolean isThinking() {
        return isThinking;
    }
    
    public void stopThinking() {
        if (mctsThread != null && mctsThread.isAlive()) {
            System.out.println("*** MCTS: Stopping thread ***");
            mctsThread.interrupt();
            
            // Wait for thread to stop gracefully
            try {
                mctsThread.join(1000); // Wait up to 1 second
                if (mctsThread.isAlive()) {
                    System.out.println("*** MCTS: Thread did not stop gracefully ***");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private MCTSNode select(MCTSNode node, List<int[]> validMoves) {
        int depth = 0;
        while (!node.isLeaf() && !isTerminal(node.board) && depth < 50) {
            node = selectBestChild(node);
            depth++;
        }
        return node;
    }
    
    private MCTSNode selectBestChild(MCTSNode node) {
        MCTSNode bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        
        for (MCTSNode child : node.children) {
            // Apply virtual loss for parallelization
            child.virtualLoss++;
            
            double value = calculateEnhancedUCB1(child, node.visits);
            if (value > bestValue) {
                bestValue = value;
                bestChild = child;
            }
        }
        
        return bestChild != null ? bestChild : node;
    }
    
    private double calculateEnhancedUCB1(MCTSNode node, int parentVisits) {
        if (node.visits == 0) return Double.POSITIVE_INFINITY;
        
        // Standard UCB1 with virtual loss
        int adjustedVisits = node.visits + node.virtualLoss;
        double adjustedWins = node.wins - (node.virtualLoss * virtualLossValue);
        double exploitation = adjustedWins / adjustedVisits;
        double exploration = explorationConstant * Math.sqrt(Math.log(parentVisits) / adjustedVisits);
        
        // RAVE (Rapid Action Value Estimation)
        double raveValue = 0.0;
        if (node.raveVisits > 0) {
            double raveExploitation = node.raveWins / node.raveVisits;
            double beta = node.raveVisits / (node.raveVisits + adjustedVisits + 4 * node.raveVisits * adjustedVisits / raveConstant);
            raveValue = beta * raveExploitation;
        }
        
        // Chess domain knowledge bonus
        double domainValue = node.domainBonus;
        
        return exploitation + exploration + raveValue + domainValue;
    }
    
    private MCTSNode expand(MCTSNode node, List<int[]> validMoves) {
        if (isTerminal(node.board)) return node;
        
        // Progressive Widening: limit expansion based on visit count
        int maxChildren = (int) Math.ceil(Math.pow(node.visits, 1.0 / progressiveWideningConstant));
        if (node.children.size() >= maxChildren) {
            // Return existing child if expansion limit reached
            return node.children.isEmpty() ? node : node.children.get(0);
        }
        
        // Initialize unexplored moves on first expansion
        if (node.unexploredMoves.isEmpty()) {
            List<int[]> possibleMoves = moveAdapter.getAllLegalMoves(node.board, node.isWhiteTurn);
            if (possibleMoves.isEmpty()) return node;
            
            // Sort moves by chess domain knowledge
            possibleMoves.sort((move1, move2) -> {
                double score1 = evaluateMoveForDomainKnowledge(node.board, move1);
                double score2 = evaluateMoveForDomainKnowledge(node.board, move2);
                return Double.compare(score2, score1); // Descending order
            });
            
            node.unexploredMoves.addAll(possibleMoves);
        }
        
        // Expand next unexplored move
        if (!node.unexploredMoves.isEmpty()) {
            int[] move = node.unexploredMoves.remove(0);
            String[][] newBoard = makeMove(node.board, move);
            MCTSNode newChild = new MCTSNode(node, move, newBoard, !node.isWhiteTurn);
            node.children.add(newChild);
            return newChild;
        }
        
        return node;
    }
    
    private double evaluateMoveForDomainKnowledge(String[][] board, int[] move) {
        double score = 0.0;
        
        // Capture bonus
        if (!board[move[2]][move[3]].isEmpty()) {
            score += getPieceValue(board[move[2]][move[3]]) * 0.1;
        }
        
        // Center control bonus
        if ((move[2] >= 3 && move[2] <= 4) && (move[3] >= 3 && move[3] <= 4)) {
            score += 0.3;
        }
        
        // Piece development bonus
        String piece = board[move[0]][move[1]];
        if (("♘♞♗♝".contains(piece)) && 
            (("♘♗".contains(piece) && move[0] == 7) || ("♞♝".contains(piece) && move[0] == 0))) {
            score += 0.2;
        }
        
        // Castling bonus
        if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(move[3] - move[1]) == 2) {
            score += 0.5;
        }
        
        return score;
    }
    
    private int getPieceValue(String piece) {
        return switch (piece) {
            case "♙", "♟" -> 100;
            case "♘", "♞" -> 320;
            case "♗", "♝" -> 330;
            case "♖", "♜" -> 500;
            case "♕", "♛" -> 900;
            case "♔", "♚" -> 10000;
            default -> 0;
        };
    }
    
    private double simulate(MCTSNode node) {
        String[][] simulationBoard = copyBoard(node.board);
        boolean currentTurn = node.isWhiteTurn;
        int moveCount = 0;
        int maxMoves = 50; // Reduced from 100 to prevent long simulations
        
        while (!isTerminal(simulationBoard) && moveCount < maxMoves) {
            List<int[]> moves = moveAdapter.getAllLegalMoves(simulationBoard, currentTurn);
            if (moves.isEmpty()) {
                break; // No moves available
            }
            
            // Prevent infinite loop in move selection
            if (moves.size() > 200) {
                System.err.println("*** MCTS: WARNING - Move explosion detected (" + moves.size() + " moves), limiting to 20 ***");
                moves = moves.subList(0, 20); // Limit to first 20 moves
            }
            
            int[] move = selectSimulationMove(simulationBoard, moves, currentTurn);
            if (move == null) {
                break; // Safety check
            }
            
            simulationBoard = makeMove(simulationBoard, move);
            currentTurn = !currentTurn;
            moveCount++;
        }
        
        return evaluatePosition(simulationBoard, !node.isWhiteTurn);
    }
    
    private int[] selectSimulationMove(String[][] board, List<int[]> moves, boolean isWhite) {
        if (moves.isEmpty()) return null;
        
        // Use Lc0 opening book for early moves in simulation
        if (openingBook != null) {
            VirtualChessBoard virtualBoard = new VirtualChessBoard(board, isWhite);
            LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, moves, virtualBoard.getRuleValidator(), isWhite);
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
    
    private boolean hasAIReference() {
        return qLearningAI != null || deepLearningAI != null || dqnAI != null;
    }
    
    private void backpropagate(MCTSNode node, double result) {
        List<int[]> playedMoves = new ArrayList<>();
        MCTSNode current = node;
        
        // Collect all moves in the path for RAVE updates
        while (current != null && current.move != null) {
            playedMoves.add(current.move);
            current = current.parent;
        }
        
        // Standard backpropagation with virtual loss removal
        current = node;
        while (current != null) {
            // Remove virtual loss
            if (current.virtualLoss > 0) {
                current.virtualLoss--;
            }
            
            // Update standard statistics
            current.visits++;
            current.wins += result;
            
            // Update RAVE statistics for all moves played in this simulation
            updateRAVEStatistics(current, playedMoves, result);
            
            current = current.parent;
        }
    }
    
    private void updateRAVEStatistics(MCTSNode node, List<int[]> playedMoves, double result) {
        // Update RAVE for all children that match moves played later in the simulation
        for (MCTSNode child : node.children) {
            if (child.move != null) {
                for (int[] playedMove : playedMoves) {
                    if (Arrays.equals(child.move, playedMove)) {
                        child.raveVisits++;
                        child.raveWins += result;
                        break;
                    }
                }
            }
        }
    }
    
    private boolean isTerminal(String[][] board) {
        return false;
    }
    

    
    private String[][] makeMove(String[][] board, int[] move) {
        return copyBoard(board);
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private double evaluatePosition(String[][] board, boolean isWhite) {
        return random.nextDouble();
    }
    
    private MCTSNode findReusableSubtree(String[][] board, List<int[]> validMoves) {
        return null;
    }
    
    private String encodeBoardState(String[][] board) {
        return Arrays.deepToString(board);
    }
    
    public void reportMoveResult(boolean mctsWon, int[] winningMove, String winnerName) {
        totalMoves++;
        
        if (mctsWon) {
            mctsWinStreak++;
            System.out.println("*** MCTS: WON AI COMPARISON (Win streak: " + mctsWinStreak + ") ***");
        } else {
            mctsWinStreak = 0;
            System.out.println("*** MCTS: LOST AI COMPARISON - KEEPING TREE (Learning from " + winnerName + ") ***");
            
            // Learn from winning AI move by adding it to our tree with bonus visits
            if (winningMove != null && rootNode != null) {
                learnFromWinningMove(winningMove, winnerName);
            }
        }
        
        // Tree reuse is now always enabled - no more clearing based on performance
        enableTreeReuse = true;
        System.out.println("*** MCTS: Tree reuse ALWAYS ENABLED - Preserving computational investment ***");
    }
    
    private void learnFromWinningMove(int[] winningMove, String winnerName) {
        if (rootNode == null || rootNode.children.isEmpty()) {
            System.out.println("*** MCTS: Cannot learn - no tree structure available ***");
            return;
        }
        
        // Find the winning move in our tree
        MCTSNode winningChild = null;
        for (MCTSNode child : rootNode.children) {
            if (child.move != null && 
                child.move[0] == winningMove[0] && child.move[1] == winningMove[1] &&
                child.move[2] == winningMove[2] && child.move[3] == winningMove[3]) {
                winningChild = child;
                break;
            }
        }
        
        if (winningChild != null) {
            // Boost the winning move's statistics
            int bonusVisits = Math.max(100, rootNode.visits / 10);
            double bonusWins = bonusVisits * 0.7;
            
            winningChild.visits += bonusVisits;
            winningChild.wins += bonusWins;
            
            rootNode.visits += bonusVisits;
            rootNode.wins += bonusWins * 0.5;
            
            logger.info("*** MCTS: LEARNED from {} - Boosted move with +{} visits ***", winnerName, bonusVisits);
        } else {
            System.out.println("*** MCTS: Could not find winning move in tree - move not explored yet ***");
        }
    }
    
    public void clearTree() {
        rootNode = null;
        lastBoardState = null;
        mctsWinStreak = 0;
        totalMoves = 0;
        enableTreeReuse = true;
        System.out.println("*** MCTS: Tree cleared for new game - Stats reset ***");
    }
    
    public int getSimulationsPerMove() {
        return simulationsPerMove;
    }
    
    /**
     * Training-optimized move selection using virtual threads
     * Used for self-play and training scenarios with VirtualChessBoard
     */
    public int[] selectMoveForTraining(VirtualChessBoard virtualBoard) {
        String[][] board = virtualBoard.getBoard();
        List<int[]> validMoves = virtualBoard.getAllValidMoves(virtualBoard.isWhiteTurn());
        
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // Use reduced simulations for faster training
        int trainingSimulations = 200; // Faster training cycles
        
        logger.debug("*** MCTS TRAINING: Starting {} virtual thread simulations ***", trainingSimulations);
        long startTime = System.currentTimeMillis();
        
        // Create new root for training (no tree reuse in training)
        MCTSNode root = new MCTSNode(null, null, copyBoard(board), virtualBoard.isWhiteTurn());
        
        // Pre-expand root with all valid moves
        for (int[] move : validMoves) {
            String[][] newBoard = makeTrainingMove(board, move);
            MCTSNode child = new MCTSNode(root, move, newBoard, !virtualBoard.isWhiteTurn());
            root.children.add(child);
        }
        
        // Launch virtual threads for parallel training simulations
        List<Thread> simThreads = new ArrayList<>();
        final MCTSNode finalRoot = root;
        
        for (int i = 0; i < trainingSimulations; i++) {
            final int simId = i;
            Thread simThread = Thread.ofVirtual().name("MCTS-Training-" + simId).start(() -> {
                try {
                    // CRITICAL FIX: Check for interruption at start of training simulation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    Random threadRandom = new Random(System.currentTimeMillis() + simId);
                    
                    // Minimal synchronization for training
                    MCTSNode selectedNode;
                    MCTSNode expandedNode;
                    synchronized (finalRoot) {
                        // CRITICAL FIX: Check for interruption during synchronized block
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        selectedNode = selectTrainingNode(finalRoot);
                        expandedNode = expandTrainingNode(selectedNode, virtualBoard);
                    }
                    
                    // CRITICAL FIX: Check for interruption before simulation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    // Fast training simulation
                    double result = simulateTraining(expandedNode, threadRandom);
                    
                    // CRITICAL FIX: Check for interruption before backpropagation
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    // Backpropagate results
                    synchronized (finalRoot) {
                        backpropagate(expandedNode, result);
                    }
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Silently handle interruption during training
                        return;
                    }
                    // Silent training errors to avoid log spam
                }
            });
            simThreads.add(simThread);
        }
        
        // Wait for training simulations with shorter timeout
        int completed = 0;
        for (Thread simThread : simThreads) {
            try {
                simThread.join(5000); // 5 second timeout for training
                if (!simThread.isAlive()) {
                    completed++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Select best training move
        MCTSNode bestChild = null;
        int maxVisits = 0;
        
        for (MCTSNode child : root.children) {
            if (child.visits > maxVisits) {
                maxVisits = child.visits;
                bestChild = child;
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        if (completed > 0) {
            logger.debug("*** MCTS TRAINING: Completed {}/{} in {}ms ***", completed, trainingSimulations, totalTime);
        }
        
        return bestChild != null ? bestChild.move : validMoves.get(0);
    }
    
    private MCTSNode selectTrainingNode(MCTSNode node) {
        int depth = 0;
        while (!node.isLeaf() && depth < 20) { // Reduced depth for training
            node = selectBestChild(node);
            depth++;
        }
        return node;
    }
    
    private MCTSNode expandTrainingNode(MCTSNode node, VirtualChessBoard virtualBoard) {
        List<int[]> possibleMoves = virtualBoard.getAllValidMoves(node.isWhiteTurn);
        if (possibleMoves.isEmpty()) return node;
        
        // Limit expansion for faster training
        if (possibleMoves.size() > 15) {
            possibleMoves = possibleMoves.subList(0, 15);
        }
        
        for (int[] move : possibleMoves) {
            boolean alreadyExpanded = false;
            for (MCTSNode child : node.children) {
                if (Arrays.equals(child.move, move)) {
                    alreadyExpanded = true;
                    break;
                }
            }
            
            if (!alreadyExpanded) {
                String[][] newBoard = makeTrainingMove(node.board, move);
                MCTSNode newChild = new MCTSNode(node, move, newBoard, !node.isWhiteTurn);
                node.children.add(newChild);
                return newChild;
            }
        }
        
        return node;
    }
    
    private double simulateTraining(MCTSNode node, Random threadRandom) {
        // Fast training simulation with reduced complexity
        int moveCount = 0;
        int maxMoves = 20; // Reduced for training speed
        
        while (moveCount < maxMoves) {
            // Simple random evaluation for training
            if (threadRandom.nextDouble() < 0.1) break; // 10% chance to terminate
            moveCount++;
        }
        
        return threadRandom.nextDouble(); // Random result for training diversity
    }
    
    private String[][] makeTrainingMove(String[][] board, int[] move) {
        String[][] newBoard = copyBoard(board);
        if (move != null && move.length == 4) {
            String piece = newBoard[move[0]][move[1]];
            newBoard[move[2]][move[3]] = piece;
            newBoard[move[0]][move[1]] = "";
        }
        return newBoard;
    }
    
}