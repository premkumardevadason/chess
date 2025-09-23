package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-agent precomputation service with resource management
 * Handles parallel AI computation with dynamic resource allocation
 */
@Service
public class MultiAgentPrecomputationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiAgentPrecomputationService.class);
    
    // Use existing AI services
    @Autowired
    private com.example.chess.mcp.ai.SharedAIService sharedAIService;
    
    @Autowired
    private com.example.chess.ChessGame chessGame;
    
    // Dynamic thread pool with work-stealing for better resource utilization
    private ExecutorService aiExecutor = Executors.newWorkStealingPool();
    
    // Resource monitoring and management
    private ResourceMonitor resourceMonitor = new ResourceMonitor();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    
    // Cache for pre-computed responses
    private Map<String, Map<String, PrecomputedResponse>> responseCache = new ConcurrentHashMap<>();
    
    // Active computation tracking for cancellation
    private volatile String currentPredictedMove = null;
    private volatile CompletableFuture<Void> activeComputation = null;
    private final Object computationLock = new Object();
    
    @Value("${chess.eyetracking.max.concurrent.ais:6}")
    private int maxConcurrentAIs;
    
    @Value("${chess.eyetracking.cpu.usage.limit:0.8}")
    private double cpuUsageLimit;
    
    @Value("${chess.eyetracking.memory.usage.limit:0.85}")
    private double memoryUsageLimit;
    
    public void preComputeAllResponses(String predictedMove, String currentPosition) {
        synchronized (computationLock) {
            // Check system resources before starting computation
            if (!canStartNewComputation()) {
                logger.warn("System resources insufficient, skipping precomputation for: {}", predictedMove);
                return;
            }
            
            // Cancel previous computation if different move predicted
            if (activeComputation != null && !predictedMove.equals(currentPredictedMove)) {
                logger.info("Canceling previous precomputation for: {} (new: {})", 
                    currentPredictedMove, predictedMove);
                activeComputation.cancel(true);
                activeComputation = null;
            }
            
            String cacheKey = currentPosition + ":" + predictedMove;
            
            if (responseCache.containsKey(cacheKey)) {
                return; // Already computed
            }
            
            // Skip if same move already being computed
            if (predictedMove.equals(currentPredictedMove) && activeComputation != null) {
                return;
            }
            
            currentPredictedMove = predictedMove;
            Map<String, PrecomputedResponse> responses = new ConcurrentHashMap<>();
            
            // Create resource-aware AI computation tasks
            activeComputation = startResourceAwareComputation(predictedMove, responses, cacheKey);
        }
    }
    
    private boolean canStartNewComputation() {
        return circuitBreaker.isOpen() && 
               resourceMonitor.getCpuUsage() < cpuUsageLimit && 
               resourceMonitor.getMemoryUsage() < memoryUsageLimit &&
               getActiveComputationCount() < maxConcurrentAIs;
    }
    
    private CompletableFuture<Void> startResourceAwareComputation(
            String predictedMove, Map<String, PrecomputedResponse> responses, String cacheKey) {
        
        // Select AIs based on system resources
        List<String> selectedAIs = selectAIsBasedOnResources();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Create computation tasks for selected AIs
        for (String aiName : selectedAIs) {
            futures.add(CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        String response = computeAIResponse(aiName, predictedMove);
                        if (!Thread.currentThread().isInterrupted() && response != null) {
                            responses.put(aiName, new PrecomputedResponse(response, System.currentTimeMillis()));
                        }
                    } catch (Exception e) {
                        logger.warn("Error computing response for AI {}: {}", aiName, e.getMessage());
                    }
                }
            }, aiExecutor));
        }
        
        // Combine all futures with cancellation support
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
            
        // Return the combined future for cancellation tracking
        return allFutures.whenComplete((result, throwable) -> {
            synchronized (computationLock) {
                if (throwable == null && !allFutures.isCancelled()) {
                    responseCache.put(cacheKey, responses);
                    logger.info("Precomputation completed for move: {} ({} responses)", 
                        predictedMove, responses.size());
                } else if (allFutures.isCancelled()) {
                    logger.info("Precomputation cancelled for move: {}", predictedMove);
                } else {
                    logger.warn("Precomputation failed for move: {}", predictedMove, throwable);
                }
                
                // Clear active computation if this was the current one
                if (activeComputation == allFutures) {
                    activeComputation = null;
                    currentPredictedMove = null;
                }
            }
        }).orTimeout(2, TimeUnit.SECONDS);
    }
    
    private List<String> selectAIsBasedOnResources() {
        List<String> priorityAIs = Arrays.asList("Negamax", "QLearning", "MCTS", "AlphaZero");
        List<String> additionalAIs = Arrays.asList("DeepLearning", "LeelaChessZero", "AlphaFold3", "A3C");
        
        List<String> selectedAIs = new ArrayList<>(priorityAIs);
        
        // Add additional AIs if resources allow
        if (resourceMonitor.getCpuUsage() < 0.6 && resourceMonitor.getMemoryUsage() < 0.7) {
            selectedAIs.addAll(additionalAIs);
        }
        
        return selectedAIs.subList(0, Math.min(selectedAIs.size(), maxConcurrentAIs));
    }
    
    private String computeAIResponse(String aiName, String predictedMove) {
        try {
            // Convert predicted move to board coordinates
            int[] moveCoords = convertMoveToCoordinates(predictedMove);
            if (moveCoords == null) {
                return null;
            }
            
            // Simulate the predicted move on a copy of the current board
            String[][] boardCopy = deepCopyBoard(chessGame.getBoard());
            
            // Make the predicted move
            boardCopy[moveCoords[2]][moveCoords[3]] = boardCopy[moveCoords[0]][moveCoords[1]];
            boardCopy[moveCoords[0]][moveCoords[1]] = null;
            
            // Get AI response for the resulting position
            int[] aiResponse = sharedAIService.findBestMove(boardCopy, !chessGame.isWhiteTurn());
            
            if (aiResponse != null && aiResponse.length == 4) {
                return convertCoordinatesToMove(aiResponse);
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error computing AI response for {}: {}", aiName, e.getMessage());
            return null;
        }
    }
    
    private int[] convertMoveToCoordinates(String move) {
        // Convert UCI move format (e.g., "e2e4") to coordinates
        if (move.length() != 4) return null;
        
        int fromCol = move.charAt(0) - 'a';
        int fromRow = 8 - Character.getNumericValue(move.charAt(1));
        int toCol = move.charAt(2) - 'a';
        int toRow = 8 - Character.getNumericValue(move.charAt(3));
        
        return new int[]{fromRow, fromCol, toRow, toCol};
    }
    
    private String convertCoordinatesToMove(int[] coords) {
        // Convert coordinates to UCI move format
        char fromFile = (char)('a' + coords[1]);
        int fromRank = 8 - coords[0];
        char toFile = (char)('a' + coords[3]);
        int toRank = 8 - coords[2];
        
        return "" + fromFile + fromRank + toFile + toRank;
    }
    
    private String[][] deepCopyBoard(String[][] original) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    public String getPrecomputedResponse(String aiName, String predictedMove, String currentPosition) {
        String cacheKey = currentPosition + ":" + predictedMove;
        Map<String, PrecomputedResponse> responses = responseCache.get(cacheKey);
        
        if (responses != null && responses.containsKey(aiName)) {
            PrecomputedResponse response = responses.get(aiName);
            // Check if response is still valid (within 5 seconds)
            if (System.currentTimeMillis() - response.timestamp < 5000) {
                return response.move;
            }
        }
        
        return null; // No precomputed response available
    }
    
    /**
     * Cancels current computation and clears state
     */
    public void cancelCurrentComputation() {
        synchronized (computationLock) {
            if (activeComputation != null) {
                logger.info("Cancelling active precomputation for: {}", currentPredictedMove);
                
                // Cancel all running AI computations
                activeComputation.cancel(true);
                
                // Clear state
                activeComputation = null;
                currentPredictedMove = null;
            }
        }
    }
    
    /**
     * Checks if computation is still valid for the current prediction
     */
    public boolean isComputationValid(String predictedMove) {
        synchronized (computationLock) {
            return predictedMove.equals(currentPredictedMove) && 
                   activeComputation != null && 
                   !activeComputation.isCancelled();
        }
    }
    
    private int getActiveComputationCount() {
        // Return number of active computations
        return activeComputation != null && !activeComputation.isDone() ? 1 : 0;
    }
    
    public static class PrecomputedResponse {
        public final String move;
        public final long timestamp;
        
        public PrecomputedResponse(String move, long timestamp) {
            this.move = move;
            this.timestamp = timestamp;
        }
    }
    
    // Resource monitoring classes
    public static class ResourceMonitor {
        public double getCpuUsage() {
            // Get current CPU usage
            return 0.5; // Placeholder
        }
        
        public double getMemoryUsage() {
            // Get current memory usage
            return 0.6; // Placeholder
        }
    }
    
    public static class CircuitBreaker {
        private boolean open = true;
        
        public boolean isOpen() {
            return open;
        }
        
        public void setOpen(boolean open) {
            this.open = open;
        }
    }
}
