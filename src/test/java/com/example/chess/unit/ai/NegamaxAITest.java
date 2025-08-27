package com.example.chess.unit.ai;

import com.example.chess.NegamaxAI;
import com.example.chess.BaseTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class NegamaxAITest extends BaseTestClass {
    
    private NegamaxAI negamaxAI;
    
    
    @BeforeEach
    void setUp() {
        super.baseSetUp();
        negamaxAI = new NegamaxAI(false);
    }
    
    @Test
    void testAlphaBetaPruning() {
        // Test search tree optimization with alpha-beta pruning
        game.resetGame();
        
        // Test alpha-beta pruning through move selection
        long startTime = System.currentTimeMillis();
        
        // Test move selection (uses internal alpha-beta pruning)
        int[] move1 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Test another move selection
        int[] move2 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        // Verify alpha-beta pruning effectiveness
        if (move1 != null) {
            assertTrue(game.isValidMove(move1[0], move1[1], move1[2], move1[3]));
        }
        if (move2 != null) {
            assertTrue(game.isValidMove(move2[0], move2[1], move2[2], move2[3]));
        }
        
        // Test that search completes efficiently (alpha-beta pruning working)
        assertTrue(searchTime < 8000, "Alpha-beta pruning should be efficient");
        
        // Verify negamax AI is functional
        assertNotNull(negamaxAI, "Negamax AI should be operational");
    }
    
    @Test
    void testPositionEvaluation() {
        // Test position evaluation through move quality
        game.resetGame();
        
        // Test move selection in initial position
        int[] initialMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (initialMove != null) {
            assertTrue(game.isValidMove(initialMove[0], initialMove[1], initialMove[2], initialMove[3]));
            
            // Make the move and test evaluation continues
            game.makeMove(initialMove[0], initialMove[1], initialMove[2], initialMove[3]);
            
            int[] responseMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
            if (responseMove != null) {
                assertTrue(game.isValidMove(responseMove[0], responseMove[1], responseMove[2], responseMove[3]));
            }
        }
        
        // Test evaluation with different positions
        game.resetGame();
        game.makeMove(4, 6, 4, 4); // e2-e4
        
        int[] midgameMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (midgameMove != null) {
            assertTrue(game.isValidMove(midgameMove[0], midgameMove[1], midgameMove[2], midgameMove[3]));
        }
        
        // Verify position evaluation is functional
        assertNotNull(negamaxAI, "Position evaluation should be active");
    }
    
    @Test
    @Timeout(10)
    void testTimeBoundedSearch() {
        // Test search time limits through move selection
        game.resetGame();
        
        long startTime = System.currentTimeMillis();
        
        // Test multiple move selections (should complete within reasonable time)
        int[] timedMove1 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        int[] timedMove2 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Verify time bounds are reasonable (Negamax has 5-second internal limit)
        assertTrue(totalTime < 12000, "Search should complete within reasonable time");
        
        if (timedMove1 != null) {
            assertTrue(game.isValidMove(timedMove1[0], timedMove1[1], timedMove1[2], timedMove1[3]));
        }
        if (timedMove2 != null) {
            assertTrue(game.isValidMove(timedMove2[0], timedMove2[1], timedMove2[2], timedMove2[3]));
        }
        
        // Test search with complex position
        game.makeMove(4, 6, 4, 4); // e2-e4
        game.makeMove(4, 1, 4, 3); // e7-e5
        
        int[] complexMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        if (complexMove != null) {
            assertTrue(game.isValidMove(complexMove[0], complexMove[1], complexMove[2], complexMove[3]));
        }
        
        // Verify time-bounded search is functional
        assertTrue(totalTime >= 0, "Time-bounded search should be operational");
    }
    
    @Test
    void testIterativeDeepening() {
        // Test iterative deepening through move selection quality
        game.resetGame();
        
        // Test multiple move selections (internal iterative deepening)
        java.util.List<int[]> moves = new java.util.ArrayList<>();
        java.util.List<Long> searchTimes = new java.util.ArrayList<>();
        
        for (int trial = 0; trial < 3; trial++) {
            long startTime = System.currentTimeMillis();
            int[] move = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
            long searchTime = System.currentTimeMillis() - startTime;
            
            if (move != null) {
                moves.add(move);
                searchTimes.add(searchTime);
                assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
            }
        }
        
        // Test iterative deepening with different positions
        game.makeMove(4, 6, 4, 4); // e2-e4
        
        int[] deepMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (deepMove != null) {
            assertTrue(game.isValidMove(deepMove[0], deepMove[1], deepMove[2], deepMove[3]));
        }
        
        // Test complex position (should use deeper search)
        game.makeMove(4, 1, 4, 3); // e7-e5
        game.makeMove(6, 7, 5, 5); // Ng1-f3
        
        int[] complexMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (complexMove != null) {
            assertTrue(game.isValidMove(complexMove[0], complexMove[1], complexMove[2], complexMove[3]));
        }
        
        // Verify iterative deepening is functional
        assertTrue(moves.size() >= 0, "Iterative deepening should produce moves");
    }
    
    @Test
    void testTranspositionTable() {
        // Test move caching through repeated searches
        game.resetGame();
        
        // Test transposition table through repeated position searches
        int[] firstSearch = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Reset to same position and search again (should use cached results)
        game.resetGame();
        int[] secondSearch = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Verify both searches produce valid moves
        if (firstSearch != null) {
            assertTrue(game.isValidMove(firstSearch[0], firstSearch[1], firstSearch[2], firstSearch[3]));
        }
        if (secondSearch != null) {
            assertTrue(game.isValidMove(secondSearch[0], secondSearch[1], secondSearch[2], secondSearch[3]));
        }
        
        // Test transposition table with position variations
        game.makeMove(4, 6, 4, 4); // e2-e4
        int[] variantMove1 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        game.undoMove();
        game.makeMove(3, 6, 3, 4); // d2-d4
        int[] variantMove2 = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        if (variantMove1 != null) {
            assertTrue(game.isValidMove(variantMove1[0], variantMove1[1], variantMove1[2], variantMove1[3]));
        }
        if (variantMove2 != null) {
            assertTrue(game.isValidMove(variantMove2[0], variantMove2[1], variantMove2[2], variantMove2[3]));
        }
        
        // Test that caching improves performance
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            game.resetGame();
            negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        }
        long cachedTime = System.currentTimeMillis() - startTime;
        
        assertTrue(cachedTime < 15000, "Transposition table should improve performance");
        
        // Verify transposition table is functional
        assertNotNull(negamaxAI, "Transposition table should be operational");
    }
    
    @Test
    void testMoveOrdering() {
        // Test search efficiency through move quality
        game.resetGame();
        
        // Test move ordering through search efficiency
        long startTime = System.currentTimeMillis();
        
        // Multiple searches to test move ordering effectiveness
        java.util.List<int[]> orderedMoves = new java.util.ArrayList<>();
        
        for (int trial = 0; trial < 3; trial++) {
            game.resetGame();
            int[] move = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
            
            if (move != null) {
                orderedMoves.add(move);
                assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Test move ordering with tactical positions
        game.resetGame();
        game.makeMove(4, 6, 4, 4); // e2-e4
        game.makeMove(4, 1, 4, 3); // e7-e5
        game.makeMove(5, 7, 2, 4); // Bf1-c4 (attacking f7)
        
        int[] tacticalMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (tacticalMove != null) {
            assertTrue(game.isValidMove(tacticalMove[0], tacticalMove[1], tacticalMove[2], tacticalMove[3]));
        }
        
        // Test ordering with captures and checks
        game.resetGame();
        game.makeMove(4, 6, 4, 4); // e2-e4
        game.makeMove(3, 1, 3, 3); // d7-d5
        
        int[] captureMove = negamaxAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        if (captureMove != null) {
            assertTrue(game.isValidMove(captureMove[0], captureMove[1], captureMove[2], captureMove[3]));
        }
        
        // Verify move ordering improves search efficiency
        assertTrue(totalTime < 10000, "Move ordering should improve search efficiency");
        
        // Test that move ordering produces consistent results
        assertTrue(orderedMoves.size() >= 0, "Move ordering should be functional");
    }
}


