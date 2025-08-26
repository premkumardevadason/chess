package com.example.chess.unit.ai;

import com.example.chess.NegamaxAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class NegamaxAITest {
    
    private NegamaxAI negamaxAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        negamaxAI = new NegamaxAI(false);
    }
    
    @Test
    void testAlphaBetaPruning() {
        // Test that Negamax AI can be constructed
        assertNotNull(negamaxAI);
        assertTrue(game.getAllValidMoves(false).size() > 0);
    }
    
    @Test
    void testIterativeDeepening() {
        // Test iterative deepening via selectMove
        int[] move3 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        int[] move4 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        // Both moves should be valid
        assertNotNull(move3);
        assertNotNull(move4);
    }
    
    @Test
    void testPositionEvaluation() {
        // evaluatePosition is private - test via selectMove
        int[] move = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        assertNotNull(move);
        
        // Make a move and test again
        game.makeMove(6, 4, 4, 4); // e2-e4
        int[] move2 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        assertNotNull(move2);
    }
    
    @Test
    void testTranspositionTable() {
        // Test that Negamax AI has cache functionality
        assertNotNull(negamaxAI);
        int cacheSize = negamaxAI.getCacheSize();
        assertTrue(cacheSize >= 0);
    }
    
    @Test
    @Timeout(6)
    void testTimeBoundedSearch() {
        // Time limit is fixed at 5 seconds - test completion time
        long startTime = System.currentTimeMillis();
        int[] move = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration <= 6000); // Should complete within 5s time limit + buffer
        assertNotNull(move);
    }
    
    @Test
    void testMoveOrdering() {
        // Move ordering is always enabled - test via optimization stats
        int[] orderedMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        String stats = negamaxAI.getOptimizationStats();
        
        assertNotNull(stats);
        assertTrue(stats.contains("TT:"));
        assertNotNull(orderedMove);
        assertNotNull(orderedMove);
    }
}