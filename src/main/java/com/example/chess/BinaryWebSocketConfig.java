package com.example.chess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class BinaryWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private BinaryWebSocketHandler binaryWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(binaryWebSocketHandler, "/ws-binary")
                .setAllowedOrigins("*");
    }
}