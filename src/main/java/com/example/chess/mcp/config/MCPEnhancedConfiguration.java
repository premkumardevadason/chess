package com.example.chess.mcp.config;

import com.example.chess.mcp.ai.ConcurrentAIManager;
import com.example.chess.mcp.notifications.MCPNotificationService;
import com.example.chess.mcp.session.ChessGameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;

@Configuration
public class MCPEnhancedConfiguration {
    
    @Autowired
    private ConcurrentAIManager concurrentAIManager;
    
    @Autowired
    private MCPNotificationService notificationService;
    
    @PostConstruct
    public void configureDependencies() {
        ChessGameSession.setConcurrentAIManager(concurrentAIManager);
        ChessGameSession.setNotificationService(notificationService);
    }
}