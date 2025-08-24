package com.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.chess.async.TrainingDataIOWrapper;


/**
 * Enhanced AlphaZero neural network with ResNet blocks and position augmentation.
 */
public class AlphaZeroNeuralNetwork implements AlphaZeroInterfaces.NeuralNetwork {
    private static final Logger logger = LogManager.getLogger(AlphaZeroNeuralNetwork.class);
    private Random random = new Random();
    
    // Enhanced neural network with ResNet-like architecture simulation
    private Map<String, AlphaZeroInterfaces.PolicyValue> positionCache = new ConcurrentHashMap<>();
    private Map<String, Double> residualCache = new ConcurrentHashMap<>(); // ResNet residual connections
    private int trainingIterations = 0;
    private int trainingEpisodes = 0; // CRITICAL FIX: Track actual episodes (self-play games)
    private boolean isTraining = false;
    private boolean modelLoaded = false; // Prevent multiple loads
    private final int resNetBlocks = 8; // Simulate 8 ResNet blocks
    
    // Phase 3: Async I/O capability (same pattern as LeelaZero)
    private TrainingDataIOWrapper ioWrapper;
    
    public AlphaZeroNeuralNetwork(boolean debugEnabled) {
        this.ioWrapper = new TrainingDataIOWrapper();
        loadModelData();
        logger.debug("*** AlphaZero NN: Initialized with {} cached positions ***", positionCache.size());
    }
    
    public AlphaZeroInterfaces.PolicyValue predict(String[][] board) {
        String boardState = encodeBoardState(board);
        
        // Check cache first
        if (positionCache.containsKey(boardState)) {
            AlphaZeroInterfaces.PolicyValue cached = positionCache.get(boardState);
            
            // DEBUG: Check if we have learned knowledge about the hanging knight move
            int hangingKnightIndex = 0 * 8 * 64 + 1 * 64 + 2 * 8 + 0; // [0,1] → [2,0]
            if (hangingKnightIndex < cached.policy.length) {
                double hangingKnightPolicy = cached.policy[hangingKnightIndex];
                if (hangingKnightPolicy < 0.001) {
                    logger.debug("*** AlphaZero NN: CACHED position has learned to avoid hanging knight [0,1]→[2,0] (policy: {}) ***", hangingKnightPolicy);
                }
            }
            
            return cached;
        }
        
        // Simulate neural network prediction
        AlphaZeroInterfaces.PolicyValue result = simulateNeuralNetworkPrediction(board);
        
        // Cache result
        if (positionCache.size() < 10000) { // Limit cache size
            positionCache.put(boardState, result);
        }
        
        return result;
    }
    
    private AlphaZeroInterfaces.PolicyValue simulateNeuralNetworkPrediction(String[][] board) {
        // Enhanced ResNet-like prediction with residual connections
        String boardKey = encodeBoardState(board);
        
        // Simulate ResNet forward pass through multiple blocks
        double[] features = extractBoardFeatures(board);
        
        // Apply residual blocks
        for (int block = 0; block < resNetBlocks; block++) {
            features = applyResidualBlock(features, block, boardKey);
        }
        
        // Generate policy with enhanced intelligence
        double[] policy = generateEnhancedPolicy(board, features);
        
        // Generate value with residual connections
        double value = generateEnhancedValue(board, features);
        
        return new AlphaZeroInterfaces.PolicyValue(policy, value);
    }
    
    private double[] extractBoardFeatures(String[][] board) {
        // Extract 256 features from board position
        double[] features = new double[256];
        int idx = 0;
        
        // Piece positions and types (64 squares * 2 = 128 features)
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                features[idx++] = encodePieceType(piece);
                features[idx++] = encodePieceColor(piece);
            }
        }
        
        // Positional features (128 features)
        for (int i = 0; i < 128; i++) {
            features[idx++] = random.nextGaussian() * 0.1; // Simulate learned features
        }
        
        return features;
    }
    
    private double[] applyResidualBlock(double[] input, int blockId, String boardKey) {
        // Simulate ResNet residual block: output = input + F(input)
        double[] output = new double[input.length];
        String residualKey = boardKey + "_block_" + blockId;
        
        // Check residual cache
        Double cachedResidual = residualCache.get(residualKey);
        double residualStrength = cachedResidual != null ? cachedResidual : random.nextGaussian() * 0.1;
        
        for (int i = 0; i < input.length; i++) {
            // Residual connection: output = input + learned_transformation(input)
            double transformation = Math.tanh(input[i] + residualStrength);
            output[i] = input[i] + transformation * 0.1; // Skip connection
        }
        
        // Cache residual for future use
        residualCache.put(residualKey, residualStrength);
        
        return output;
    }
    
    private double[] generateEnhancedPolicy(String[][] board, double[] features) {
        double[] policy = new double[4096]; // 64*64 possible moves
        Arrays.fill(policy, 0.0001); // Very small uniform probability
        
        // Enhanced move preferences with defensive awareness
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                int fromRow = i / 8, fromCol = i % 8;
                int toRow = j / 8, toCol = j % 8;
                
                if (fromRow < 0 || fromRow >= 8 || toRow < 0 || toRow >= 8) continue;
                
                double moveScore = 0.001;
                String piece = board[fromRow][fromCol];
                
                // CRITICAL: Avoid hanging pieces and Scholar's Mate traps
                if (wouldHangPiece(board, fromRow, fromCol, toRow, toCol)) {
                    moveScore *= 0.01; // Extremely severely penalize hanging moves and traps
                }
                
                // EXTRA PENALTY for Scholar's Mate knight traps
                if ("♞".equals(piece) && isScholarsMateKnightTrap(board, fromRow, fromCol, toRow, toCol)) {
                    moveScore *= 0.001; // Nearly eliminate these moves
                }
                
                // Defensive moves against Scholar's Mate
                if (isDefensiveMove(board, fromRow, fromCol, toRow, toCol)) {
                    moveScore += 0.5; // Highly favor defensive moves
                }
                
                // Center control
                if (toRow >= 3 && toRow <= 4 && toCol >= 3 && toCol <= 4) {
                    moveScore += 0.02;
                }
                
                // Safe piece development
                if ("♞♝".contains(piece) && fromRow == 0 && !wouldHangPiece(board, fromRow, fromCol, toRow, toCol)) {
                    moveScore += 0.03; // Safe development
                }
                
                // King safety moves
                if ("♚".equals(piece) && Math.abs(toCol - fromCol) <= 1 && Math.abs(toRow - fromRow) <= 1) {
                    moveScore += 0.01;
                }
                
                policy[i * 64 + j] = moveScore;
            }
        }
        
        // Normalize policy
        double sum = Arrays.stream(policy).sum();
        if (sum > 0) {
            for (int i = 0; i < policy.length; i++) {
                policy[i] /= sum;
            }
        }
        
        return policy;
    }
    
    private boolean wouldHangPiece(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        // Enhanced check if move would hang the piece or fall into Scholar's Mate trap
        String piece = board[fromRow][fromCol];
        if (piece.isEmpty()) return false;
        
        // CRITICAL: Detect Scholar's Mate knight trap pattern
        if ("♞".equals(piece) && isScholarsMateKnightTrap(board, fromRow, fromCol, toRow, toCol)) {
            return true; // This is a Scholar's Mate trap!
        }
        
        // Check if destination is attacked by opponent
        return isSquareAttackedByOpponent(board, toRow, toCol, piece);
    }
    
    private boolean isScholarsMateKnightTrap(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        // Detect if knight move falls into Scholar's Mate trap
        if (!"♞".equals(board[fromRow][fromCol])) return false;
        
        // Check for Scholar's Mate setup: White bishop on c4 and queen ready to attack f7
        boolean bishopOnC4 = "♗".equals(board[4][2]); // Bishop on c4
        boolean queenThreatening = false;
        
        // Check if white queen is positioned to threaten f7
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♕".equals(board[i][j])) {
                    // Queen can reach f7 (1,5) in one move
                    if (canPieceAttack("♕", i, j, 1, 5)) {
                        queenThreatening = true;
                        break;
                    }
                }
            }
        }
        
        // If Scholar's Mate setup detected and knight moving to f6 (2,5)
        if (bishopOnC4 && queenThreatening && toRow == 2 && toCol == 5) {
            return true; // This is the Scholar's Mate knight trap!
        }
        
        // Also check for knight moving to c6 (2,2) which can be similarly trapped
        if (bishopOnC4 && queenThreatening && toRow == 2 && toCol == 2) {
            return true; // Another Scholar's Mate trap variation
        }
        
        return false;
    }
    
    private boolean isDefensiveMove(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        // Check if move defends against Scholar's Mate
        String piece = board[fromRow][fromCol];
        
        // Moving piece to defend f7 square
        if (toRow == 1 && toCol == 5) return true;
        
        // Blocking diagonal to f7
        if ((toRow == 2 && toCol == 4) || (toRow == 3 && toCol == 3)) return true;
        
        // Developing pieces safely
        if ("♞".equals(piece) && toRow == 2 && (toCol == 2 || toCol == 5)) return true;
        
        return false;
    }
    
    private boolean isSquareAttackedByOpponent(String[][] board, int row, int col, String piece) {
        boolean isBlackPiece = "♜♞♝♛♚♟".contains(piece);
        
        // Simple attack detection - check for opponent pieces that could attack this square
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String attacker = board[i][j];
                if (attacker.isEmpty()) continue;
                
                boolean isOpponentPiece = isBlackPiece ? "♔♕♖♗♘♙".contains(attacker) : "♜♞♝♛♚♟".contains(attacker);
                if (!isOpponentPiece) continue;
                
                // Check if this piece can attack the target square
                if (canPieceAttack(attacker, i, j, row, col)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean canPieceAttack(String piece, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♕": case "♛": // Queen
                return rowDiff == 0 || colDiff == 0 || rowDiff == colDiff;
            case "♖": case "♜": // Rook
                return rowDiff == 0 || colDiff == 0;
            case "♗": case "♝": // Bishop
                return rowDiff == colDiff;
            case "♘": case "♞": // Knight
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♙": // White pawn
                return rowDiff == 1 && colDiff == 1 && toRow == fromRow - 1;
            case "♟": // Black pawn
                return rowDiff == 1 && colDiff == 1 && toRow == fromRow + 1;
            default:
                return false;
        }
    }
    
    private double generateEnhancedValue(String[][] board, double[] features) {
        // Enhanced position evaluation with defensive awareness
        double materialValue = evaluatePosition(board);
        
        // Check for Scholar's Mate vulnerability
        double scholarsMateRisk = detectScholarsMateRisk(board);
        
        // Add positional evaluation from features
        double positionalValue = 0.0;
        for (int i = 128; i < 256; i++) {
            positionalValue += features[i] * 0.01;
        }
        
        // Combine material, positional, and defensive factors
        double value = materialValue * 0.6 + positionalValue * 0.2 - scholarsMateRisk * 0.2;
        
        return Math.max(-1.0, Math.min(1.0, value));
    }
    
    private double detectScholarsMateRisk(String[][] board) {
        // Detect Scholar's Mate attack pattern
        double risk = 0.0;
        
        // Check if white queen is on f3 and bishop on c4 (Scholar's Mate setup)
        if ("♕".equals(board[5][5]) && "♗".equals(board[4][2])) {
            risk += 0.8; // High risk detected
            
            // Check if f7 pawn is vulnerable
            if ("♟".equals(board[1][5])) {
                risk += 0.2; // f7 pawn still there - critical danger
            }
        }
        
        // Check for early queen development (risky for white, opportunity for black)
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♕".equals(board[i][j]) && i < 6) { // Queen moved early
                    risk += 0.1;
                }
            }
        }
        
        return risk;
    }
    
    private double encodePieceType(String piece) {
        if (piece.isEmpty()) return 0.0;
        return switch (piece) {
            case "♙", "♟" -> 0.1;
            case "♘", "♞" -> 0.3;
            case "♗", "♝" -> 0.3;
            case "♖", "♜" -> 0.5;
            case "♕", "♛" -> 0.9;
            case "♔", "♚" -> 1.0;
            default -> 0.0;
        };
    }
    
    private double encodePieceColor(String piece) {
        if (piece.isEmpty()) return 0.0;
        return "♔♕♖♗♘♙".contains(piece) ? 1.0 : -1.0;
    }
    
    private double evaluatePosition(String[][] board) {
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
        
        double totalScore = whiteScore + blackScore;
        if (totalScore == 0) return 0.0;
        
        return (whiteScore - blackScore) / totalScore;
    }
    
    private double getPieceValue(String piece) {
        switch (piece) {
            case "♙": case "♟": return 1.0;
            case "♘": case "♞": return 3.0;
            case "♗": case "♝": return 3.0;
            case "♖": case "♜": return 5.0;
            case "♕": case "♛": return 9.0;
            case "♔": case "♚": return 100.0;
            default: return 0.0;
        }
    }
    
    private String encodeBoardState(String[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                sb.append(board[i][j] == null || board[i][j].isEmpty() ? "." : board[i][j]);
            }
        }
        return sb.toString();
    }
    
    public void train(List<AlphaZeroInterfaces.TrainingExample> examples) {
        if (examples.isEmpty()) {
            logger.debug("*** AlphaZero NN: No training examples provided - skipping training ***");
            return;
        }
        
        logger.debug("*** AlphaZero NN: ENHANCED TRAINING STARTED - Iteration {} ***", trainingIterations + 1);
        long startTime = System.currentTimeMillis();
        
        isTraining = true;
        trainingIterations++;
        
        // Enhanced training with position augmentation
        List<AlphaZeroInterfaces.TrainingExample> augmentedExamples = augmentTrainingData(examples);
        logger.debug("*** AlphaZero NN: Augmented {} examples to {} ***", examples.size(), augmentedExamples.size());
        
        int updatedPositions = 0;
        // Enhanced training with ResNet-style updates
        for (AlphaZeroInterfaces.TrainingExample example : augmentedExamples) {
            String boardState = encodeBoardState(example.board);
            
            // Apply ResNet-style training update
            AlphaZeroInterfaces.PolicyValue current = positionCache.get(boardState);
            AlphaZeroInterfaces.PolicyValue improved;
            
            if (current != null) {
                // Enhanced learning with higher rate for losing positions
                double learningRate = example.value < -0.5 ? 0.8 : 0.3; // High rate for losses
                double[] newPolicy = new double[example.policy.length];
                for (int i = 0; i < newPolicy.length; i++) {
                    newPolicy[i] = current.policy[i] + learningRate * (example.policy[i] - current.policy[i]);
                }
                double newValue = current.value + learningRate * (example.value - current.value);
                improved = new AlphaZeroInterfaces.PolicyValue(newPolicy, newValue);
            } else {
                improved = new AlphaZeroInterfaces.PolicyValue(example.policy, example.value);
            }
            
            positionCache.put(boardState, improved);
            updatedPositions++;
            
            if (updatedPositions % 100 == 0) {
                logger.debug("*** AlphaZero NN: Processed {}/{} examples ***", updatedPositions, augmentedExamples.size());
            }
        }
        
        long trainingTime = System.currentTimeMillis() - startTime;
        isTraining = false;
        
        logger.debug("*** AlphaZero NN: ENHANCED TRAINING COMPLETED - Iteration {} in {}ms ***", 
            trainingIterations, trainingTime);
        logger.debug("*** AlphaZero NN: Updated {} positions, Total cache: {}, Residual cache: {} ***", 
            updatedPositions, positionCache.size(), residualCache.size());
        
        // Log learning progress for debugging
        if (trainingIterations % 5 == 0) {
            logger.info("*** AlphaZero: Learning Progress - {} training iterations, {} positions learned ***", 
                trainingIterations, positionCache.size());
        }
    }
    
    /**
     * Augment training data with board symmetries (rotations and reflections)
     */
    private List<AlphaZeroInterfaces.TrainingExample> augmentTrainingData(List<AlphaZeroInterfaces.TrainingExample> examples) {
        List<AlphaZeroInterfaces.TrainingExample> augmented = new ArrayList<>(examples);
        
        for (AlphaZeroInterfaces.TrainingExample example : examples) {
            // Add horizontal flip
            String[][] flippedBoard = flipBoardHorizontally(example.board);
            double[] flippedPolicy = flipPolicyHorizontally(example.policy);
            augmented.add(new AlphaZeroInterfaces.TrainingExample(flippedBoard, flippedPolicy, example.value));
            
            // Add vertical flip (less common but useful)
            if (random.nextDouble() < 0.3) {
                String[][] vFlippedBoard = flipBoardVertically(example.board);
                double[] vFlippedPolicy = flipPolicyVertically(example.policy);
                augmented.add(new AlphaZeroInterfaces.TrainingExample(vFlippedBoard, vFlippedPolicy, -example.value));
            }
        }
        
        return augmented;
    }
    
    private String[][] flipBoardHorizontally(String[][] board) {
        String[][] flipped = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                flipped[i][7-j] = board[i][j];
            }
        }
        return flipped;
    }
    
    private String[][] flipBoardVertically(String[][] board) {
        String[][] flipped = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                flipped[7-i][j] = board[i][j];
            }
        }
        return flipped;
    }
    
    private double[] flipPolicyHorizontally(double[] policy) {
        double[] flipped = new double[policy.length];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                int fromRow = i / 8, fromCol = i % 8;
                int toRow = j / 8, toCol = j % 8;
                
                int flippedFromCol = 7 - fromCol;
                int flippedToCol = 7 - toCol;
                
                int originalIdx = i * 64 + j;
                int flippedIdx = (fromRow * 8 + flippedFromCol) * 64 + (toRow * 8 + flippedToCol);
                
                if (flippedIdx < policy.length) {
                    flipped[flippedIdx] = policy[originalIdx];
                }
            }
        }
        return flipped;
    }
    
    private double[] flipPolicyVertically(double[] policy) {
        double[] flipped = new double[policy.length];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                int fromRow = i / 8, fromCol = i % 8;
                int toRow = j / 8, toCol = j % 8;
                
                int flippedFromRow = 7 - fromRow;
                int flippedToRow = 7 - toRow;
                
                int originalIdx = i * 64 + j;
                int flippedIdx = (flippedFromRow * 8 + fromCol) * 64 + (flippedToRow * 8 + toCol);
                
                if (flippedIdx < policy.length) {
                    flipped[flippedIdx] = policy[originalIdx];
                }
            }
        }
        return flipped;
    }
    
    public String getStatus() {
        return "Iterations: " + trainingIterations + ", Episodes: " + trainingEpisodes + ", Cache size: " + positionCache.size() + 
            ", Residual cache: " + residualCache.size() + ", ResNet blocks: " + resNetBlocks +
            ", Training: " + (isTraining ? "Yes" : "No");
    }
    
    public String getTrainingStatus() {
        return getStatus();
    }
    
    public int getTrainingEpisodes() {
        return trainingEpisodes; // CRITICAL FIX: Return actual episodes, not iterations
    }
    
    public void incrementEpisodes(int episodes) {
        trainingEpisodes += episodes;
        logger.info("*** AlphaZero NN: Episodes incremented by {} to total {} ***", episodes, trainingEpisodes);
        
        // CRITICAL FIX: Save immediately to ensure episode count persistence
        if (trainingEpisodes % 5 == 0) {
            saveModel();
            logger.info("*** AlphaZero NN: Episode count saved at {} episodes ***", trainingEpisodes);
        }
    }
    
    public void saveModel() {
        saveModelData();
    }
    
    public void shutdown() {
        saveModelData();
        positionCache.clear();
        logger.debug("*** AlphaZero NN: Model saved and shutdown complete ***");
    }
    
    private void loadModelData() {
        if (modelLoaded) {
            logger.debug("*** AlphaZero NN: Model already loaded, skipping reload ***");
            return;
        }
        
        try {
            // Phase 3: Dual-path implementation (same pattern as LeelaZero)
            if (ioWrapper.isAsyncEnabled()) {
                logger.info("*** ASYNC I/O: AlphaZero loading cache using NIO.2 async LOAD path ***");
                Object loadedData = ioWrapper.loadAIData("AlphaZero", "alphazero_cache.dat");
                
                if (loadedData instanceof AlphaZeroSaveData) {
                    // New format - AlphaZeroSaveData wrapper
                    AlphaZeroSaveData saveData = (AlphaZeroSaveData) loadedData;
                    positionCache.putAll(saveData.positionCache);
                    residualCache.putAll(saveData.residualCache);
                    trainingIterations = saveData.trainingIterations;
                    trainingEpisodes = saveData.trainingEpisodes;
                    
                    logger.info("*** AlphaZero NN: Loaded via async I/O (new format) - {} positions, {} residuals, {} iterations, {} episodes ***", 
                        positionCache.size(), residualCache.size(), trainingIterations, trainingEpisodes);
                    modelLoaded = true;
                    return;
                } else {
                    logger.info("*** AlphaZero: Async load returned unexpected format ({}), falling back to sync ***", 
                        loadedData != null ? loadedData.getClass().getSimpleName() : "null");
                }
            }
            
            // Fallback to direct I/O for backward compatibility
            java.io.File datFile = new java.io.File("alphazero_cache.dat");
            if (datFile.exists()) {
                logger.info("*** AlphaZero: Loading from DAT file using sync I/O ***");
                loadFromDatFile(datFile);
            } else {
                logger.info("*** AlphaZero: No existing cache file found, starting fresh ***");
            }
        } catch (Exception e) {
            logger.error("*** AlphaZero NN: Failed to load model data: {} ***", e.getMessage());
        }
    }
    

    private void loadFromDatFile(java.io.File datFile) {
        try {
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                    new java.io.FileInputStream(datFile))) {
                Object firstObject = ois.readObject();
                
                if (firstObject instanceof AlphaZeroSaveData) {
                    // New format - AlphaZeroSaveData wrapper
                    AlphaZeroSaveData saveData = (AlphaZeroSaveData) firstObject;
                    positionCache.putAll(saveData.positionCache);
                    residualCache.putAll(saveData.residualCache);
                    trainingIterations = saveData.trainingIterations;
                    trainingEpisodes = saveData.trainingEpisodes;
                    logger.info("*** AlphaZero NN: Loaded from DAT (new format) - {} positions, {} residuals, {} iterations, {} episodes ***", 
                        positionCache.size(), residualCache.size(), trainingIterations, trainingEpisodes);
                    modelLoaded = true;
                } else if (firstObject instanceof Map) {
                    // Old format - direct Map serialization
                    @SuppressWarnings("unchecked")
                    Map<String, AlphaZeroInterfaces.PolicyValue> loaded = (Map<String, AlphaZeroInterfaces.PolicyValue>) firstObject;
                    positionCache.putAll(loaded);
                    
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Double> loadedResiduals = (Map<String, Double>) ois.readObject();
                        residualCache.putAll(loadedResiduals);
                    } catch (Exception e) {
                        logger.debug("No residual cache found in DAT file (backward compatibility)");
                    }
                    
                    trainingIterations = ois.readInt();
                    
                    try {
                        trainingEpisodes = ois.readInt();
                    } catch (Exception e) {
                        trainingEpisodes = trainingIterations * 10;
                        logger.info("*** AlphaZero NN: Estimated {} episodes from {} iterations (backward compatibility) ***", 
                            trainingEpisodes, trainingIterations);
                    }
                    logger.info("*** AlphaZero NN: Loaded from DAT (old format) - {} positions, {} residuals, {} iterations, {} episodes ***", 
                        positionCache.size(), residualCache.size(), trainingIterations, trainingEpisodes);
                    modelLoaded = true;
                } else {
                    logger.error("*** AlphaZero NN: Unknown DAT file format: {} ***", firstObject.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            logger.error("*** AlphaZero NN: Failed to load DAT file: {} ***", e.getMessage());
        }
    }
    
    private void saveModelData() {
        try {
            // Phase 3: Dual-path implementation (same pattern as LeelaZero)
            if (ioWrapper.isAsyncEnabled()) {
                // Create serializable data structure for async I/O
                AlphaZeroSaveData saveData = new AlphaZeroSaveData(
                    new ConcurrentHashMap<>(positionCache),
                    new ConcurrentHashMap<>(residualCache),
                    trainingIterations,
                    trainingEpisodes
                );
                
                ioWrapper.saveAIData("AlphaZero", saveData, "alphazero_cache.dat");
                logger.info("AlphaZero: Enhanced neural network saved via async I/O ({} positions, {} residuals, {} episodes)", 
                    positionCache.size(), residualCache.size(), trainingEpisodes);
            } else {
                // Fallback to direct I/O
                try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(
                        new java.io.FileOutputStream("alphazero_cache.dat"))) {
                    AlphaZeroSaveData saveData = new AlphaZeroSaveData(
                        new ConcurrentHashMap<>(positionCache),
                        new ConcurrentHashMap<>(residualCache),
                        trainingIterations,
                        trainingEpisodes
                    );
                    oos.writeObject(saveData);
                    logger.info("AlphaZero: Enhanced neural network saved via fallback I/O ({} positions, {} residuals, {} episodes)", 
                        positionCache.size(), residualCache.size(), trainingEpisodes);
                }
            }
        } catch (Exception e) {
            logger.error("*** AlphaZero NN: Failed to save model data: {} ***", e.getMessage());
        }
    }
    
    // Serializable data structure for async I/O (same pattern as other AIs)
    private static class AlphaZeroSaveData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final Map<String, AlphaZeroInterfaces.PolicyValue> positionCache;
        public final Map<String, Double> residualCache;
        public final int trainingIterations;
        public final int trainingEpisodes;
        
        public AlphaZeroSaveData(Map<String, AlphaZeroInterfaces.PolicyValue> positions,
                                Map<String, Double> residuals, int iterations, int episodes) {
            this.positionCache = positions;
            this.residualCache = residuals;
            this.trainingIterations = iterations;
            this.trainingEpisodes = episodes;
        }
    }
    

}