package com.example.chess;

import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enhanced AlphaZero MCTS with proper PUCT algorithm and parallel simulations.
 */
public class AlphaZeroMCTS implements AlphaZeroInterfaces.MCTSEngine {
    private static final Logger logger = LogManager.getLogger(AlphaZeroMCTS.class);
    private final AlphaZeroInterfaces.NeuralNetwork neuralNetwork;
    private final AlphaZeroInterfaces.ChessRules chessRules;
    private final int simulations;
    private final double cPuct;
    private final double cPuctBase = 19652.0; // AlphaZero paper value
    private final double cPuctInit = 1.25;    // AlphaZero paper value
    private final ChessLegalMoveAdapter moveAdapter;
    
    public AlphaZeroMCTS(AlphaZeroInterfaces.NeuralNetwork neuralNetwork, AlphaZeroInterfaces.ChessRules chessRules, int simulations, double cPuct) {
        this.neuralNetwork = neuralNetwork;
        this.chessRules = chessRules;
        this.simulations = simulations;
        this.cPuct = cPuct;
        this.moveAdapter = new ChessLegalMoveAdapter();
        logger.debug("*** AlphaZero MCTS: Initialized with {} simulations ***", simulations);
    }
    
    private Random random = new Random();
    
    public int[] selectBestMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL FIX: ChessGame already filters to BLACK pieces, don't filter again
        // The validMoves list already contains only BLACK piece moves
        List<int[]> blackMoves = validMoves; // Use the pre-filtered list directly
        
        if (blackMoves.isEmpty()) return validMoves.get(0);
        
        logger.debug("*** AlphaZero MCTS: Starting {} parallel simulations ***", simulations);
        long startTime = System.currentTimeMillis();
        
        // Create root node
        AlphaZeroNode root = new AlphaZeroNode(null, null, copyBoard(board), false);
        
        // CRITICAL FIX: Expand root node BEFORE running simulations
        expand(root);
        logger.debug("*** AlphaZero MCTS: Root expanded with {} children ***", root.children.size());
        
        // Get neural network prediction for root
        AlphaZeroInterfaces.PolicyValue rootPrediction = neuralNetwork.predict(board);
        root.setPriorProbabilities(blackMoves, rootPrediction.policy);
        
        // Enhanced parallel MCTS with virtual threads
        int parallelBatches = Math.min(8, simulations / 10); // Up to 8 parallel batches
        int simulationsPerBatch = simulations / parallelBatches;
        
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        for (int batch = 0; batch < parallelBatches; batch++) {
            final int batchId = batch;
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                return runSimulationBatch(root, simulationsPerBatch, batchId);
            });
            futures.add(future);
        }
        
        // Wait for all batches to complete
        int totalCompletedSimulations = 0;
        for (CompletableFuture<Integer> future : futures) {
            try {
                totalCompletedSimulations += future.get(8, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.debug("Parallel simulation batch error: {}", e.getMessage());
            }
        }
        
        // Select move using enhanced selection criteria
        AlphaZeroNode bestChild = selectBestMoveFromRoot(root);
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.debug("*** AlphaZero MCTS: Completed {}/{} parallel simulations in {}ms ***", 
            totalCompletedSimulations, simulations, totalTime);
        
        if (bestChild != null) {
            logger.debug("*** AlphaZero: Selected move [{},{}]→[{},{}] with {} visits, value: {}, prior: {} ***", 
                bestChild.move[0], bestChild.move[1], bestChild.move[2], bestChild.move[3],
                bestChild.visits, bestChild.visits > 0 ? bestChild.totalValue / bestChild.visits : 0.0, 
                bestChild.priorProbability);
        }
        
        return bestChild != null ? bestChild.move : blackMoves.get(0);
    }
    
    private int runSimulationBatch(AlphaZeroNode root, int batchSimulations, int batchId) {
        int completed = 0;
        
        for (int i = 0; i < batchSimulations; i++) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                AlphaZeroNode leaf = select(root);
                double value = evaluate(leaf);
                backpropagate(leaf, value);
                completed++;
                
            } catch (Exception e) {
                logger.debug("Batch {} simulation error: {}", batchId, e.getMessage());
                break;
            }
        }
        
        return completed;
    }
    
    private AlphaZeroNode selectBestMoveFromRoot(AlphaZeroNode root) {
        if (root.children.isEmpty()) {
            logger.error("*** AlphaZero MCTS: ROOT HAS NO CHILDREN - This should never happen! ***");
            return null;
        }
        
        // CRITICAL FIX: Use neural network policy as PRIMARY selection criteria
        AlphaZeroInterfaces.PolicyValue rootPrediction = neuralNetwork.predict(root.board);
        
        AlphaZeroNode bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        logger.debug("*** AlphaZero MCTS: Evaluating {} child moves using neural network policy ***", root.children.size());
        
        for (AlphaZeroNode child : root.children) {
            if (child.move == null) continue;
            
            // Get neural network policy score for this move
            int policyIndex = child.move[0] * 8 * 64 + child.move[1] * 64 + child.move[2] * 8 + child.move[3];
            double policyScore = policyIndex < rootPrediction.policy.length ? rootPrediction.policy[policyIndex] : 0.001;
            
            // DEBUG: Log the hanging knight move specifically
            if (child.move[0] == 0 && child.move[1] == 1 && child.move[2] == 2 && child.move[3] == 0) {
                logger.info("*** AlphaZero MCTS: Hanging knight move [0,1]→[2,0] has policy score: {} ***", policyScore);
            }
            
            // CRITICAL FIX: Use neural network policy as PRIMARY factor (90%)
            double policyWeight = policyScore * 0.9; // DOMINANT factor - learned knowledge
            double visitWeight = child.visits > 0 ? Math.log(child.visits + 1) * 0.05 : 0.0;
            double valueWeight = child.visits > 0 ? (child.totalValue / child.visits) * 0.05 : 0.0;
            
            double combinedScore = policyWeight + visitWeight + valueWeight;
            
            // CRITICAL: Eliminate moves that neural network learned are terrible
            if (policyScore < 0.0001) {
                combinedScore = 0.0; // Completely eliminate terrible moves
                logger.debug("*** AlphaZero MCTS: ELIMINATING terrible move [{},{}]→[{},{}] with policy {} ***", 
                    child.move[0], child.move[1], child.move[2], child.move[3], policyScore);
            } else if (policyScore < 0.001) {
                combinedScore *= 0.01; // 99% penalty for very bad moves
                logger.debug("*** AlphaZero MCTS: Heavily penalizing bad move [{},{}]→[{},{}] with policy {} ***", 
                    child.move[0], child.move[1], child.move[2], child.move[3], policyScore);
            }
            
            if (combinedScore > bestScore) {
                bestScore = combinedScore;
                bestChild = child;
            }
        }
        
        if (bestChild != null) {
            logger.debug("*** AlphaZero MCTS: Selected move [{},{}]→[{},{}] with score {:.6f} ***", 
                bestChild.move[0], bestChild.move[1], bestChild.move[2], bestChild.move[3], bestScore);
        }
        
        // CRITICAL: If no good move found, select highest policy move directly
        if (bestChild == null || bestScore <= 0.0) {
            logger.debug("*** AlphaZero MCTS: No good move found, using pure neural network policy ***");
            double bestPolicy = Double.NEGATIVE_INFINITY;
            for (AlphaZeroNode child : root.children) {
                if (child.move == null) continue;
                int policyIndex = child.move[0] * 8 * 64 + child.move[1] * 64 + child.move[2] * 8 + child.move[3];
                double policyScore = policyIndex < rootPrediction.policy.length ? rootPrediction.policy[policyIndex] : 0.001;
                if (policyScore > bestPolicy) {
                    bestPolicy = policyScore;
                    bestChild = child;
                }
            }
            logger.debug("*** AlphaZero MCTS: Fallback selected move [{},{}]→[{},{}] with policy {:.6f} ***", 
                bestChild.move[0], bestChild.move[1], bestChild.move[2], bestChild.move[3], bestPolicy);
        }
        
        return bestChild;
    }
    
    private synchronized AlphaZeroNode select(AlphaZeroNode node) {
        while (!node.isLeaf()) {
            node = selectBestChild(node);
        }
        
        // Expand if not terminal
        if (!isTerminal(node.board)) {
            expand(node);
            if (!node.children.isEmpty()) {
                node = node.children.get(0); // Select first child for evaluation
            }
        }
        
        return node;
    }
    
    private AlphaZeroNode selectBestChild(AlphaZeroNode node) {
        AlphaZeroNode bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        
        // Enhanced PUCT formula from AlphaZero paper
        double dynamicCPuct = Math.log((node.visits + cPuctBase + 1) / cPuctBase) + cPuctInit;
        double sqrtParentVisits = Math.sqrt(node.visits);
        
        for (AlphaZeroNode child : node.children) {
            // Q-value (average value)
            double qValue = child.visits > 0 ? child.totalValue / child.visits : 0.0;
            
            // U-value (exploration term) with enhanced PUCT
            double uValue = dynamicCPuct * child.priorProbability * sqrtParentVisits / (1 + child.visits);
            
            // Add noise for exploration at root
            double noise = 0.0;
            if (node.parent == null && child.visits > 0) {
                noise = 0.25 * random.nextGaussian() * 0.1; // Dirichlet-like noise simulation
            }
            
            double puctValue = qValue + uValue + noise;
            
            if (puctValue > bestValue) {
                bestValue = puctValue;
                bestChild = child;
            }
        }
        
        return bestChild != null ? bestChild : node;
    }
    
    private synchronized void expand(AlphaZeroNode node) {
        if (!node.children.isEmpty()) return; // Already expanded
        
        // Use unified move adapter for consistent legal moves
        List<int[]> validMoves = moveAdapter.getAllLegalMoves(node.board, !node.isWhiteTurn);
        logger.debug("*** AlphaZero MCTS: Found {} total valid moves for expansion ***", validMoves.size());
        
        if (validMoves.isEmpty()) {
            logger.debug("*** AlphaZero MCTS: No valid moves found - terminal position ***");
            return;
        }
        
        // Get neural network prediction BEFORE creating children
        AlphaZeroInterfaces.PolicyValue prediction = neuralNetwork.predict(node.board);
        
        // CRITICAL: Filter out terrible moves based on neural network policy
        List<int[]> filteredMoves = new ArrayList<>();
        for (int[] move : validMoves) {
            int policyIndex = move[0] * 8 * 64 + move[1] * 64 + move[2] * 8 + move[3];
            double policyScore = policyIndex < prediction.policy.length ? prediction.policy[policyIndex] : 0.001;
            
            // Only include moves that neural network doesn't consider terrible
            if (policyScore >= 0.0001) {
                filteredMoves.add(move);
            } else {
                logger.debug("*** AlphaZero MCTS: Filtering out terrible move [{},{}]→[{},{}] with policy {} ***", 
                    move[0], move[1], move[2], move[3], policyScore);
            }
        }
        
        // If all moves are terrible, keep the best ones
        if (filteredMoves.isEmpty()) {
            logger.debug("*** AlphaZero MCTS: All moves are terrible, keeping top 3 ***");
            validMoves.sort((a, b) -> {
                int aIndex = a[0] * 8 * 64 + a[1] * 64 + a[2] * 8 + a[3];
                int bIndex = b[0] * 8 * 64 + b[1] * 64 + b[2] * 8 + b[3];
                double aPolicy = aIndex < prediction.policy.length ? prediction.policy[aIndex] : 0.001;
                double bPolicy = bIndex < prediction.policy.length ? prediction.policy[bIndex] : 0.001;
                return Double.compare(bPolicy, aPolicy);
            });
            filteredMoves = validMoves.subList(0, Math.min(3, validMoves.size()));
        }
        
        logger.debug("*** AlphaZero MCTS: Using {} filtered BLACK moves (from {} total) ***", 
            filteredMoves.size(), validMoves.size());
        
        // Create child nodes for filtered moves
        for (int[] move : filteredMoves) {
            String[][] newBoard = makeMove(node.board, move);
            AlphaZeroNode child = new AlphaZeroNode(node, move, newBoard, !node.isWhiteTurn);
            node.children.add(child);
        }
        
        logger.debug("*** AlphaZero MCTS: Created {} child nodes ***", node.children.size());
        
        // Set prior probabilities from neural network
        node.setPriorProbabilities(filteredMoves, prediction.policy);
    }
    
    private double evaluate(AlphaZeroNode node) {
        // Use neural network for evaluation (no random rollouts!)
        AlphaZeroInterfaces.PolicyValue prediction = neuralNetwork.predict(node.board);
        return prediction.value;
    }
    
    private void backpropagate(AlphaZeroNode node, double value) {
        while (node != null) {
            // Enhanced thread-safe backpropagation
            synchronized (node.lock) {
                node.visits++;
                node.totalValue += value;
            }
            value = -value; // Flip value for opponent
            node = node.parent;
        }
    }
    
    private boolean isTerminal(String[][] board) {
        return chessRules.isGameOver(board);
    }
    
    private List<int[]> getAllValidMoves(String[][] board, boolean forWhite) {
        return moveAdapter.getAllLegalMoves(board, forWhite);
    }
    
    private boolean isBasicValidMove(String[][] board, int[] move, boolean forWhite) {
        String piece = board[move[0]][move[1]];
        String target = board[move[2]][move[3]];
        
        if (piece.isEmpty()) return false;
        
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        if (isPieceWhite != forWhite) return false;
        
        if (!target.isEmpty()) {
            boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
            if (isTargetWhite == isPieceWhite) return false;
        }
        
        return true;
    }
    

    
    private String[][] makeMove(String[][] board, int[] move) {
        return chessRules.makeMove(board, move);
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                copy[i][j] = board[i][j] == null ? "" : board[i][j];
            }
        }
        return copy;
    }
    
    // Enhanced AlphaZero MCTS Node with thread safety
    private static class AlphaZeroNode {
        AlphaZeroNode parent;
        int[] move;
        String[][] board;
        boolean isWhiteTurn;
        List<AlphaZeroNode> children;
        volatile int visits; // Thread-safe
        volatile double totalValue; // Thread-safe
        double priorProbability;
        private final Object lock = new Object(); // For synchronization
        
        AlphaZeroNode(AlphaZeroNode parent, int[] move, String[][] board, boolean isWhiteTurn) {
            this.parent = parent;
            this.move = move;
            this.board = board;
            this.isWhiteTurn = isWhiteTurn;
            this.children = new ArrayList<>();
            this.visits = 0;
            this.totalValue = 0.0;
            this.priorProbability = 0.0;
        }
        
        boolean isLeaf() {
            return children.isEmpty();
        }
        
        void setPriorProbabilities(List<int[]> validMoves, double[] policy) {
            for (int i = 0; i < children.size() && i < validMoves.size(); i++) {
                int[] move = validMoves.get(i);
                int policyIndex = move[0] * 8 * 64 + move[1] * 64 + move[2] * 8 + move[3];
                if (policyIndex < policy.length) {
                    children.get(i).priorProbability = policy[policyIndex];
                } else {
                    children.get(i).priorProbability = 0.001; // Small default
                }
            }
        }
    }
}