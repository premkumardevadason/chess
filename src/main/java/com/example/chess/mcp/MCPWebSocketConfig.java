package com.example.chess.mcp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MCPWebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private MCPWebSocketHandler mcpWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpWebSocketHandler, "/mcp")
                .setAllowedOrigins("*"); // Allow all origins for MCP clients
    }
}