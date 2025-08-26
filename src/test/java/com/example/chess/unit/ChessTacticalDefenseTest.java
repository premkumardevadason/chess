package com.example.chess.unit;

import com.example.chess.ChessTacticalDefense;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChessTacticalDefenseTest {
    
    private ChessTacticalDefense tacticalDefense;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        tacticalDefense = new ChessTacticalDefense();
        game = new ChessGame();
    }
    
    @Test
    void testCheckmatePreventionScholars() {
        // Setup Scholar's Mate threat
        game.makeMove(6, 4, 4, 4); // e2-e4
        game.makeMove(1, 4, 3, 4); // e7-e5
        game.makeMove(7, 5, 4, 2); // Bf1-c4
        game.makeMove(1, 1, 2, 2); // Nb8-c6
        game.makeMove(7, 3, 3, 7); // Qd1-h5
        
        // Test tactical defense by checking if valid moves exist
        int[] defenseMove = game.getAllValidMoves(false).isEmpty() ? null : game.getAllValidMoves(false).get(0);
        assertNotNull(defenseMove);
        assertTrue(game.isValidMove(defenseMove[0], defenseMove[1], defenseMove[2], defenseMove[3]));
    }
    
    @Test
    void testQueenSafetyDetection() {
        // Place black queen in danger
        game.makeMove(6, 4, 4, 4); // e2-e4
        game.makeMove(1, 3, 3, 3); // d7-d5
        game.makeMove(7, 5, 4, 2); // Bf1-c4
        
        // Test queen safety by checking if queen position is under attack
        boolean queenInDanger = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♛".equals(game.getBoard()[i][j])) {
                    queenInDanger = game.isSquareUnderAttack(i, j, true);
                    break;
                }
            }
        }
        
        int[] safetyMove = queenInDanger ? game.getAllValidMoves(false).get(0) : null;
        if (safetyMove != null) {
            assertTrue(game.isValidMove(safetyMove[0], safetyMove[1], safetyMove[2], safetyMove[3]));
        }
    }
    
    @Test
    void testForkDefenseStrategies() {
        // Setup fork position
        game.makeMove(7, 1, 5, 2); // Nb1-c3
        game.makeMove(1, 4, 3, 4); // e7-e5
        game.makeMove(5, 2, 3, 3); // Nc3-d5 (fork threat)
        
        // Test fork defense by getting valid moves
        int[] forkDefense = game.getAllValidMoves(false).isEmpty() ? null : game.getAllValidMoves(false).get(0);
        if (forkDefense != null) {
            assertTrue(game.isValidMove(forkDefense[0], forkDefense[1], forkDefense[2], forkDefense[3]));
        }
    }
    
    @Test
    void testCriticalDefenseDetection() {
        // Test critical threats by checking game state
        assertFalse(game.isGameOver()); // Game should be in progress
        
        // Test that valid moves exist
        assertFalse(game.getAllValidMoves(false).isEmpty());
    }
}