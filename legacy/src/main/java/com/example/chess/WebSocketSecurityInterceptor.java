package com.example.chess;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {
    
    // Track active sessions for cleanup
    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sessionMessageCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> trainingActiveSessions = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_SESSION = 10000; // Increased for training
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            String sessionId = accessor.getSessionId();
            
            // Handle connection
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                if (!validateConnection(accessor)) {
                    return null; // Reject connection
                }
                activeSessions.put(sessionId, System.currentTimeMillis());
                sessionMessageCount.put(sessionId, new AtomicInteger(0));
            }
            
            // Handle disconnection
            else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                cleanupSession(sessionId);
            }
            
            // Validate all messages
            else if (sessionId != null) {
                if (!validateMessage(accessor, sessionId)) {
                    return null; // Reject message
                }
            }
        }
        
        return message;
    }
    
    private boolean validateConnection(StompHeaderAccessor accessor) {
        // Validate origin header to prevent CSWSH
        String origin = accessor.getFirstNativeHeader("origin");
        if (origin != null && !isAllowedOrigin(origin)) {
            return false;
        }
        
        // Additional connection validation can be added here
        return true;
    }
    
    private boolean validateMessage(StompHeaderAccessor accessor, String sessionId) {
        // Check session exists
        if (!activeSessions.containsKey(sessionId)) {
            return false;
        }
        
        // Skip rate limiting for training sessions
        boolean isTrainingActive = trainingActiveSessions.getOrDefault(sessionId, false);
        if (!isTrainingActive) {
            AtomicInteger messageCount = sessionMessageCount.get(sessionId);
            if (messageCount != null && messageCount.incrementAndGet() > MAX_MESSAGES_PER_SESSION) {
                cleanupSession(sessionId);
                return false;
            }
        }
        
        // Check if this is a training start/stop message
        String destination = accessor.getDestination();
        if ("/app/train".equals(destination)) {
            trainingActiveSessions.put(sessionId, true);
        } else if ("/app/stop-training".equals(destination)) {
            trainingActiveSessions.put(sessionId, false);
        }
        
        // Validate destination
        if (destination != null && !isAllowedDestination(destination)) {
            return false;
        }
        
        return true;
    }
    
    private boolean isAllowedOrigin(String origin) {
        // Allow localhost origins for development
        return origin.startsWith("http://localhost:") || 
               origin.startsWith("https://localhost:") ||
               origin.equals("http://localhost:8081") ||
               origin.equals("https://localhost:8081");
    }
    
    private boolean isAllowedDestination(String destination) {
        // Whitelist allowed destinations
        return destination.startsWith("/app/move") ||
               destination.startsWith("/app/newgame") ||
               destination.startsWith("/app/train") ||
               destination.startsWith("/app/validate") ||
               destination.startsWith("/app/board") ||
               destination.startsWith("/app/ai-status") ||
               destination.startsWith("/app/delete-training") ||
               destination.startsWith("/app/training-progress") ||
               destination.startsWith("/app/undo") ||
               destination.startsWith("/app/redo") ||
               destination.startsWith("/app/stop-training");
    }
    
    private void cleanupSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            sessionMessageCount.remove(sessionId);
            trainingActiveSessions.remove(sessionId);
            
            // Controller cleanup handled separately
        }
    }
    
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        // Log security events if needed
        if (ex != null) {
            // Handle send failures
        }
    }
}