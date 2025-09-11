package com.example.chess.mcp;

import com.example.chess.mcp.security.SecureMCPWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MCPWebSocketConfig implements WebSocketConfigurer {
    
    @Autowired(required = false)
    @Qualifier("secureMCPWebSocketHandler")
    private SecureMCPWebSocketHandler secureMCPWebSocketHandler;
    
    @Autowired
    @Qualifier("MCPWebSocketHandler")
    private MCPWebSocketHandler mcpWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Use secure handler if encryption is enabled, otherwise use standard handler
        if (secureMCPWebSocketHandler != null) {
            registry.addHandler(secureMCPWebSocketHandler, "/mcp")
                    .setAllowedOrigins("*"); // Allow all origins for MCP clients
        } else {
            registry.addHandler(mcpWebSocketHandler, "/mcp")
                    .setAllowedOrigins("*"); // Allow all origins for MCP clients
        }
    }
}