package com.example.chess;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * WebSocket configuration with security and performance optimizations.
 * Supports real-time chess gameplay and AI training visualization.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Main WebSocket for chess game and control messages
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(60000);
        
        // Dedicated WebSocket for video frames (high bandwidth)
        registry.addEndpoint("/ws-video")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(30000);
        
        // Dedicated WebSocket for calibration data
        registry.addEndpoint("/ws-calibration")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(30000);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(1024 * 1024) // 1MB max message size for video frames
                .setSendBufferSizeLimit(16 * 1024 * 1024) // 16MB send buffer for video
                .setSendTimeLimit(30 * 1000) // 30 second send timeout
                .setTimeToFirstMessage(10 * 1000); // 10 second timeout for first message
    }
    

}