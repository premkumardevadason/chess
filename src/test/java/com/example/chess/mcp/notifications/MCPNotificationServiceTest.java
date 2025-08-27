package com.example.chess.mcp.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class MCPNotificationServiceTest {
    
    private MCPNotificationService notificationService;
    
    @BeforeEach
    void setUp() {
        notificationService = new MCPNotificationService();
    }
    
    @Test
    void testSubscribeAndNotify() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        
        MCPNotificationService.NotificationListener listener = notification -> {
            notificationCount.incrementAndGet();
            assertEquals("test_method", notification.getMethod());
        };
        
        notificationService.subscribe("agent1", listener);
        notificationService.notifyAgent("agent1", "test_method", Map.of("key", "value"));
        
        assertEquals(1, notificationCount.get());
    }
    
    @Test
    void testUnsubscribe() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        
        MCPNotificationService.NotificationListener listener = notification -> 
            notificationCount.incrementAndGet();
        
        notificationService.subscribe("agent1", listener);
        notificationService.notifyAgent("agent1", "test_method", Map.of());
        assertEquals(1, notificationCount.get());
        
        notificationService.unsubscribe("agent1", listener);
        notificationService.notifyAgent("agent1", "test_method", Map.of());
        assertEquals(1, notificationCount.get()); // Should not increment
    }
    
    @Test
    void testNotifyGameMove() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        
        MCPNotificationService.NotificationListener listener = notification -> {
            notificationCount.incrementAndGet();
            assertEquals("notifications/chess/ai_move", notification.getMethod());
            assertTrue(notification.getParams().containsKey("sessionId"));
            assertTrue(notification.getParams().containsKey("playerMove"));
            assertTrue(notification.getParams().containsKey("aiMove"));
        };
        
        notificationService.subscribe("agent1", listener);
        notificationService.notifyGameMove("agent1", "session1", "e4", "e5");
        
        assertEquals(1, notificationCount.get());
    }
    
    @Test
    void testNotifyGameStateChange() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        
        MCPNotificationService.NotificationListener listener = notification -> {
            notificationCount.incrementAndGet();
            assertEquals("notifications/chess/game_state", notification.getMethod());
            assertEquals("checkmate", notification.getParams().get("status"));
        };
        
        notificationService.subscribe("agent1", listener);
        notificationService.notifyGameStateChange("agent1", "session1", "checkmate");
        
        assertEquals(1, notificationCount.get());
    }
}