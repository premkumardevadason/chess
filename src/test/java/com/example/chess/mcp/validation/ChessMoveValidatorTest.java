package com.example.chess.mcp.validation;

import com.example.chess.ChessGame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ChessMoveValidatorTest {
    
    private static final Logger logger = LogManager.getLogger(ChessMoveValidatorTest.class);
    
    private ChessMoveValidator validator;
    private ChessGame game;
    
    @BeforeEach
    public void setUp() {
        validator = new ChessMoveValidator();
        game = new ChessGame();
    }
    
    @Test
    public void testValidMove() {
        logger.info("Testing valid chess move validation");
        
        ChessMoveValidator.MoveValidationResult result = validator.validateMove("test-session", "e4", game);
        assertTrue(result.isValid());
    }
    
    @Test
    public void testForbiddenPatterns() {
        logger.info("Testing forbidden pattern detection");
        
        String[] forbiddenMoves = {"DROP TABLE", "DELETE FROM", "EXEC cmd", "SYSTEM ls"};
        
        for (String move : forbiddenMoves) {
            ChessMoveValidator.MoveValidationResult result = validator.validateMove("test-session", move, game);
            assertFalse(result.isValid());
            assertTrue(result.isForbidden());
        }
    }
    
    @Test
    public void testInvalidChessNotation() {
        logger.info("Testing invalid chess notation");
        
        ChessMoveValidator.MoveValidationResult result = validator.validateMove("test-session", "invalid-move", game);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("Invalid chess notation"));
    }
    
    @Test
    public void testValidChessNotations() {
        logger.info("Testing valid chess notations");
        
        String[] validMoves = {"e4", "Nf3", "Bb5", "O-O", "O-O-O", "exd5", "Qh5+", "Rxf7#"};
        
        for (String move : validMoves) {
            ChessMoveValidator.MoveValidationResult result = validator.validateMove("test-session", move, game);
            // Note: Some moves may be illegal in starting position but notation is valid
            if (!result.isValid() && !result.getError().contains("Illegal move")) {
                fail("Move " + move + " should have valid notation: " + result.getError());
            }
        }
    }
}