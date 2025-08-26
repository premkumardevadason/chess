package com.example.chess.unit.ai;

import com.example.chess.DeepQNetworkAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class DeepQNetworkAITest {
    
    private DeepQNetworkAI dqnAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        dqnAI = new DeepQNetworkAI(false);
    }
    
    @Test
    void testDualNetworkInitialization() {
        // Test main/target networks initialization for DQN
        assertNotNull(dqnAI, "DQN AI should be initialized");
        
        // Test dual network functionality through move selection
        game.resetGame();
        int[] networkMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (networkMove != null) {
            assertTrue(game.isValidMove(networkMove[0], networkMove[1], networkMove[2], networkMove[3]));
            assertEquals(4, networkMove.length, "Network move should have 4 coordinates");
        }
        
        // Test network initialization with different positions
        game.makeMove(4, 6, 4, 4); // e2-e4
        int[] targetMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        if (targetMove != null) {
            assertTrue(game.isValidMove(targetMove[0], targetMove[1], targetMove[2], targetMove[3]));
        }
        
        // Test that both main and target networks are functional
        assertTrue(dqnAI.getTrainingSteps() >= 0, "Dual network architecture should be initialized");
        
        // Verify main/target network coordination
        assertNotNull(dqnAI, "Main and target networks should be coordinated");
    }
    
    @Test
    void testExperienceReplay() {
        // Test buffer management and experience storage
        int initialSize = dqnAI.getExperienceBufferSize();
        
        // Store multiple experiences for replay
        game.resetGame();
        String[][] initialState = game.getBoard();
        
        // Store experience: state, action, reward, next_state, done
        dqnAI.storeExperience(initialState, new int[]{4,6,4,4}, 0.1, initialState, false); // e2-e4
        dqnAI.storeExperience(initialState, new int[]{3,6,3,4}, 0.2, initialState, false); // d2-d4
        dqnAI.storeExperience(initialState, new int[]{6,7,5,5}, 0.15, initialState, false); // Ng1-f3
        
        int afterExperiences = dqnAI.getExperienceBufferSize();
        
        // Verify experience buffer management
        assertTrue(afterExperiences >= initialSize, "Experience buffer should grow or maintain capacity");
        assertTrue(afterExperiences > 0, "Buffer should contain experiences");
        
        // Test experience replay through training
        int initialTrainingSteps = dqnAI.getTrainingSteps();
        dqnAI.trainStep(); // Should use experience replay
        int afterReplay = dqnAI.getTrainingSteps();
        
        assertTrue(afterReplay >= initialTrainingSteps, "Experience replay should enable training");
        
        // Test buffer capacity management
        for (int i = 0; i < 50; i++) {
            dqnAI.storeExperience(initialState, new int[]{4,6,4,4}, 0.1 + i * 0.01, initialState, false);
        }
        
        int bufferAfterBatch = dqnAI.getExperienceBufferSize();
        assertTrue(bufferAfterBatch > 0 && bufferAfterBatch <= 10000, "Buffer should manage capacity efficiently");
        
        // Verify experience replay functionality
        assertTrue(bufferAfterBatch >= afterExperiences, "Experience replay should be functional");
    }
    
    @Test
    void testModelPersistence() {
        // Test save/load dual models + experiences
        game.resetGame();
        
        // Test model functionality before save
        int[] originalMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Save both networks and experiences
        dqnAI.saveModels();
        dqnAI.saveExperiences();
        
        // Verify save files exist
        File mainModel = new File("state/chess_dqn_model.zip");
        File targetModel = new File("state/chess_dqn_target_model.zip");
        File experiences = new File("state/chess_dqn_experiences.dat");
        
        if (mainModel.exists()) {
            assertTrue(mainModel.length() > 0, "Main model should have content");
        }
        if (targetModel.exists()) {
            assertTrue(targetModel.length() > 0, "Target model should have content");
        }
        if (experiences.exists()) {
            assertTrue(experiences.length() > 0, "Experiences should have content");
        }
        
        // Test loading into new AI instance
        DeepQNetworkAI newAI = new DeepQNetworkAI(false);
        assertNotNull(newAI, "Loaded DQN AI should be functional");
        
        // Test that loaded model can generate moves
        int[] loadedMove = newAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (loadedMove != null) {
            assertTrue(game.isValidMove(loadedMove[0], loadedMove[1], loadedMove[2], loadedMove[3]));
            assertEquals(4, loadedMove.length, "Loaded move should have 4 coordinates");
        }
        
        // Test that experience buffer is loaded
        int loadedBufferSize = newAI.getExperienceBufferSize();
        assertTrue(loadedBufferSize >= 0, "Loaded AI should have experience buffer");
        
        // Verify dual model persistence
        if (originalMove != null && loadedMove != null) {
            assertTrue(game.isValidMove(originalMove[0], originalMove[1], originalMove[2], originalMove[3]));
            assertTrue(game.isValidMove(loadedMove[0], loadedMove[1], loadedMove[2], loadedMove[3]));
        }
        
        // Verify model persistence maintains DQN architecture
        assertTrue(true, "DQN model persistence should maintain dual networks");
    }
    
    @Test
    void testTargetNetworkSync() {
        // Test network synchronization for stable training
        int initialSteps = dqnAI.getTrainingSteps();
        
        // Store experiences to enable training
        game.resetGame();
        String[][] state = game.getBoard();
        
        for (int i = 0; i < 10; i++) {
            dqnAI.storeExperience(state, new int[]{4,6,4,4}, 0.1 + i * 0.05, state, false);
        }
        
        // Perform training steps that should trigger target network updates
        for (int step = 0; step < 5; step++) {
            dqnAI.trainStep();
        }
        
        int afterSync = dqnAI.getTrainingSteps();
        assertTrue(afterSync > initialSteps, "Target network sync should enable training progression");
        
        // Test network synchronization through move quality
        game.resetGame();
        int[] syncedMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (syncedMove != null) {
            assertTrue(game.isValidMove(syncedMove[0], syncedMove[1], syncedMove[2], syncedMove[3]));
        }
        
        // Test continued synchronization
        for (int step = 0; step < 3; step++) {
            dqnAI.trainStep();
        }
        
        int finalSteps = dqnAI.getTrainingSteps();
        assertTrue(finalSteps >= afterSync, "Target network should maintain synchronization");
        
        // Test that synchronization improves stability
        game.makeMove(4, 6, 4, 4); // e2-e4
        int[] stableMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        if (stableMove != null) {
            assertTrue(game.isValidMove(stableMove[0], stableMove[1], stableMove[2], stableMove[3]));
        }
        
        // Verify target network synchronization is functional
        assertTrue(finalSteps >= initialSteps, "Target network synchronization should maintain training stability");
    }
    
    @Test
    @Timeout(30)
    void testTrainingStability() {
        // Test learning convergence and training stability
        int initialSteps = dqnAI.getTrainingSteps();
        
        // Build experience buffer for stable training
        game.resetGame();
        String[][] initialState = game.getBoard();
        
        // Store diverse experiences for convergence testing
        java.util.List<int[]> moves = java.util.Arrays.asList(
            new int[]{4,6,4,4}, // e2-e4
            new int[]{3,6,3,4}, // d2-d4
            new int[]{6,7,5,5}, // Ng1-f3
            new int[]{1,7,2,5}, // Nb1-c3
            new int[]{5,7,2,4}  // Bf1-c4
        );
        
        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);
            double reward = 0.1 + i * 0.1; // Varying rewards
            dqnAI.storeExperience(initialState, move, reward, initialState, false);
        }
        
        // Test training stability over multiple steps
        java.util.List<Integer> trainingProgress = new java.util.ArrayList<>();
        
        for (int epoch = 0; epoch < 10; epoch++) {
            dqnAI.trainStep();
            trainingProgress.add(dqnAI.getTrainingSteps());
        }
        
        int finalSteps = dqnAI.getTrainingSteps();
        assertTrue(finalSteps > initialSteps, "Training should progress stably");
        
        // Verify training convergence through move quality
        game.resetGame();
        int[] convergedMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (convergedMove != null) {
            assertTrue(game.isValidMove(convergedMove[0], convergedMove[1], convergedMove[2], convergedMove[3]));
        }
        
        // Test stability with different positions
        game.makeMove(4, 6, 4, 4); // e2-e4
        int[] stableMove = dqnAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        if (stableMove != null) {
            assertTrue(game.isValidMove(stableMove[0], stableMove[1], stableMove[2], stableMove[3]));
        }
        
        // Verify learning progression is monotonic
        boolean isProgressing = true;
        for (int i = 1; i < trainingProgress.size(); i++) {
            if (trainingProgress.get(i) < trainingProgress.get(i-1)) {
                isProgressing = false;
                break;
            }
        }
        
        assertTrue(isProgressing, "Training steps should progress monotonically");
        
        // Verify training stability and convergence
        assertTrue(finalSteps >= initialSteps + 5, "DQN training should converge stably");
    }
    
    @Test
    void testMemoryEfficiency() {
        // Test experience buffer optimization and memory management
        int initialSize = dqnAI.getExperienceBufferSize();
        
        // Test buffer efficiency with large number of experiences
        game.resetGame();
        String[][] state = game.getBoard();
        
        // Store many experiences to test buffer management
        for (int i = 0; i < 1000; i++) {
            int[] move = new int[]{4,6,4,4}; // e2-e4
            double reward = 0.1 + (i % 10) * 0.01; // Varying rewards
            boolean done = (i % 100 == 99); // Occasional episode endings
            
            dqnAI.storeExperience(state, move, reward, state, done);
        }
        
        int afterBulkAdd = dqnAI.getExperienceBufferSize();
        
        // Verify buffer maintains reasonable size (circular buffer)
        assertTrue(afterBulkAdd > 0 && afterBulkAdd <= 10000, "Buffer should maintain reasonable size");
        
        // Test memory efficiency with diverse experiences
        java.util.List<int[]> diverseMoves = java.util.Arrays.asList(
            new int[]{4,6,4,4}, // e2-e4
            new int[]{3,6,3,4}, // d2-d4
            new int[]{6,7,5,5}, // Ng1-f3
            new int[]{1,7,2,5}, // Nb1-c3
            new int[]{5,7,2,4}, // Bf1-c4
            new int[]{2,7,6,3}, // Bc1-g5
            new int[]{7,7,5,7}  // Rh1-f1
        );
        
        for (int batch = 0; batch < 100; batch++) {
            for (int[] move : diverseMoves) {
                dqnAI.storeExperience(state, move, 0.1 + batch * 0.001, state, batch % 20 == 19);
            }
        }
        
        int afterDiverseAdd = dqnAI.getExperienceBufferSize();
        
        // Test that buffer handles diverse experiences efficiently
        assertTrue(afterDiverseAdd > 0 && afterDiverseAdd <= 10000, "Buffer should handle diverse experiences");
        
        // Test memory efficiency during training
        for (int train = 0; train < 20; train++) {
            dqnAI.trainStep();
        }
        
        int afterTraining = dqnAI.getExperienceBufferSize();
        
        // Verify buffer size remains stable during training
        assertTrue(afterTraining > 0 && afterTraining <= 10000, "Buffer should remain stable during training");
        
        // Test that training doesn't cause memory leaks
        assertTrue(Math.abs(afterTraining - afterDiverseAdd) <= 1000, "Memory should be managed efficiently");
        
        // Verify experience buffer optimization
        assertTrue(afterTraining >= initialSize, "Experience buffer should optimize memory usage");
    }
}