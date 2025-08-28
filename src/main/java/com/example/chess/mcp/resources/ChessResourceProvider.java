package com.example.chess.mcp.resources;

import com.example.chess.mcp.session.MCPSessionManager;
import com.example.chess.mcp.session.ChessGameSession;
import com.example.chess.mcp.agent.MCPAgentRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChessResourceProvider {
    
    private static final Logger logger = LogManager.getLogger(ChessResourceProvider.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private MCPAgentRegistry agentRegistry;
    
    public Resource getResource(String agentId, String uri) {
        logger.debug("Agent {} requesting resource: {}", agentId, uri);
        
        switch (uri) {
            case "chess://game-sessions":
                return getAgentGameSessions(agentId);
            case "chess://ai-systems":
                return getAvailableAISystems();
            case "chess://opening-book":
                return getOpeningBook();
            case "chess://training-stats":
                return getTrainingStats();
            case "chess://tactical-patterns":
                return getTacticalPatterns();
            default:
                if (uri.startsWith("chess://game-sessions/")) {
                    String sessionId = uri.substring("chess://game-sessions/".length());
                    return getSpecificGameSession(agentId, sessionId);
                }
                throw new IllegalArgumentException("Unknown resource URI: " + uri);
        }
    }
    
    private Resource getAgentGameSessions(String agentId) {
        List<ChessGameSession> sessions = sessionManager.getAgentSessions(agentId);
        
        Map<String, Object> sessionData = sessions.stream()
            .collect(Collectors.toMap(
                ChessGameSession::getSessionId,
                session -> Map.of(
                    "sessionId", session.getSessionId(),
                    "aiOpponent", session.getAIOpponent(),
                    "playerColor", session.getPlayerColor(),
                    "gameStatus", session.getGameStatus(),
                    "movesPlayed", session.getMovesPlayed(),
                    "createdAt", session.getCreatedAt().toString()
                )
            ));
        
        return new Resource("chess://game-sessions", "application/json", 
                          toJson(Map.of("sessions", sessionData)));
    }
    
    private Resource getSpecificGameSession(String agentId, String sessionId) {
        ChessGameSession session = sessionManager.getSession(sessionId);
        
        if (session == null || !session.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("Session not found or access denied: " + sessionId);
        }
        
        ChessGameSession.GameState gameState = session.getGameState();
        return new Resource("chess://game-sessions/" + sessionId, "application/json", 
                          toJson(gameState));
    }
    
    private Resource getAvailableAISystems() {
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                             "MCTS", "Negamax", "OpenAI", "QLearning", 
                             "DeepLearning", "CNN", "DQN", "Genetic"};
        return new Resource("chess://ai-systems", "application/json", 
                          toJson(Map.of("aiSystems", aiSystems)));
    }
    
    private Resource getOpeningBook() {
        return new Resource("chess://opening-book", "application/json", 
                          toJson(Map.of("openings", "100+ professional openings available")));
    }
    
    private Resource getTrainingStats() {
        return new Resource("chess://training-stats", "application/json", 
                          toJson(Map.of("stats", "AI training metrics available")));
    }
    
    private Resource getTacticalPatterns() {
        return new Resource("chess://tactical-patterns", "application/json", 
                          toJson(Map.of("patterns", "Chess tactical patterns available")));
    }
    
    private String toJson(Object obj) {
        // Simple JSON conversion - in production use Jackson
        return obj.toString();
    }
    
    public static class Resource {
        private final String uri;
        private final String mimeType;
        private final String content;
        
        public Resource(String uri, String mimeType, String content) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.content = content;
        }
        
        public String getUri() { return uri; }
        public String getMimeType() { return mimeType; }
        public String getContent() { return content; }
    }
}