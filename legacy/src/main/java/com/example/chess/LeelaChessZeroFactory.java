package com.example.chess;

/**
 * Factory for creating properly configured LeelaChessZero components.
 * Handles dependency injection and configuration.
 */
public class LeelaChessZeroFactory {
    
    public static LeelaChessZeroAI createLeelaChessZeroAI(boolean debugEnabled) {
        return new LeelaChessZeroAI(debugEnabled);
    }
    
    public static LeelaChessZeroTrainer createTrainer(boolean debugEnabled) {
        LeelaChessZeroNetwork neuralNetwork = new LeelaChessZeroNetwork(debugEnabled);
        LeelaChessZeroMCTS mcts = new LeelaChessZeroMCTS(neuralNetwork, debugEnabled);
        LeelaChessZeroOpeningBook openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        
        return new LeelaChessZeroTrainer(neuralNetwork, mcts, openingBook, debugEnabled);
    }
}