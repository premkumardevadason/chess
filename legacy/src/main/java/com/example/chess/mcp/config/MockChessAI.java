package com.example.chess.mcp.config;

import com.example.chess.ChessAI;
import com.example.chess.ChessGame;
import java.util.List;
import java.util.Random;

/**
 * Mock Chess AI implementation for testing MCP functionality.
 * Makes random legal moves.
 */
public class MockChessAI implements ChessAI {
    
    private final String name;
    private final Random random = new Random();
    
    public MockChessAI(String name) {
        this.name = name;
    }
    
    @Override
    public String getMove(ChessGame game) {
        // Simple mock moves for testing
        String[] basicMoves = {"e4", "d4", "Nf3", "Nc3", "Bc4", "Be2"};
        return basicMoves[random.nextInt(basicMoves.length)];
    }
    
    @Override
    public String getName() {
        return name;
    }
}