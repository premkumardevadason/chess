package com.example.chess.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Test
    @Timeout(30)
    void testConnectionEstablishment() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        StandardWebSocketClient client = new StandardWebSocketClient();
        
        WebSocketSession session = client.doHandshake(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                latch.countDown();
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {}
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}
            
            @Override
            public boolean supportsPartialMessages() { return false; }
        }, null, URI.create("ws://localhost:" + port + "/ws")).get();
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(session.isOpen());
        session.close();
    }
    
    @Test
    @Timeout(30)
    void testGameStateUpdates() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);
        StringBuilder receivedMessage = new StringBuilder();
        
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.doHandshake(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                try {
                    session.sendMessage(new TextMessage("{\"type\":\"move\",\"fromRow\":6,\"fromCol\":4,\"toRow\":4,\"toCol\":4}"));
                } catch (Exception e) {
                    fail("Failed to send message: " + e.getMessage());
                }
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                receivedMessage.append(message.getPayload());
                messageLatch.countDown();
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}
            
            @Override
            public boolean supportsPartialMessages() { return false; }
        }, null, URI.create("ws://localhost:" + port + "/ws")).get();
        
        assertTrue(messageLatch.await(15, TimeUnit.SECONDS));
        assertTrue(receivedMessage.length() > 0);
        session.close();
    }
    
    @Test
    @Timeout(45)
    void testTrainingProgressBroadcast() throws Exception {
        CountDownLatch trainingLatch = new CountDownLatch(1);
        StringBuilder trainingMessage = new StringBuilder();
        
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.doHandshake(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                try {
                    session.sendMessage(new TextMessage("{\"type\":\"train\",\"games\":50}"));
                } catch (Exception e) {
                    fail("Failed to send training message: " + e.getMessage());
                }
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                String payload = message.getPayload().toString();
                if (payload.contains("training") || payload.contains("progress")) {
                    trainingMessage.append(payload);
                    trainingLatch.countDown();
                }
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {}
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {}
            
            @Override
            public boolean supportsPartialMessages() { return false; }
        }, null, URI.create("ws://localhost:" + port + "/ws")).get();
        
        assertTrue(trainingLatch.await(40, TimeUnit.SECONDS));
        assertTrue(trainingMessage.length() > 0);
        session.close();
    }
}