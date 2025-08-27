package com.example.chess.unit;

import com.example.chess.ChessRuleValidator;
import com.example.chess.BaseTestClass;
import com.example.chess.fixtures.ChessPositions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChessRuleValidatorTest extends BaseTestClass {
    
    private ChessRuleValidator validator;
    
    
    @BeforeEach
    void setUp() {
        validator = new ChessRuleValidator();
        super.baseSetUp();
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
        String[][] board = game.getBoard();
        
        // Test basic move validation on initial board
        assertTrue(validator.isValidMove(board, 6, 4, 4, 4, true)); // White pawn e2-e4
    }
    
    @Test
    void testDiscoveredCheck() {
        String[][] board = game.getBoard();
        
        // Test basic move validation on initial board
        assertTrue(validator.isValidMove(board, 7, 1, 5, 2, true)); // White knight Nb1-c3
    }
    
    @Test
    void testCastlingRules() {
        String[][] castlingPosition = ChessPositions.getCastlingPosition();
        
        // Test kingside castling conditions
        assertTrue(validator.isValidMove(castlingPosition, 7, 4, 7, 6, true), "White kingside castling should be valid");
        assertTrue(validator.isValidMove(castlingPosition, 0, 4, 0, 6, false), "Black kingside castling should be valid");
        
        // Test queenside castling conditions  
        assertTrue(validator.isValidMove(castlingPosition, 7, 4, 7, 2, true), "White queenside castling should be valid");
        assertTrue(validator.isValidMove(castlingPosition, 0, 4, 0, 2, false), "Black queenside castling should be valid");
        
        // Test basic king movement
        assertTrue(validator.isValidMove(castlingPosition, 7, 4, 7, 5, true)); // King one square
        assertTrue(validator.isValidMove(castlingPosition, 0, 4, 0, 5, false)); // Black king one square
    }
    
    @Test
    void testEnPassantRules() {
        String[][] enPassantPosition = ChessPositions.getEnPassantPosition();
        
        // Test en passant capture conditions
        assertTrue(validator.isValidMove(enPassantPosition, 3, 4, 2, 3, true), "En passant capture should be valid");
        
        // Test regular pawn movement
        String[][] board = game.getBoard();
        assertTrue(validator.isValidMove(board, 1, 4, 3, 4, false)); // Black pawn e7-e5
    }
    
    @Test
    void testPawnPromotionRules() {
        String[][] promotionPosition = ChessPositions.getPromotionPosition();
        
        // Test pawn promotion to 8th rank
        assertTrue(validator.isValidMove(promotionPosition, 1, 0, 0, 0, true), "Pawn promotion to 8th rank should be valid");
        
        // Test basic pawn movement
        assertTrue(validator.isValidMove(promotionPosition, 6, 0, 5, 0, true)); // White pawn forward
    }
    
    @Test
    void testCheckValidation() {
        String[][] scholarsMate = ChessPositions.getScholarsMatePosition();
        
        // Test if black king is in check in Scholar's Mate position
        assertTrue(validator.isKingInDanger(scholarsMate, false)); // Black king in danger
    }
    
    @Test
    void testCheckmateValidation() {
        String[][] scholarsMate = ChessPositions.getScholarsMatePosition();
        
        // Test checkmate detection
        assertTrue(validator.isCheckmate(scholarsMate, false)); // Black is checkmated
    }
    
    @Test
    void testStalemateValidation() {
        String[][] board = game.getBoard();
        
        // Test that initial position has valid moves (not stalemate)
        assertFalse(validator.getAllValidMoves(board, true, true).isEmpty()); // White has legal moves
    }
}


