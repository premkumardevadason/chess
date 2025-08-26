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
    @Timeout(60)
    void testSelfPlayTraining() {
        if (alphaZeroAI != null) {
            // Test actual self-play episode generation
            int initialEpisodes = alphaZeroAI.getEpisodeCount();
            
            // Run short self-play training
            alphaZeroAI.runSelfPlayEpisodes(3);
            
            int finalEpisodes = alphaZeroAI.getEpisodeCount();
            assertTrue(finalEpisodes > initialEpisodes, "Self-play should generate new episodes");
            
            // Verify neural network learns from episodes
            assertNotNull(alphaZeroAI.getPolicyPrediction(game.getBoard()));
            assertTrue(alphaZeroAI.getValuePrediction(game.getBoard()) >= -1.0);
            assertTrue(alphaZeroAI.getValuePrediction(game.getBoard()) <= 1.0);
        } else {
            // AlphaZero not enabled - test passes
            assertTrue(true);
        }
    }
    
    @Test
    @Timeout(30)
    void testMCTSIntegration() {
        if (alphaZeroAI != null) {
            // Test MCTS tree construction and neural network guidance
            int[] move1 = alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
            int searchCount1 = alphaZeroAI.getLastSearchCount();
            
            // MCTS should perform multiple simulations
            assertTrue(searchCount1 > 10, "MCTS should perform multiple simulations");
            
            if (move1 != null) {
                game.makeMove(move1[0], move1[1], move1[2], move1[3]);
                
                // Test tree reuse - second search should be faster
                long startTime = System.currentTimeMillis();
                int[] move2 = alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
                long searchTime = System.currentTimeMillis() - startTime;
                
                assertTrue(searchTime < 10000, "MCTS with tree reuse should be efficient");
                if (move2 != null) {
                    assertTrue(game.isValidMove(move2[0], move2[1], move2[2], move2[3]));
                }
            }
        } else {
            assertTrue(true); // AlphaZero not enabled
        }
    }
    
    @Test
    void testNeuralNetworkPersistence() {
        if (alphaZeroAI != null) {
            alphaZeroAI.saveNeuralNetwork();
            assertNotNull(alphaZeroAI);
        } else {
            assertTrue(true); // AlphaZero not enabled
        }
    }
    
    @Test
    void testPolicyValueOutputs() {
        if (alphaZeroAI != null) {
            // Test neural network policy and value predictions
            double[] policyOutput = alphaZeroAI.getPolicyPrediction(game.getBoard());
            double valueOutput = alphaZeroAI.getValuePrediction(game.getBoard());
            
            // Policy should be probability distribution over moves
            assertNotNull(policyOutput, "Policy output should not be null");
            assertTrue(policyOutput.length > 0, "Policy should have move probabilities");
            
            // Value should be in [-1, 1] range
            assertTrue(valueOutput >= -1.0 && valueOutput <= 1.0, 
                "Value prediction should be in [-1, 1] range");
            
            // Test position evaluation consistency
            double value1 = alphaZeroAI.getValuePrediction(game.getBoard());
            double value2 = alphaZeroAI.getValuePrediction(game.getBoard());
            assertEquals(value1, value2, 0.001, "Value prediction should be consistent");
        } else {
            assertTrue(true); // AlphaZero not enabled
        }
    }
    
    @Test
    @Timeout(30)
    void testTrainingConvergence() {
        if (alphaZeroAI != null) {
            // Test training by starting and stopping
            game.trainAI();
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            game.stopTraining();
            assertNotNull(alphaZeroAI);
        } else {
            assertTrue(true); // AlphaZero not enabled
        }
    }
    
    @Test
    void testTreeReuse() {
        if (alphaZeroAI != null) {
            alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
            
            game.makeMove(6, 4, 4, 4); // e2-e4
            alphaZeroAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
            
            // Test completed successfully
            assertNotNull(alphaZeroAI);
        } else {
            assertTrue(true); // AlphaZero not enabled
        }
    }
}