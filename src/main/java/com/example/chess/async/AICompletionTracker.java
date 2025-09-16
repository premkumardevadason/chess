package com.example.chess.async;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AICompletionTracker {
    private final Map<String, AtomicBoolean> aiActiveStatus = new ConcurrentHashMap<>();
    
    public AICompletionTracker() {
        Arrays.asList("QLearning", "AlphaFold3", "DeepQNetwork", "GeneticAlgorithm", 
                     "AlphaZero", "LeelaChessZero", "MonteCarloTreeSearch", "Negamax",
                     "OpenAI", "DeepLearning", "DeepLearningCNN")
              .forEach(ai -> aiActiveStatus.put(ai, new AtomicBoolean(false)));
    }
    
    public void markAIActive(String aiName) {
        if (aiActiveStatus.containsKey(aiName)) {
            aiActiveStatus.get(aiName).set(true);
        }
    }
    
    public void markAIComplete(String aiName) {
        if (aiActiveStatus.containsKey(aiName)) {
            aiActiveStatus.get(aiName).set(false);
        }
    }
    
    public void waitForAllAICompletion() {
        try {
            boolean allComplete = false;
            int attempts = 0;
            // PERFORMANCE FIX: Reduced timeout from 30 seconds to 5 seconds
            while (!allComplete && attempts < 5) {
                allComplete = aiActiveStatus.values().stream()
                    .noneMatch(AtomicBoolean::get);
                
                if (!allComplete) {
                    Thread.sleep(1000);
                    attempts++;
                }
            }
            
            if (!allComplete) {
                // Log which AIs are still active after timeout
                aiActiveStatus.entrySet().stream()
                    .filter(e -> e.getValue().get())
                    .forEach(e -> System.out.println("*** AI still active after timeout: " + e.getKey() + " ***"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public boolean hasActiveAI() {
        return aiActiveStatus.values().stream().anyMatch(AtomicBoolean::get);
    }
}