package com.example.chess;

/**
 * Enhanced factory for creating optimized AlphaZero components with ResNet, PUCT, and parallel training.
 * Handles dependency injection and advanced configuration.
 */
public class AlphaZeroFactory {
    
    public static AlphaZeroAI createAlphaZeroAI(boolean debugEnabled) {
        // Enhanced neural network with ResNet blocks and position augmentation
        AlphaZeroInterfaces.NeuralNetwork neuralNetwork = new AlphaZeroNeuralNetwork(debugEnabled);
        AlphaZeroInterfaces.ChessRules chessRules = new AlphaZeroChessRules();
        
        // Enhanced MCTS with proper PUCT algorithm and parallel simulations
        AlphaZeroInterfaces.MCTSEngine mctsEngine = new AlphaZeroMCTS(neuralNetwork, chessRules, 200, 1.25);
        
        return new AlphaZeroAI(neuralNetwork, mctsEngine);
    }
    
    public static AlphaZeroTrainingService createTrainingService(boolean debugEnabled) {
        // Enhanced components for parallel self-play training
        AlphaZeroInterfaces.NeuralNetwork neuralNetwork = new AlphaZeroNeuralNetwork(debugEnabled);
        AlphaZeroInterfaces.ChessRules chessRules = new AlphaZeroChessRules();
        
        // Higher simulation count for training quality
        AlphaZeroInterfaces.MCTSEngine mctsEngine = new AlphaZeroMCTS(neuralNetwork, chessRules, 400, 1.25);
        LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        
        return new AlphaZeroTrainingService(neuralNetwork, mctsEngine, chessRules, openingBook);
    }
    
    /**
     * Create enhanced AlphaZero trainer with parallel self-play capabilities
     */
    public static AlphaZeroTrainer createEnhancedTrainer(boolean debugEnabled) {
        AlphaZeroInterfaces.NeuralNetwork neuralNetwork = new AlphaZeroNeuralNetwork(debugEnabled);
        AlphaZeroInterfaces.ChessRules chessRules = new AlphaZeroChessRules();
        AlphaZeroInterfaces.MCTSEngine mctsEngine = new AlphaZeroMCTS(neuralNetwork, chessRules, 300, 1.25);
        LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        
        return new AlphaZeroTrainer(neuralNetwork, mctsEngine, openingBook, debugEnabled);
    }
}