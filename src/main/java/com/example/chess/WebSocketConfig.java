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
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*")
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(60000); // Increased to 60 seconds for training
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(handler -> new WebSocketExceptionHandler(handler))
                .setMessageSizeLimit(512 * 1024) // 512KB max message size for training data
                .setSendBufferSizeLimit(8 * 1024 * 1024) // 8MB send buffer for training
                .setSendTimeLimit(60 * 1000) // 60 second send timeout for training
                .setTimeToFirstMessage(30 * 1000); // 30 second timeout for first message
    }
    

}