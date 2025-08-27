package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MCPInputValidatorTest {
    
    private static final Logger logger = LogManager.getLogger(MCPInputValidatorTest.class);
    
    private MCPInputValidator validator;
    
    @BeforeEach
    public void setUp() {
        validator = new MCPInputValidator();
    }
    
    @Test
    public void testValidCreateGameInput() {
        logger.info("Testing valid create game input validation");
        
        Map<String, Object> args = new HashMap<>();
        args.put("aiOpponent", "AlphaZero");
        args.put("playerColor", "white");
        args.put("difficulty", 7);
        
        ValidationResult result = validator.validateToolCall("create_chess_game", args);
        assertTrue(result.isValid());
    }
    
    @Test
    public void testInvalidAIOpponent() {
        logger.info("Testing invalid AI opponent validation");
        
        Map<String, Object> args = new HashMap<>();
        args.put("aiOpponent", "InvalidAI");
        args.put("playerColor", "white");
        
        ValidationResult result = validator.validateToolCall("create_chess_game", args);
        assertFalse(result.isValid());
        assertTrue(result.getError().contains("Invalid AI opponent"));
    }
    
    @Test
    public void testValidMakeMoveInput() {
        logger.info("Testing valid make move input validation");
        
        Map<String, Object> args = new HashMap<>();
        args.put("sessionId", "12345678-1234-1234-1234-123456789012");
        args.put("move", "e4");
        
        ValidationResult result = validator.validateToolCall("make_chess_move", args);
        assertTrue(result.isValid());
    }
    
    @Test
    public void testAllValidAIOpponents() {
        logger.info("Testing all valid AI opponents");
        
        String[] validAI = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                           "MCTS", "Negamax", "OpenAI", "QLearning", 
                           "DeepLearning", "CNN", "DQN", "Genetic"};
        
        for (String ai : validAI) {
            Map<String, Object> args = new HashMap<>();
            args.put("aiOpponent", ai);
            args.put("playerColor", "white");
            
            ValidationResult result = validator.validateToolCall("create_chess_game", args);
            assertTrue(result.isValid(), "AI " + ai + " should be valid");
        }
    }
}



