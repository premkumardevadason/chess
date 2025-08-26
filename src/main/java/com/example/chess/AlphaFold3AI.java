package com.example.chess;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.util.zip.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.chess.async.TrainingDataIOWrapper;

/**
 * AlphaFold3-Inspired Chess AI
 * 
 * Implements diffusion modeling and pairwise attention mechanisms inspired by AlphaFold3
 * for chess move generation and endgame solving.
 * 
 * Core Components:
 * - Diffuser: Stochastic refinement of move trajectories
 * - PieceFormer: Attention module for inter-piece cooperation
 * - Evaluator: Position evaluation for proximity to checkmate
 * - Sampler: Legal move transitions in latent space
 */
public class AlphaFold3AI {
    private static final Logger logger = LogManager.getLogger(AlphaFold3AI.class);
    
    private final Diffuser diffuser;
    private final PieceFormer pieceFormer;
    private final Evaluator evaluator;
    private final Sampler sampler;
    private final Random random;
    private final boolean debugMode;
    private final LeelaChessZeroOpeningBook openingBook;
    private final ChessLegalMoveAdapter moveAdapter;
    private ChessGame chessGameValidator;
    
    private volatile boolean isThinking = false;
    private volatile Thread trainingThread = null;
    private volatile boolean trainingStopRequested = false;
    private volatile boolean stopTrainingFlag = false;
    private ExecutorService executorService;
    
    // Persistent learning state - thread-safe for concurrent access
    private final ConcurrentHashMap<String, Double> positionEvaluations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<int[]>> learnedTrajectories = new ConcurrentHashMap<>();
    private int trainingEpisodes = 0;
    private static final String STATE_FILE = "state/alphafold3_state.dat";
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    // Progressive optimization thresholds (adjusted for 13K+ episodes)
    private static final int BASIC_LEARNING_THRESHOLD = 1000;
    private static final int TRAJECTORY_CACHING_THRESHOLD = 3000;
    private static final int NOISE_SCHEDULING_THRESHOLD = 6000;
    private static final int ATTENTION_MASKING_THRESHOLD = 10000;
    
    public AlphaFold3AI(boolean debugMode) {
        this.debugMode = debugMode;
        this.random = new Random();
        this.diffuser = new Diffuser();
        this.pieceFormer = new PieceFormer();
        this.evaluator = new Evaluator();
        this.sampler = new Sampler();
        this.openingBook = new LeelaChessZeroOpeningBook(debugMode);
        this.moveAdapter = new ChessLegalMoveAdapter();
        
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AlphaFold3-Thread");
            t.setDaemon(true);
            return t;
        });
        this.ioWrapper = new TrainingDataIOWrapper();
        
        loadState();
        
        // Always log training episodes at startup for debugging
        logger.info("*** AlphaFold3 AI: Initialized with diffusion modeling - Episodes: {} ***", trainingEpisodes);
        
        if (trainingEpisodes > 0) {
            logger.info("*** AlphaFold3: Loaded existing training state - {} positions, {} trajectories ***", 
                positionEvaluations.size(), learnedTrajectories.size());
        }
        
        if (debugMode) {
            logger.debug("*** AlphaFold3 AI: Debug mode enabled - State file: {} ***", STATE_FILE);
        }
    }
    
    /**
     * Select best move using diffusion-based trajectory refinement
     */
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        isThinking = true;
        
        try {
            Future<int[]> future = executorService.submit(() -> {
                try {
                    return selectMoveWithDiffusion(board, validMoves);
                } finally {
                    isThinking = false;
                }
            });
            
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (debugMode) {
                logger.error("*** AlphaFold3: Error in move selection - {} ***", e.getMessage());
            }
            isThinking = false;
            return validMoves.get(0);
        }
    }
    
    /**
     * Core diffusion-based move selection with learned knowledge
     */
    private int[] selectMoveWithDiffusion(String[][] board, List<int[]> validMoves) {
        if (debugMode) {
            logger.debug("*** AlphaFold3: Starting diffusion trajectory refinement ***");
        }
        
        long startTime = System.currentTimeMillis();
        String boardKey = encodeBoardKey(board);
        
        // Step 1: Check opening book first (early game) but verify no immediate threats
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, false);
        LeelaChessZeroOpeningBook.OpeningMoveResult openingMove = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), false);
        if (openingMove != null) {
            // Check if there's an immediate threat that requires deviation from opening
            if (!hasImmediateThreat(board, validMoves)) {
                if (debugMode) {
                    logger.debug("*** AlphaFold3: Using opening book - {} ***", openingMove.openingName);
                }
                return openingMove.move;
            } else if (debugMode) {
                logger.debug("*** AlphaFold3: Threat detected - deviating from opening book ***");
            }
        }
        
        // Step 2: Check learned trajectories (only after sufficient learning)
        if (trainingEpisodes >= TRAJECTORY_CACHING_THRESHOLD && learnedTrajectories.containsKey(boardKey)) {
            List<int[]> learned = learnedTrajectories.get(boardKey);
            for (int[] move : learned) {
                if (validMoves.stream().anyMatch(vm -> 
                    vm[0] == move[0] && vm[1] == move[1] && vm[2] == move[2] && vm[3] == move[3])) {
                    if (debugMode) {
                        logger.debug("*** AlphaFold3: Using learned trajectory (episodes: {}) ***", trainingEpisodes);
                    }
                    return move;
                }
            }
        }
        
        // Step 3: Encode board state as dynamic lattice
        double[][] boardLattice = encodeBoardLattice(board);
        
        // Step 4: Generate initial trajectory (biased by learned positions)
        MoveTrajectory trajectory = generateBiasedTrajectory(validMoves, boardKey);
        
        // Step 5: Diffusion refinement process
        for (int step = 0; step < 10; step++) {
            trajectory = diffuser.refineTrajectory(trajectory, boardLattice, board);
            
            if (trajectory.isCheckmateTrajectory) {
                if (debugMode) {
                    logger.debug("*** AlphaFold3: Checkmate trajectory discovered at step {} ***", step);
                }
                break;
            }
        }
        
        // Step 6: Apply pairwise attention (only after advanced learning)
        int[] bestMove = trainingEpisodes >= ATTENTION_MASKING_THRESHOLD ? 
            pieceFormer.selectCooperativeMove(trajectory, board) : 
            trajectory.moves.get(0);
        
        long totalTime = System.currentTimeMillis() - startTime;
        if (debugMode) {
            logger.debug("*** AlphaFold3: Completed diffusion in {}ms ***", totalTime);
        }
        
        return bestMove != null ? bestMove : validMoves.get(0);
    }
    
    /**
     * Encode chessboard as dynamic state lattice
     */
    private double[][] encodeBoardLattice(String[][] board) {
        double[][] lattice = new double[8][8];
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece.isEmpty()) {
                    lattice[i][j] = 0.0; // Empty square
                } else {
                    // Encode piece type and color as continuous values
                    lattice[i][j] = encodePieceValue(piece);
                }
            }
        }
        
        return lattice;
    }
    
    private double encodePieceValue(String piece) {
        switch (piece) {
            case "♟": return -1.0; // Black pawn
            case "♞": return -3.2; // Black knight
            case "♝": return -3.0; // Black bishop
            case "♜": return -5.0; // Black rook
            case "♛": return -9.0; // Black queen
            case "♚": return -100.0; // Black king
            case "♙": return 1.0; // White pawn
            case "♘": return 3.2; // White knight
            case "♗": return 3.0; // White bishop
            case "♖": return 5.0; // White rook
            case "♕": return 9.0; // White queen
            case "♔": return 100.0; // White king
            default: return 0.0;
        }
    }
    
    /**
     * Generate initial semi-random trajectory
     */
    private MoveTrajectory generateInitialTrajectory(List<int[]> validMoves) {
        List<int[]> trajectory = new ArrayList<>();
        
        // Add 3-5 random valid moves to form initial trajectory
        int trajectoryLength = 3 + random.nextInt(3);
        for (int i = 0; i < Math.min(trajectoryLength, validMoves.size()); i++) {
            trajectory.add(validMoves.get(random.nextInt(validMoves.size())));
        }
        
        return new MoveTrajectory(trajectory);
    }
    
    public boolean isThinking() {
        return isThinking;
    }
    
    public void stopThinking() {
        if (executorService != null) {
            executorService.shutdownNow();
            // Reinitialize executor for next use
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AlphaFold3-Thread");
                t.setDaemon(true);
                return t;
            });
        }
    }
    
    public void startTraining(int episodes) {
        // Check if training is already active
        if (trainingThread != null && trainingThread.isAlive()) {
            logger.info("*** AlphaFold3: Training already in progress - ignoring request ***");
            return;
        }
        
        logger.info("*** AlphaFold3: Starting diffusion training with {} episodes ***", episodes);
        
        // Reset stop flags
        trainingStopRequested = false;
        stopTrainingFlag = false;
        
        trainingThread = new Thread(() -> {
            try {
                runDiffusionTraining(episodes);
            } catch (InterruptedException e) {
                logger.info("*** AlphaFold3: Training interrupted ***");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("*** AlphaFold3: Training error - {} ***", e.getMessage());
            }
        }, "AlphaFold3-Training");
        
        trainingThread.setDaemon(true);
        trainingThread.start();
    }
    
    private void runDiffusionTraining(int episodes) throws InterruptedException {
        int startingEpisodes = trainingEpisodes;
        logger.info("*** AlphaFold3: Starting training - Current episodes: {}, Target: +{} ***", startingEpisodes, episodes);
        
        for (int episode = 0; episode < episodes && !stopTrainingFlag; episode++) {
            if (stopTrainingFlag) {
                logger.info("*** AlphaFold3 AI: STOP DETECTED at episode {} - Exiting training ***", episode);
                return;
            }
            

            
            // Simulate self-play with diffusion refinement
            VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
            List<String> gamePositions = new ArrayList<>();
            List<int[]> gameMoves = new ArrayList<>();
            
            while (!virtualBoard.isGameOver() && virtualBoard.getMoveCount() < 100 && !stopTrainingFlag) {
                // Check stop flag more frequently
                if (stopTrainingFlag) {
                    logger.info("*** AlphaFold3 AI: STOP DETECTED during game simulation - Exiting training ***");
                    return;
                }
                
                List<int[]> validMoves = virtualBoard.getAllValidMoves(virtualBoard.isWhiteTurn());
                if (validMoves.isEmpty()) break;
                
                // Capture position BEFORE making move
                String boardKey = encodeBoardKey(virtualBoard.getBoard());
                if (boardKey != null && !boardKey.isEmpty()) {
                    gamePositions.add(boardKey);
                    
                    // Select and store move
                    int[] move = validMoves.get(random.nextInt(validMoves.size()));
                    if (move != null && move.length == 4) {
                        gameMoves.add(move.clone());
                        virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                    } else {
                        break; // Invalid move, end game
                    }
                } else {
                    break; // Invalid board state
                }
                
                // Check stop flag after each move
                if (stopTrainingFlag) {
                    logger.info("*** AlphaFold3 AI: STOP DETECTED after move in game simulation - Exiting training ***");
                    return;
                }
            }
            
            // Learn from game outcome
            double gameResult = virtualBoard.isGameOver() ? 1.0 : 0.0;
            if (!gamePositions.isEmpty() && !gameMoves.isEmpty()) {
                learnFromGame(gamePositions, gameMoves, gameResult);
                logger.info("*** AlphaFold3: Episode {} learned from {} positions, {} moves ***", 
                    trainingEpisodes, gamePositions.size(), gameMoves.size());
            } else {
                logger.warn("*** AlphaFold3: Episode {} - No data to learn from ***", trainingEpisodes);
            }
            trainingEpisodes++;
            
            // Save state every 10 episodes to prevent data loss
            if (trainingEpisodes % 10 == 0) {
                saveState();
                logger.info("*** AlphaFold3: Saved state at episode {} (total: {}) - {} positions, {} trajectories ***", 
                    episode, trainingEpisodes, positionEvaluations.size(), learnedTrajectories.size());
                
                // Add 1 second pause after every 10 episodes
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            if (episode % 10 == 0) {
                logger.info("*** AlphaFold3: Training episode {} completed (total: {}) ***", episode, trainingEpisodes);
            }
        }
        
        saveState();
        int newEpisodes = trainingEpisodes - startingEpisodes;
        logger.info("*** AlphaFold3: Training completed - {} new episodes trained (total: {}) ***", newEpisodes, trainingEpisodes);
        
        if (newEpisodes == 0) {
            logger.warn("*** AlphaFold3: WARNING - No new episodes were trained! ***");
        }
    }
    
    public void stopTraining() {
        logger.info("*** AlphaFold3 AI: STOP REQUEST RECEIVED - Setting training flags ***");
        trainingStopRequested = true;
        stopTrainingFlag = true;
        saveState();
        logger.info("*** AlphaFold3 AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    public void shutdown() {
        stopThinking();
        stopTraining();
        saveState();
        if (debugMode) {
            logger.debug("*** AlphaFold3: Shutdown complete ***");
        }
    }
    
    /**
     * Diffuser: Implements diffusion-based trajectory refinement
     */
    private class Diffuser {
        
        public MoveTrajectory refineTrajectory(MoveTrajectory trajectory, double[][] lattice, String[][] board) {
            List<int[]> refinedMoves = new ArrayList<>();
            
            for (int[] move : trajectory.moves) {
                // Apply Markov chain-like refinement
                int[] refinedMove = applyDiffusionStep(move, lattice, board);
                refinedMoves.add(refinedMove);
            }
            
            MoveTrajectory refined = new MoveTrajectory(refinedMoves);
            refined.isCheckmateTrajectory = evaluator.isCheckmateTrajectory(refined, board);
            
            return refined;
        }
        
        private int[] applyDiffusionStep(int[] move, double[][] lattice, String[][] board) {
            // Progressive noise scheduling - only apply after sufficient learning
            if (trainingEpisodes < NOISE_SCHEDULING_THRESHOLD) {
                return move; // No noise during basic learning phase
            }
            
            // Add Gaussian noise to move coordinates in latent space
            double noise = 0.1;
            int fromRow = Math.max(0, Math.min(7, move[0] + (int)(random.nextGaussian() * noise)));
            int fromCol = Math.max(0, Math.min(7, move[1] + (int)(random.nextGaussian() * noise)));
            int toRow = Math.max(0, Math.min(7, move[2] + (int)(random.nextGaussian() * noise)));
            int toCol = Math.max(0, Math.min(7, move[3] + (int)(random.nextGaussian() * noise)));
            
            // Validate refined move
            if (isValidChessMove(fromRow, fromCol, toRow, toCol, board)) {
                return new int[]{fromRow, fromCol, toRow, toCol};
            }
            
            return move; // Return original if refinement invalid
        }
    }
    
    /**
     * PieceFormer: Pairwise attention for piece cooperation
     */
    private class PieceFormer {
        
        public int[] selectCooperativeMove(MoveTrajectory trajectory, String[][] board) {
            double bestCooperationScore = Double.NEGATIVE_INFINITY;
            int[] bestMove = null;
            
            for (int[] move : trajectory.moves) {
                double score = calculateCooperationScore(move, board);
                if (score > bestCooperationScore) {
                    bestCooperationScore = score;
                    bestMove = move;
                }
            }
            
            return bestMove;
        }
        
        private double calculateCooperationScore(int[] move, String[][] board) {
            double score = 0.0;
            
            String movingPiece = board[move[0]][move[1]];
            if (movingPiece.isEmpty()) return score;
            
            // Analyze cooperation with other pieces
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    String piece = board[i][j];
                    if (!piece.isEmpty() && isSameColor(movingPiece, piece)) {
                        score += calculatePairwiseAttention(move, new int[]{i, j}, movingPiece, piece);
                    }
                }
            }
            
            return score;
        }
        
        private double calculatePairwiseAttention(int[] move1, int[] pos2, String piece1, String piece2) {
            // Knight + Queen cooperation bonus
            if ((piece1.equals("♞") && piece2.equals("♛")) || (piece1.equals("♛") && piece2.equals("♞"))) {
                return 0.8;
            }
            
            // Bishop pair coordination
            if (piece1.equals("♝") && piece2.equals("♝")) {
                return 0.6;
            }
            
            // Rook + Queen cooperation
            if ((piece1.equals("♜") && piece2.equals("♛")) || (piece1.equals("♛") && piece2.equals("♜"))) {
                return 0.7;
            }
            
            return 0.1; // Base cooperation
        }
        
        private boolean isSameColor(String piece1, String piece2) {
            boolean piece1Black = "♚♛♜♝♞♟".contains(piece1);
            boolean piece2Black = "♚♛♜♝♞♟".contains(piece2);
            return piece1Black == piece2Black;
        }
    }
    
    /**
     * Evaluator: Position evaluation for strategic dominance
     */
    private class Evaluator {
        
        public boolean isCheckmateTrajectory(MoveTrajectory trajectory, String[][] board) {
            // Simulate trajectory and check for checkmate
            VirtualChessBoard virtualBoard = new VirtualChessBoard();
            virtualBoard.setBoard(copyBoard(board));
            
            for (int[] move : trajectory.moves) {
                if (virtualBoard.isValidMove(move[0], move[1], move[2], move[3])) {
                    virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                    
                    if (virtualBoard.isGameOver()) {
                        return true; // Checkmate achieved
                    }
                }
            }
            
            return false;
        }
        
        private String[][] copyBoard(String[][] board) {
            String[][] copy = new String[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(board[i], 0, copy[i], 0, 8);
            }
            return copy;
        }
    }
    
    /**
     * Sampler: Legal move transitions in latent space
     */
    private class Sampler {
        
        public List<int[]> sampleLegalMoves(String[][] board, int numSamples) {
            List<int[]> samples = new ArrayList<>();
            
            for (int sample = 0; sample < numSamples; sample++) {
                int[] move = sampleRandomLegalMove(board);
                if (move != null) {
                    samples.add(move);
                }
            }
            
            return samples;
        }
        
        private int[] sampleRandomLegalMove(String[][] board) {
            // Sample from continuous latent space and project to legal moves
            for (int attempt = 0; attempt < 50; attempt++) {
                int fromRow = random.nextInt(8);
                int fromCol = random.nextInt(8);
                int toRow = random.nextInt(8);
                int toCol = random.nextInt(8);
                
                if (isValidChessMove(fromRow, fromCol, toRow, toCol, board)) {
                    return new int[]{fromRow, fromCol, toRow, toCol};
                }
            }
            
            return null;
        }
    }
    
    /**
     * Move trajectory representation
     */
    private static class MoveTrajectory {
        final List<int[]> moves;
        boolean isCheckmateTrajectory = false;
        
        MoveTrajectory(List<int[]> moves) {
            this.moves = new ArrayList<>(moves);
        }
    }
    
    /**
     * Chess move validation using unified adapter
     */
    private boolean isValidChessMove(int fromRow, int fromCol, int toRow, int toCol, String[][] board) {
        return moveAdapter.isLegalMove(board, fromRow, fromCol, toRow, toCol, false); // AI plays black
    }
    
    public String getTrainingStatus() {
        return trainingThread != null && trainingThread.isAlive() ? "Training" : "Ready";
    }
    
    public int getTrainingIterations() {
        return trainingEpisodes;
    }
    
    // Persistent state management with ZIP compression
    public void saveState() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            Map<String, Object> stateData = new HashMap<>();
            // Create snapshots to avoid concurrent modification during serialization
            stateData.put("positionEvaluations", new HashMap<>(positionEvaluations));
            stateData.put("learnedTrajectories", new HashMap<>(learnedTrajectories));
            stateData.put("trainingEpisodes", trainingEpisodes);
            ioWrapper.saveAIData("AlphaFold3", stateData, STATE_FILE);
        } else {
            // Existing synchronous code with snapshot protection
            try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(STATE_FILE));
                 ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
                // Create snapshots to avoid concurrent modification during serialization
                oos.writeObject(new HashMap<>(positionEvaluations));
                oos.writeObject(new HashMap<>(learnedTrajectories));
                oos.writeInt(trainingEpisodes);
                
                // Always log saves to track state changes
                java.io.File file = new java.io.File(STATE_FILE);
                logger.info("*** AlphaFold3: State saved - {} episodes, {} positions, {} trajectories, {} bytes ***", 
                    trainingEpisodes, positionEvaluations.size(), learnedTrajectories.size(), file.length());
            } catch (IOException e) {
                logger.error("*** AlphaFold3: Failed to save state - {} ***", e.getMessage());
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadState() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: AlphaFold3 loading state using NIO.2 async LOAD path ***");
                Object data = ioWrapper.loadAIData("AlphaFold3", STATE_FILE);
                if (data instanceof Map) {
                    Map<String, Object> stateData = (Map<String, Object>) data;
                    Map<String, Double> loadedPositions = (Map<String, Double>) stateData.get("positionEvaluations");
                    Map<String, List<int[]>> loadedTrajectories = (Map<String, List<int[]>>) stateData.get("learnedTrajectories");
                    if (loadedPositions != null) positionEvaluations.putAll(loadedPositions);
                    if (loadedTrajectories != null) learnedTrajectories.putAll(loadedTrajectories);
                    trainingEpisodes = (Integer) stateData.get("trainingEpisodes");
                    logger.info("*** AlphaFold3: State loaded using async I/O - {} episodes, {} positions, {} trajectories ***", 
                        trainingEpisodes, positionEvaluations.size(), learnedTrajectories.size());
                    return;
                }
            } catch (Exception e) {
                logger.warn("*** AlphaFold3: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Existing synchronous code with thread-safe loading
        try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(STATE_FILE));
             ObjectInputStream ois = new ObjectInputStream(gzis)) {
            Map<String, Double> loadedPositions = (Map<String, Double>) ois.readObject();
            Map<String, List<int[]>> loadedTrajectories = (Map<String, List<int[]>>) ois.readObject();
            if (loadedPositions != null) positionEvaluations.putAll(loadedPositions);
            if (loadedTrajectories != null) learnedTrajectories.putAll(loadedTrajectories);
            trainingEpisodes = ois.readInt();
            logger.info("*** AlphaFold3: State loaded successfully - {} episodes, {} positions, {} trajectories ***", 
                trainingEpisodes, positionEvaluations.size(), learnedTrajectories.size());
        } catch (Exception e) {
            positionEvaluations.clear();
            learnedTrajectories.clear();
            trainingEpisodes = 0;
            logger.info("*** AlphaFold3: Starting fresh - no saved state ({}): {} ***", STATE_FILE, e.getMessage());
        }
    }
    
    private void learnFromGame(List<String> positions, List<int[]> moves, double result) {
        if (positions.isEmpty() || moves.isEmpty()) {
            logger.warn("*** AlphaFold3: No positions or moves to learn from ***");
            return;
        }
        
        int learned = 0;
        int beforePositions = positionEvaluations.size();
        
        for (int i = 0; i < positions.size() && i < moves.size(); i++) {
            String pos = positions.get(i);
            int[] move = moves.get(i);
            
            if (pos != null && !pos.isEmpty() && move != null && move.length == 4) {
                // Progressive learning strategy
                if (trainingEpisodes < BASIC_LEARNING_THRESHOLD) {
                    // Phase 1: Direct learning without complex evaluation
                    positionEvaluations.put(pos, result);
                    List<int[]> trajectories = learnedTrajectories.computeIfAbsent(pos, k -> new ArrayList<>());
                    trajectories.add(move.clone());
                } else {
                    // Phase 2: Advanced learning with evaluation blending
                    double currentEval = positionEvaluations.getOrDefault(pos, 0.0);
                    double newEval = currentEval * 0.9 + result * 0.1;
                    positionEvaluations.put(pos, newEval);
                    
                    List<int[]> trajectories = learnedTrajectories.computeIfAbsent(pos, k -> new ArrayList<>());
                    boolean isDuplicate = trajectories.stream().anyMatch(existing -> 
                        existing[0] == move[0] && existing[1] == move[1] && 
                        existing[2] == move[2] && existing[3] == move[3]);
                    
                    if (!isDuplicate) {
                        trajectories.add(move.clone());
                    }
                }
                learned++;
            }
        }
        
        int afterPositions = positionEvaluations.size();
        String phase = trainingEpisodes < BASIC_LEARNING_THRESHOLD ? "BASIC" : "ADVANCED";
        logger.info("*** AlphaFold3: {} LEARNING - {} positions processed, Total: {}→{} positions, {} trajectories ***", 
            phase, learned, beforePositions, afterPositions, learnedTrajectories.size());
    }
    
    private String encodeBoardKey(String[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                sb.append(board[i][j].isEmpty() ? "." : board[i][j]);
            }
        }
        return sb.toString();
    }
    
    private MoveTrajectory generateBiasedTrajectory(List<int[]> validMoves, String boardKey) {
        List<int[]> trajectory = new ArrayList<>();
        int trajectoryLength = 3;
        
        // Progressive biasing - start with pure random, gradually add bias
        if (trainingEpisodes < BASIC_LEARNING_THRESHOLD) {
            // Phase 1: Pure random learning
            for (int i = 0; i < Math.min(trajectoryLength, validMoves.size()); i++) {
                trajectory.add(validMoves.get(random.nextInt(validMoves.size())));
            }
        } else {
            // Phase 2: Biased trajectory generation
            double positionValue = positionEvaluations.getOrDefault(boardKey, 0.0);
            for (int i = 0; i < Math.min(trajectoryLength, validMoves.size()); i++) {
                if (positionValue > 0.3 && !learnedTrajectories.isEmpty() && random.nextDouble() < 0.5) {
                    String randomKey = learnedTrajectories.keySet().iterator().next();
                    List<int[]> learned = learnedTrajectories.get(randomKey);
                    if (!learned.isEmpty()) {
                        trajectory.add(learned.get(random.nextInt(learned.size())));
                        continue;
                    }
                }
                trajectory.add(validMoves.get(random.nextInt(validMoves.size())));
            }
        }
        
        return new MoveTrajectory(trajectory);
    }
    
    /**
     * Check for immediate tactical threats using ChessGame rule validation
     */
    private boolean hasImmediateThreat(String[][] board, List<int[]> validMoves) {
        if (chessGameValidator == null) {
            return false; // No validator available, proceed with opening book
        }
        
        // Use ChessGame's existing threat detection methods
        int[] kingInCheck = chessGameValidator.getKingInCheckPosition();
        return kingInCheck != null || hasValuablePieceUnderAttack(board);
    }
    
    /**
     * Check if any valuable pieces are under attack using ChessGame validation
     */
    private boolean hasValuablePieceUnderAttack(String[][] board) {
        if (chessGameValidator == null) return false;
        
        String[] valuablePieces = {"♛", "♜", "♝", "♞"}; // Queen, Rook, Bishop, Knight
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                for (String valuable : valuablePieces) {
                    if (valuable.equals(piece)) {
                        // Use ChessGame's attack detection
                        if (chessGameValidator.isSquareUnderAttack(i, j, true)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    public void setChessGameValidator(ChessGame chessGame) {
        this.chessGameValidator = chessGame;
    }
    
    public void addHumanGameData(String[][] board, List<String> moveHistory, boolean blackWon) {
        if (moveHistory.size() < 2) return;
        
        List<String> positions = new ArrayList<>();
        List<int[]> moves = new ArrayList<>();
        
        // Reconstruct game positions and moves
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        for (String moveStr : moveHistory) {
            String[] parts = moveStr.split(",");
            if (parts.length == 4) {
                try {
                    int[] move = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 
                                 Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
                    positions.add(encodeBoardKey(virtualBoard.getBoard()));
                    moves.add(move);
                    virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
                } catch (NumberFormatException e) {
                    continue;
                }
            }
        }
        
        double result = blackWon ? 1.0 : 0.0;
        learnFromGame(positions, moves, result);
        saveState();
        
        if (debugMode) {
            logger.debug("*** AlphaFold3: Learned from human game - Result: {} ***", result);
        }
    }
}