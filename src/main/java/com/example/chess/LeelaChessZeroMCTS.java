package com.example.chess;

import java.util.*;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enhanced Monte Carlo Tree Search for Leela Chess Zero
 * 
 * Improvements over standard MCTS:
 * - Chess-specific node evaluation
 * - Enhanced UCB formula with chess heuristics
 * - Tactical pattern recognition
 * - Endgame optimization
 */
public class LeelaChessZeroMCTS {
    private static final Logger logger = LogManager.getLogger(LeelaChessZeroMCTS.class);
    private LeelaChessZeroNetwork neuralNetwork;
    private boolean debugEnabled;
    private final ChessLegalMoveAdapter moveAdapter;
    
    // MCTS parameters optimized for chess
    private static final int SIMULATIONS = 800; // Increased for better play quality
    private static final double C_PUCT = 1.5; // Exploration constant
    private static final double TACTICAL_BONUS = 0.2; // Bonus for tactical moves
    private static final double ENDGAME_FACTOR = 1.3; // Endgame evaluation multiplier
    private static final double PIECE_SAFETY_PENALTY = -5.0; // Strong penalty for hanging pieces
    
    private Map<String, MCTSNode> nodeCache = new ConcurrentHashMap<>();
    private volatile boolean simulationsStopped = false;
    
    public LeelaChessZeroMCTS(LeelaChessZeroNetwork neuralNetwork, boolean debugEnabled) {
        this.neuralNetwork = neuralNetwork;
        this.debugEnabled = debugEnabled;
        this.moveAdapter = new ChessLegalMoveAdapter();
        
        logger.info("*** LeelaZero MCTS: Initialized with enhanced chess algorithms ***");
    }
    
    public int[] selectBestMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        String boardKey = boardToString(board);
        MCTSNode root = nodeCache.computeIfAbsent(boardKey, k -> new MCTSNode(null, board, validMoves));
        
        // Simplified MCTS with timeout protection
        long timeoutMs = 10000; // 10 second absolute timeout
        long endTime = System.currentTimeMillis() + timeoutMs;
        int completedSimulations = 0;
        
        for (int i = 0; i < SIMULATIONS && !simulationsStopped && System.currentTimeMillis() < endTime; i++) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                runSimulation(root, copyBoard(board));
                completedSimulations++;
                
            } catch (Exception e) {
                logger.debug("LeelaZero MCTS simulation error: {}", e.getMessage());
                break;
            }
        }
        
        // Select best move based on visit count and value
        int[] bestMove = selectBestChild(root);
        
        if (debugEnabled) {
            logger.debug("*** LeelaZero MCTS: Completed " + completedSimulations + "/" + SIMULATIONS + " simulations ***");
            printTopMoves(root, 3);
        }
        
        return bestMove;
    }
    
    private void runSimulation(MCTSNode node, String[][] board) {
        List<MCTSNode> path = new ArrayList<>();
        MCTSNode current = node;
        String[][] currentBoard = copyBoard(board);
        
        // Selection phase - traverse to leaf
        while (!current.isLeaf() && !isGameOver(currentBoard)) {
            current = selectChild(current);
            path.add(current);
            
            if (current.move != null) {
                applyMove(currentBoard, current.move);
            }
        }
        
        // Expansion phase
        if (!isGameOver(currentBoard) && current.visitCount > 0) {
            expandNode(current, currentBoard);
            if (!current.children.isEmpty()) {
                current = current.children.get(0);
                path.add(current);
                if (current.move != null) {
                    applyMove(currentBoard, current.move);
                }
            }
        }
        
        // Evaluation phase - use neural network
        double value = evaluatePosition(currentBoard, current);
        
        // Backpropagation phase with synchronization
        for (int i = path.size() - 1; i >= 0; i--) {
            MCTSNode pathNode = path.get(i);
            synchronized (pathNode) {
                pathNode.visitCount++;
                pathNode.totalValue += value;
            }
            value = -value; // Flip value for opponent
        }
        
        // Update root with synchronization
        synchronized (node) {
            node.visitCount++;
            node.totalValue += value;
        }
    }
    
    private MCTSNode selectChild(MCTSNode node) {
        double bestScore = Double.NEGATIVE_INFINITY;
        MCTSNode bestChild = null;
        
        for (MCTSNode child : node.children) {
            double score = calculateUCBScore(child, node.visitCount);
            
            // Add chess-specific bonuses
            score += calculateChessBonus(child);
            
            if (score > bestScore) {
                bestScore = score;
                bestChild = child;
            }
        }
        
        return bestChild != null ? bestChild : node.children.get(0);
    }
    
    private double calculateUCBScore(MCTSNode child, int parentVisits) {
        if (child.visitCount == 0) {
            return Double.POSITIVE_INFINITY; // Unvisited nodes have highest priority
        }
        
        double exploitation = child.totalValue / child.visitCount;
        double exploration = C_PUCT * Math.sqrt(Math.log(parentVisits) / child.visitCount);
        
        return exploitation + exploration;
    }
    
    private double calculateChessBonus(MCTSNode child) {
        if (child.move == null) return 0.0;
        
        double bonus = 0.0;
        
        // CRITICAL: Piece safety check - prevent hanging valuable pieces
        String piece = child.board[child.move[0]][child.move[1]];
        if (Math.abs(getPieceValue(piece)) >= 3.0) { // Bishop, Knight, Rook, Queen
            // Create temporary board to simulate move
            String[][] tempBoard = copyBoard(child.board);
            String captured = tempBoard[child.move[2]][child.move[3]];
            tempBoard[child.move[2]][child.move[3]] = piece;
            tempBoard[child.move[0]][child.move[1]] = "";
            
            boolean pieceUnderAttack = wouldBeUnderAttack(child.move[2], child.move[3], tempBoard);
            
            if (pieceUnderAttack) {
                double capturedValue = Math.abs(getPieceValue(captured));
                double pieceValue = Math.abs(getPieceValue(piece));
                if (capturedValue < pieceValue * 0.8) {
                    bonus += PIECE_SAFETY_PENALTY; // Strong penalty for hanging valuable pieces
                    logger.debug("*** LeelaZero MCTS: PIECE SAFETY PENALTY applied - {} hanging {} ***", piece, pieceValue);
                }
            }
        }
        
        // Tactical move bonus (captures, checks)
        if (isTacticalMove(child.move, child.board)) {
            bonus += TACTICAL_BONUS;
        }
        
        // Center control bonus
        if (controlsCenter(child.move)) {
            bonus += 0.1;
        }
        
        // Piece development bonus (early game)
        if (isDevelopmentMove(child.move, child.board)) {
            bonus += 0.05;
        }
        
        // Endgame bonus
        if (isEndgame(child.board)) {
            bonus *= ENDGAME_FACTOR;
        }
        
        return bonus;
    }
    
    private boolean isTacticalMove(int[] move, String[][] board) {
        // Check if move is a capture
        String targetSquare = board[move[2]][move[3]];
        if (!targetSquare.isEmpty()) {
            return true; // Capture
        }
        
        // Check if move gives check (simplified)
        String piece = board[move[0]][move[1]];
        if (piece.equals("♛") || piece.equals("♜") || piece.equals("♗")) {
            return true; // Major piece move (potential check)
        }
        
        return false;
    }
    
    private boolean controlsCenter(int[] move) {
        int toRow = move[2];
        int toCol = move[3];
        
        // Center squares: d4, d5, e4, e5
        return (toRow == 3 || toRow == 4) && (toCol == 3 || toCol == 4);
    }
    
    private boolean isDevelopmentMove(int[] move, String[][] board) {
        String piece = board[move[0]][move[1]];
        int fromRow = move[0];
        
        // Knight or bishop moving from back rank
        if ((piece.equals("♞") || piece.equals("♝")) && fromRow == 0) {
            return true;
        }
        
        return false;
    }
    
    private boolean isEndgame(String[][] board) {
        int pieceCount = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!board[i][j].isEmpty()) {
                    pieceCount++;
                }
            }
        }
        return pieceCount <= 12; // Endgame threshold
    }
    
    private synchronized void expandNode(MCTSNode node, String[][] board) {
        if (!node.children.isEmpty()) return; // Already expanded
        if (node.validMoves == null || node.validMoves.isEmpty()) {
            return;
        }
        
        for (int[] move : node.validMoves) {
            String[][] newBoard = copyBoard(board);
            applyMove(newBoard, move);
            
            // Generate valid moves for new position - alternate turns
            boolean nextTurn = !node.isWhiteTurn;
            List<int[]> newValidMoves = moveAdapter.getAllLegalMoves(newBoard, nextTurn);
            
            MCTSNode child = new MCTSNode(move, newBoard, newValidMoves);
            child.isWhiteTurn = nextTurn;
            node.children.add(child);
        }
    }
    
    private double evaluatePosition(String[][] board, MCTSNode node) {
        try {
            // Use neural network for evaluation
            if (node.move != null) {
                return neuralNetwork.evaluateMove(board, node.move);
            } else {
                // Evaluate overall position
                return evaluatePositionHeuristic(board);
            }
        } catch (Exception e) {
            // Fallback to heuristic evaluation
            return evaluatePositionHeuristic(board);
        }
    }
    
    private double evaluatePositionHeuristic(String[][] board) {
        double score = 0.0;
        
        // Material evaluation
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                score += getPieceValue(piece);
            }
        }
        
        // Positional factors
        score += evaluatePositionalFactors(board);
        
        return Math.tanh(score / 10.0); // Normalize to [-1, 1]
    }
    
    private double getPieceValue(String piece) {
        switch (piece) {
            case "♛": return 9.0;   // Black Queen
            case "♜": return 5.0;   // Black Rook
            case "♝": return 3.0;   // Black Bishop
            case "♞": return 3.0;   // Black Knight
            case "♟": return 1.0;   // Black Pawn
            case "♕": return -9.0;  // White Queen
            case "♖": return -5.0;  // White Rook
            case "♗": return -3.0;  // White Bishop
            case "♘": return -3.0;  // White Knight
            case "♙": return -1.0;  // White Pawn
            default: return 0.0;
        }
    }
    
    private boolean wouldBeUnderAttack(int row, int col, String[][] board) {
        // Simplified attack detection for MCTS performance
        String enemyPieces = "♔♕♖♗♘♙"; // White pieces
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && enemyPieces.contains(piece)) {
                    if (canSimpleAttack(i, j, row, col, piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean canSimpleAttack(int fromRow, int fromCol, int toRow, int toCol, String piece) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": return fromRow - toRow == 1 && colDiff == 1; // White pawn
            case "♖": // Rook
                return (rowDiff == 0 || colDiff == 0) && rowDiff + colDiff <= 7;
            case "♗": // Bishop
                return rowDiff == colDiff && rowDiff <= 7;
            case "♕": // Queen (rook + bishop moves)
                return ((rowDiff == 0 || colDiff == 0) || (rowDiff == colDiff)) && rowDiff + colDiff <= 7;
            case "♘": // Knight
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": // King
                return rowDiff <= 1 && colDiff <= 1;
        }
        return false;
    }
    
    private double evaluatePositionalFactors(String[][] board) {
        double score = 0.0;
        
        // Center control
        score += evaluateCenterControl(board);
        
        // King safety
        score += evaluateKingSafety(board);
        
        // Piece mobility (simplified)
        score += evaluateMobility(board);
        
        return score;
    }
    
    private double evaluateCenterControl(String[][] board) {
        double score = 0.0;
        int[] centerSquares = {27, 28, 35, 36}; // d4, e4, d5, e5
        
        for (int square : centerSquares) {
            int row = square / 8;
            int col = square % 8;
            String piece = board[row][col];
            
            if (!piece.isEmpty()) {
                if (isBlackPiece(piece)) {
                    score += 0.1;
                } else {
                    score -= 0.1;
                }
            }
        }
        
        return score;
    }
    
    private double evaluateKingSafety(String[][] board) {
        // Simplified king safety evaluation
        return 0.0;
    }
    
    private double evaluateMobility(String[][] board) {
        // Simplified mobility evaluation
        return 0.0;
    }
    
    private boolean isBlackPiece(String piece) {
        return piece.equals("♚") || piece.equals("♛") || piece.equals("♜") || 
               piece.equals("♝") || piece.equals("♞") || piece.equals("♟");
    }
    
    private int[] selectBestChild(MCTSNode root) {
        MCTSNode bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (MCTSNode child : root.children) {
            if (child.visitCount == 0) continue;
            
            // Combine visit count and average value
            double score = (child.totalValue / child.visitCount) + 
                          Math.sqrt(child.visitCount) * 0.1;
            
            if (score > bestScore) {
                bestScore = score;
                bestChild = child;
            }
        }
        
        return bestChild != null ? bestChild.move : root.validMoves.get(0);
    }
    
    public void stopSimulations() {
        simulationsStopped = true;
        logger.info("*** LeelaZero MCTS: Simulations stopped ***");
    }
    
    public void resetSimulations() {
        simulationsStopped = false;
    }
    
    private void printTopMoves(MCTSNode root, int count) {
        List<MCTSNode> sortedChildren = new ArrayList<>(root.children);
        sortedChildren.sort((a, b) -> Integer.compare(b.visitCount, a.visitCount));
        
        logger.debug("*** LeelaZero MCTS: Top " + count + " moves:");
        for (int i = 0; i < Math.min(count, sortedChildren.size()); i++) {
            MCTSNode child = sortedChildren.get(i);
            double avgValue = child.visitCount > 0 ? child.totalValue / child.visitCount : 0.0;
            logger.debug("  " + (i + 1) + ". Move: " + Arrays.toString(child.move) + 
                             " Visits: " + child.visitCount + " Avg: " + String.format("%.3f", avgValue));
        }
    }
    
    // Utility methods
    private String boardToString(String[][] board) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : board) {
            for (String piece : row) {
                sb.append(piece).append(",");
            }
        }
        return sb.toString();
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private void applyMove(String[][] board, int[] move) {
        String piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = "";
        board[move[2]][move[3]] = piece;
    }
    
    private boolean isGameOver(String[][] board) {
        // Simplified game over check
        boolean hasBlackKing = false, hasWhiteKing = false;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j].equals("♚")) hasBlackKing = true;
                if (board[i][j].equals("♔")) hasWhiteKing = true;
            }
        }
        
        return !hasBlackKing || !hasWhiteKing;
    }
    

    
    // MCTS Node class
    private static class MCTSNode {
        int[] move;
        String[][] board;
        List<int[]> validMoves;
        List<MCTSNode> children = new ArrayList<>();
        int visitCount = 0;
        double totalValue = 0.0;
        boolean isWhiteTurn;
        
        MCTSNode(int[] move, String[][] board, List<int[]> validMoves) {
            this.move = move;
            this.board = board;
            this.validMoves = validMoves;
            this.isWhiteTurn = false; // AI plays as BLACK
        }
        
        boolean isLeaf() {
            return children.isEmpty();
        }
    }
}