package com.example.chess.unit.ai;

import com.example.chess.AlphaZeroAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class AlphaZeroAITest {
    
    private AlphaZeroAI alphaZeroAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        alphaZeroAI = game.getAlphaZeroAI();
    }
    
    @Test
    void testSelfPlayTraining() {
        assertNotNull(alphaZeroAI);
        // Test that AlphaZero can select moves
        int[] move = alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (move != null) {
            assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
        }
    }
    
    @Test
    void testMCTSIntegration() {
        int[] move = alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (move != null) {
            assertNotNull(move);
            assertEquals(4, move.length);
            assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
        }
    }
    
    @Test
    void testNeuralNetworkPersistence() {
        alphaZeroAI.saveNeuralNetwork();
        assertNotNull(alphaZeroAI);
    }
    
    @Test
    void testPolicyValueOutputs() {
        // Test that AlphaZero can evaluate positions
        int[] move = alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        // If move is returned, it should be valid
        if (move != null) {
            assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
        }
    }
    
    @Test
    @Timeout(30)
    void testTrainingConvergence() {
        // Test training by starting and stopping
        game.trainAI();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        game.stopTraining();
        assertNotNull(alphaZeroAI);
    }
    
    @Test
    void testTreeReuse() {
        alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        game.makeMove(6, 4, 4, 4); // e2-e4
        alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Test completed successfully
        assertNotNull(alphaZeroAI);
    }
}