package com.example.chess.mcp.config;

import com.example.chess.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class MCPConfiguration {
    
    @Bean
    public Map<String, ChessAI> aiSystems() {
        Map<String, ChessAI> aiMap = new HashMap<>();
        
        // Initialize AI systems that exist
        // Use mock AI for all systems for now
        aiMap.put("Negamax", new MockChessAI("Negamax"));
        
        // Add mock AIs for systems not yet implemented
        aiMap.put("AlphaZero", new MockChessAI("AlphaZero"));
        aiMap.put("LeelaChessZero", new MockChessAI("LeelaChessZero"));
        aiMap.put("AlphaFold3", new MockChessAI("AlphaFold3"));
        aiMap.put("A3C", new MockChessAI("A3C"));
        aiMap.put("MCTS", new MockChessAI("MCTS"));
        aiMap.put("OpenAI", new MockChessAI("OpenAI"));
        aiMap.put("QLearning", new MockChessAI("QLearning"));
        aiMap.put("DeepLearning", new MockChessAI("DeepLearning"));
        aiMap.put("CNN", new MockChessAI("CNN"));
        aiMap.put("DQN", new MockChessAI("DQN"));
        aiMap.put("Genetic", new MockChessAI("Genetic"));
        
        return aiMap;
    }
}