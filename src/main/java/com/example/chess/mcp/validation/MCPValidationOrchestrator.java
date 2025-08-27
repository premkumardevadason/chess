package com.example.chess.mcp.validation;

import com.example.chess.mcp.session.ChessGameSession;
import com.example.chess.mcp.session.MCPSessionManager;
import com.example.chess.mcp.ratelimit.MCPRateLimiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MCPValidationOrchestrator {
    
    private static final Logger logger = LogManager.getLogger(MCPValidationOrchestrator.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private MCPInputValidator inputValidator;
    
    @Autowired
    private ChessMoveValidator moveValidator;
    
    @Autowired
    private ResourceScopeValidator resourceValidator;
    
    @Autowired
    private MCPRateLimiter rateLimiter;
    
    public ValidationResult validateToolCall(String agentId, String toolName, Map<String, Object> arguments) {
        logger.debug("Validating tool call from agent {}: {}", agentId, toolName);
        
        // 1. Rate limiting check (simplified for now)
        if (!rateLimiter.allowRequest(agentId, toolName)) {
            logger.warn("Rate limit exceeded for agent {}", agentId);
            return ValidationResult.rateLimited("Rate limit exceeded", java.time.Duration.ofMinutes(1));
        }
        
        // 2. Input schema validation
        ValidationResult inputValidation = inputValidator.validateToolCall(toolName, arguments);
        if (!inputValidation.isValid()) {
            logger.warn("Input validation failed for agent {}: {}", agentId, inputValidation.getError());
            return inputValidation;
        }
        
        // 3. Tool-specific validation
        if ("make_chess_move".equals(toolName)) {
            return validateMoveToolCall(agentId, arguments);
        }
        
        return ValidationResult.valid();
    }
    
    public ValidationResult validateResourceAccess(String agentId, String resourceUri) {
        logger.debug("Validating resource access for agent {}: {}", agentId, resourceUri);
        
        // 1. Rate limiting check (simplified for now)
        if (!rateLimiter.allowRequest(agentId, "resource_access")) {
            return ValidationResult.rateLimited("Rate limit exceeded", java.time.Duration.ofMinutes(1));
        }
        
        // 2. Resource scope validation
        ResourceScopeValidator.AccessValidationResult accessValidation = resourceValidator.validateResourceAccess(agentId, resourceUri);
        if (!accessValidation.isAllowed()) {
            logger.warn("Resource access denied for agent {}: {}", agentId, accessValidation.getReason());
            return ValidationResult.accessDenied(accessValidation.getReason());
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMoveToolCall(String agentId, Map<String, Object> arguments) {
        String sessionId = (String) arguments.get("sessionId");
        String move = (String) arguments.get("move");
        
        // Get game session
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ValidationResult.invalid("Session not found: " + sessionId);
        }
        
        // Verify agent owns session
        if (!session.getAgentId().equals(agentId)) {
            logger.warn("Agent {} attempted to make move in session {} owned by {}", 
                       agentId, sessionId, session.getAgentId());
            return ValidationResult.accessDenied("Session belongs to different agent");
        }
        
        // Chess move validation
        ChessMoveValidator.MoveValidationResult moveValidation = moveValidator.validateMove(sessionId, move, session.getGame());
        if (!moveValidation.isValid()) {
            return ValidationResult.invalid(moveValidation.getError());
        }
        
        return ValidationResult.valid();
    }
}

