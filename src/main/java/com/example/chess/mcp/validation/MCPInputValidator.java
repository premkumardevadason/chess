package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class MCPInputValidator {
    
    private static final Logger logger = LogManager.getLogger(MCPInputValidator.class);
    
    private static final Set<String> VALID_AI_OPPONENTS = Set.of(
        "AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
        "MCTS", "Negamax", "OpenAI", "QLearning", 
        "DeepLearning", "CNN", "DQN", "Genetic"
    );
    
    private static final Set<String> VALID_COLORS = Set.of("white", "black");
    private static final Set<String> VALID_PROMOTIONS = Set.of("Q", "R", "B", "N");
    private static final Set<String> VALID_HINT_LEVELS = Set.of("beginner", "intermediate", "advanced", "master");
    
    public ValidationResult validateToolCall(String toolName, Map<String, Object> arguments) {
        logger.debug("Validating tool call: {} with args: {}", toolName, arguments);
        
        switch (toolName) {
            case "create_chess_game":
                return validateCreateGameInput(arguments);
            case "make_chess_move":
                return validateMakeMoveInput(arguments);
            case "get_board_state":
                return validateGetBoardStateInput(arguments);
            case "analyze_position":
                return validateAnalyzePositionInput(arguments);
            case "get_legal_moves":
                return validateGetLegalMovesInput(arguments);
            case "get_move_hint":
                return validateGetMoveHintInput(arguments);
            case "create_tournament":
                return validateCreateTournamentInput(arguments);
            case "get_tournament_status":
                return validateGetTournamentStatusInput(arguments);
            default:
                return ValidationResult.invalid("Unknown tool: " + toolName);
        }
    }
    
    private ValidationResult validateCreateGameInput(Map<String, Object> args) {
        String aiOpponent = (String) args.get("aiOpponent");
        if (!VALID_AI_OPPONENTS.contains(aiOpponent)) {
            return ValidationResult.invalid("Invalid AI opponent: " + aiOpponent);
        }
        
        String playerColor = (String) args.get("playerColor");
        if (!VALID_COLORS.contains(playerColor)) {
            return ValidationResult.invalid("Invalid player color: " + playerColor);
        }
        
        Integer difficulty = (Integer) args.get("difficulty");
        if (difficulty != null && (difficulty < 1 || difficulty > 10)) {
            return ValidationResult.invalid("Invalid difficulty: " + difficulty);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMakeMoveInput(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        if (!isValidSessionId(sessionId)) {
            return ValidationResult.invalid("Invalid session ID format: " + sessionId);
        }
        
        String move = (String) args.get("move");
        if (!isValidMoveFormat(move)) {
            return ValidationResult.invalid("Invalid move format: " + move);
        }
        
        String promotion = (String) args.get("promotion");
        if (promotion != null && !VALID_PROMOTIONS.contains(promotion)) {
            return ValidationResult.invalid("Invalid promotion piece: " + promotion);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateGetBoardStateInput(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        if (!isValidSessionId(sessionId)) {
            return ValidationResult.invalid("Invalid session ID format: " + sessionId);
        }
        return ValidationResult.valid();
    }
    
    private ValidationResult validateAnalyzePositionInput(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        if (!isValidSessionId(sessionId)) {
            return ValidationResult.invalid("Invalid session ID format: " + sessionId);
        }
        
        Integer depth = (Integer) args.get("depth");
        if (depth != null && (depth < 1 || depth > 20)) {
            return ValidationResult.invalid("Invalid analysis depth: " + depth);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateGetLegalMovesInput(Map<String, Object> args) {
        return validateGetBoardStateInput(args); // Same validation
    }
    
    private ValidationResult validateGetMoveHintInput(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        if (!isValidSessionId(sessionId)) {
            return ValidationResult.invalid("Invalid session ID format: " + sessionId);
        }
        
        String hintLevel = (String) args.get("hintLevel");
        if (hintLevel != null && !VALID_HINT_LEVELS.contains(hintLevel)) {
            return ValidationResult.invalid("Invalid hint level: " + hintLevel);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateCreateTournamentInput(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        if (agentId == null || agentId.trim().isEmpty()) {
            return ValidationResult.invalid("Agent ID is required");
        }
        
        String playerColor = (String) args.get("playerColor");
        if (!VALID_COLORS.contains(playerColor)) {
            return ValidationResult.invalid("Invalid player color: " + playerColor);
        }
        
        Integer difficulty = (Integer) args.get("difficulty");
        if (difficulty != null && (difficulty < 1 || difficulty > 10)) {
            return ValidationResult.invalid("Invalid difficulty: " + difficulty);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateGetTournamentStatusInput(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        if (agentId == null || agentId.trim().isEmpty()) {
            return ValidationResult.invalid("Agent ID is required");
        }
        return ValidationResult.valid();
    }
    
    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("^chess-session-[a-zA-Z0-9-]+$");
    }
    
    private boolean isValidMoveFormat(String move) {
        return move != null && move.matches("^[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?$|^O-O(-O)?[+#]?$");
    }
}