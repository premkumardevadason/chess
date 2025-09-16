package com.example.chess.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class AtomicFeatureCoordinator {
    private final ReadWriteLock globalLock = new ReentrantReadWriteLock();
    private final AtomicReference<AtomicFeature> activeFeature = new AtomicReference<>();
    private final AICompletionTracker aiTracker;
    
    public enum AtomicFeature {
        STARTUP, SHUTDOWN, TRAINING_STOP_SAVE, GAME_RESET_SAVE, UI_READ_STATE
    }
    
    public AtomicFeatureCoordinator(AICompletionTracker aiTracker) {
        this.aiTracker = aiTracker;
    }
    
    public boolean isShuttingDown() {
        return activeFeature.get() == AtomicFeature.SHUTDOWN;
    }
    
    public CompletableFuture<Void> executeAtomicFeature(AtomicFeature feature, Runnable operation) {
        // PERFORMANCE FIX: Only use write lock for SHUTDOWN to prevent blocking during saves
        if (feature == AtomicFeature.SHUTDOWN) {
            return CompletableFuture.runAsync(() -> {
                globalLock.writeLock().lock();
                try {
                    activeFeature.set(feature);
                    aiTracker.waitForAllAICompletion();
                    operation.run();
                } finally {
                    activeFeature.set(null);
                    globalLock.writeLock().unlock();
                }
            });
        } else {
            // For regular saves (TRAINING_STOP_SAVE, GAME_RESET_SAVE), allow parallel execution
            return CompletableFuture.runAsync(() -> {
                activeFeature.set(feature);
                try {
                    // Still wait for AI completion but without blocking other operations
                    aiTracker.waitForAllAICompletion();
                    operation.run();
                } finally {
                    activeFeature.set(null);
                }
            });
        }
    }
    
    public CompletableFuture<Void> executeAsyncIO(String aiName, Supplier<CompletableFuture<Void>> ioOperation) {
        return CompletableFuture.supplyAsync(() -> {
            globalLock.readLock().lock();
            try {
                if (activeFeature.get() != null) {
                    globalLock.readLock().unlock();
                    globalLock.writeLock().lock();
                    globalLock.writeLock().unlock();
                    globalLock.readLock().lock();
                }
                return ioOperation.get();
            } finally {
                globalLock.readLock().unlock();
            }
        }).thenCompose(future -> future);
    }
}