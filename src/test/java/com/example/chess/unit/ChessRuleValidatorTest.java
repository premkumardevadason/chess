package com.example.chess.unit;

import com.example.chess.ChessRuleValidator;
import com.example.chess.ChessGame;
import com.example.chess.fixtures.ChessPositions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChessRuleValidatorTest {
    
    private ChessRuleValidator validator;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        validator = new ChessRuleValidator();
        game = new ChessGame();
    }
    
    @Test
    void testMoveValidation() {
        // Valid moves
        assertTrue(validator.isValidMove(game.getBoard(), 6, 4, 4, 4, true)); // e2-e4
        assertTrue(validator.isValidMove(game.getBoard(), 7, 1, 5, 2, true)); // Nb1-c3
        
        // Invalid moves
        assertFalse(validator.isValidMove(game.getBoard(), 7, 0, 5, 0, true)); // Blocked rook
        assertFalse(validator.isValidMove(game.getBoard(), 6, 0, 4, 2, true)); // Invalid pawn move
        assertFalse(validator.isValidMove(game.getBoard(), 0, 0, 7, 7, true)); // Wrong color
    }
    
    @Test
    void testPinnedPieceMovement() {
        String[][] pinPosition = ChessPositions.getPinPosition();
        game.setBoard(pinPosition);
        
        // Knight should be pinned and unable to move
        assertFalse(validator.isValidMove(pinPosition, 0, 1, 2, 2, false));
        
        // King should be able to move
        assertTrue(validator.isValidMove(pinPosition, 0, 4, 0, 3, false));
    }
    
    @Test
    void testDiscoveredCheck() {
        String[][] position = ChessPositions.getPinPosition();
        game.setBoard(position);
        
        // Moving pinned piece should not be allowed if it exposes king
        assertFalse(validator.isValidMove(position, 0, 1, 2, 0, false));
    }
    
    @Test
    void testCastlingRules() {
        String[][] castlingPosition = ChessPositions.getCastlingPosition();
        game.setBoard(castlingPosition);
        
        // Both sides should be able to castle
        // Test castling by checking if king can move 2 squares
        game.setBoard(castlingPosition);
        assertTrue(game.isValidMove(7, 4, 7, 6)); // White kingside
        assertTrue(game.isValidMove(7, 4, 7, 2)); // White queenside
        assertTrue(game.isValidMove(0, 4, 0, 6)); // Black kingside
        assertTrue(game.isValidMove(0, 4, 0, 2)); // Black queenside
    }
    
    @Test
    void testEnPassantRules() {
        String[][] enPassantPosition = ChessPositions.getEnPassantPosition();
        game.setBoard(enPassantPosition);
        // Test en passant by checking if move is valid
        game.setBoard(enPassantPosition);
        // Simulate the double pawn move that enables en passant
        assertTrue(game.isValidMove(3, 4, 2, 5)); // En passant capture
    }
    
    @Test
    void testPawnPromotionRules() {
        String[][] promotionPosition = ChessPositions.getPromotionPosition();
        game.setBoard(promotionPosition);
        
        // Test pawn promotion by checking board state
        game.setBoard(promotionPosition);
        assertTrue(game.isValidMove(1, 0, 0, 0)); // Pawn can move to promotion square
    }
    
    @Test
    void testCheckValidation() {
        String[][] scholarsMate = ChessPositions.getScholarsMatePosition();
        
        game.setBoard(scholarsMate);
        assertTrue(game.isGameOver()); // Game should be over in checkmate position
    }
    
    @Test
    void testCheckmateValidation() {
        String[][] scholarsMate = ChessPositions.getScholarsMatePosition();
        
        game.setBoard(scholarsMate);
        assertTrue(game.isGameOver()); // Black is checkmated
    }
    
    @Test
    void testStalemateValidation() {
        String[][] stalemate = ChessPositions.getStalematePosition();
        
        game.setBoard(stalemate);
        assertTrue(game.isGameOver()); // Stalemate position
    }
}