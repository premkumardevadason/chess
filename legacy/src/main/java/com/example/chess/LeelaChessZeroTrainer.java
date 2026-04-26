package com.example.chess;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Leela Chess Zero Training System
 * 
 * Features:
 * - Self-play training with human game initialization
 * - Distributed training simulation
 * - Progressive difficulty adjustment
 * - Opening book integration
 */
public class LeelaChessZeroTrainer {
    private static final Logger logger = LogManager.getLogger(LeelaChessZeroTrainer.class);
    private LeelaChessZeroNetwork neuralNetwork;
    private LeelaChessZeroMCTS mcts;
    private LeelaChessZeroOpeningBook openingBook;
    private boolean debugEnabled;
    private volatile boolean stopRequested = false;
    private final ChessLegalMoveAdapter moveAdapter;
    
    // Training parameters
    private static final int GAMES_PER_BATCH = 10;
    private static final int MAX_GAME_LENGTH = 200;
    private static final double LEARNING_RATE_DECAY = 0.95;
    
    // Training data collection
    private List<float[]> trainingInputs = new ArrayList<>();
    private List<float[]> policyTargets = new ArrayList<>();
    private List<Float> valueTargets = new ArrayList<>();
    
    public LeelaChessZeroTrainer(LeelaChessZeroNetwork neuralNetwork, 
                                LeelaChessZeroMCTS mcts, 
                                LeelaChessZeroOpeningBook openingBook, 
                                boolean debugEnabled) {
        this.neuralNetwork = neuralNetwork;
        this.mcts = mcts;
        this.openingBook = openingBook != null ? openingBook : new LeelaChessZeroOpeningBook(debugEnabled);
        this.debugEnabled = debugEnabled;
        this.moveAdapter = new ChessLegalMoveAdapter();
        
        logger.info("*** LeelaZero Trainer: Initialized with human-guided training ***");
    }
    
    public void runSelfPlayTraining(int totalGames) {
        logger.info("*** LeelaZero Trainer: Starting self-play training for " + totalGames + " games ***");
        logger.info("*** LeelaZero Trainer: TRAINING STARTED - " + totalGames + " games ***");
        
        int gamesCompleted = 0;
        int batchCount = 0;
        
        while (gamesCompleted < totalGames && !stopRequested) {
            if (stopRequested) {
                logger.info("*** LeelaZero Trainer: STOP REQUESTED - ENDING TRAINING ***");
                return;
            }
            
            // Play a batch of games
            for (int i = 0; i < GAMES_PER_BATCH && gamesCompleted < totalGames; i++) {
                if (stopRequested) {
                    logger.info("*** LeelaZero Trainer: STOP REQUESTED IN GAME LOOP ***");
                    return;
                }
                
                playTrainingGame(gamesCompleted + 1);
                gamesCompleted++;
                
                if (gamesCompleted % 200 == 0) {
                    logger.info("*** LeelaZero Trainer: Completed " + gamesCompleted + "/" + totalGames + " games ***");
                    logger.debug("*** LeelaZero Trainer: Progress " + gamesCompleted + "/" + totalGames + " games ***");
                }
            }
            
            // CRITICAL FIX: Check for interruption before training batch
            if (Thread.currentThread().isInterrupted()) {
                logger.info("*** LeelaZero Trainer: Training interrupted before batch training ***");
                return;
            }
            
            // Train on collected data
            if (!trainingInputs.isEmpty() && !stopRequested) {
                trainOnBatch();
                batchCount++;
                
                logger.info("*** LeelaZero Trainer: Completed training batch " + batchCount + " ***");
            }
            
            // Check stop flag after training batch
            if (stopRequested) {
                logger.info("*** LeelaZero Trainer: STOP REQUESTED AFTER BATCH TRAINING ***");
                return;
            }
            
            // Periodic model saving - save every 20 games
            if (gamesCompleted % 20 == 0 && !stopRequested) {
                neuralNetwork.saveModel();
                logger.info("*** LeelaZero Trainer: Model saved at {} games ***", gamesCompleted);
                
                // Add 1 second pause after every 20 games
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Check stop flag after periodic save
            if (stopRequested) {
                logger.info("*** LeelaZero Trainer: STOP REQUESTED AFTER PERIODIC SAVE ***");
                return;
            }
        }
        
        // Final training and save
        if (!trainingInputs.isEmpty()) {
            trainOnBatch();
        }
        
        // Update neural network with total games completed
        neuralNetwork.setTrainingGames(neuralNetwork.getTrainingGames() + gamesCompleted);
        
        neuralNetwork.saveModel();
        saveTrainingGames(neuralNetwork.getTrainingGames());
        
        logger.info("*** LeelaZero Trainer: Training completed - " + gamesCompleted + " games played ***");
        logger.info("*** LeelaZero Trainer: TRAINING COMPLETED - " + gamesCompleted + " games played ***");
    }
    
    public void requestStop() {
        stopRequested = true;
        logger.info("*** LeelaZero Trainer: STOP REQUESTED ***");
    }
    
    private void playTrainingGame(int gameNumber) {
        // Create isolated virtual board with random Lc0 opening for this training game
        VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
        String[][] board = virtualBoard.getBoard();
        List<String> moveHistory = new ArrayList<>();
        List<GamePosition> gamePositions = new ArrayList<>();
        
        boolean isWhiteTurn = virtualBoard.isWhiteTurn();
        int moveCount = 0;
        
        if (gameNumber % 500 == 0) {
            logger.info("*** LeelaZero Trainer: Playing training game " + gameNumber + " ***");
        }
        
        while (moveCount < MAX_GAME_LENGTH && !moveAdapter.isGameOver(board) && !stopRequested) {
            if (stopRequested) {
                logger.info("*** LeelaZero Trainer: STOP REQUESTED DURING GAME ***");
                return;
            }
            
            List<int[]> validMoves = moveAdapter.getAllLegalMoves(board, isWhiteTurn);
            if (validMoves.isEmpty()) break;
            
            int[] selectedMove;
            
            // Use Lc0 opening book for early moves during training
            if (moveCount < 15 && openingBook != null) {
                LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), isWhiteTurn);
                if (openingResult != null) {
                    selectedMove = openingResult.move;
                } else {
                    selectedMove = mcts.selectBestMove(board, validMoves);
                }
            } else {
                selectedMove = mcts.selectBestMove(board, validMoves);
            }
            
            // Record position for training
            GamePosition position = new GamePosition(
                copyBoard(board), 
                selectedMove, 
                validMoves, 
                isWhiteTurn
            );
            gamePositions.add(position);
            
            // Apply move
            applyMove(board, selectedMove);
            moveHistory.add(moveToAlgebraic(selectedMove));
            
            isWhiteTurn = !isWhiteTurn;
            moveCount++;
        }
        
        // Determine game result
        GameResult result = evaluateGameResult(board, isWhiteTurn);
        
        // Convert game positions to training data
        convertGameToTrainingData(gamePositions, result);
        
        // Update opening book with training game moves
        if (moveHistory.size() >= 10 && openingBook != null) {
            openingBook.addGameMoves(moveHistory);
        }
    }
    
    private void convertGameToTrainingData(List<GamePosition> positions, GameResult result) {
        for (int i = 0; i < positions.size(); i++) {
            GamePosition pos = positions.get(i);
            
            // Create input representation (7168 elements)
            float[] input = boardToInput(pos.board, pos.selectedMove);
            trainingInputs.add(input);
            
            // Create policy target (move probabilities)
            float[] policyTarget = createPolicyTarget(pos.validMoves, pos.selectedMove);
            policyTargets.add(policyTarget);
            
            // Create value target (position evaluation)
            float valueTarget = calculateValueTarget(result, pos.isWhiteTurn, i, positions.size());
            valueTargets.add(valueTarget);
        }
    }
    
    private float[] createPolicyTarget(List<int[]> validMoves, int[] selectedMove) {
        float[] policy = new float[4096]; // All possible moves
        Arrays.fill(policy, 0.001f); // Small probability for all moves
        
        // Higher probability for the selected move
        int selectedIndex = moveToIndex(selectedMove);
        policy[selectedIndex] = 0.8f;
        
        // Distribute remaining probability among valid moves
        float remainingProb = 0.2f / validMoves.size();
        for (int[] move : validMoves) {
            int moveIndex = moveToIndex(move);
            if (moveIndex != selectedIndex) {
                policy[moveIndex] = remainingProb;
            }
        }
        
        return policy;
    }
    
    private float calculateValueTarget(GameResult result, boolean isWhiteTurn, int moveIndex, int totalMoves) {
        float baseValue;
        
        switch (result) {
            case WHITE_WIN:
                baseValue = isWhiteTurn ? 1.0f : -1.0f;
                break;
            case BLACK_WIN:
                baseValue = isWhiteTurn ? -1.0f : 1.0f;
                break;
            case DRAW:
            default:
                baseValue = 0.0f;
                break;
        }
        
        // Apply temporal discount - moves closer to end have more certain values
        float discount = 1.0f - (0.1f * (totalMoves - moveIndex) / totalMoves);
        return baseValue * discount;
    }
    
    private void trainOnBatch() {
        if (trainingInputs.isEmpty() || stopRequested) return;
        
        try {
            // Check stop flag before expensive neural network training
            if (stopRequested) {
                logger.info("*** LeelaZero Trainer: STOP REQUESTED BEFORE NEURAL NETWORK TRAINING ***");
                return;
            }
            
            neuralNetwork.trainOnGameData(trainingInputs, policyTargets, valueTargets);
            
            logger.info("*** LeelaZero Trainer: Trained on " + trainingInputs.size() + " positions ***");
            
            // Clear training data
            trainingInputs.clear();
            policyTargets.clear();
            valueTargets.clear();
            
        } catch (Exception e) {
            logger.error("*** LeelaZero Trainer: Training error - " + e.getMessage() + " ***");
        }
    }
    
    public void addHumanGameExperience(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        if (moveHistory.size() < 10) return;
        
        try {
            // Reconstruct game positions
            String[][] board = initializeBoard();
            boolean isWhiteTurn = true;
            
            for (int i = 0; i < Math.min(moveHistory.size(), 30); i++) { // Focus on opening/middlegame
                String moveStr = moveHistory.get(i);
                int[] move = algebraicToMove(moveStr);
                
                if (move != null) {
                    List<int[]> validMoves = moveAdapter.getAllLegalMoves(board, isWhiteTurn);
                    
                    // Create training data from human move
                    float[] input = boardToInput(board, move);
                    float[] policy = createHumanPolicyTarget(validMoves, move);
                    float value = blackWon ? (isWhiteTurn ? -0.1f : 0.1f) : (isWhiteTurn ? 0.1f : -0.1f);
                    
                    trainingInputs.add(input);
                    policyTargets.add(policy);
                    valueTargets.add(value);
                    
                    applyMove(board, move);
                    isWhiteTurn = !isWhiteTurn;
                }
            }
            
            // Train immediately on human data (higher priority)
            if (!trainingInputs.isEmpty()) {
                trainOnBatch();
            }
            
            logger.info("*** LeelaZero Trainer: Added human game experience (" + moveHistory.size() + " moves) ***");
            
        } catch (Exception e) {
            logger.error("*** LeelaZero Trainer: Error adding human game - " + e.getMessage() + " ***");
        }
    }
    
    private float[] createHumanPolicyTarget(List<int[]> validMoves, int[] humanMove) {
        float[] policy = new float[4096];
        Arrays.fill(policy, 0.001f);
        
        // Give high probability to human move (trust human expertise)
        int humanMoveIndex = moveToIndex(humanMove);
        policy[humanMoveIndex] = 0.9f;
        
        return policy;
    }
    
    // Utility methods
    private String[][] initializeBoard() {
        VirtualChessBoard virtualBoard = new VirtualChessBoard();
        return virtualBoard.getBoard();
    }
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private void applyMove(String[][] board, int[] move) {
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
        virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
        String[][] updatedBoard = virtualBoard.getBoard();
        // Copy back to original board
        for (int i = 0; i < 8; i++) {
            System.arraycopy(updatedBoard[i], 0, board[i], 0, 8);
        }
    }
    

    
    private GameResult evaluateGameResult(String[][] board, boolean isWhiteTurn) {
        boolean hasWhiteKing = false, hasBlackKing = false;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j].equals("♔")) hasWhiteKing = true;
                if (board[i][j].equals("♚")) hasBlackKing = true;
            }
        }
        
        if (!hasWhiteKing) return GameResult.BLACK_WIN;
        if (!hasBlackKing) return GameResult.WHITE_WIN;
        return GameResult.DRAW;
    }
    

    
    private boolean isCorrectColor(String piece, boolean isWhiteTurn) {
        boolean isWhitePiece = "♔♕♖♗♘♙".contains(piece);
        return isWhiteTurn == isWhitePiece;
    }
    
    private float[] boardToInput(String[][] board, int[] move) {
        float[] input = new float[7168]; // 112 planes × 64 squares
        int planeOffset = 0;
        
        // Planes 0-11: Current position (piece types)
        for (int plane = 0; plane < 12; plane++) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    int index = planeOffset + i * 8 + j;
                    input[index] = encodePieceForPlane(board[i][j], plane);
                }
            }
            planeOffset += 64;
        }
        
        // Planes 12-111: History and auxiliary planes (100 planes)
        for (int plane = 12; plane < 112; plane++) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    int index = planeOffset + i * 8 + j;
                    input[index] = (plane % 2 == 0) ? 0.1f : 0.0f;
                }
            }
            planeOffset += 64;
        }
        
        return input;
    }
    
    private float encodePieceForPlane(String piece, int plane) {
        switch (plane) {
            case 0: return piece.equals("♙") ? 1.0f : 0.0f; // White pawns
            case 1: return piece.equals("♘") ? 1.0f : 0.0f; // White knights
            case 2: return piece.equals("♗") ? 1.0f : 0.0f; // White bishops
            case 3: return piece.equals("♖") ? 1.0f : 0.0f; // White rooks
            case 4: return piece.equals("♕") ? 1.0f : 0.0f; // White queens
            case 5: return piece.equals("♔") ? 1.0f : 0.0f; // White king
            case 6: return piece.equals("♟") ? 1.0f : 0.0f; // Black pawns
            case 7: return piece.equals("♞") ? 1.0f : 0.0f; // Black knights
            case 8: return piece.equals("♝") ? 1.0f : 0.0f; // Black bishops
            case 9: return piece.equals("♜") ? 1.0f : 0.0f; // Black rooks
            case 10: return piece.equals("♛") ? 1.0f : 0.0f; // Black queens
            case 11: return piece.equals("♚") ? 1.0f : 0.0f; // Black king
            default: return 0.0f;
        }
    }
    
    private int moveToIndex(int[] move) {
        return (move[0] * 8 + move[1]) * 64 + (move[2] * 8 + move[3]);
    }
    
    private String moveToAlgebraic(int[] move) {
        char fromFile = (char)('a' + move[1]);
        int fromRank = 8 - move[0];
        char toFile = (char)('a' + move[3]);
        int toRank = 8 - move[2];
        return "" + fromFile + fromRank + toFile + toRank;
    }
    
    private int[] algebraicToMove(String algebraic) {
        if (algebraic.length() != 4) return null;
        
        try {
            int fromCol = algebraic.charAt(0) - 'a';
            int fromRow = 8 - (algebraic.charAt(1) - '0');
            int toCol = algebraic.charAt(2) - 'a';
            int toRow = 8 - (algebraic.charAt(3) - '0');
            
            return new int[]{fromRow, fromCol, toRow, toCol};
        } catch (Exception e) {
            return null;
        }
    }
    
    // Helper classes
    private static class GamePosition {
        String[][] board;
        int[] selectedMove;
        List<int[]> validMoves;
        boolean isWhiteTurn;
        
        GamePosition(String[][] board, int[] selectedMove, List<int[]> validMoves, boolean isWhiteTurn) {
            this.board = board;
            this.selectedMove = selectedMove;
            this.validMoves = validMoves;
            this.isWhiteTurn = isWhiteTurn;
        }
    }
    
    private void saveTrainingGames(int totalGames) {
        try {
            java.io.File gamesFile = new java.io.File("leela_training_games.dat");
            try (java.io.FileWriter writer = new java.io.FileWriter(gamesFile)) {
                writer.write(String.valueOf(totalGames));
            }
            logger.info("*** LeelaZero Trainer: Saved training games count: {} ***", totalGames);
        } catch (Exception e) {
            logger.error("*** LeelaZero Trainer: Failed to save training games - {} ***", e.getMessage());
        }
    }
    
    private enum GameResult {
        WHITE_WIN, BLACK_WIN, DRAW
    }
}