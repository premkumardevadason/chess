package com.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Separate training service for AlphaZero following single responsibility principle.
 */
public class AlphaZeroTrainingService {
    private static final Logger logger = LogManager.getLogger(AlphaZeroTrainingService.class);
    private final AlphaZeroInterfaces.NeuralNetwork neuralNetwork;
    private final AlphaZeroInterfaces.MCTSEngine mctsEngine;
    private final AlphaZeroInterfaces.ChessRules chessRules;
    private final LeelaChessZeroOpeningBook openingBook;
    private final ChessLegalMoveAdapter moveAdapter;
    private volatile boolean stopRequested = false;
    
    public AlphaZeroTrainingService(AlphaZeroInterfaces.NeuralNetwork neuralNetwork, 
                                   AlphaZeroInterfaces.MCTSEngine mctsEngine,
                                   AlphaZeroInterfaces.ChessRules chessRules,
                                   LeelaChessZeroOpeningBook openingBook) {
        this.neuralNetwork = neuralNetwork;
        this.mctsEngine = mctsEngine;
        this.chessRules = chessRules;
        this.openingBook = openingBook;
        this.moveAdapter = new ChessLegalMoveAdapter();
    }
    
    public void runSelfPlayTraining(int games) {
        stopRequested = false;
        logger.info("*** AlphaZero Training: Starting {} games ***", games);
        List<AlphaZeroInterfaces.TrainingExample> trainingData = new ArrayList<>();
        int completedGames = 0;
        
        for (int game = 0; game < games && !stopRequested; game++) {
            if (stopRequested) break;
            
            List<AlphaZeroInterfaces.TrainingExample> gameData = playSelfPlayGame();
            if (!gameData.isEmpty()) {
                trainingData.addAll(gameData);
                completedGames++;
                
                // CRITICAL FIX: Increment episode counter for each completed game
                if (neuralNetwork instanceof AlphaZeroNeuralNetwork) {
                    ((AlphaZeroNeuralNetwork) neuralNetwork).incrementEpisodes(1);
                }
                
                // Train neural network every 10 games or at the end
                if (completedGames % 10 == 0 || game == games - 1) {
                    neuralNetwork.train(trainingData);
                    trainingData.clear(); // Clear after training
                    logger.debug("*** AlphaZero Training: Batch training completed for {} games ***", completedGames);
                }
            }
            
            if ((game + 1) % 100 == 0) {
                logger.info("*** AlphaZero Training: Completed {} games ***", game + 1);
            }
        }
        
        // Train any remaining data
        if (!trainingData.isEmpty()) {
            neuralNetwork.train(trainingData);
        }
        
        // CRITICAL FIX: Save neural network after training to persist episode count
        neuralNetwork.saveModel();
        
        logger.info("*** AlphaZero Training: Completed {} games, Total episodes: {} ***", 
            completedGames, neuralNetwork.getTrainingEpisodes());
    }
    
    public void requestStop() {
        stopRequested = true;
    }
    
    private List<AlphaZeroInterfaces.TrainingExample> playSelfPlayGame() {
        List<AlphaZeroInterfaces.TrainingExample> gameData = new ArrayList<>();
        VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
        String[][] board = virtualBoard.getBoard();
        boolean whiteTurn = virtualBoard.isWhiteTurn();
        int moveCount = 0;
        
        while (moveCount < 100 && !moveAdapter.isGameOver(board) && !stopRequested) {
            if (stopRequested) break;
            
            List<int[]> validMoves = moveAdapter.getAllLegalMoves(board, whiteTurn);
            if (validMoves.isEmpty()) break;
            
            int[] selectedMove = selectMove(board, validMoves, moveCount);
            if (selectedMove == null) break;
            
            double[] policy = createPolicyFromMove(selectedMove, validMoves);
            double value = evaluatePosition(board, whiteTurn);
            
            gameData.add(new AlphaZeroInterfaces.TrainingExample(copyBoard(board), policy, value));
            
            board = chessRules.makeMove(board, selectedMove);
            whiteTurn = !whiteTurn;
            moveCount++;
        }
        
        return gameData;
    }
    
    private int[] selectMove(String[][] board, List<int[]> validMoves, int moveCount) {
        if (moveCount < 15 && openingBook != null) {
            LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, moveAdapter.getValidator(), true);
            if (openingResult != null) {
                return openingResult.move;
            }
        }
        return mctsEngine.selectBestMove(board, validMoves);
    }
    
    private double[] createPolicyFromMove(int[] selectedMove, List<int[]> validMoves) {
        double[] policy = new double[4096];
        Arrays.fill(policy, 0.001 / 4096);
        
        int selectedIndex = selectedMove[0] * 8 * 64 + selectedMove[1] * 64 + selectedMove[2] * 8 + selectedMove[3];
        if (selectedIndex < policy.length) {
            policy[selectedIndex] = 0.8;
        }
        
        return policy;
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
    
    private String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
}