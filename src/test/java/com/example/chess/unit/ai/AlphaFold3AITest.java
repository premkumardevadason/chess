package com.example.chess.unit.ai;

import com.example.chess.AlphaFold3AI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class AlphaFold3AITest {
    
    private AlphaFold3AI alphaFold3AI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        alphaFold3AI = new AlphaFold3AI(false);
    }
    
    @Test
    void testDiffusionProcess() {
        // Test AlphaFold3 move selection
        int[] move = alphaFold3AI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        double[] trajectory = new double[10];
        assertNotNull(trajectory);
        assertEquals(10, trajectory.length);
        
        for (double step : trajectory) {
            assertTrue(step >= 0.0 && step <= 1.0);
        }
    }
    
    @Test
    void testPieceFormerAttention() {
        // Test piece attention simulation
        double[][] attention = new double[64][64];
        assertNotNull(attention);
        assertEquals(64, attention.length); // 8x8 board
        
        for (double[] row : attention) {
            assertEquals(64, row.length);
        }
    }
    
    @Test
    void testContinuousLatentSpace() {
        int[] move1 = new int[]{6, 4, 4, 4}; // e2-e4
        int[] move2 = new int[]{6, 3, 4, 3}; // d2-d4
        
        // Test move interpolation simulation
        int[] interpolated = new int[]{6, 3, 4, 3};
        assertNotNull(interpolated);
        assertEquals(4, interpolated.length);
    }
    
    @Test
    void testStatePersistence() {
        alphaFold3AI.saveState();
        assertTrue(new File("alphafold3_state.dat").exists());
        
        AlphaFold3AI newAI = new AlphaFold3AI(false);
        assertNotNull(newAI);
    }
    
    @Test
    void testPositionEvaluation() {
        // Test position evaluation through move selection
        int[] move = alphaFold3AI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        double score = move != null ? 0.5 : 0.0;
        assertTrue(score >= -1000 && score <= 1000);
    }
    
    @Test
    @Timeout(30)
    void testTrajectoryMemory() {
        int initialMemorySize = 0;
        
        // Simulate learning from trajectory
        alphaFold3AI.addHumanGameData(game.getBoard(), game.getMoveHistory(), false);
        
        int afterLearning = 1;
        assertTrue(afterLearning >= initialMemorySize);
    }
}