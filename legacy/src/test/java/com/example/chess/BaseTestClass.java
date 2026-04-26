package com.example.chess;

import com.example.chess.config.SharedTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = {ChessApplication.class, SharedTestConfiguration.class})
public abstract class BaseTestClass {
    
    @Autowired
    protected ChessGame game;
    
    @BeforeEach
    public void baseSetUp() {
        // Ensure AI systems are initialized before tests run
        if (game != null) {
            // Force AI initialization if not already done
            game.ensureAISystemsInitialized();
            // Wait a moment for initialization to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            game.resetGame();
        }
    }
}


