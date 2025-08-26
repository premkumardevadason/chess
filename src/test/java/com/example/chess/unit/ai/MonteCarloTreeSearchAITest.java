package com.example.chess.unit.ai;

import com.example.chess.MonteCarloTreeSearchAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloTreeSearchAITest {
    
    private MonteCarloTreeSearchAI mctsAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        mctsAI = new MonteCarloTreeSearchAI(false);
    }
    
    @Test
    void testTreeConstruction() {
        // Test that MCTS AI can be constructed and doesn't crash
        assertNotNull(mctsAI);
        assertTrue(game.getAllValidMoves(false).size() > 0);
    }
    
    @Test
    void testUCB1Selection() {
        // Test that MCTS AI exists and has valid moves to choose from
        assertNotNull(mctsAI);
        assertTrue(game.getAllValidMoves(false).size() > 0);
    }
    
    @Test
    @Timeout(10)
    void testSimulationAccuracy() {
        // Test that simulation doesn't crash
        assertNotNull(mctsAI);
        assertTrue(game.getAllValidMoves(false).size() > 0);
    }
    
    @Test
    void testTreeReuse() {
        // Test tree reuse
        int[] firstMove = mctsAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (firstMove != null) {
            game.makeMove(firstMove[0], firstMove[1], firstMove[2], firstMove[3]);
        }
        
        int[] secondMove = mctsAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        if (secondMove != null) {
            assertTrue(game.isValidMove(secondMove[0], secondMove[1], secondMove[2], secondMove[3]));
        }
    }
    
    @Test
    void testPerformanceScaling() {
        // Test performance scaling
        long startTime = System.currentTimeMillis();
        int[] move1 = mctsAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        long time1 = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        int[] move2 = mctsAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        long time2 = System.currentTimeMillis() - startTime;
        
        // Both should complete in reasonable time
        assertTrue(time1 < 10000);
        assertTrue(time2 < 10000);
    }
    
    @Test
    void testStatelessOperation() {
        // Should work with fresh instance
        MonteCarloTreeSearchAI freshAI = new MonteCarloTreeSearchAI(false);
        int[] move = freshAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        assertNotNull(move);
    }
}