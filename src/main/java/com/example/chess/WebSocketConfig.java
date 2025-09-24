package com.example.chess;

import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${chess.websocket.message-size-limit-mb:20}")
    private int messageSizeLimitMB;
    
    @Value("${chess.websocket.send-buffer-size-limit-mb:128}")
    private int sendBufferSizeLimitMB;
    
    @Value("${chess.websocket.send-time-limit-seconds:60}")
    private int sendTimeLimitSeconds;
    
    @Value("${chess.websocket.time-to-first-message-seconds:15}")
    private int timeToFirstMessageSeconds;

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
        registry.setMessageSizeLimit(messageSizeLimitMB * 1024 * 1024)
                .setSendBufferSizeLimit(sendBufferSizeLimitMB * 1024 * 1024)
                .setSendTimeLimit(sendTimeLimitSeconds * 1000)
                .setTimeToFirstMessage(timeToFirstMessageSeconds * 1000);
    }
}