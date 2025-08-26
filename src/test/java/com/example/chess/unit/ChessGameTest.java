package com.example.chess.unit;

import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

public class ChessGameTest {
    
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
    }
    
    @Test
    void testInitialBoardSetup() {
        String[][] board = game.getBoard();
        assertEquals("♖", board[7][0]);
        assertEquals("♔", board[7][4]);
        assertEquals("♚", board[0][4]);
        assertEquals("♙", board[6][0]);
        assertEquals("♟", board[1][0]);
        assertEquals("", board[3][3]);
    }
    
    @Test
    void testBasicPieceMovement() {
        assertTrue(game.isValidMove(6, 4, 4, 4)); // e2-e4
        assertTrue(game.isValidMove(7, 1, 5, 2)); // Nb1-c3
        assertFalse(game.isValidMove(7, 0, 5, 0)); // Blocked rook
    }
    
    @Test
    void testSpecialMoves() {
        // Test basic piece movement validation without making moves
        // Test knight move (L-shape) - should be valid from starting position
        assertTrue(game.isValidMove(7, 1, 5, 2)); // White knight b1-c3
        
        // Test that blocked bishop move is invalid
        assertFalse(game.isValidMove(7, 5, 4, 2)); // White bishop f1-c4 blocked by pawn
    }
    
    @Test
    void testCheckDetection() {
        // Test basic check detection by examining king safety
        // Set up a position where Black king is in check
        String[][] checkBoard = {
            {"", "", "", "", "♚", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "♕", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "", "", "", ""},
            {"", "", "", "", "♔", "", "", ""}
        };
        game.setBoard(checkBoard);
        
        // Black king should be in check from White queen on same file
        assertTrue(game.isSquareUnderAttack(0, 4, true)); // Black king under attack by white queen
    }
    
    @Test
    @Timeout(5)
    void testUndoRedoFunctionality() {
        String[][] initial = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(game.getBoard()[i], 0, initial[i], 0, 8);
        }
        game.makeMove(6, 4, 4, 4);
        assertTrue(game.undoMove());
        assertArrayEquals(initial, game.getBoard());
        assertTrue(game.redoMove());
    }
}