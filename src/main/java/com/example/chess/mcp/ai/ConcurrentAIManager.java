package com.example.chess.mcp.ai;

import com.example.chess.ChessAI;
import com.example.chess.ChessGame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ConcurrentAIManager {
    
    private static final Logger logger = LogManager.getLogger(ConcurrentAIManager.class);
    
    private final ExecutorService neuralNetworkPool = Executors.newFixedThreadPool(4);
    private final ExecutorService classicalEnginePool = Executors.newFixedThreadPool(8);
    private final ExecutorService machineLearningPool = Executors.newFixedThreadPool(6);
    
    private final ConcurrentHashMap<String, AtomicInteger> aiSystemLoad = new ConcurrentHashMap<>();
    
    public CompletableFuture<String> getAIMoveAsync(ChessAI ai, ChessGame game, String sessionId) {
        String aiType = ai.getClass().getSimpleName();
        
        aiSystemLoad.computeIfAbsent(aiType, k -> new AtomicInteger(0)).incrementAndGet();
        
        ExecutorService executor = selectExecutorForAI(aiType);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("AI {} processing move for session {}", aiType, sessionId);
                long startTime = System.currentTimeMillis();
                
                String move = ai.getMove(game);
                
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("AI {} completed move {} in {}ms for session {}", 
                           aiType, move, duration, sessionId);
                
                return move;
            } finally {
                aiSystemLoad.get(aiType).decrementAndGet();
            }
        }, executor);
    }
    
    public int getAISystemLoad(String aiType) {
        return aiSystemLoad.getOrDefault(aiType, new AtomicInteger(0)).get();
    }
    
    public ConcurrentHashMap<String, AtomicInteger> getAllAILoads() {
        return new ConcurrentHashMap<>(aiSystemLoad);
    }
    
    private ExecutorService selectExecutorForAI(String aiType) {
        switch (aiType) {
            case "AlphaZeroAI":
            case "LeelaChessZeroAI":
            case "AlphaFold3AI":
            case "AsynchronousAdvantageActorCriticAI":
                return neuralNetworkPool;
            case "NegamaxAI":
            case "MonteCarloTreeSearchAI":
            case "OpenAiChessAI":
                return classicalEnginePool;
            default:
                return machineLearningPool;
        }
    }
    
    public void shutdown() {
        neuralNetworkPool.shutdown();
        classicalEnginePool.shutdown();
        machineLearningPool.shutdown();
    }
}