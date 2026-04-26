package com.example.chess;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class WebSocketExceptionHandler extends WebSocketHandlerDecorator {

    public WebSocketExceptionHandler(WebSocketHandler delegate) {
        super(delegate);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        try {
            super.afterConnectionClosed(session, closeStatus);
        } catch (Exception e) {
            // Suppress WebSocket shutdown exceptions
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        try {
            super.handleTransportError(session, exception);
        } catch (Exception e) {
            // Suppress transport errors
        }
    }
}