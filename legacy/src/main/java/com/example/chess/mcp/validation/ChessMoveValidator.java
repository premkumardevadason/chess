package com.example.chess.mcp.validation;

import com.example.chess.mcp.game.MCPGameState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

@Component
public class ChessMoveValidator {
    
    private static final Logger logger = LogManager.getLogger(ChessMoveValidator.class);
    
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "DROP", "DELETE", "UPDATE", "INSERT", "CREATE", "ALTER", "TRUNCATE",
        "EXEC", "EXECUTE", "SYSTEM", "CMD", "SHELL", "SCRIPT", "EVAL"
    );
    
    public MoveValidationResult validateMove(String sessionId, String move, MCPGameState gameState) {
        logger.debug("Validating move '{}' for session {}", move, sessionId);
        
        // 1. Security validation - prevent injection attacks
        if (containsForbiddenPatterns(move)) {
            logger.warn("Blocked potentially malicious move: {}", move);
            return MoveValidationResult.forbidden("Move contains forbidden patterns");
        }
        
        // 2. Format validation
        if (!isValidChessNotation(move)) {
            return MoveValidationResult.invalid("Invalid chess notation: " + move);
        }
        
        // 3. Game state validation
        if (gameState.isGameOver()) {
            return MoveValidationResult.invalid("Game is already finished");
        }
        
        // 4. Chess rule validation (simplified - would use ChessRuleValidator in full implementation)
        // For now, assume basic validation is sufficient
        if (move.length() < 2 || move.length() > 7) {
            return MoveValidationResult.invalid("Move length invalid: " + move);
        }
        
        // 5. Additional chess-specific validations
        if (!validateChessSpecificRules(move, gameState)) {
            return MoveValidationResult.invalid("Move violates chess rules");
        }
        
        return MoveValidationResult.valid();
    }
    
    private boolean containsForbiddenPatterns(String move) {
        String upperMove = move.toUpperCase();
        return FORBIDDEN_PATTERNS.stream().anyMatch(upperMove::contains);
    }
    
    private boolean isValidChessNotation(String move) {
        return move.matches("^[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?$|^O-O(-O)?[+#]?$");
    }
    
    private boolean validateChessSpecificRules(String move, MCPGameState gameState) {
        // Additional chess rule validations - simplified for MCP
        return true;
    }
    
    public static class MoveValidationResult {
        private final boolean valid;
        private final String error;
        private final List<String> legalMoves;
        private final boolean forbidden;
        
        private MoveValidationResult(boolean valid, String error, List<String> legalMoves, boolean forbidden) {
            this.valid = valid;
            this.error = error;
            this.legalMoves = legalMoves;
            this.forbidden = forbidden;
        }
        
        public static MoveValidationResult valid() {
            return new MoveValidationResult(true, null, null, false);
        }
        
        public static MoveValidationResult invalid(String error) {
            return new MoveValidationResult(false, error, null, false);
        }
        
        public static MoveValidationResult illegal(String move, List<String> legalMoves) {
            return new MoveValidationResult(false, "Illegal move: " + move, legalMoves, false);
        }
        
        public static MoveValidationResult forbidden(String error) {
            return new MoveValidationResult(false, error, null, true);
        }
        
        public boolean isValid() { return valid; }
        public String getError() { return error; }
        public List<String> getLegalMoves() { return legalMoves; }
        public boolean isForbidden() { return forbidden; }
    }
}