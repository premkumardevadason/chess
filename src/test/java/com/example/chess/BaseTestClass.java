package com.example.chess;

import com.example.chess.config.SharedTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = {ChessApplication.class, SharedTestConfiguration.class})
public abstract class BaseTestClass {
    
    protected static ChessGame game;
    
    @BeforeEach
    public void baseSetUp() {
        if (game == null) {
            game = new ChessGame();
        }
        game.resetGame();
    }
}


