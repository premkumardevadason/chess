package com.example.chess.mcp.validation;

import com.example.chess.mcp.session.ChessGameSession;
import com.example.chess.mcp.session.MCPSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class ResourceScopeValidator {
    
    private static final Logger logger = LogManager.getLogger(ResourceScopeValidator.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    private static final Set<String> GLOBAL_RESOURCES = Set.of(
        "chess://ai-systems",
        "chess://opening-book", 
        "chess://training-stats",
        "chess://tactical-patterns"
    );
    
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "system://", "file://", "http://", "https://", "ftp://",
        "database", "config", "admin", "root", "system"
    );
    
    public AccessValidationResult validateResourceAccess(String agentId, String resourceUri) {
        logger.debug("Validating resource access for agent {} to {}", agentId, resourceUri);
        
        // 1. URI format validation
        if (!isValidResourceUri(resourceUri)) {
            return AccessValidationResult.invalid("Invalid resource URI format");
        }
        
        // 2. Agent-specific resource access
        if (resourceUri.startsWith("chess://game-sessions/")) {
            return validateGameSessionAccess(agentId, resourceUri);
        }
        
        // 3. Global resource access (allowed for all agents)
        if (GLOBAL_RESOURCES.contains(resourceUri)) {
            return AccessValidationResult.allowed();
        }
        
        // 4. Agent sessions resource
        if ("chess://game-sessions".equals(resourceUri)) {
            return AccessValidationResult.allowed(); // Agent can access their own sessions
        }
        
        // 5. Forbidden resource patterns
        if (isForbiddenResource(resourceUri)) {
            logger.warn("Agent {} attempted to access forbidden resource: {}", agentId, resourceUri);
            return AccessValidationResult.forbidden("Access to system resources not allowed");
        }
        
        return AccessValidationResult.denied("Resource not found or access denied");
    }
    
    private boolean isValidResourceUri(String uri) {
        return uri != null && uri.startsWith("chess://") && !uri.contains("..") && !uri.contains("//");
    }
    
    private AccessValidationResult validateGameSessionAccess(String agentId, String resourceUri) {
        String sessionId = extractSessionId(resourceUri);
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return AccessValidationResult.denied("Session not found");
        }
        
        if (!session.getAgentId().equals(agentId)) {
            logger.warn("Agent {} attempted to access session {} owned by {}", 
                       agentId, sessionId, session.getAgentId());
            return AccessValidationResult.denied("Access denied - session belongs to different agent");
        }
        
        return AccessValidationResult.allowed();
    }
    
    private boolean isForbiddenResource(String uri) {
        String lowerUri = uri.toLowerCase();
        return FORBIDDEN_PATTERNS.stream().anyMatch(lowerUri::contains);
    }
    
    private String extractSessionId(String resourceUri) {
        return resourceUri.substring("chess://game-sessions/".length());
    }
    
    public static class AccessValidationResult {
        private final boolean allowed;
        private final String reason;
        private final boolean forbidden;
        
        private AccessValidationResult(boolean allowed, String reason, boolean forbidden) {
            this.allowed = allowed;
            this.reason = reason;
            this.forbidden = forbidden;
        }
        
        public static AccessValidationResult allowed() {
            return new AccessValidationResult(true, null, false);
        }
        
        public static AccessValidationResult denied(String reason) {
            return new AccessValidationResult(false, reason, false);
        }
        
        public static AccessValidationResult forbidden(String reason) {
            return new AccessValidationResult(false, reason, true);
        }
        
        public static AccessValidationResult invalid(String reason) {
            return new AccessValidationResult(false, reason, false);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public boolean isForbidden() { return forbidden; }
    }
}