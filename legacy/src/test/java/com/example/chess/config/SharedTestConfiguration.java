package com.example.chess.config;

import com.example.chess.ChessGame;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class SharedTestConfiguration {
    
    private static ChessGame sharedGame;
    
    @Bean
    @Primary
    public ChessGame sharedChessGame() {
        if (sharedGame == null) {
            sharedGame = new ChessGame();
            // Ensure AI systems are initialized immediately
            try {
                sharedGame.ensureAISystemsInitialized();
                // Give AI systems time to initialize
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return sharedGame;
    }
    
    public static void resetSharedGame() {
        if (sharedGame != null) {
            sharedGame.resetGame();
        }
    }
}


