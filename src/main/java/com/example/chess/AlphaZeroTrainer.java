package com.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Enhanced AlphaZero trainer with parallel self-play and advanced training techniques.
 */
public class AlphaZeroTrainer {
    private static final Logger logger = LogManager.getLogger(AlphaZeroTrainer.class);
    private AlphaZeroInterfaces.NeuralNetwork neuralNetwork;
    private AlphaZeroInterfaces.MCTSEngine mcts;
    private LeelaChessZeroOpeningBook openingBook;
    private boolean debugEnabled;
    private ConcurrentLinkedQueue<AlphaZeroInterfaces.TrainingExample> trainingData;
    private ExecutorService parallelExecutor;
    private volatile boolean stopRequested = false;
    private final ChessLegalMoveAdapter moveAdapter;
    
    public AlphaZeroTrainer(AlphaZeroInterfaces.NeuralNetwork neuralNetwork, AlphaZeroInterfaces.MCTSEngine mcts, LeelaChessZeroOpeningBook openingBook, boolean debugEnabled) {
        this.neuralNetwork = neuralNetwork;
        this.mcts = mcts;
        this.openingBook = openingBook;
        this.debugEnabled = debugEnabled;
        this.trainingData = new ConcurrentLinkedQueue<>();
        this.parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.moveAdapter = new ChessLegalMoveAdapter();
        logger.debug("*** AlphaZero Trainer: Initialized with Lc0 opening book ***");
    }
    
    public void runSelfPlayTraining(int games) {
        logger.debug("\n*** ENHANCED ALPHAZERO TRAINING CYCLE STARTED ***");
        logger.debug("*** AlphaZero: Starting parallel self-play training with {} games ***", games);
        long trainingStartTime = System.currentTimeMillis();
        
        stopRequested = false;
        int completedGames = 0;
        int totalTrainingExamples = 0;
        
        // Enhanced parallel self-play
        int parallelGames = Math.min(8, Math.max(1, games / 10)); // Up to 8 parallel games
        int gamesPerBatch = Math.max(1, games / parallelGames);
        
        logger.debug("*** AlphaZero: Running {} parallel game batches of {} games each ***", 
            parallelGames, gamesPerBatch);
        
        List<CompletableFuture<GameResult>> futures = new ArrayList<>();
        
        // Reinitialize executor if terminated
        if (parallelExecutor.isTerminated() || parallelExecutor.isShutdown()) {
            logger.debug("*** AlphaZero Trainer: Reinitializing terminated ExecutorService ***");
            parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();
        }
        
        // Launch parallel self-play batches
        for (int batch = 0; batch < parallelGames && !stopRequested; batch++) {
            final int batchId = batch;
            final int batchGames = (batch == parallelGames - 1) ? 
                (games - batch * gamesPerBatch) : gamesPerBatch;
            
            CompletableFuture<GameResult> future = CompletableFuture.supplyAsync(() -> {
                return runParallelGameBatch(batchId, batchGames);
            }, parallelExecutor);
            
            futures.add(future);
        }
        
        // Collect results from parallel batches
        for (CompletableFuture<GameResult> future : futures) {
            try {
                GameResult result = future.get(300, TimeUnit.SECONDS); // 5 minute timeout per batch
                completedGames += result.gamesCompleted;
                totalTrainingExamples += result.trainingExamples;
                
                logger.debug("*** AlphaZero: Batch completed - {} games, {} examples ***", 
                    result.gamesCompleted, result.trainingExamples);
                
            } catch (Exception e) {
                logger.error("*** AlphaZero: Parallel batch error - {} ***", e.getMessage());
            }
        }
        
        // Enhanced training with accumulated data
        if (!trainingData.isEmpty()) {
            logger.debug("*** AlphaZero: ENHANCED TRAINING with {} accumulated examples ***", trainingData.size());
            
            List<AlphaZeroInterfaces.TrainingExample> batchData = new ArrayList<>();
            while (!trainingData.isEmpty() && batchData.size() < 10000) {
                AlphaZeroInterfaces.TrainingExample example = trainingData.poll();
                if (example != null) {
                    batchData.add(example);
                }
            }
            
            if (!batchData.isEmpty()) {
                neuralNetwork.train(batchData);
                neuralNetwork.saveModel(); // Save after training
                logger.debug("*** AlphaZero: Enhanced training completed with {} examples and saved model ***", batchData.size());
            }
        }
        
        long totalTrainingTime = System.currentTimeMillis() - trainingStartTime;
        logger.info("AlphaZero: Enhanced parallel training completed - {} games, {} examples, {}s", 
            completedGames, totalTrainingExamples, totalTrainingTime/1000.0);
    }
    
    private GameResult runParallelGameBatch(int batchId, int batchGames) {
        int completed = 0;
        int examples = 0;
        
        logger.debug("*** AlphaZero: Batch {} starting {} games ***", batchId, batchGames);
        
        for (int game = 0; game < batchGames && !stopRequested; game++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.debug("*** AlphaZero: Batch {} interrupted at game {} ***", batchId, game);
                break;
            }
            
            try {
                List<AlphaZeroInterfaces.TrainingExample> gameData = playSelfPlayGameEnhanced(batchId, game);
                trainingData.addAll(gameData);
                examples += gameData.size();
                completed++;
                
                // Periodic training and saving during long batches
                if (completed % 50 == 0 && trainingData.size() > 1000) {
                    performIncrementalTraining();
                }
                
                // Save neural network every 25 games
                if (completed % 25 == 0) {
                    neuralNetwork.saveModel();
                    logger.debug("*** AlphaZero: Neural network saved at game {} ***", completed);
                }
                
            } catch (Exception e) {
                logger.debug("*** AlphaZero: Batch {} game {} error - {} ***", batchId, game, e.getMessage());
            }
        }
        
        logger.debug("*** AlphaZero: Batch {} completed - {} games, {} examples ***", 
            batchId, completed, examples);
        
        return new GameResult(completed, examples);
    }
    
    private void performIncrementalTraining() {
        List<AlphaZeroInterfaces.TrainingExample> batch = new ArrayList<>();
        for (int i = 0; i < 500 && !trainingData.isEmpty(); i++) {
            AlphaZeroInterfaces.TrainingExample example = trainingData.poll();
            if (example != null) {
                batch.add(example);
            }
        }
        
        if (!batch.isEmpty()) {
            neuralNetwork.train(batch);
            logger.debug("*** AlphaZero: Incremental training with {} examples ***", batch.size());
        }
    }
    
    private static class GameResult {
        final int gamesCompleted;
        final int trainingExamples;
        
        GameResult(int games, int examples) {
            this.gamesCompleted = games;
            this.trainingExamples = examples;
        }
    }
    
    private List<AlphaZeroInterfaces.TrainingExample> playSelfPlayGameEnhanced(int batchId, int gameId) {
        List<AlphaZeroInterfaces.TrainingExample> gameData = new ArrayList<>();
        VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
        String[][] board = virtualBoard.getBoard();
        boolean whiteTurn = virtualBoard.isWhiteTurn();
        int moveCount = 0;
        int maxMoves = 100;
        
        while (moveCount < maxMoves && !isGameOver(board)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            
            List<int[]> validMoves = moveAdapter.getAllLegalMoves(board, whiteTurn);
            if (validMoves.isEmpty()) break;
            
            int[] selectedMove;
            
            // Enhanced move selection with opening book and exploration
            if (moveCount < 15 && openingBook != null) {
                LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), whiteTurn);
                if (openingResult != null) {
                    selectedMove = openingResult.move;
                } else {
                    selectedMove = mcts.selectBestMove(board, validMoves);
                }
            } else {
                // Add exploration noise for training diversity
                if (Math.random() < 0.1) { // 10% random exploration
                    selectedMove = validMoves.get((int)(Math.random() * validMoves.size()));
                } else {
                    selectedMove = mcts.selectBestMove(board, validMoves);
                }
            }
            
            if (selectedMove == null) break;
            
            // Create enhanced training example with position augmentation
            double[] policy = createEnhancedPolicyFromMove(selectedMove, validMoves, board);
            double value = evaluatePositionEnhanced(board, whiteTurn, moveCount);
            
            gameData.add(new AlphaZeroInterfaces.TrainingExample(
                copyBoard(board), policy, value));
            
            // Make the move
            board = makeMove(board, selectedMove);
            whiteTurn = !whiteTurn;
            moveCount++;
        }
        
        // Update all examples with final game outcome
        double gameOutcome = evaluateFinalPosition(board);
        for (int i = 0; i < gameData.size(); i++) {
            AlphaZeroInterfaces.TrainingExample example = gameData.get(i);
            double finalValue = (i % 2 == 0) ? gameOutcome : -gameOutcome; // Alternate for each player
            gameData.set(i, new AlphaZeroInterfaces.TrainingExample(
                example.board, example.policy, finalValue));
        }
        
        logger.debug("*** AlphaZero: Batch {} game {} completed with {} positions ***", 
            batchId, gameId, gameData.size());
        
        return gameData;
    }
    
    private double[] createEnhancedPolicyFromMove(int[] selectedMove, List<int[]> validMoves, String[][] board) {
        double[] policy = new double[4096]; // 64*64 possible moves
        Arrays.fill(policy, 0.0001 / 4096); // Very small uniform probability
        
        // Enhanced policy with move quality assessment
        int selectedIndex = selectedMove[0] * 8 * 64 + selectedMove[1] * 64 + 
                           selectedMove[2] * 8 + selectedMove[3];
        if (selectedIndex < policy.length) {
            policy[selectedIndex] = 0.7; // High probability for selected move
        }
        
        // Distribute probability based on move quality
        double totalRemainingProb = 0.3;
        double[] moveQualities = new double[validMoves.size()];
        double totalQuality = 0.0;
        
        for (int i = 0; i < validMoves.size(); i++) {
            int[] move = validMoves.get(i);
            if (!Arrays.equals(move, selectedMove)) {
                double quality = assessMoveQuality(board, move);
                moveQualities[i] = quality;
                totalQuality += quality;
            }
        }
        
        // Assign probabilities based on move quality
        for (int i = 0; i < validMoves.size(); i++) {
            int[] move = validMoves.get(i);
            if (!Arrays.equals(move, selectedMove) && totalQuality > 0) {
                int index = move[0] * 8 * 64 + move[1] * 64 + move[2] * 8 + move[3];
                if (index < policy.length) {
                    policy[index] = totalRemainingProb * (moveQualities[i] / totalQuality);
                }
            }
        }
        
        return policy;
    }
    
    private double assessMoveQuality(String[][] board, int[] move) {
        double quality = 0.1; // Base quality
        
        String piece = board[move[0]][move[1]];
        String target = board[move[2]][move[3]];
        
        // Capture bonus
        if (!target.isEmpty()) {
            quality += getPieceValue(target) * 0.1;
        }
        
        // Center control bonus
        if (move[2] >= 3 && move[2] <= 4 && move[3] >= 3 && move[3] <= 4) {
            quality += 0.2;
        }
        
        // Development bonus
        if ("♞♝".contains(piece) && move[0] == 0) {
            quality += 0.15;
        }
        
        return quality;
    }
    
    private double evaluatePosition(String[][] board, boolean forWhite) {
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
        
        double advantage = (whiteScore - blackScore) / totalScore;
        return forWhite ? advantage : -advantage;
    }
    
    private double evaluatePositionEnhanced(String[][] board, boolean forWhite, int moveCount) {
        double materialValue = evaluatePosition(board, forWhite);
        
        // Add game phase consideration
        double phaseBonus = 0.0;
        if (moveCount < 20) {
            phaseBonus = 0.1; // Opening bonus
        } else if (moveCount > 60) {
            phaseBonus = -0.1; // Endgame penalty for long games
        }
        
        return Math.max(-1.0, Math.min(1.0, materialValue + phaseBonus));
    }
    
    private double evaluateFinalPosition(String[][] board) {
        return evaluatePosition(board, true);
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
    
    private List<int[]> getAllValidMoves(String[][] board, boolean forWhite) {
        return moveAdapter.getAllLegalMoves(board, forWhite);
    }
    

    
    private boolean isGameOver(String[][] board) {
        return moveAdapter.isGameOver(board);
    }
    
    private String[][] makeMove(String[][] board, int[] move) {
        String[][] newBoard = copyBoard(board);
        String piece = newBoard[move[0]][move[1]];
        newBoard[move[2]][move[3]] = piece;
        newBoard[move[0]][move[1]] = "";
        return newBoard;
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
    
    public void addHumanGameExperience(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.debug("*** Enhanced AlphaZero: Processing human game with {} moves ***", moveHistory.size());
        
        List<AlphaZeroInterfaces.TrainingExample> gameData = new ArrayList<>();
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        String[][] board = virtualBoard.getBoard();
        boolean whiteTurn = true;
        
        for (int i = 0; i < moveHistory.size(); i++) {
            String moveStr = moveHistory.get(i);
            String[] parts = moveStr.split(",");
            if (parts.length == 4) {
                try {
                    int[] move = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 
                                 Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
                    
                    List<int[]> validMoves = getAllValidMoves(board, whiteTurn);
                    double[] policy = createEnhancedPolicyFromMove(move, validMoves, board);
                    double value = blackWon ? (whiteTurn ? -0.7 : 0.7) : (whiteTurn ? 0.7 : -0.7);
                    
                    gameData.add(new AlphaZeroInterfaces.TrainingExample(copyBoard(board), policy, value));
                    
                    board = makeMove(board, move);
                    whiteTurn = !whiteTurn;
                } catch (NumberFormatException e) {
                    logger.debug("Invalid move format: {}", moveStr);
                }
            }
        }
        
        if (!gameData.isEmpty()) {
            neuralNetwork.train(gameData);
            logger.debug("*** Enhanced AlphaZero: Trained on {} human game positions ***", gameData.size());
        }
    }
    
    public void requestStop() {
        stopRequested = true;
        if (parallelExecutor != null) {
            parallelExecutor.shutdown();
        }
    }
    
    public void shutdown() {
        requestStop();
        try {
            if (parallelExecutor != null && !parallelExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
                // Reinitialize executor for potential next use
                parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            // Reinitialize executor for potential next use
            parallelExecutor = Executors.newVirtualThreadPerTaskExecutor();
            Thread.currentThread().interrupt();
        }
    }
}