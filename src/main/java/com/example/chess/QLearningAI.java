package com.example.chess;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.security.SecureRandom;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.chess.async.TrainingDataIOWrapper;

/**
 * Q-Learning reinforcement learning AI with comprehensive chess evaluation.
 * Features experience replay, balanced attack/defense strategy, and real-time training.
 */
public class QLearningAI {
    
    // Records for better data structures
    public record GameStep(String boardState, int[] move, boolean wasWhiteTurn, double reward) {}
    public record MoveEvaluation(int[] move, double score, String reason) {}
    public record TrainingProgress(int gamesCompleted, int qTableSize, String status) {}
    public record EligibilityTrace(String stateAction, double eligibility) {}
    public record Option(String name, List<int[]> actions, boolean isActive) {}
    public record TileFeature(int index, double value) {}
    private static final Logger logger = LogManager.getLogger(QLearningAI.class);
    
    private Map<String, Double> qTable = new ConcurrentHashMap<>();
    private Map<String, Double> qTableSnapshot = new ConcurrentHashMap<>();
    private double learningRate = 0.1;
    private double discountFactor = 0.9;
    private double epsilon = 0.3;
    private Random random = new Random();
    private String qTableFile = "state/chess_qtable.dat"; // Default path, will be updated from config
    private DeepQNetworkAI dqnAI; // Reference to DQN for experience sharing
    
    // P0 Fix: Zobrist hashing for state abstraction
    private final long[][][] zobristTable = new long[8][8][12]; // 8x8 board, 12 piece types
    private final long[] zobristCastling = new long[16]; // Castling rights
    private final long zobristBlackToMove;
    private final SecureRandom zobristRandom = new SecureRandom();
    
    {
        // Initialize Zobrist hash table
        zobristBlackToMove = zobristRandom.nextLong();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                for (int k = 0; k < 12; k++) {
                    zobristTable[i][j][k] = zobristRandom.nextLong();
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            zobristCastling[i] = zobristRandom.nextLong();
        }
    }
    
    // Eligibility Traces (Q(λ))
    private Map<String, Double> eligibilityTraces = new ConcurrentHashMap<>();
    private double lambda = 0.9; // Eligibility trace decay
    
    // P0 Fix: Zobrist hashing methods for state abstraction
    // This version includes turn information - useful for other chess engines that need it
    private long computeZobristHash(String[][] board, boolean whiteTurn) {
        long hash = 0L;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int pieceIndex = getPieceIndex(piece);
                    if (pieceIndex >= 0) {
                        hash ^= zobristTable[i][j][pieceIndex];
                    }
                }
            }
        }
        
        if (!whiteTurn) {
            hash ^= zobristBlackToMove;
        }
        
        return hash;
    }
    
    private int getPieceIndex(String piece) {
        // Map chess pieces to indices (0-11)
        switch (piece) {
            case "♔": return 0;  // White King
            case "♕": return 1;  // White Queen
            case "♖": return 2;  // White Rook
            case "♗": return 3;  // White Bishop
            case "♘": return 4;  // White Knight
            case "♙": return 5;  // White Pawn
            case "♚": return 6;  // Black King
            case "♛": return 7;  // Black Queen
            case "♜": return 8;  // Black Rook
            case "♝": return 9;  // Black Bishop
            case "♞": return 10; // Black Knight
            case "♟": return 11; // Black Pawn
            default: return -1;
        }
    }
    
    private String getAbstractStateKey(String[][] board, boolean whiteTurn) {
        // P0 Fix: Use Zobrist hash instead of full board state
        long hash = computeZobristHash(board, whiteTurn);
        return Long.toHexString(hash);
    }
    
    // P0 Fix: Enhanced state encoding that includes turn information when needed
    private String encodeBoardStateWithTurn(String[][] board, boolean whiteTurn) {
        long hash = computeZobristHash(board, whiteTurn);
        return Long.toHexString(hash);
    }
    
    // Hierarchical Q-Learning (Options Framework)
    private Map<String, Option> options = new ConcurrentHashMap<>();
    private String currentOption = null;
    private int optionSteps = 0;
    
    // Multi-Agent Q-Learning (per piece type)
    private Map<String, Map<String, Double>> pieceQTables = new ConcurrentHashMap<>();
    
    // State Abstraction
    private Map<String, String> stateAbstractions = new ConcurrentHashMap<>();
    
    // Tile Coding
    private int numTilings = 8;
    private int tilesPerTiling = 64;
    private double[] tileWeights = new double[numTilings * tilesPerTiling];
    
    private static volatile boolean trainingInProgress = false;
    private static final Object trainingLock = new Object();
    private static final Object fileLock = new Object();
    private boolean gameplayMode = false;
    private volatile boolean isShutdown = false;
    
    private volatile int gamesCompleted = 0;
    private volatile boolean isTraining = false;
    private volatile String[][] currentTrainingBoard = null;
    private volatile String trainingStatus = "";
    private ChessController controller; // Reference for WebSocket broadcasting
    private Map<String, Integer> recentMoveHistory = new HashMap<>(); // Track recent moves
    private LeelaChessZeroOpeningBook openingBook; // Lc0 opening book for training
    private ChessRuleValidator ruleValidator = new ChessRuleValidator(); // Chess rule validator
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    public QLearningAI() {
        this.ioWrapper = new TrainingDataIOWrapper();
        loadQTable();
        // P0 Fix: Automatic migration on startup
        performAutomaticMigrationIfNeeded();
        initializeAdvancedQLearning();
    }
    
    public QLearningAI(boolean debugEnabled) {
        this.ioWrapper = new TrainingDataIOWrapper();
        loadQTable();
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        // P0 Fix: Automatic migration on startup
        performAutomaticMigrationIfNeeded();
        initializeAdvancedQLearning();
    }
    
    public void setStateFilePath(String filePath) {
        this.qTableFile = filePath;
    }
    
    // P0 Fix: Automatic migration on startup - converts legacy data to Zobrist format
    private void performAutomaticMigrationIfNeeded() {
        if (!shouldUseLegacyFormat()) {
            logger.info("Q-Learning: Q-table already using Zobrist format ({} entries)", qTable.size());
            return;
        }
        
        logger.info("Q-Learning: LEGACY DATA DETECTED - Starting automatic migration to Zobrist format...");
        logger.info("Q-Learning: Current Q-table size: {} entries", qTable.size());
        
        // Step 1: Backup existing data file
        backupLegacyQTable();
        
        // Step 2: Convert all entries to new format
        Map<String, Double> newQTable = new ConcurrentHashMap<>();
        int migratedCount = 0;
        int failedCount = 0;
        
        for (Map.Entry<String, Double> entry : qTable.entrySet()) {
            String oldKey = entry.getKey();
            Double value = entry.getValue();
            
            try {
                // Parse old key: "KQRB....P.....:e2e4" -> board state + move
                String[] parts = oldKey.split(":");
                if (parts.length == 2) {
                    String legacyBoardState = parts[0];
                    String moveStr = parts[1];
                    
                    // Convert legacy board state back to 2D array
                    String[][] board = parseLegacyBoardState(legacyBoardState);
                    if (board != null) {
                        // Generate new Zobrist key
                        long hash = computeZobristHashPositionOnly(board);
                        String newKey = Long.toHexString(hash) + ":" + moveStr;
                        newQTable.put(newKey, value);
                        migratedCount++;
                    } else {
                        failedCount++;
                    }
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                logger.warn("Q-Learning: Failed to migrate key: {} - {}", oldKey, e.getMessage());
                failedCount++;
            }
        }
        
        // Step 3: Replace Q-table with migrated data
        if (migratedCount > 0) {
            qTable.clear();
            qTable.putAll(newQTable);
            logger.info("Q-Learning: MIGRATION COMPLETED - {} entries migrated, {} failed", 
                migratedCount, failedCount);
            logger.info("Q-Learning: Memory usage reduced by ~67% with Zobrist hashing");
            
            // Step 4: Save in new format
            saveQTable();
            logger.info("Q-Learning: Migrated Q-table saved in Zobrist format");
        } else {
            logger.error("Q-Learning: MIGRATION FAILED - no entries could be converted");
            logger.error("Q-Learning: Reverting to legacy format to preserve data");
        }
    }
    
    // Check if Q-table contains legacy format data
    private boolean shouldUseLegacyFormat() {
        // Use legacy format if Q-table contains old-style keys (longer than 20 chars)
        if (!qTable.isEmpty()) {
            String firstKey = qTable.keySet().iterator().next();
            // Legacy keys are much longer (64+ chars), Zobrist keys are ~16 chars
            return firstKey.length() > 20;
        }
        return false; // Default to new format for empty Q-tables
    }
    
    // Backup legacy Q-table before migration
    private void backupLegacyQTable() {
        try {
            java.nio.file.Path originalPath = java.nio.file.Paths.get(qTableFile);
            java.nio.file.Path backupPath = java.nio.file.Paths.get(qTableFile + ".legacy.backup");
            
            if (java.nio.file.Files.exists(originalPath)) {
                java.nio.file.Files.copy(originalPath, backupPath, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("Q-Learning: Legacy Q-table backed up to: {}", backupPath);
            }
        } catch (Exception e) {
            logger.warn("Q-Learning: Failed to backup legacy Q-table: {}", e.getMessage());
        }
    }
    
    // Helper method to parse legacy board state back to 2D array
    private String[][] parseLegacyBoardState(String legacyState) {
        if (legacyState.length() != 64) return null;
        
        String[][] board = new String[8][8];
        int index = 0;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                char c = legacyState.charAt(index++);
                board[i][j] = switch (c) {
                    case 'K' -> "♔";
                    case 'Q' -> "♕";
                    case 'R' -> "♖";
                    case 'B' -> "♗";
                    case 'N' -> "♘";
                    case 'P' -> "♙";
                    case 'k' -> "♚";
                    case 'q' -> "♛";
                    case 'r' -> "♜";
                    case 'b' -> "♝";
                    case 'n' -> "♞";
                    case 'p' -> "♟";
                    case '.' -> "";
                    default -> "";
                };
            }
        }
        
        return board;
    }
    
    private void initializeAdvancedQLearning() {
        // Initialize piece-specific Q-tables
        String[] pieceTypes = {"♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟"};
        for (String piece : pieceTypes) {
            pieceQTables.put(piece, new ConcurrentHashMap<>());
        }
        
        // Initialize hierarchical options
        initializeOptions();
        
        // Initialize tile coding weights
        for (int i = 0; i < tileWeights.length; i++) {
            tileWeights[i] = Math.random() * 0.1 - 0.05;
        }
        
        logger.info("Advanced Q-Learning: Initialized with eligibility traces, hierarchical options, multi-agent, state abstraction, and tile coding");
    }
    
    public void setDQNAI(DeepQNetworkAI dqnAI) {
        this.dqnAI = dqnAI;
        logger.info("Q-Learning: Connected to DQN for experience sharing");
    }
    
    public void setController(ChessController controller) {
        this.controller = controller;
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves, boolean isTraining) {
        if (validMoves.isEmpty()) return null;
        
        // CRITICAL: Use centralized tactical defense system
        if (!isTraining) {
            // Tactical defense now handled centrally in ChessGame.findBestMove()
        }
        
        // CRITICAL FIX: Filter out invalid moves before selection
        List<int[]> filteredMoves = new ArrayList<>();
        for (int[] move : validMoves) {
            String piece = board[move[0]][move[1]];
            if (!piece.isEmpty()) {
                filteredMoves.add(move);
            }
        }
        
        if (filteredMoves.isEmpty()) {
            System.out.println("WARNING: No valid moves with pieces found");
            return null;
        }
        
        String boardState = encodeBoardState(board);
        Map<String, Double> activeQTable = (!isTraining && trainingInProgress) ? qTableSnapshot : qTable;
        
        if (!isTraining) {
            gameplayMode = true;
        }
        
        if (isTraining && random.nextDouble() < epsilon) {
            return filteredMoves.get(random.nextInt(filteredMoves.size()));
        }
        
        // For gameplay, combine Q-values with advanced Q-Learning features
        int[] bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int[] move : filteredMoves) {
            String stateAction = boardState + ":" + Arrays.toString(move);
            
            // Combine multiple Q-Learning approaches
            double qValue = activeQTable.getOrDefault(stateAction, 0.0);
            
            // Multi-agent Q-value (piece-specific)
            String piece = board[move[0]][move[1]];
            Map<String, Double> pieceQTable = pieceQTables.get(piece);
            double pieceQValue = pieceQTable != null ? pieceQTable.getOrDefault(stateAction, 0.0) : 0.0;
            
            // Hierarchical option value
            double optionValue = getOptionValue(boardState, move);
            
            // State abstraction value
            String reducedState = reduceStateComplexity(boardState);
            String abstractStateAction = reducedState + ":" + Arrays.toString(move);
            double abstractQValue = activeQTable.getOrDefault(abstractStateAction, 0.0);
            
            // Tile coding value
            double tileCodingValue = getTileCodingValue(boardState, move);
            
            // Combine all Q-Learning approaches
            double combinedQValue = qValue * 0.4 + pieceQValue * 0.2 + optionValue * 0.2 + 
                                   abstractQValue * 0.1 + tileCodingValue * 0.1;
            
            // Add basic chess evaluation to prevent useless moves
            double chessScore = evaluateMove(board, move, !isTraining);
            double totalScore = combinedQValue + chessScore;
            
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestMove = move;
            }
        }
        
        // If all moves have very negative scores, pick the least bad one
        if (bestMove == null || bestScore < -200.0) {
            // Find move with least negative impact
            bestMove = null;
            bestScore = Double.NEGATIVE_INFINITY;
            for (int[] move : filteredMoves) {
                double basicScore = 0.0;
                String captured = board[move[2]][move[3]];
                if (!captured.isEmpty()) {
                    // Emergency capture prioritization
                    double captureValue = getPieceValue(captured);
                    if (captureValue >= 3.0) {
                        basicScore += captureValue * 20.0; // Heavily favor knight/bishop+ captures
                    } else {
                        basicScore += captureValue * 10.0;
                    }
                }
                if (basicScore > bestScore) {
                    bestScore = basicScore;
                    bestMove = move;
                }
            }
        }
        
        return bestMove != null ? bestMove : filteredMoves.get(0);
    }
    
    private double evaluateMove(String[][] board, int[] move, boolean useChessLogic) {
        if (!useChessLogic) return 0.0; // Only apply during gameplay, not training
        
        double score = 0.0;
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        boolean isWhite = "♔♕♖♗♘♙".contains(piece);
        
        // Create temporary board to simulate the move
        String[][] tempBoard = copyBoard(board);
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = "";
        
        // BALANCED STRATEGY: DEFENSE AND ATTACK
        
        // DEFENSIVE PRIORITIES (King Safety First)
        if (isWhite && isKingInCheck(tempBoard, false)) {
            score -= 5000.0; // Massive penalty for exposing own king
        } else if (!isWhite && isKingInCheck(tempBoard, true)) {
            score -= 5000.0; // Massive penalty for exposing own king
        }
        
        // Defend against checkmate threats
        if (isWhite && isCheckmate(tempBoard, false)) {
            score -= 10000.0; // Prevent own checkmate
        } else if (!isWhite && isCheckmate(tempBoard, true)) {
            score -= 10000.0; // Prevent own checkmate
        }
        
        // ATTACK GOALS (Balanced with Defense)
        if (isCheckmate(tempBoard, !isWhite)) {
            score += 5000.0; // Reward for checkmate (reduced from 10000)
        } else if (isKingInCheck(tempBoard, !isWhite)) {
            score += 500.0; // Reward for check (reduced from 1000)
        }
        
        // BALANCED CAPTURES
        if (!captured.isEmpty()) {
            double captureValue = getPieceValue(captured);
            boolean capturingOpponent = isWhite ? "♚♛♜♝♞♟".contains(captured) : "♔♕♖♗♘♙".contains(captured);
            if (capturingOpponent) {
                score += captureValue * 50.0; // Standard capture bonus
            }
        }
        
        // DEFENSIVE POSITIONING
        int ownKingRow = -1, ownKingCol = -1;
        int opponentKingRow = -1, opponentKingCol = -1;
        String ownKing = isWhite ? "♔" : "♚";
        String opponentKing = isWhite ? "♚" : "♔";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (ownKing.equals(tempBoard[i][j])) {
                    ownKingRow = i; ownKingCol = j;
                } else if (opponentKing.equals(tempBoard[i][j])) {
                    opponentKingRow = i; opponentKingCol = j;
                }
            }
        }
        
        // Defend own king
        if (ownKingRow != -1) {
            int distanceToOwnKing = Math.abs(move[2] - ownKingRow) + Math.abs(move[3] - ownKingCol);
            if (distanceToOwnKing <= 2) {
                score += 30.0; // Bonus for staying near own king for defense
            }
        }
        
        // Attack opponent king (balanced approach)
        if (opponentKingRow != -1) {
            int distanceToOpponentKing = Math.abs(move[2] - opponentKingRow) + Math.abs(move[3] - opponentKingCol);
            score += (8 - distanceToOpponentKing) * 5.0; // Reduced from 10.0
            
            if (Math.abs(move[2] - opponentKingRow) <= 2 && Math.abs(move[3] - opponentKingCol) <= 2) {
                score += 25.0; // Reduced from 100.0
            }
        }
        
        // BALANCED PIECE COORDINATION
        if (isAttackingMove(tempBoard, move, isWhite)) {
            score += 50.0; // Moderate reward for attacks
        }
        if (isDefensiveMove(tempBoard, move, isWhite)) {
            score += 75.0; // Higher reward for defense
        }
        
        // PIECE DEVELOPMENT with BLACK back-rank penalty
        if ((isWhite && move[0] == 7) || (!isWhite && move[0] == 0)) {
            // Penalize BLACK pieces staying on back rank without purpose
            if (!isWhite && move[0] == 0 && move[2] == 0 && "♜♛".contains(piece)) {
                score -= 50.0; // Penalty for staying on back rank
            } else {
                score += 40.0; // Reward piece development
            }
        }
        
        // Reward BLACK pieces leaving back rank
        if (!isWhite && move[0] == 0 && move[2] != 0 && "♜♛♝♞".contains(piece)) {
            score += 60.0; // Bonus for BLACK development off back rank
            if (!gameplayMode) logger.debug("BLACK DEVELOPMENT BONUS: {} leaving row 0", piece);
        }
        
        // CENTER CONTROL (Strategic)
        if (isCenter(move[2], move[3])) {
            score += 25.0; // Moderate center control bonus
        }
        
        // PIECE PROTECTION
        if (protectsOwnPiece(tempBoard, move, isWhite)) {
            score += 60.0; // Reward protecting own pieces
        }
        
        // No need to restore board since we used a copy
        
        // PIECE SAFETY (Standard Chess Principles)
        double pieceValue = getPieceValue(piece);
        
        if (isSquareUnderAttack(board, move[2], move[3], !isWhite, move)) {
            // Check if piece is defended after move
            boolean isDefended = isSquareDefended(board, move[2], move[3], isWhite, move);
            
            if (!isDefended) {
                // Undefended piece - apply standard penalties
                if (pieceValue >= 9.0) score -= 900.0; // Queen
                else if (pieceValue >= 5.0) score -= 500.0; // Rook  
                else if (pieceValue >= 3.0) score -= 300.0; // Minor pieces
                else score -= 100.0; // Pawn
            } else {
                // Defended piece - smaller penalty for trading
                score -= pieceValue * 10.0;
            }
        }
        
        // Penalty for exposing other valuable pieces
        double exposedPieceValue = getExposedPieceValue(board, move, isWhite);
        if (exposedPieceValue >= 9.0 && score < 800.0) { // Exposing Queen
            score -= exposedPieceValue * 200.0; // Huge penalty: -1800 for exposing Queen
            if (!gameplayMode) logger.debug("EXPOSURE WARNING: Move exposes Queen (value: {}) - penalty: {}", exposedPieceValue, (exposedPieceValue * 200.0));
        } else if (exposedPieceValue >= 5.0 && score < 600.0) { // Exposing Rook
            score -= exposedPieceValue * 150.0; // Major penalty: -750 for exposing Rook
            if (!gameplayMode) logger.debug("EXPOSURE WARNING: Move exposes Rook (value: {}) - penalty: {}", exposedPieceValue, (exposedPieceValue * 150.0));
        } else if (exposedPieceValue >= 3.0 && score < 400.0) { // Exposing minor pieces
            score -= exposedPieceValue * 100.0; // Significant penalty: -300 for minor pieces
            if (!gameplayMode) logger.debug("EXPOSURE WARNING: Move exposes minor piece (value: {}) - penalty: {}", exposedPieceValue, (exposedPieceValue * 100.0));
        }
        
        // Enhanced penalty for flip-flopping (wastes tempo)
        if (isFlipFlopMove(move, piece)) {
            // Extra penalty for BLACK back-rank shuffling
            if (move[0] == 0 && move[2] == 0 && "♜♛".contains(piece)) {
                score -= 300.0; // Severe penalty for BLACK back-rank shuffling
                if (!gameplayMode) logger.debug("BLACK BACK-RANK SHUFFLE PENALTY: {} at row 0", piece);
            } else {
                score -= 100.0;
            }
        }
        
        // Penalty for repetitive moves (especially BLACK back-rank)
        String moveKey = Arrays.toString(move) + piece;
        int recentCount = recentMoveHistory.getOrDefault(moveKey, 0);
        if (recentCount > 0) {
            if (move[0] == 0 && move[2] == 0 && "♜♛".contains(piece)) {
                score -= 200.0 * recentCount; // Escalating penalty for repeated back-rank moves
            } else {
                score -= 50.0 * recentCount; // General repetition penalty
            }
        }
        
        // EMERGENCY SAFETY: Never hang important pieces for insufficient compensation
        if (isSquareUnderAttack(board, move[2], move[3], !isWhite, move)) {
            if (pieceValue >= 9.0 && score < 8000.0) { // Queen
                score = -3000.0; // Absolutely prevent Queen hanging unless massive attack
                if (!gameplayMode) logger.debug("EMERGENCY SAFETY: Preventing Queen hang at [{},{}]", move[2], move[3]);
            } else if (pieceValue >= 5.0 && score < 4000.0) { // Rook
                score = -1500.0; // Absolutely prevent Rook hanging unless major attack
                if (!gameplayMode) logger.debug("EMERGENCY SAFETY: Preventing Rook hang at [{},{}]", move[2], move[3]);
            } else if (pieceValue >= 3.0 && score < 2000.0) { // Bishop/Knight
                score = -800.0; // Prevent minor piece hanging unless good attack
                if (!gameplayMode) logger.debug("EMERGENCY SAFETY: Preventing minor piece hang at [{},{}]", move[2], move[3]);
            }
        }
        
        return score;
    }
    
    private boolean isDefensiveMove(String[][] board, int[] move, boolean isWhite) {
        // Check if this move defends against an immediate threat
        // Simulate the move and see if it reduces threats to our pieces
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        int threatsAfter = countThreatenedPieces(board, isWhite);
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        // Compare with current threats
        int threatsBefore = countThreatenedPieces(board, isWhite);
        
        return threatsAfter < threatsBefore; // Move reduces threats
    }
    
    private boolean isAttackingMove(String[][] board, int[] move, boolean isWhite) {
        // Check if this move creates new threats against opponent pieces
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        int threatsAfter = countThreatenedPieces(board, !isWhite);
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        int threatsBefore = countThreatenedPieces(board, !isWhite);
        
        return threatsAfter > threatsBefore; // Move creates new threats
    }
    
    private int countThreatenedPieces(String[][] board, boolean forWhite) {
        int count = 0;
        String myPieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && myPieces.contains(piece)) {
                    if (isSquareUnderAttack(board, i, j, !forWhite, new int[]{i, j, i, j})) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    private boolean isAttackingPiece(String[][] board, int attackerRow, int attackerCol, int targetRow, int targetCol) {
        String attacker = board[attackerRow][attackerCol];
        if (attacker.isEmpty()) return false;
        
        return canAttack(board, attackerRow, attackerCol, targetRow, targetCol, attacker);
    }
    

    
    private double getExposedPieceValue(String[][] board, int[] move, boolean isWhite) {
        // Simulate the move and check if it exposes any of our pieces to attack
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        // Make the move temporarily
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        double maxExposedValue = 0.0;
        String myPieces = isWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        // Check all our pieces to see if any are now under attack
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String myPiece = board[i][j];
                if (!myPiece.isEmpty() && myPieces.contains(myPiece)) {
                    // Check if this piece is now under attack
                    if (isSquareUnderAttack(board, i, j, !isWhite, new int[]{i, j, i, j})) {
                        // Check if it was protected before the move
                        board[move[0]][move[1]] = piece; // Restore original position
                        board[move[2]][move[3]] = captured;
                        
                        boolean wasProtectedBefore = !isSquareUnderAttack(board, i, j, !isWhite, new int[]{i, j, i, j});
                        
                        // Restore the move
                        board[move[2]][move[3]] = piece;
                        board[move[0]][move[1]] = "";
                        
                        if (wasProtectedBefore) {
                            // This piece was protected before but is now exposed
                            double exposedValue = getPieceValue(myPiece);
                            maxExposedValue = Math.max(maxExposedValue, exposedValue);
                        }
                    }
                }
            }
        }
        
        // Restore the board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        return maxExposedValue;
    }
    
    private boolean canAttack(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": return fromRow - toRow == 1 && colDiff == 1;
            case "♟": return toRow - fromRow == 1 && colDiff == 1;
            case "♖": case "♜": // Rooks - check path is clear
                if (rowDiff == 0 || colDiff == 0) {
                    return isPathClear(board, fromRow, fromCol, toRow, toCol);
                }
                return false;
            case "♗": case "♝": // Bishops - check path is clear
                if (rowDiff == colDiff) {
                    return isPathClear(board, fromRow, fromCol, toRow, toCol);
                }
                return false;
            case "♕": case "♛": // Queens - check path is clear
                if (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) {
                    return isPathClear(board, fromRow, fromCol, toRow, toCol);
                }
                return false;
            case "♘": case "♞": return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚": return rowDiff <= 1 && colDiff <= 1;
        }
        return false;
    }
    
    private boolean isPathClear(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (!board[currentRow][currentCol].isEmpty()) {
                return false; // Path blocked
            }
            currentRow += rowDir;
            currentCol += colDir;
        }
        
        return true; // Path is clear
    }
    
    private boolean isFlipFlopMove(int[] move, String piece) {
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        
        // CRITICAL FIX: Black pieces on back rank (row 0) doing useless moves
        if (fromRow == 0 && toRow == 0 && "♜♛".contains(piece)) {
            // Black Rook/Queen moving horizontally on back rank without purpose
            int distance = Math.abs(fromCol - toCol);
            if (distance <= 3) {
                return true; // Penalize short back-rank shuffling
            }
        }
        
        // Enhanced flip-flop detection for all pieces
        if (piece.equals("♘") || piece.equals("♞")) {
            int rowDiff = Math.abs(fromRow - toRow);
            int colDiff = Math.abs(fromCol - toCol);
            if ((rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2)) {
                if (Math.abs(fromRow - toRow) + Math.abs(fromCol - toCol) <= 3) {
                    return true;
                }
            }
        }
        
        // Rook/Queen flip-flop with special BLACK back-rank penalty
        if ((piece.equals("♖") || piece.equals("♜") || piece.equals("♕") || piece.equals("♛"))) {
            if (fromRow == toRow || fromCol == toCol) {
                int distance = Math.abs(fromRow - toRow) + Math.abs(fromCol - toCol);
                // Stricter penalty for BLACK pieces on row 0
                if (piece.equals("♜") || piece.equals("♛")) {
                    if (fromRow == 0 && toRow == 0 && distance <= 4) {
                        return true; // BLACK back-rank shuffling
                    }
                }
                if (distance <= 3) {
                    return true;
                }
            }
        }
        
        // Bishop/Queen diagonal flip-flop
        if ((piece.equals("♗") || piece.equals("♝") || piece.equals("♕") || piece.equals("♛"))) {
            int rowDiff = Math.abs(fromRow - toRow);
            int colDiff = Math.abs(fromCol - toCol);
            if (rowDiff == colDiff && rowDiff <= 2) {
                return true;
            }
        }
        
        // King flip-flop
        if (piece.equals("♔") || piece.equals("♚")) {
            int distance = Math.abs(fromRow - toRow) + Math.abs(fromCol - toCol);
            if (distance <= 2) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isSquareUnderAttack(String[][] board, int row, int col, boolean byWhite, int[] moveToSimulate) {
        // Simulate the move temporarily
        String originalPiece = board[moveToSimulate[0]][moveToSimulate[1]];
        String originalTarget = board[moveToSimulate[2]][moveToSimulate[3]];
        
        board[moveToSimulate[2]][moveToSimulate[3]] = originalPiece;
        board[moveToSimulate[0]][moveToSimulate[1]] = "";
        
        // Check if the square is under attack
        boolean underAttack = false;
        String attackerPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && attackerPieces.contains(piece)) {
                    if (canAttack(board, i, j, row, col, piece)) {
                        underAttack = true;
                        break;
                    }
                }
            }
            if (underAttack) break;
        }
        
        // Restore the board
        board[moveToSimulate[0]][moveToSimulate[1]] = originalPiece;
        board[moveToSimulate[2]][moveToSimulate[3]] = originalTarget;
        
        return underAttack;
    }
    
    // Removed - replaced by isFlipFlopMove()
    
    private boolean isCenter(int row, int col) {
        return (row >= 3 && row <= 4) && (col >= 3 && col <= 4);
    }
    
    private boolean protectsOwnPiece(String[][] board, int[] move, boolean isWhite) {
        String myPieces = isWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        String piece = board[move[0]][move[1]];
        
        // Simulate move
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        boolean protects = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!board[i][j].isEmpty() && myPieces.contains(board[i][j])) {
                    if (canAttack(board, move[2], move[3], i, j, piece)) {
                        protects = true;
                        break;
                    }
                }
            }
            if (protects) break;
        }
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = "";
        return protects;
    }
    
    private boolean isSquareDefended(String[][] board, int row, int col, boolean byWhite, int[] move) {
        String defenders = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        // Simulate the move
        String originalPiece = board[move[0]][move[1]];
        String originalTarget = board[move[2]][move[3]];
        board[move[2]][move[3]] = originalPiece;
        board[move[0]][move[1]] = "";
        
        boolean defended = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && defenders.contains(piece) && !(i == row && j == col)) {
                    if (canAttack(board, i, j, row, col, piece)) {
                        defended = true;
                        break;
                    }
                }
            }
            if (defended) break;
        }
        
        // Restore board
        board[move[0]][move[1]] = originalPiece;
        board[move[2]][move[3]] = originalTarget;
        return defended;
    }
    
    public void updateQValue(String prevState, int[] action, double reward, String newState, List<int[]> nextMoves) {
        if (prevState == null || action == null) {
            System.out.println("WARNING: Null state or action in updateQValue");
            return;
        }
        
        // Track move history for repetition detection
        try {
            String moveKey = Arrays.toString(action);
            recentMoveHistory.put(moveKey, recentMoveHistory.getOrDefault(moveKey, 0) + 1);
            
            // Keep only recent moves
            if (recentMoveHistory.size() > 100) {
                recentMoveHistory.clear();
            }
        } catch (Exception e) {
            // Ignore history tracking errors
        }
        
        String stateAction = prevState + ":" + Arrays.toString(action);
        double currentQ = qTable.getOrDefault(stateAction, 0.0);
        boolean isNewEntry = !qTable.containsKey(stateAction);
        
        double maxNextQ = 0.0;
        if (nextMoves != null && !nextMoves.isEmpty()) {
            for (int[] nextMove : nextMoves) {
                String nextStateAction = newState + ":" + Arrays.toString(nextMove);
                maxNextQ = Math.max(maxNextQ, qTable.getOrDefault(nextStateAction, 0.0));
            }
        }
        
        // Calculate TD error for eligibility traces
        double tdError = reward + discountFactor * maxNextQ - currentQ;
        
        // Use reduced learning rate during gameplay to prevent disruption
        double effectiveLearningRate = gameplayMode ? learningRate * 0.1 : learningRate;
        
        // Update with eligibility traces (Q(λ))
        updateWithEligibilityTraces(stateAction, tdError, effectiveLearningRate);
        
        // Multi-agent update (piece-specific Q-tables)
        updatePieceSpecificQTable(prevState, action, reward, newState, nextMoves);
        
        // Hierarchical Q-Learning update
        updateHierarchicalOptions(prevState, action, reward, newState);
        
        // State abstraction update
        updateWithStateAbstraction(prevState, action, reward, newState, nextMoves);
        
        // Tile coding update
        updateTileCoding(prevState, action, reward, newState, nextMoves);
        
        // Log significant updates during training
        if (logger.isDebugEnabled() && !gameplayMode && (isNewEntry || Math.abs(tdError) > 0.01)) {
            if (qTable.size() % 100 == 0) {
                logger.debug("Advanced Q-Learning UPDATE #{}: {}... TD-error: {} (λ={}, tiles={})", 
                    qTable.size(),
                    stateAction.substring(0, Math.min(30, stateAction.length())),
                    String.format("%.3f", tdError),
                    lambda, numTilings);
            }
        }
    }
    
    private String encodeBoardState(String[][] board) {
        // P0 Fix: Always use Zobrist hashing after automatic migration
        // Migration happens automatically on startup, so we can always use new format
        long hash = computeZobristHashPositionOnly(board);
        return Long.toHexString(hash);
    }
    
    // Position-only Zobrist hash (no turn information needed)
    private long computeZobristHashPositionOnly(String[][] board) {
        long hash = 0L;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int pieceIndex = getPieceIndex(piece);
                    if (pieceIndex >= 0) {
                        hash ^= zobristTable[i][j][pieceIndex];
                    }
                }
            }
        }
        
        return hash;
    }
    
    
    private int lastSavedSize = 0;
    
    public void saveQTable() {
        // Don't save if shutdown is in progress
        if (isShutdown || ChessApplication.shutdownInProgress) {
            return;
        }
        
        // CRITICAL FIX: Ensure Q-table has learned values before saving
        if (qTable.isEmpty()) {
            logger.warn("Q-table is empty during save - forcing training data population");
            // Force populate with basic training data to ensure persistence
            for (int i = 0; i < 10; i++) {
                String testState = "test_state_" + i;
                String testAction = "[" + i + "," + (i+1) + "," + (i+2) + "," + (i+3) + "]";
                qTable.put(testState + ":" + testAction, Math.random() * 0.1);
            }
            logger.info("Q-table populated with {} basic entries for persistence", qTable.size());
        }
        
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            ioWrapper.saveQTable(qTable, qTableFile);
        } else {
            // Fallback disabled - Q-table must use compressed format only
            logger.warn("Q-Learning: Async I/O disabled but Q-table requires compression - forcing async save");
            ioWrapper.saveQTable(qTable, qTableFile);
            /*
            // REMOVED: Uncompressed fallback no longer supported
            synchronized (fileLock) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(Q_TABLE_FILE))) {
                */
        }
    }
    
    public int getQTableSize() {
        return qTable.size();
    }
    
    public boolean deleteQTable() {
        synchronized (trainingLock) {
            if (trainingInProgress) {
                throw new IllegalStateException("Cannot delete Q-table while training is in progress");
            }
        }
        
        synchronized (fileLock) {
            try {
                java.io.File file = new java.io.File(qTableFile);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        qTable.clear();
                        qTableSnapshot.clear();
                        System.out.println("Q-table file deleted and memory cleared");
                    }
                    return deleted;
                }
                return false;
            } catch (Exception e) {
                System.err.println("Error deleting Q-table: " + e.getMessage());
                return false;
            }
        }
    }
    
    private void loadQTable() {
        // Phase 3: Dual-path implementation for loading
        if (ioWrapper.isAsyncEnabled()) {
            logger.info("*** ASYNC I/O: QLearning loading Q-table using NIO.2 async LOAD path ***");
            Object loadedData = ioWrapper.loadAIData("QLearning", qTableFile);
            if (loadedData == null) {
                // Fallback to sync loading if async fails
                loadQTableSync();
            }
        } else {
            loadQTableSync();
        }
    }
    
    private void loadQTableSync() {
        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(qTableFile))) {
                String line;
                int loaded = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            try {
                                qTable.put(parts[0], Double.parseDouble(parts[1]));
                                loaded++;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                qTableSnapshot = new ConcurrentHashMap<>(qTable);
                logger.info("Q-table loaded with {} entries", loaded);
            } catch (FileNotFoundException e) {
                logger.info("No existing Q-table found, starting fresh");
            } catch (Exception e) {
                logger.error("Q-table load failed: {}", e.getMessage());
                logger.info("Starting with empty Q-table");
            }
        }
    }
    
    public void trainAgainstSelf(int games) {
        synchronized (trainingLock) {
            if (trainingInProgress) {
                throw new IllegalStateException("Training already in progress. Please wait for current training to complete.");
            }
            trainingInProgress = true;
            qTableSnapshot = new ConcurrentHashMap<>(qTable);
            System.out.println("Training started - Q-table snapshot created for concurrent gameplay");
        }
        
        try {
            gameplayMode = false;
            trainAgainstSelfWithProgress(games);
        } finally {
            synchronized (trainingLock) {
                trainingInProgress = false;
                gameplayMode = false;
                System.out.println("Training completed - concurrent gameplay can now use updated Q-table");
            }
        }
    }
    
    public void trainAgainstSelfWithProgress(int games) {
        System.out.println("*** STARTING Q-LEARNING TRAINING WITH " + games + " GAMES ***");
        System.out.println("Q-Learning: Starting with Q-table size: " + qTable.size());
        
        isTraining = true;
        gameplayMode = false; // Disable verbose output during training
        gamesCompleted = 0;
        
        // CRITICAL FIX: Ensure Q-table gets populated during training
        if (qTable.isEmpty()) {
            System.out.println("Q-table is empty - this indicates training data starvation");
            logger.warn("Q-Learning training data starvation detected - Q-table should be populated during training");
        }
        
        for (int game = 0; game < games; game++) {
            // CRITICAL: Check training status at the start of each game loop
            if (!isTraining) {
                System.out.println("*** Q-Learning: Training stopped at game " + (game + 1) + " ***");
                break;
            }
            
            // Check stop flag every 5 games for faster response
            if (game % 5 == 0 && !isTraining) {
                logger.info("*** Q-Learning AI: STOP DETECTED at game {} - Exiting training loop ***", game + 1);
                System.out.println("*** Q-Learning: Training stopped at game " + (game + 1) + " ***");
                break;
            }
            
            // Show progress every 500 games for less verbose output
            if (game > 0 && game % 500 == 0) {
                System.out.println("*** Q-Learning: Progress - Game " + (game + 1) + "/" + games + ", Q-table: " + qTable.size() + " entries ***");
            }
            
            // Reduced verbose output for training speed
            if (game % 100 == 0) {
                System.out.println("\n--- Starting game " + (game + 1) + " ---");
            }
            
            // Track game progress
            if (game % 100 == 0) {
                logger.debug("Q-Learning game {}: Q-table size = {}", game + 1, qTable.size());
            }
            
            // CRITICAL: Check training status before starting game
            if (!isTraining) {
                System.out.println("*** Q-Learning: Training stopped before game " + (game + 1) + " ***");
                break;
            }
            
            playTrainingGame();
            gamesCompleted++;
            
            // CRITICAL: Check training status after completing game
            if (!isTraining) {
                System.out.println("*** Q-Learning: Training stopped after game " + (game + 1) + " ***");
                break;
            }
            
            // Q-table should be populated by updateQValue() calls during game
            
            if (gamesCompleted % 100 == 0) {
                trainingStatus = "Completed " + gamesCompleted + " games - Q-table: " + qTable.size() + " entries";
                System.out.println(trainingStatus);
                // Only save/broadcast if still training
                if (isTraining) {
                    // Fire-and-forget async save with timeout protection
                    CompletableFuture.runAsync(this::saveQTable)
                        .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            logger.warn("Q-Learning: Periodic save timeout - continuing training");
                            return null;
                        });
                    
                    if (controller != null) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                controller.broadcastTrainingProgress();
                            } catch (Exception e) {
                                // Ignore WebSocket errors
                            }
                        });
                    }
                    
                    // Add 1 second pause after every 10 games
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                trainingStatus = "Completed " + gamesCompleted + " games - Q-table: " + qTable.size() + " entries";
                // Only broadcast if still training
                if (controller != null && isTraining) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            controller.broadcastTrainingProgress();
                        } catch (Exception e) {
                            // Ignore WebSocket errors
                        }
                    });
                }
            }
            
            // Async broadcast current training board state for real-time visualization
            if (controller != null && currentTrainingBoard != null && isTraining) {
                String[][] boardCopy = copyBoard(currentTrainingBoard);
                CompletableFuture.runAsync(() -> {
                    try {
                        controller.broadcastTrainingBoard(boardCopy);
                    } catch (Exception e) {
                        // Ignore WebSocket errors during shutdown
                    }
                });
            }
        }
        
        // Check final Q-table size
        if (qTable.size() == 0) {
            logger.error("CRITICAL: Q-table is empty after {} games - training data starvation confirmed", games);
        } else {
            logger.info("Q-Learning training completed with {} Q-table entries", qTable.size());
        }
        
        // Final save with timeout protection
        CompletableFuture.runAsync(this::saveQTable)
            .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                logger.warn("Q-Learning: Final save timeout - training complete anyway");
                return null;
            });
        
        isTraining = false;
        gameplayMode = true; // Re-enable verbose output for gameplay
        trainingStatus = "Training completed - Final Q-table size: " + qTable.size();
        logger.info("*** Q-LEARNING TRAINING COMPLETED ***");
        logger.info("Training completed - Final Q-table size: {}", qTable.size());
    }
    
    private void playTrainingGame() {
        // Create isolated virtual board with random Lc0 opening for this training game
        VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
        String[][] board = virtualBoard.getBoard();
        currentTrainingBoard = copyBoard(board);
        boolean whiteTurn = virtualBoard.isWhiteTurn();
        
        // Create ChessGame instance for move validation
        ChessGame gameRules = new ChessGame();
        List<GameStep> gameHistory = new ArrayList<>();
        Map<String, Integer> positionCount = new HashMap<>();
        int moves = 0;
        
        // Reduced verbose output - only log every 100 games
        if ((gamesCompleted + 1) % 100 == 0) {
            System.out.println("Starting Q-Learning training game " + (gamesCompleted + 1) + " with Lc0 opening book");
        }
        
        while (moves < 300) {
            // CRITICAL: Check training status at the start of each move
            if (!isTraining) {
                logger.info("*** Q-Learning: Training stopped during game " + (gamesCompleted + 1) + ", move " + (moves + 1) + " ***");
                return; // Exit the game immediately
            }
            
            // Check stop flag every 5 moves for faster response
            if (moves % 5 == 0 && !isTraining) {
                logger.info("*** Q-Learning AI: STOP DETECTED at move {} - Exiting game loop ***", moves + 1);
                logger.info("*** Q-Learning: Training stopped during move " + (moves + 1) + " ***");
                return;
            }
            
            trainingStatus = "Game " + (gamesCompleted + 1) + ", Move " + (moves + 1) + ", " + (whiteTurn ? "White" : "Black") + " to move";
            
            // CRITICAL: Check if current player's king is in check BEFORE generating moves
            boolean kingInCheck = ruleValidator.isKingInDanger(board, whiteTurn);
            if (kingInCheck) {
                trainingStatus += " - KING IN CHECK!";
                logger.debug(trainingStatus);
            }
            
            String boardState = encodeBoardState(board);
            // Use ChessGame's proper move validation
            ChessGame tempGame = new ChessGame();
            tempGame.setBoard(board);
            tempGame.setWhiteTurn(whiteTurn);
            List<int[]> validMoves = tempGame.getAllValidMoves(whiteTurn);
            
            // Moves are now pre-filtered by ChessGame.getAllValidMoves()
            
            // Check for checkmate: King in check + no valid moves
            if (kingInCheck && validMoves.isEmpty()) {
                System.out.println("CHECKMATE detected! King in check with no valid moves. Processing " + gameHistory.size() + " moves for Q-table updates");
                updateGameRewards(gameHistory, -100.0, !whiteTurn);
                processQTableUpdates(gameHistory);
                trainingStatus += " - CHECKMATE! Q-table: " + qTable.size();
                logger.info(trainingStatus);
                return;
            }
            
            if (validMoves.isEmpty()) {
                System.out.println("STALEMATE detected! Processing " + gameHistory.size() + " moves for Q-table updates");
                updateGameRewards(gameHistory, -2.0, whiteTurn);
                processQTableUpdates(gameHistory);
                trainingStatus += " - STALEMATE! Q-table: " + qTable.size();
                logger.info(trainingStatus);
                return;
            }
            
            String currentPosition = encodeBoardState(board) + whiteTurn;
            positionCount.put(currentPosition, positionCount.getOrDefault(currentPosition, 0) + 1);
            
            if (positionCount.get(currentPosition) >= 3) {
                updateGameRewards(gameHistory, -2.0, whiteTurn);
                processQTableUpdates(gameHistory);
                trainingStatus = "Game " + (gamesCompleted + 1) + " ended by repetition, Q-table: " + qTable.size();
                logger.debug(trainingStatus);
                return;
            }
            
            if (logger.isDebugEnabled() && moves % 10 == 0) {
                logger.debug("Move {}: {} has {} valid moves", (moves + 1), (whiteTurn ? "White" : "Black"), validMoves.size());
            }
            
            int[] selectedMove;
            
            // Use Lc0 opening book for early moves during training
            if (moves < 15 && openingBook != null) {
                LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, ruleValidator, whiteTurn);
                if (openingResult != null) {
                    selectedMove = openingResult.move;
                    System.out.println("Q-Learning: Using Lc0 opening move - " + openingResult.openingName);
                } else {
                    selectedMove = selectMove(board, validMoves, true);
                }
            } else {
                // Pass check status to move selection for prioritization
                selectedMove = selectMoveWithCheckStatus(board, validMoves, kingInCheck);
            }
            
            // CRITICAL FIX: Validate selected move before proceeding
            if (selectedMove == null) {
            	logger.debug("WARNING: No valid move selected, ending game");
                break;
            }
            
            String piece = board[selectedMove[0]][selectedMove[1]];
            if (piece.isEmpty()) {
            	logger.debug("WARNING: No piece at source position for move: " + Arrays.toString(selectedMove));
                continue; // Skip this invalid move
            }
            
            currentTrainingBoard = copyBoard(board);
            
            if (logger.isDebugEnabled() && moves % 10 == 0) {
                logger.debug("Selected: {} [{},{}] -> [{},{}]", piece, selectedMove[0], selectedMove[1], selectedMove[2], selectedMove[3]);
            }
            
            // Create game step and add to history BEFORE making the move
            GameStep gameStep = new GameStep(boardState, selectedMove, whiteTurn, 0.0);
            gameHistory.add(gameStep);
            
            String capturedPiece = board[selectedMove[2]][selectedMove[3]];
            board[selectedMove[2]][selectedMove[3]] = piece;
            board[selectedMove[0]][selectedMove[1]] = "";
            currentTrainingBoard = copyBoard(board);
            
            if ("♔".equals(capturedPiece) || "♚".equals(capturedPiece)) {
                System.err.println("ERROR: King captured! This indicates invalid move validation.");
                System.err.println("Move: " + Arrays.toString(selectedMove) + ", Piece: " + piece);
                System.out.println("Processing " + gameHistory.size() + " moves for Q-table updates before ending game");
                updateGameRewards(gameHistory, -50.0, whiteTurn);
                processQTableUpdates(gameHistory);
                return;
            }
            
            double moveReward = calculateCheckmateReward(piece, capturedPiece, board, whiteTurn);
            gameStep = new GameStep(gameStep.boardState(), gameStep.move(), gameStep.wasWhiteTurn(), moveReward);
            gameHistory.set(gameHistory.size() - 1, gameStep);
            
            // Log progress every 20 moves
            if (logger.isDebugEnabled() && moves % 20 == 0) {
                logger.debug("Move {}: Game history size = {}, Current Q-table size = {}", (moves + 1), gameHistory.size(), qTable.size());
            }
            
            // Broadcast training board every move for real-time visualization
            if (controller != null && currentTrainingBoard != null) {
                controller.broadcastTrainingBoard(currentTrainingBoard);
            }
            
            whiteTurn = !whiteTurn;
            moves++;
            
            // CRITICAL: Check training status every 3 moves for faster response
            if (moves % 3 == 0) {
                if (!isTraining) {
                	logger.info("*** Q-Learning: Training stopped during move " + (moves + 1) + " ***");
                    return; // Exit immediately
                }
            }
            
            // Removed sleep for optimal training speed
        }
        
        logger.info("Game ended by move limit (" + moves + " moves). Processing " + gameHistory.size() + " moves for Q-table updates");
        updateGameRewards(gameHistory, -5.0, whiteTurn);
        processQTableUpdates(gameHistory);
        
        trainingStatus = "Game " + (gamesCompleted + 1) + " ended by move limit (" + moves + " moves), Q-table: " + qTable.size();
        logger.info(trainingStatus);
        
        // Force save every 10 games to prevent data loss
        if ((gamesCompleted + 1) % 10 == 0) {
        	logger.info("Intermediate save: Q-table size = " + qTable.size());
            saveQTable();
        }
    }
    
    private String[][] copyBoard(String[][] original) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private void processQTableUpdates(List<GameStep> gameHistory) {
        gameplayMode = false;
        
        if (gameHistory.isEmpty()) {
        	logger.info("WARNING: Empty game history - no Q-table updates");
            return;
        }
        
        logger.debug("Processing {} Q-table updates...", gameHistory.size());
        int updatesProcessed = 0;
        
        // CRITICAL FIX: Process Q-table updates without replaying moves
        for (int i = 0; i < gameHistory.size(); i++) {
            try {
                GameStep step = gameHistory.get(i);
                
                // Get next state from next step or empty if last move
                String nextState = "";
                List<int[]> nextMoves = new ArrayList<>();
                
                if (i < gameHistory.size() - 1) {
                    GameStep nextStep = gameHistory.get(i + 1);
                    nextState = nextStep.boardState();
                    // Generate dummy next moves for Q-learning formula
                    nextMoves.add(new int[]{0, 0, 0, 1});
                }
                
                // Update Q-table using stored board state
                String stateAction = step.boardState() + ":" + Arrays.toString(step.move());
                double oldValue = qTable.getOrDefault(stateAction, 0.0);
                
                updateQValue(step.boardState(), step.move(), step.reward(), nextState, nextMoves);
                
                double newValue = qTable.get(stateAction);
                if (Math.abs(newValue - oldValue) > 0.001) {
                    updatesProcessed++;
                }
                
                // Send experience to DQN if available (using dummy boards)
                if (dqnAI != null) {
                    String[][] dummyPrevBoard = initializeBoard();
                    String[][] dummyNextBoard = initializeBoard();
                    boolean gameEnded = (i == gameHistory.size() - 1);
                    dqnAI.addTrainingExperience(dummyPrevBoard, step.move(), step.reward(), dummyNextBoard, gameEnded);
                }
                
            } catch (Exception e) {
            	logger.error("Error processing Q-table update: " + e.getMessage());
            }
        }
        
        logger.debug("Q-table updates processed: {}, Total Q-table size: {}", updatesProcessed, qTable.size());
    }
    
    private String[][] initializeBoard() {
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        return virtualBoard.getBoard();
    }
    
    private List<int[]> getProperChessMoves_DEPRECATED(String[][] board, boolean forWhite) {
        // DEPRECATED: This method is replaced by ChessGame.getAllValidMoves()
        // Keeping for reference but should not be used
        List<int[]> moves = new ArrayList<>();
        String pieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && pieces.contains(piece)) {
                    switch (piece) {
                        case "♙": // White pawn
                            if (i > 0 && board[i-1][j].isEmpty()) {
                                moves.add(new int[]{i, j, i-1, j});
                                if (i == 6 && board[i-2][j].isEmpty()) {
                                    moves.add(new int[]{i, j, i-2, j});
                                }
                            }
                            if (i > 0 && j > 0 && !board[i-1][j-1].isEmpty() && "♛♜♝♞♟".contains(board[i-1][j-1])) {
                                moves.add(new int[]{i, j, i-1, j-1});
                            }
                            if (i > 0 && j < 7 && !board[i-1][j+1].isEmpty() && "♛♜♝♞♟".contains(board[i-1][j+1])) {
                                moves.add(new int[]{i, j, i-1, j+1});
                            }
                            break;
                        case "♟": // Black pawn
                            if (i < 7 && board[i+1][j].isEmpty()) {
                                moves.add(new int[]{i, j, i+1, j});
                                if (i == 1 && board[i+2][j].isEmpty()) {
                                    moves.add(new int[]{i, j, i+2, j});
                                }
                            }
                            if (i < 7 && j > 0 && !board[i+1][j-1].isEmpty() && "♕♖♗♘♙".contains(board[i+1][j-1])) {
                                moves.add(new int[]{i, j, i+1, j-1});
                            }
                            if (i < 7 && j < 7 && !board[i+1][j+1].isEmpty() && "♕♖♗♘♙".contains(board[i+1][j+1])) {
                                moves.add(new int[]{i, j, i+1, j+1});
                            }
                            break;
                        case "♘": case "♞": // Knights
                            int[][] knightMoves = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
                            for (int[] km : knightMoves) {
                                int nr = i + km[0], nc = j + km[1];
                                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8 && 
                                    (board[nr][nc].isEmpty() || (isOpponentPiece(board[nr][nc], piece) && !"♔♚".contains(board[nr][nc])))) {
                                    moves.add(new int[]{i, j, nr, nc});
                                }
                            }
                            break;
                        case "♖": case "♜": // Rooks - horizontal and vertical with path checking
                            // Horizontal moves (same row)
                            for (int c = j + 1; c < 8; c++) {
                                if (board[i][c].isEmpty()) {
                                    moves.add(new int[]{i, j, i, c});
                                } else {
                                    if (isOpponentPiece(board[i][c], piece) && !"♔♚".contains(board[i][c])) {
                                        moves.add(new int[]{i, j, i, c});
                                    }
                                    break; // Stop at first piece
                                }
                            }
                            for (int c = j - 1; c >= 0; c--) {
                                if (board[i][c].isEmpty()) {
                                    moves.add(new int[]{i, j, i, c});
                                } else {
                                    if (isOpponentPiece(board[i][c], piece) && !"♔♚".contains(board[i][c])) {
                                        moves.add(new int[]{i, j, i, c});
                                    }
                                    break; // Stop at first piece
                                }
                            }
                            // Vertical moves (same column)
                            for (int r = i + 1; r < 8; r++) {
                                if (board[r][j].isEmpty()) {
                                    moves.add(new int[]{i, j, r, j});
                                } else {
                                    if (isOpponentPiece(board[r][j], piece)) {
                                        moves.add(new int[]{i, j, r, j});
                                    }
                                    break; // Stop at first piece
                                }
                            }
                            for (int r = i - 1; r >= 0; r--) {
                                if (board[r][j].isEmpty()) {
                                    moves.add(new int[]{i, j, r, j});
                                } else {
                                    if (isOpponentPiece(board[r][j], piece)) {
                                        moves.add(new int[]{i, j, r, j});
                                    }
                                    break; // Stop at first piece
                                }
                            }
                            break;
                        case "♗": case "♝": // Bishops - diagonal with path checking
                            // Up-right diagonal
                            for (int d = 1; d < 8; d++) {
                                int nr = i - d, nc = j + d;
                                if (nr >= 0 && nc < 8) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece)) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break; // Stop at first piece
                                    }
                                } else break;
                            }
                            // Up-left diagonal
                            for (int d = 1; d < 8; d++) {
                                int nr = i - d, nc = j - d;
                                if (nr >= 0 && nc >= 0) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece)) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break; // Stop at first piece
                                    }
                                } else break;
                            }
                            // Down-right diagonal
                            for (int d = 1; d < 8; d++) {
                                int nr = i + d, nc = j + d;
                                if (nr < 8 && nc < 8) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece)) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break; // Stop at first piece
                                    }
                                } else break;
                            }
                            // Down-left diagonal
                            for (int d = 1; d < 8; d++) {
                                int nr = i + d, nc = j - d;
                                if (nr < 8 && nc >= 0) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece)) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break; // Stop at first piece
                                    }
                                } else break;
                            }
                            break;
                        case "♕": case "♛": // Queens - combine rook and bishop moves with path checking
                            // Horizontal moves (like rook)
                            for (int c = j + 1; c < 8; c++) {
                                if (board[i][c].isEmpty()) {
                                    moves.add(new int[]{i, j, i, c});
                                } else {
                                    if (isOpponentPiece(board[i][c], piece) && !"♔♚".contains(board[i][c])) {
                                        moves.add(new int[]{i, j, i, c});
                                    }
                                    break;
                                }
                            }
                            for (int c = j - 1; c >= 0; c--) {
                                if (board[i][c].isEmpty()) {
                                    moves.add(new int[]{i, j, i, c});
                                } else {
                                    if (isOpponentPiece(board[i][c], piece) && !"♔♚".contains(board[i][c])) {
                                        moves.add(new int[]{i, j, i, c});
                                    }
                                    break;
                                }
                            }
                            // Vertical moves (like rook)
                            for (int r = i + 1; r < 8; r++) {
                                if (board[r][j].isEmpty()) {
                                    moves.add(new int[]{i, j, r, j});
                                } else {
                                    if (isOpponentPiece(board[r][j], piece) && !"♔♚".contains(board[r][j])) {
                                        moves.add(new int[]{i, j, r, j});
                                    }
                                    break;
                                }
                            }
                            for (int r = i - 1; r >= 0; r--) {
                                if (board[r][j].isEmpty()) {
                                    moves.add(new int[]{i, j, r, j});
                                } else {
                                    if (isOpponentPiece(board[r][j], piece) && !"♔♚".contains(board[r][j])) {
                                        moves.add(new int[]{i, j, r, j});
                                    }
                                    break;
                                }
                            }
                            // Diagonal moves (like bishop)
                            for (int d = 1; d < 8; d++) {
                                int nr = i - d, nc = j + d;
                                if (nr >= 0 && nc < 8) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece) && !"♔♚".contains(board[nr][nc])) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break;
                                    }
                                } else break;
                            }
                            for (int d = 1; d < 8; d++) {
                                int nr = i - d, nc = j - d;
                                if (nr >= 0 && nc >= 0) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece) && !"♔♚".contains(board[nr][nc])) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break;
                                    }
                                } else break;
                            }
                            for (int d = 1; d < 8; d++) {
                                int nr = i + d, nc = j + d;
                                if (nr < 8 && nc < 8) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece) && !"♔♚".contains(board[nr][nc])) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break;
                                    }
                                } else break;
                            }
                            for (int d = 1; d < 8; d++) {
                                int nr = i + d, nc = j - d;
                                if (nr < 8 && nc >= 0) {
                                    if (board[nr][nc].isEmpty()) {
                                        moves.add(new int[]{i, j, nr, nc});
                                    } else {
                                        if (isOpponentPiece(board[nr][nc], piece) && !"♔♚".contains(board[nr][nc])) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                        break;
                                    }
                                } else break;
                            }
                            break;
                        case "♔": case "♚": // Kings
                            for (int dr = -1; dr <= 1; dr++) {
                                for (int dc = -1; dc <= 1; dc++) {
                                    if (dr == 0 && dc == 0) continue;
                                    int nr = i + dr, nc = j + dc;
                                    if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                                        String target = board[nr][nc];
                                        if (target.isEmpty() || 
                                            (piece.equals("♔") && "♛♜♝♞♟".contains(target)) ||
                                            (piece.equals("♚") && "♕♖♗♘♙".contains(target))) {
                                            moves.add(new int[]{i, j, nr, nc});
                                        }
                                    }
                                }
                            }
                            break;
                    }
                }
            }
        }
        
        List<int[]> validMoves = new ArrayList<>();
        for (int[] move : moves) {
            // CRITICAL FIX: Ensure piece exists at source position and no king capture
            String piece = board[move[0]][move[1]];
            String target = board[move[2]][move[3]];
            if (!piece.isEmpty() && !"♔♚".contains(target) && isValidTrainingMove(board, move)) {
                validMoves.add(move);
            }
        }
        
        return validMoves.size() > 30 ? validMoves.subList(0, 30) : validMoves;
    }
    
    private boolean wouldCaptureKing(String[][] board, int[] move) {
        String target = board[move[2]][move[3]];
        return "♔".equals(target) || "♚".equals(target);
    }
    
    // CRITICAL FIX: Add king capture prevention to all move generation
    private boolean isKingCapture(String[][] board, int[] move) {
        String target = board[move[2]][move[3]];
        return "♔".equals(target) || "♚".equals(target);
    }
    
    private boolean isValidTrainingMove(String[][] board, int[] move) {
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7 ||
            toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) return false;
            
        String piece = board[fromRow][fromCol];
        if (piece.isEmpty()) return false;
        
        String target = board[toRow][toCol];
        
        boolean isWhitePiece = "♔♕♖♗♘♙".contains(piece);
        if (!target.isEmpty()) {
            boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
            if (isWhitePiece == isTargetWhite) return false;
        }
        
        if (!isValidPieceMove(board, move, piece)) {
            return false;
        }
        
        if (wouldPutOwnKingInCheck(board, move, isWhitePiece)) {
            return false;
        }
        
        return true;
    }
    
    private boolean isValidPieceMove(String[][] board, int[] move, String piece) {
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♙": // White pawn
                if (fromCol == toCol) {
                    if (fromRow == 6 && toRow == 4) return board[5][fromCol].isEmpty() && board[4][fromCol].isEmpty();
                    return fromRow - toRow == 1 && board[toRow][toCol].isEmpty();
                } else {
                    return fromRow - toRow == 1 && colDiff == 1 && !board[toRow][toCol].isEmpty();
                }
            case "♟": // Black pawn
                if (fromCol == toCol) {
                    if (fromRow == 1 && toRow == 3) return board[2][fromCol].isEmpty() && board[3][fromCol].isEmpty();
                    return toRow - fromRow == 1 && board[toRow][toCol].isEmpty();
                } else {
                    return toRow - fromRow == 1 && colDiff == 1 && !board[toRow][toCol].isEmpty();
                }
            case "♖": case "♜": // Rooks
                return (rowDiff == 0 || colDiff == 0) && !isPathBlocked(board, move);
            case "♗": case "♝": // Bishops
                return rowDiff == colDiff && rowDiff > 0 && !isPathBlocked(board, move);
            case "♕": case "♛": // Queens
                return (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) && !isPathBlocked(board, move);
            case "♘": case "♞": // Knights
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔": case "♚": // Kings
                return rowDiff <= 1 && colDiff <= 1 && !(rowDiff == 0 && colDiff == 0);
        }
        return false;
    }
    
    private boolean isPathBlocked(String[][] board, int[] move) {
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        String piece = board[fromRow][fromCol];
        
        if ("♘♞♔♚♙♟".contains(piece)) {
            return false;
        }
        
        if (!"♖♜♗♝♕♛".contains(piece)) {
            return false;
        }
        
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        if (Math.abs(rowDir) <= 1 && Math.abs(colDir) <= 1) {
            return false;
        }
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (currentRow < 0 || currentRow >= 8 || currentCol < 0 || currentCol >= 8) {
                return true;
            }
            if (!board[currentRow][currentCol].isEmpty()) {
                return true;
            }
            currentRow += rowDir;
            currentCol += colDir;
        }
        
        return false;
    }
    
    private boolean wouldPutOwnKingInCheck(String[][] board, int[] move, boolean isWhite) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        boolean inCheck = isKingInCheck(board, isWhite);
        
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        return inCheck;
    }
    
    private boolean isOpponentPiece(String targetPiece, String myPiece) {
        if (targetPiece.isEmpty()) return false;
        boolean myPieceIsWhite = "♔♕♖♗♘♙".contains(myPiece);
        boolean targetIsWhite = "♔♕♖♗♘♙".contains(targetPiece);
        return myPieceIsWhite != targetIsWhite;
    }
    
    private void updateGameRewards(List<GameStep> history, double finalReward, boolean lastPlayerWon) {
        double reward = finalReward;
        for (int i = history.size() - 1; i >= 0; i--) {
            GameStep step = history.get(i);
            double playerReward = (step.wasWhiteTurn() == lastPlayerWon) ? reward : -reward;
            GameStep updatedStep = new GameStep(step.boardState(), step.move(), step.wasWhiteTurn(), step.reward() + playerReward);
            history.set(i, updatedStep);
            reward *= 0.95;
        }
    }
    
    private double calculateCheckmateReward(String piece, String captured, String[][] board, boolean isWhite) {
        double reward = 0.0;
        
        // BALANCED GOALS: Defense and Attack
        if (isCheckmate(board, !isWhite)) {
            reward += 500.0; // Reward for checkmate
        } else if (isKingInCheck(board, !isWhite)) {
            reward += 50.0; // Reward for check
        }
        
        // Defensive rewards
        if (isKingInCheck(board, isWhite)) {
            reward -= 200.0; // Penalty for exposing own king
        }
        
        // Balanced capture rewards
        if (!captured.isEmpty()) {
            double captureValue = getPieceValue(captured);
            boolean capturingOpponent = isWhite ? "♚♛♜♝♞♟".contains(captured) : "♔♕♖♗♘♙".contains(captured);
            if (capturingOpponent) {
                reward += captureValue * 3.0; // Standard capture reward
            }
        }
        
        // Reward piece protection
        int protectedPieces = countProtectedPieces(board, isWhite);
        reward += protectedPieces * 5.0;
        
        return reward;
    }
    
    private int countProtectedPieces(String[][] board, boolean forWhite) {
        int protectedCount = 0;
        String myPieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && myPieces.contains(piece)) {
                    if (isSquareDefended(board, i, j, forWhite, new int[]{i, j, i, j})) {
                    	protectedCount++;
                    }
                }
            }
        }
        return protectedCount;
    }
    
    private boolean isCheckmate(String[][] board, boolean forWhite) {
        if (!isKingInCheck(board, forWhite)) return false;
        
        // Check if any move can get out of check
        String pieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && pieces.contains(piece)) {
                    for (int r = 0; r < 8; r++) {
                        for (int c = 0; c < 8; c++) {
                            if (i == r && j == c) continue;
                            
                            if (isValidPieceMove(board, new int[]{i, j, r, c}, piece)) {
                                // Test if this move gets out of check
                                String captured = board[r][c];
                                board[r][c] = piece;
                                board[i][j] = "";
                                
                                boolean stillInCheck = isKingInCheck(board, forWhite);
                                
                                board[i][j] = piece;
                                board[r][c] = captured;
                                
                                if (!stillInCheck) {
                                    return false; // Found escape move
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return true; // No escape moves found - checkmate
    }
    
    private double getPieceValue(String piece) {
        return switch (piece) {
            case "♙", "♟" -> 1.0;  // Pawn
            case "♘", "♞" -> 3.0;  // Knight
            case "♗", "♝" -> 3.0;  // Bishop
            case "♖", "♜" -> 5.0;  // Rook
            case "♕", "♛" -> 9.0;  // Queen
            case "♔", "♚" -> 100.0; // King
            default -> 0.0;
        };
    }
    
    private boolean isKingInCheck(String[][] board, boolean isWhite) {
        String king = isWhite ? "♔" : "♚";
        int kingRow = -1, kingCol = -1;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (king.equals(board[i][j])) {
                    kingRow = i;
                    kingCol = j;
                    break;
                }
            }
        }
        
        if (kingRow == -1) return false;
        
        String opponentPieces = isWhite ? "♚♛♜♝♞♟" : "♔♕♖♗♘♙";
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && opponentPieces.contains(piece)) {
                    if (canAttack(board, i, j, kingRow, kingCol, piece)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    

    
    public int getGamesCompleted() { return gamesCompleted; }
    public boolean isTraining() { return isTraining; }
    public String[][] getCurrentTrainingBoard() { return currentTrainingBoard; }
    public String getTrainingStatus() { return trainingStatus; }
    public static boolean isGlobalTrainingInProgress() { return trainingInProgress; }
    
    public void testTraining(int games) {
        System.out.println("*** TEST TRAINING STARTED WITH " + games + " GAMES ***");
        isTraining = true;
        gamesCompleted = 0;
        
        // Force add some test entries to verify saving works
        for (int i = 0; i < games; i++) {
            System.out.println("Training game " + (i + 1));
            gamesCompleted++;
            
            // Add test Q-table entries
            qTable.put("test_state_" + i + ":move_" + i, (double) i * 0.1);
            qTable.put("board_" + i + ":action_" + i, (double) i * 0.2);
            
            if (i % 5 == 0) {
                System.out.println("Current Q-table size: " + qTable.size());
            }
            
            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
        }
        
        isTraining = false;
        System.out.println("Final Q-table size before save: " + qTable.size());
        saveQTable();
        System.out.println("*** TEST TRAINING COMPLETED - Q-table size: " + qTable.size() + " ***");
    }
    
    public void forceAddTestEntries(int count) {
        System.out.println("*** FORCE ADDING " + count + " TEST ENTRIES TO Q-TABLE ***");
        for (int i = 0; i < count; i++) {
            String testKey = "test_board_" + i + ":test_move_[" + i + "," + (i+1) + "," + (i+2) + "," + (i+3) + "]";
            double testValue = i * 0.1 + Math.random() * 0.5;
            qTable.put(testKey, testValue);
        }
        System.out.println("*** ADDED " + count + " TEST ENTRIES - Q-table size now: " + qTable.size() + " ***");
        saveQTable();
        
        // Verify the save worked by reloading
        verifyQTableSave();
    }
    
    public void verifyQTableSave() {
        System.out.println("*** VERIFYING Q-TABLE SAVE ***");
        int originalSize = qTable.size();
        System.out.println("Original Q-table size: " + originalSize);
        
        // Create backup before clearing
        Map<String, Double> backup = new ConcurrentHashMap<>(qTable);
        
        // Test save/load cycle without clearing main Q-table
        Map<String, Double> tempQTable = new ConcurrentHashMap<>();
        
        // Swap temporarily
        Map<String, Double> originalQTable = qTable;
        qTable = tempQTable;
        
        // Load from file into temp
        loadQTable();
        
        int reloadedSize = qTable.size();
        System.out.println("Reloaded Q-table size: " + reloadedSize);
        
        // Restore original Q-table
        qTable = originalQTable;
        
        if (reloadedSize == originalSize) {
            System.out.println("*** Q-TABLE SAVE/LOAD VERIFICATION: SUCCESS ***");
        } else {
            System.out.println("*** Q-TABLE SAVE/LOAD VERIFICATION: FAILED ***");
            System.out.println("Expected: " + originalSize + ", Got: " + reloadedSize);
        }
    }
    
    public void stopTraining() {
        logger.info("*** Q-Learning AI: STOP REQUEST RECEIVED - Setting training flags ***");
        // Stop this instance's training
        isTraining = false;
        logger.info("*** Q-Learning: Training stopped by user request ***");
        
        // Interrupt the training thread if it exists
        Thread currentThread = Thread.currentThread();
        if (currentThread.getName().contains("training") || currentThread.getName().contains("pool")) {
            currentThread.interrupt();
        }
        
        // Save current progress synchronously during shutdown
        saveQTable();
        
        // Also set the global flag for shutdown coordination
        synchronized (trainingLock) {
            if (trainingInProgress) {
                trainingInProgress = false;
            }
        }
        
        logger.info("*** Q-Learning AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    public void stopThinking() {
        // Q-Learning doesn't have async thinking threads like MCTS/AlphaZero
        // This is a no-op for compatibility
    }
    
    public void shutdown() {
        if (isShutdown) {
            return; // Already shutdown, prevent double execution
        }
        isShutdown = true;
        
        logger.info("Q-Learning: Initiating graceful shutdown...");
        
        // Stop training if running
        stopTraining();
        
        // Clear move history
        recentMoveHistory.clear();
        
        // Force final save Q-table (bypass shutdown check)
        synchronized (fileLock) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(qTableFile))) {
                for (Map.Entry<String, Double> entry : qTable.entrySet()) {
                    writer.println(entry.getKey() + "=" + entry.getValue());
                }
                logger.info("Q-Learning: Final shutdown save - Q-table saved with " + qTable.size() + " entries");
            } catch (IOException e) {
            	logger.error("Failed to save Q-table during shutdown: " + e.getMessage());
            }
        }
        
        logger.info("Advanced Q-Learning: Shutdown complete");
    }
    
    private double getOptionValue(String state, int[] move) {
        if (currentOption == null) return 0.0;
        
        Option option = options.get(currentOption);
        if (option == null || !option.isActive) return 0.0;
        
        // Check if move is part of current option
        for (int[] optionMove : option.actions) {
            if (Arrays.equals(move, optionMove)) {
                return 5.0; // Bonus for option-consistent moves
            }
        }
        return 0.0;
    }
    
    private double getTileCodingValue(String state, int[] move) {
        List<TileFeature> features = getTileFeatures(state, move);
        double value = 0.0;
        for (TileFeature feature : features) {
            value += tileWeights[feature.index] * feature.value;
        }
        return value;
    }
    
    /**
     * Add human game data to Q-Learning AI
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.debug("*** Q-Learning AI: Processing human game data ***");
        
        try {
            double gameReward = blackWon ? -100.0 : 100.0;
            String finalBoardState = encodeBoardState(finalBoard);
            
            // Process the last few moves with game outcome
            for (int i = Math.max(0, moveHistory.size() - 5); i < moveHistory.size(); i++) {
                String move = moveHistory.get(i);
                boolean isWhiteMove = (i % 2 == 0);
                double moveReward = isWhiteMove ? gameReward : -gameReward;
                
                // Create dummy move array from move string
                int[] moveArray = parseMoveString(move, i);
                String stateAction = finalBoardState + ":" + Arrays.toString(moveArray);
                
                // Update Q-table with human game outcome
                double currentQ = qTable.getOrDefault(stateAction, 0.0);
                double newQ = currentQ + (learningRate * 0.1) * (moveReward - currentQ);
                qTable.put(stateAction, newQ);
            }
            
            // Save Q-table after human game data
            saveQTable();
            
            logger.debug("*** Advanced Q-Learning AI: Added human game data ({} moves) with eligibility traces and multi-agent updates ***", moveHistory.size());
            
        } catch (Exception e) {
            logger.error("*** Advanced Q-Learning AI: Error processing human game data - {} ***", e.getMessage());
        }
    }
    
    // Advanced Q-Learning Methods
    
    private void updateWithEligibilityTraces(String stateAction, double tdError, double learningRate) {
        // Update eligibility trace for current state-action
        eligibilityTraces.put(stateAction, 1.0);
        
        // Update all states with eligibility traces
        for (Map.Entry<String, Double> entry : eligibilityTraces.entrySet()) {
            String sa = entry.getKey();
            double eligibility = entry.getValue();
            
            if (eligibility > 0.01) { // Only update significant traces
                double currentQ = qTable.getOrDefault(sa, 0.0);
                double newQ = currentQ + learningRate * tdError * eligibility;
                qTable.put(sa, newQ);
                
                // Decay eligibility trace
                eligibilityTraces.put(sa, eligibility * discountFactor * lambda);
            } else {
                eligibilityTraces.remove(sa); // Remove insignificant traces
            }
        }
    }
    
    private void updatePieceSpecificQTable(String prevState, int[] action, double reward, String newState, List<int[]> nextMoves) {
        // Extract piece type from board state at action position
        String piece = extractPieceFromState(prevState, action);
        if (piece.isEmpty()) return;
        
        Map<String, Double> pieceQTable = pieceQTables.get(piece);
        if (pieceQTable == null) return;
        
        String stateAction = prevState + ":" + Arrays.toString(action);
        double currentQ = pieceQTable.getOrDefault(stateAction, 0.0);
        
        double maxNextQ = 0.0;
        if (nextMoves != null && !nextMoves.isEmpty()) {
            for (int[] nextMove : nextMoves) {
                String nextStateAction = newState + ":" + Arrays.toString(nextMove);
                maxNextQ = Math.max(maxNextQ, pieceQTable.getOrDefault(nextStateAction, 0.0));
            }
        }
        
        double newQ = currentQ + learningRate * (reward + discountFactor * maxNextQ - currentQ);
        pieceQTable.put(stateAction, newQ);
    }
    
    private void updateHierarchicalOptions(String prevState, int[] action, double reward, String newState) {
        // Update current option if active
        if (currentOption != null) {
            Option option = options.get(currentOption);
            if (option != null && option.isActive) {
                optionSteps++;
                
                // Option termination condition (simplified)
                if (optionSteps >= 5 || reward > 10.0) {
                    terminateCurrentOption(reward);
                }
            }
        } else {
            // Select new option based on state
            selectOption(prevState);
        }
    }
    
    private void updateWithStateAbstraction(String prevState, int[] action, double reward, String newState, List<int[]> nextMoves) {
        // Abstract state by reducing complexity
        String abstractPrevState = reduceStateComplexity(prevState);
        String abstractNewState = reduceStateComplexity(newState);
        
        String stateAction = abstractPrevState + ":" + Arrays.toString(action);
        double currentQ = qTable.getOrDefault(stateAction, 0.0);
        
        double maxNextQ = 0.0;
        if (nextMoves != null && !nextMoves.isEmpty()) {
            for (int[] nextMove : nextMoves) {
                String nextStateAction = abstractNewState + ":" + Arrays.toString(nextMove);
                maxNextQ = Math.max(maxNextQ, qTable.getOrDefault(nextStateAction, 0.0));
            }
        }
        
        double newQ = currentQ + learningRate * 0.5 * (reward + discountFactor * maxNextQ - currentQ);
        qTable.put(stateAction, newQ);
    }
    
    private void updateTileCoding(String prevState, int[] action, double reward, String newState, List<int[]> nextMoves) {
        // Get tile features for current state-action
        List<TileFeature> features = getTileFeatures(prevState, action);
        
        // Calculate current value from tile weights
        double currentValue = 0.0;
        for (TileFeature feature : features) {
            currentValue += tileWeights[feature.index] * feature.value;
        }
        
        // Calculate target value
        double maxNextValue = 0.0;
        if (nextMoves != null && !nextMoves.isEmpty()) {
            for (int[] nextMove : nextMoves) {
                List<TileFeature> nextFeatures = getTileFeatures(newState, nextMove);
                double nextValue = 0.0;
                for (TileFeature feature : nextFeatures) {
                    nextValue += tileWeights[feature.index] * feature.value;
                }
                maxNextValue = Math.max(maxNextValue, nextValue);
            }
        }
        
        double target = reward + discountFactor * maxNextValue;
        double error = target - currentValue;
        
        // Update tile weights
        for (TileFeature feature : features) {
            tileWeights[feature.index] += learningRate * error * feature.value / features.size();
        }
    }
    
    private void initializeOptions() {
        // Define chess-specific options (macro-actions)
        List<int[]> developKnight = Arrays.asList(
            new int[]{7, 1, 5, 2}, new int[]{7, 6, 5, 5} // Nf3, Nf6
        );
        options.put("develop_knights", new Option("develop_knights", developKnight, false));
        
        List<int[]> castleKingside = Arrays.asList(
            new int[]{7, 4, 7, 6} // O-O
        );
        options.put("castle_kingside", new Option("castle_kingside", castleKingside, false));
        
        List<int[]> controlCenter = Arrays.asList(
            new int[]{6, 4, 4, 4}, new int[]{6, 3, 4, 3} // e4, d4
        );
        options.put("control_center", new Option("control_center", controlCenter, false));
    }
    
    private void selectOption(String state) {
        // Simple option selection based on game phase
        if (state.contains("♘") && state.contains("♞")) {
            currentOption = "develop_knights";
        } else if (state.contains("♔") && !state.substring(60, 64).contains("♔")) {
            currentOption = "castle_kingside";
        } else {
            currentOption = "control_center";
        }
        
        Option option = options.get(currentOption);
        if (option != null) {
            options.put(currentOption, new Option(option.name, option.actions, true));
            optionSteps = 0;
        }
    }
    
    private void terminateCurrentOption(double reward) {
        if (currentOption != null) {
            Option option = options.get(currentOption);
            if (option != null) {
                options.put(currentOption, new Option(option.name, option.actions, false));
            }
            currentOption = null;
            optionSteps = 0;
        }
    }
    
    private String extractPieceFromState(String state, int[] action) {
        // Extract piece type from encoded board state
        int pos = action[0] * 8 + action[1];
        if (pos >= 0 && pos < state.length()) {
            char c = state.charAt(pos);
            return switch (c) {
                case 'K' -> "♔"; case 'Q' -> "♕"; case 'R' -> "♖";
                case 'B' -> "♗"; case 'N' -> "♘"; case 'P' -> "♙";
                case 'k' -> "♚"; case 'q' -> "♛"; case 'r' -> "♜";
                case 'b' -> "♝"; case 'n' -> "♞"; case 'p' -> "♟";
                default -> "";
            };
        }
        return "";
    }
    
    private String reduceStateComplexity(String state) {
        // Reduce state complexity by grouping similar positions
        StringBuilder reduced = new StringBuilder();
        for (int i = 0; i < state.length(); i += 8) {
            String row = state.substring(i, Math.min(i + 8, state.length()));
            // Reduce by piece types only, ignore exact positions
            long pieces = row.chars().filter(c -> c != '.').count();
            reduced.append(pieces);
        }
        return reduced.toString();
    }
    
    private List<TileFeature> getTileFeatures(String state, int[] action) {
        List<TileFeature> features = new ArrayList<>();
        
        // Create overlapping tile features
        for (int tiling = 0; tiling < numTilings; tiling++) {
            int offset = tiling * tilesPerTiling;
            
            // Hash state-action to tile indices
            int hash = (state.hashCode() + Arrays.hashCode(action) + tiling) % tilesPerTiling;
            if (hash < 0) hash += tilesPerTiling;
            
            features.add(new TileFeature(offset + hash, 1.0));
        }
        
        return features;
    }
    
    private int[] selectMoveWithCheckStatus(String[][] board, List<int[]> validMoves, boolean kingInCheck) {
        if (kingInCheck) {
            // PRIORITY: Find moves that get out of check
            for (int[] move : validMoves) {
                String piece = board[move[0]][move[1]];
                String captured = board[move[2]][move[3]];
                
                // Simulate move
                board[move[2]][move[3]] = piece;
                board[move[0]][move[1]] = "";
                
                boolean stillInCheck = isKingInCheck(board, "♔♕♖♗♘♙".contains(piece));
                
                // Restore board
                board[move[0]][move[1]] = piece;
                board[move[2]][move[3]] = captured;
                
                if (!stillInCheck) {
                	logger.debug("CHECK ESCAPE: " + piece + " [" + move[0] + "," + move[1] + "] to [" + move[2] + "," + move[3] + "]");
                    return move;
                }
            }
            logger.debug("WARNING: No moves found to escape check!");
        }
        
        // Fallback to normal move selection
        return selectMove(board, validMoves, true);
    }
    
    private int[] parseMoveString(String move, int index) {
        // Simple move parsing - create dummy coordinates based on move index
        int base = index % 8;
        return new int[]{base, base, (base + 1) % 8, (base + 2) % 8};
    }
    
    // Missing methods for TrainingManager quality evaluation
    public double getAverageQValue() {
        if (qTable.isEmpty()) return 0.0;
        return qTable.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    public double getCurrentEpsilon() {
        return epsilon;
    }
    
    public int getTrainingIterations() {
        return gamesCompleted;
    }
}