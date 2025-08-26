package com.example.chess.mcp.session;

import com.example.chess.ChessGame;
import com.example.chess.ChessAI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class MCPSessionManager {
    
    private static final Logger logger = LogManager.getLogger(MCPSessionManager.class);
    
    private final ConcurrentHashMap<String, ChessGameSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> agentSessions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock();
    
    private static final int MAX_SESSIONS_PER_AGENT = 10;
    private static final int MAX_TOTAL_SESSIONS = 1000;
    
    @Autowired
    private Map<String, ChessAI> aiSystems;
    
    public String createSession(String agentId, String aiOpponent, String playerColor, int difficulty) {
        sessionLock.writeLock().lock();
        try {
            validateSessionLimits(agentId);
            
            String sessionId = generateSessionId(agentId);
            ChessGame game = new ChessGame();
            ChessAI ai = selectAI(aiOpponent, difficulty);
            
            ChessGameSession session = new ChessGameSession(
                sessionId, agentId, game, ai, playerColor, difficulty
            );
            
            activeSessions.put(sessionId, session);
            agentSessions.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            
            logger.info("Created session {} for agent {} with AI {}", sessionId, agentId, aiOpponent);
            return sessionId;
            
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    public ChessGameSession getSession(String sessionId) {
        sessionLock.readLock().lock();
        try {
            return activeSessions.get(sessionId);
        } finally {
            sessionLock.readLock().unlock();
        }
    }
    
    public List<ChessGameSession> getAgentSessions(String agentId) {
        sessionLock.readLock().lock();
        try {
            Set<String> sessionIds = agentSessions.get(agentId);
            if (sessionIds == null) return Collections.emptyList();
            
            return sessionIds.stream()
                .map(activeSessions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            sessionLock.readLock().unlock();
        }
    }
    
    public void endSession(String sessionId) {
        sessionLock.writeLock().lock();
        try {
            ChessGameSession session = activeSessions.remove(sessionId);
            if (session != null) {
                String agentId = session.getAgentId();
                Set<String> agentSessionIds = agentSessions.get(agentId);
                if (agentSessionIds != null) {
                    agentSessionIds.remove(sessionId);
                    if (agentSessionIds.isEmpty()) {
                        agentSessions.remove(agentId);
                    }
                }
                logger.info("Ended session {} for agent {}", sessionId, agentId);
            }
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    public Collection<ChessGameSession> getAllActiveSessions() {
        return activeSessions.values();
    }
    
    private void validateSessionLimits(String agentId) {
        if (activeSessions.size() >= MAX_TOTAL_SESSIONS) {
            throw new IllegalStateException("Maximum total sessions reached: " + MAX_TOTAL_SESSIONS);
        }
        
        Set<String> agentSessionIds = agentSessions.get(agentId);
        if (agentSessionIds != null && agentSessionIds.size() >= MAX_SESSIONS_PER_AGENT) {
            throw new IllegalStateException("Maximum sessions per agent reached: " + MAX_SESSIONS_PER_AGENT);
        }
    }
    
    private String generateSessionId(String agentId) {
        return "chess-session-" + agentId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private ChessAI selectAI(String aiOpponent, int difficulty) {
        ChessAI ai = aiSystems.get(aiOpponent);
        if (ai == null) {
            throw new IllegalArgumentException("Unknown AI opponent: " + aiOpponent);
        }
        return ai;
    }
}