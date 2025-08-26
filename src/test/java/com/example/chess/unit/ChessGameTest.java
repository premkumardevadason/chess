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
        game.makeMove(6, 4, 4, 4);
        game.makeMove(1, 4, 3, 4);
        game.makeMove(7, 5, 4, 2);
        // Test castling by checking if king can move 2 squares
        assertTrue(game.isValidMove(7, 4, 7, 6)); // Kingside castling
    }
    
    @Test
    void testCheckDetection() {
        ChessGame checkGame = new ChessGame();
        checkGame.makeMove(6, 4, 4, 4);
        checkGame.makeMove(1, 4, 3, 4);
        checkGame.makeMove(7, 5, 4, 2);
        checkGame.makeMove(1, 1, 2, 2);
        checkGame.makeMove(7, 3, 3, 7);
        checkGame.makeMove(1, 5, 2, 5);
        checkGame.makeMove(3, 7, 1, 5);
        // Check if game is over (indicates checkmate)
        assertTrue(checkGame.isGameOver());
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