package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class MCPValidationOrchestrator {
    
    private static final Logger logger = LogManager.getLogger(MCPValidationOrchestrator.class);
    
    private static final Set<String> VALID_AI_OPPONENTS = Set.of(
        "AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", "MCTS", "Negamax",
        "OpenAI", "QLearning", "DeepLearning", "CNN", "DQN", "Genetic"
    );
    
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "DROP", "DELETE", "UPDATE", "INSERT", "CREATE", "ALTER", "TRUNCATE",
        "EXEC", "EXECUTE", "SYSTEM", "CMD", "SHELL", "SCRIPT", "EVAL"
    );
    
    public ValidationResult validateToolCall(String agentId, String toolName, Map<String, Object> arguments) {
        logger.debug("Validating tool call from agent {}: {}", agentId, toolName);
        
        // Basic input validation
        ValidationResult inputValidation = validateInputs(toolName, arguments);
        if (!inputValidation.isValid()) {
            return inputValidation;
        }
        
        // Tool-specific validation
        switch (toolName) {
            case "create_chess_game":
            case "create_tournament":
                return validateCreateGameInput(arguments);
            case "make_chess_move":
                return validateMakeMoveInput(arguments);
            default:
                return ValidationResult.valid();
        }
    }
    
    private ValidationResult validateInputs(String toolName, Map<String, Object> arguments) {
        if (arguments == null) {
            return ValidationResult.invalid("Arguments cannot be null");
        }
        
        // Check for required fields based on tool
        switch (toolName) {
            case "create_chess_game":
            case "create_tournament":
                if (!arguments.containsKey("agentId") || !arguments.containsKey("aiOpponent") || !arguments.containsKey("playerColor")) {
                    return ValidationResult.invalid("Missing required fields: agentId, aiOpponent, playerColor");
                }
                break;
            case "make_chess_move":
                if (!arguments.containsKey("sessionId") || !arguments.containsKey("move")) {
                    return ValidationResult.invalid("Missing required fields: sessionId, move");
                }
                break;
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateCreateGameInput(Map<String, Object> args) {
        // Validate AI opponent
        String aiOpponent = (String) args.get("aiOpponent");
        if (!VALID_AI_OPPONENTS.contains(aiOpponent)) {
            return ValidationResult.invalid("Invalid AI opponent: " + aiOpponent);
        }
        
        // Validate player color
        String playerColor = (String) args.get("playerColor");
        if (!"white".equals(playerColor) && !"black".equals(playerColor)) {
            return ValidationResult.invalid("Invalid player color: " + playerColor);
        }
        
        // Validate difficulty
        Object difficultyObj = args.get("difficulty");
        if (difficultyObj != null) {
            Integer difficulty = (Integer) difficultyObj;
            if (difficulty < 1 || difficulty > 10) {
                return ValidationResult.invalid("Invalid difficulty: " + difficulty);
            }
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMakeMoveInput(Map<String, Object> args) {
        // Validate session ID format
        String sessionId = (String) args.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ValidationResult.invalid("Session ID cannot be empty");
        }
        
        // Validate move format and security
        String move = (String) args.get("move");
        if (move == null || move.trim().isEmpty()) {
            return ValidationResult.invalid("Move cannot be empty");
        }
        
        // Security check - prevent injection attacks
        String upperMove = move.toUpperCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (upperMove.contains(pattern)) {
                logger.warn("Blocked potentially malicious move: {}", move);
                return ValidationResult.invalid("Move contains forbidden patterns");
            }
        }
        
        // Basic chess notation validation
        if (!isValidChessNotation(move)) {
            return ValidationResult.invalid("Invalid chess notation: " + move);
        }
        
        return ValidationResult.valid();
    }
    
    private boolean isValidChessNotation(String move) {
        // Basic algebraic notation validation
        return move.matches("^[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?$|^O-O(-O)?[+#]?$");
    }
}

