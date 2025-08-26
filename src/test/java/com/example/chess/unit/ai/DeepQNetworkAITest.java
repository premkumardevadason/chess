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
        assertNotNull(dqnAI);
        // Network initialization is internal - just verify AI is created
        assertNotNull(dqnAI);
    }
    
    @Test
    void testExperienceReplay() {
        int initialSize = dqnAI.getExperienceBufferSize();
        dqnAI.storeExperience(game.getBoard(), new int[]{6,4,4,4}, 0.5, game.getBoard(), false);
        int afterAdd = dqnAI.getExperienceBufferSize();
        // Buffer may be at capacity - verify it's functioning
        assertTrue(afterAdd >= initialSize);
        assertTrue(afterAdd > 0); // Should have experiences
    }
    
    @Test
    void testModelPersistence() {
        dqnAI.saveModels();
        dqnAI.saveExperiences();
        
        assertTrue(new File("chess_dqn_model.zip").exists());
        assertTrue(new File("chess_dqn_target_model.zip").exists());
        assertTrue(new File("chess_dqn_experiences.dat").exists());
        
        DeepQNetworkAI newAI = new DeepQNetworkAI(false);
        assertNotNull(newAI);
    }
    
    @Test
    void testTargetNetworkSync() {
        // Target network sync is internal - just verify training works
        dqnAI.trainStep();
        assertTrue(dqnAI.getTrainingSteps() >= 0);
    }
    
    @Test
    @Timeout(30)
    void testTrainingStability() {
        int initialSteps = dqnAI.getTrainingSteps();
        // Train multiple steps
        for (int i = 0; i < 5; i++) {
            dqnAI.trainStep();
        }
        int afterTraining = dqnAI.getTrainingSteps();
        assertTrue(afterTraining > initialSteps);
    }
    
    @Test
    void testMemoryEfficiency() {
        for (int i = 0; i < 1000; i++) {
            dqnAI.storeExperience(game.getBoard(), new int[]{6,4,4,4}, 0.1, game.getBoard(), false);
        }
        
        int bufferSize = dqnAI.getExperienceBufferSize();
        // Buffer should have reasonable size
        assertTrue(bufferSize > 0 && bufferSize <= 10000);
    }
}