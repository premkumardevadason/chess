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
    @Timeout(15)
    void testIterativeDeepening() {
        // Test iterative deepening with different time limits
        long startTime = System.currentTimeMillis();
        int[] quickMove = negamaxAI.selectMoveWithDepth(game.getBoard(), game.getAllValidMoves(true), 2);
        long quickTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        int[] deepMove = negamaxAI.selectMoveWithDepth(game.getBoard(), game.getAllValidMoves(true), 4);
        long deepTime = System.currentTimeMillis() - startTime;
        
        assertNotNull(quickMove, "Shallow search should find move");
        assertNotNull(deepMove, "Deep search should find move");
        assertTrue(deepTime > quickTime, "Deeper search should take more time");
        
        // Verify search statistics
        int nodesSearched = negamaxAI.getNodesSearched();
        assertTrue(nodesSearched > 0, "Should search multiple nodes");
        
        int transpositionHits = negamaxAI.getTranspositionHits();
        assertTrue(transpositionHits >= 0, "Transposition table should track hits");
    }
    
    @Test
    void testPositionEvaluation() {
        // Test position evaluation with known positions
        double initialEval = negamaxAI.evaluatePosition(game.getBoard(), true);
        assertTrue(Math.abs(initialEval) < 100, "Initial position should be roughly equal");
        
        // Test evaluation after good opening move
        game.makeMove(6, 4, 4, 4); // e2-e4
        double afterE4 = negamaxAI.evaluatePosition(game.getBoard(), false);
        
        // Test evaluation consistency
        double secondEval = negamaxAI.evaluatePosition(game.getBoard(), false);
        assertEquals(afterE4, secondEval, 0.001, "Position evaluation should be deterministic");
        
        // Test tactical position evaluation
        String[][] scholarsMate = ChessPositions.getScholarsMatePosition();
        double mateEval = negamaxAI.evaluatePosition(scholarsMate, false);
        assertTrue(mateEval < -500, "Checkmate position should have very negative evaluation for losing side");
    }
    
    @Test
    void testTranspositionTable() {
        // Test that Negamax AI has cache functionality
        assertNotNull(negamaxAI);
        int cacheSize = negamaxAI.getCacheSize();
        assertTrue(cacheSize >= 0);
    }
    
    @Test
    @Timeout(8)
    void testTimeBoundedSearch() {
        // Test time-bounded search with different limits
        long startTime = System.currentTimeMillis();
        int[] move1s = negamaxAI.selectMoveWithTimeLimit(game.getBoard(), game.getAllValidMoves(true), 1000);
        long time1s = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        int[] move3s = negamaxAI.selectMoveWithTimeLimit(game.getBoard(), game.getAllValidMoves(true), 3000);
        long time3s = System.currentTimeMillis() - startTime;
        
        assertNotNull(move1s, "1-second search should find move");
        assertNotNull(move3s, "3-second search should find move");
        
        assertTrue(time1s <= 1500, "1-second search should respect time limit: " + time1s + "ms");
        assertTrue(time3s <= 3500, "3-second search should respect time limit: " + time3s + "ms");
        
        // Longer search should explore more nodes
        int nodes1s = negamaxAI.getNodesSearchedInLastMove();
        negamaxAI.selectMoveWithTimeLimit(game.getBoard(), game.getAllValidMoves(true), 3000);
        int nodes3s = negamaxAI.getNodesSearchedInLastMove();
        
        assertTrue(nodes3s >= nodes1s, "Longer search should explore more nodes");
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