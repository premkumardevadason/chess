package com.example.chess;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

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
        // Single WebSocket for all communication
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(60000);
        
        // Binary WebSocket for large data (calibration/video frames)
        registry.addEndpoint("/ws-binary")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .setHandshakeHandler(new DefaultHandshakeHandler());
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(10 * 1024 * 1024)
                .setSendBufferSizeLimit(32 * 1024 * 1024)
                .setSendTimeLimit(60 * 1000)
                .setTimeToFirstMessage(15 * 1000);
    }
}