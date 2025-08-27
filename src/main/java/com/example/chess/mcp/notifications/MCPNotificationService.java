package com.example.chess.mcp.notifications;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MCPNotificationService {
    
    private static final Logger logger = LogManager.getLogger(MCPNotificationService.class);
    
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<NotificationListener>> listeners = new ConcurrentHashMap<>();
    
    public void subscribe(String agentId, NotificationListener listener) {
        listeners.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(listener);
        logger.debug("Agent {} subscribed to notifications", agentId);
    }
    
    public void unsubscribe(String agentId, NotificationListener listener) {
        CopyOnWriteArrayList<NotificationListener> agentListeners = listeners.get(agentId);
        if (agentListeners != null) {
            agentListeners.remove(listener);
            logger.debug("Agent {} unsubscribed from notifications", agentId);
        }
    }
    
    public void notifyAgent(String agentId, String method, Map<String, Object> params) {
        CopyOnWriteArrayList<NotificationListener> agentListeners = listeners.get(agentId);
        if (agentListeners != null) {
            Notification notification = new Notification(method, params);
            for (NotificationListener listener : agentListeners) {
                try {
                    listener.onNotification(notification);
                } catch (Exception e) {
                    logger.error("Error sending notification to agent {}: {}", agentId, e.getMessage());
                }
            }
        }
    }
    
    public void notifyGameMove(String agentId, String sessionId, String move, String aiResponse) {
        notifyAgent(agentId, "notifications/chess/ai_move", Map.of(
            "sessionId", sessionId,
            "playerMove", move,
            "aiMove", aiResponse
        ));
    }
    
    public void notifyGameStateChange(String agentId, String sessionId, String status) {
        notifyAgent(agentId, "notifications/chess/game_state", Map.of(
            "sessionId", sessionId,
            "status", status,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    public void notifyTrainingProgress(String agentId, String aiName, double progress, int gamesPlayed) {
        notifyAgent(agentId, "notifications/chess/training_progress", Map.of(
            "aiName", aiName,
            "progress", progress,
            "gamesPlayed", gamesPlayed,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    public void notifyTournamentUpdate(String agentId, String tournamentId, Map<String, Object> status) {
        notifyAgent(agentId, "notifications/chess/tournament_update", Map.of(
            "tournamentId", tournamentId,
            "status", status,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    public interface NotificationListener {
        void onNotification(Notification notification);
    }
    
    public static class Notification {
        private final String method;
        private final Map<String, Object> params;
        
        public Notification(String method, Map<String, Object> params) {
            this.method = method;
            this.params = params;
        }
        
        public String getMethod() { return method; }
        public Map<String, Object> getParams() { return params; }
    }
}