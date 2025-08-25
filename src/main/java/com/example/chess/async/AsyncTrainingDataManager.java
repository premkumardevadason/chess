package com.example.chess.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.AsyncIOMetrics;

public class AsyncTrainingDataManager {
    private static final Logger logger = LogManager.getLogger(AsyncTrainingDataManager.class);
    private final AtomicFeatureCoordinator coordinator;
    private final AICompletionTracker aiTracker;
    private final Map<String, AsynchronousFileChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> dirtyFlags = new ConcurrentHashMap<>();
    private final Map<String, Object> dataCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSaveTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastSaveHash = new ConcurrentHashMap<>();
    // CRITICAL FIX: File-level locks to prevent race conditions
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(11);
    private volatile boolean trainingStopRequested = false;
    private volatile boolean userGameDataProcessing = false;
    private final Map<String, AtomicBoolean> aiSaveInProgress = new ConcurrentHashMap<>();
    private final AsyncIOMetrics metrics = new AsyncIOMetrics();
    private static final long SAVE_DEBOUNCE_MS = 1000; // 1 second debounce
    private final java.util.concurrent.atomic.AtomicInteger activeIOOperations = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<Void>> queuedOperations = new java.util.concurrent.ConcurrentHashMap<>();
    
    public AsyncTrainingDataManager() {
        this.aiTracker = new AICompletionTracker();
        this.coordinator = new AtomicFeatureCoordinator(aiTracker);
    }
    
    private boolean shouldStopIO() {
        // Allow saves during user game data processing even if training stopped
        if (userGameDataProcessing) {
            return false;
        }
        
        // Check immediate stop flag first
        if (trainingStopRequested) {
            return true;
        }
        
        // During shutdown, block saves same as training stop
        if (coordinator.isShuttingDown()) {
            return true; // Block shutdown saves
        }
        
        // Check if training has been stopped via TrainingManager
        try {
            Object chessGame = getChessGameInstance();
            if (chessGame != null) {
                java.lang.reflect.Method getTrainingManagerMethod = chessGame.getClass().getMethod("getTrainingManager");
                Object trainingManager = getTrainingManagerMethod.invoke(chessGame);
                if (trainingManager != null) {
                    java.lang.reflect.Method isTrainingActiveMethod = trainingManager.getClass().getMethod("isTrainingActive");
                    boolean isTrainingActive = (Boolean) isTrainingActiveMethod.invoke(trainingManager);
                    if (!isTrainingActive) {
                        trainingStopRequested = true; // Set flag for faster future checks
                        logger.info("*** ASYNC I/O: Training stopped via TrainingManager - cancelling I/O operations ***");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not check training status: {}", e.getMessage());
        }
        
        return false;
    }
    
    private CompletableFuture<Void> writeDataAsyncForShutdown(String filename, Object data) {
        // Special version for shutdown that never cancels operations
        return CompletableFuture.runAsync(() -> {
            Object fileLock = fileLocks.computeIfAbsent(filename, k -> new Object());
            
            synchronized (fileLock) {
                try {
                    // No stop checks during shutdown - these operations must complete
                    
                    // Handle DeepLearning4J models with stream bridge
                    if (data.getClass().getSimpleName().equals("MultiLayerNetwork") || filename.endsWith(".zip")) {
                        saveDeepLearning4JModel(filename, data);
                        return;
                    }
                    
                    // Handle Java serializable objects
                    if (data instanceof java.io.Serializable && !data.getClass().equals(String.class)) {
                        saveSerializableObject(filename, data);
                        return;
                    }
                    
                    // Handle regular data
                    Path filePath = Paths.get(filename);
                    if (filePath.getParent() != null) {
                        java.nio.file.Files.createDirectories(filePath.getParent());
                    }
                    
                    AsynchronousFileChannel channel = getOrCreateChannel(filename, 
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    
                    ByteBuffer buffer = ByteBuffer.wrap(data.toString().getBytes());
                    
                    CompletableFuture<Integer> writeFuture = new CompletableFuture<>();
                    channel.write(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            dirtyFlags.computeIfAbsent(filename, k -> new AtomicBoolean()).set(false);
                            String aiName = filename.replace(".dat", "");
                            logger.info("*** ASYNC I/O: {} saved during shutdown ({} bytes) - CRITICAL SAVE COMPLETED ***", aiName, result);
                            writeFuture.complete(result);
                        }
                        
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            String aiName = filename.replace(".dat", "");
                            logger.error("*** ASYNC I/O: {} shutdown save FAILED - {} ***", aiName, exc.getMessage());
                            writeFuture.completeExceptionally(exc);
                        }
                    });
                    
                    writeFuture.join();
                    
                } catch (Exception e) {
                    String aiName = filename.replace(".dat", "");
                    logger.error("*** ASYNC I/O: {} shutdown save FAILED - {} ***", aiName, e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }, ioExecutor);
    }
    
    public CompletableFuture<Void> startup() {
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.STARTUP, () -> {
            logger.info("*** ASYNC I/O: NIO.2 AsynchronousFileChannel system STARTUP ***");
            // Initialize channels and load initial data
        });
    }
    
    public CompletableFuture<Void> shutdown() {
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.SHUTDOWN, () -> {
            logger.info("*** ASYNC I/O: NIO.2 AsynchronousFileChannel system SHUTDOWN - saving all data ***");
            saveAllDirtyData().join();
            closeAllChannels();
            ioExecutor.shutdown();
            logger.info("*** ASYNC I/O: NIO.2 system shutdown complete ***");
        });
    }
    
    public CompletableFuture<Void> saveOnTrainingStop() {
        trainingStopRequested = true; // Set stop flag immediately
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.TRAINING_STOP_SAVE, () -> {
            // Cancel all queued operations first
            cancelQueuedOperations();
            // Save only dirty data that was marked before training stopped
            saveAllDirtyData().join();
            // Clear all dirty flags to prevent shutdown saves
            clearAllDirtyFlags();
            logger.info("*** ASYNC I/O: Training stop save completed - all dirty flags cleared ***");
        });
    }
    
    private void cancelQueuedOperations() {
        int cancelledCount = 0;
        for (Map.Entry<String, CompletableFuture<Void>> entry : queuedOperations.entrySet()) {
            CompletableFuture<Void> operation = entry.getValue();
            if (!operation.isDone()) {
                operation.cancel(true);
                cancelledCount++;
                logger.info("*** ASYNC I/O: Cancelled queued operation for {} ***", entry.getKey());
            }
        }
        queuedOperations.clear();
        if (cancelledCount > 0) {
            logger.info("*** ASYNC I/O: Cancelled {} queued operations due to training stop ***", cancelledCount);
        }
    }
    
    public void clearAllDirtyFlags() {
        int clearedCount = 0;
        for (Map.Entry<String, AtomicBoolean> entry : dirtyFlags.entrySet()) {
            if (entry.getValue().get()) {
                entry.getValue().set(false);
                clearedCount++;
                logger.info("*** ASYNC I/O: Cleared dirty flag for {} ***", entry.getKey());
            }
        }
        // Also clear the data cache to prevent any future saves
        dataCache.clear();
        if (clearedCount > 0) {
            logger.info("*** ASYNC I/O: Cleared {} dirty flags and data cache due to training stop ***", clearedCount);
        }
    }
    
    public CompletableFuture<Void> saveOnGameReset() {
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.GAME_RESET_SAVE, () -> {
            saveAllDirtyData().join();
        });
    }
    
    public CompletableFuture<Void> saveAIData(String aiName, Object data, String filename) {
        // CRITICAL: Check if training stopped before queuing new saves - but allow completion of in-progress AI saves
        if (shouldStopIO() && !aiSaveInProgress.computeIfAbsent(aiName, k -> new AtomicBoolean(false)).get()) {
            logger.info("*** ASYNC I/O: Blocking new save request for {} - training stopped ***", aiName);
            return CompletableFuture.completedFuture(null);
        }
        
        // Mark this AI's save as in progress to ensure atomicity
        aiSaveInProgress.computeIfAbsent(aiName, k -> new AtomicBoolean(false)).set(true);
        
        // Cache the data for potential flush during shutdown
        dataCache.put(filename, data);
        markDirty(filename);
        
        CompletableFuture<Void> operation = coordinator.executeAsyncIO(aiName, () -> {
            aiTracker.markAIActive(aiName);
            try {
                return writeDataAsync(filename, data);
            } finally {
                aiTracker.markAIComplete(aiName);
                queuedOperations.remove(filename); // Remove from tracking when complete
                // Check if this was the last file for this AI (handle all multi-file AIs)
                boolean hasMoreFiles = queuedOperations.keySet().stream().anyMatch(f -> 
                    f.startsWith(aiName.toLowerCase()) || 
                    (aiName.equals("DQN") && (f.contains("dqn_") || f.contains("DQN_"))) ||
                    (aiName.equals("A3C") && f.contains("a3c_")) ||
                    (aiName.equals("LeelaZero") && f.contains("leela_")) ||
                    (aiName.equals("Genetic") && f.contains("ga_"))
                );
                if (!hasMoreFiles && aiSaveInProgress.containsKey(aiName)) {
                    aiSaveInProgress.get(aiName).set(false); // Mark AI save as complete
                    logger.info("*** ASYNC I/O: {} atomic save completed - all files saved ***", aiName);
                }
            }
        });
        
        // Track the operation so we can cancel it if training stops
        queuedOperations.put(filename, operation);
        return operation;
    }
    
    public CompletableFuture<Void> saveAIData(String aiName, Object data) {
        // CRITICAL: Check if training stopped before queuing new saves
        if (shouldStopIO()) {
            logger.info("*** ASYNC I/O: Blocking new save request for {} - training stopped ***", aiName);
            return CompletableFuture.completedFuture(null);
        }
        return saveAIData(aiName, data, aiName + ".dat");
    }
    
    public CompletableFuture<Object> loadAIData(String aiName, String filename) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        
        coordinator.executeAsyncIO(aiName, () -> {
            aiTracker.markAIActive(aiName);
            try {
                readDataAsync(filename).thenAccept(data -> {
                    result.complete(data);
                }).exceptionally(ex -> {
                    result.completeExceptionally(ex);
                    return null;
                });
                return CompletableFuture.completedFuture(null);
            } finally {
                aiTracker.markAIComplete(aiName);
            }
        });
        
        return result;
    }
    
    public void markDirty(String filename) {
        // CRITICAL: Don't mark dirty if training has stopped, unless processing user game data
        if (shouldStopIO()) {
            logger.debug("*** ASYNC I/O: Skipping dirty mark for {} - training stopped ***", filename);
            return;
        }
        
        // Only mark dirty if actual state changes occurred
        if (shouldMarkDirty()) {
            dirtyFlags.computeIfAbsent(filename, k -> new AtomicBoolean()).set(true);
        }
    }
    
    private boolean shouldMarkDirty() {
        // Check if training was started or user played a game
        try {
            // Get ChessGame instance to check state
            Object chessGame = getChessGameInstance();
            if (chessGame != null) {
                java.lang.reflect.Method hasStateChanged = chessGame.getClass().getMethod("hasAIStateChanged");
                boolean stateChanged = (Boolean) hasStateChanged.invoke(chessGame);
                logger.debug("State change check: hasAIStateChanged={}", stateChanged);
                return stateChanged;
            }
        } catch (Exception e) {
            logger.debug("Could not check state change status: {}", e.getMessage());
        }
        // Default to true during shutdown to ensure all active AI state is saved
        logger.debug("State change check: defaulting to true (fallback)");
        return true;
    }
    
    private Object getChessGameInstance() {
        try {
            // Try to get ChessGame from Spring context
            Class<?> chessAppClass = Class.forName("com.example.chess.ChessApplication");
            java.lang.reflect.Method getContextMethod = chessAppClass.getMethod("getApplicationContext");
            Object context = getContextMethod.invoke(null);
            if (context != null) {
                java.lang.reflect.Method getBeanMethod = context.getClass().getMethod("getBean", Class.class);
                return getBeanMethod.invoke(context, Class.forName("com.example.chess.ChessGame"));
            }
        } catch (Exception e) {
            logger.debug("Could not get ChessGame instance: {}", e.getMessage());
        }
        return null;
    }
    
    private CompletableFuture<Void> writeDataAsync(String filename, Object data) {
        activeIOOperations.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            // CRITICAL FIX: File-level synchronization for all save operations
            Object fileLock = fileLocks.computeIfAbsent(filename, k -> new Object());
            
            synchronized (fileLock) {
                try {
                    // Check if training stopped before expensive I/O
                    if (shouldStopIO()) {
                        logger.info("*** ASYNC I/O: Training operation cancelled - training stopped or shutdown in progress ***");
                        return;
                    }
                    
                    // Enhanced deduplication check
                    long currentTime = System.currentTimeMillis();
                    int dataHash = data.hashCode();
                    
                    Long lastTime = lastSaveTime.get(filename);
                    Integer lastHash = lastSaveHash.get(filename);
                    
                    if (lastTime != null && lastHash != null && 
                        (currentTime - lastTime) < SAVE_DEBOUNCE_MS && 
                        dataHash == lastHash) {
                        // Skip duplicate save
                        logger.debug("*** ASYNC I/O: Skipping duplicate save for {} (debounced) ***", filename);
                        return;
                    }
                    
                    // PERFORMANCE FIX: Reduce AlphaZero debounce to allow more frequent saves
                    if (filename.endsWith(".zip") && lastTime != null) {
                        long debounceTime = filename.contains("alphazero") ? (30 * 1000) : (30 * 60 * 1000); // 30sec for AlphaZero, 30min for others
                        if ((currentTime - lastTime) < debounceTime) {
                            logger.debug("*** ASYNC I/O: Skipping frequent DeepLearning4J save for {} ({}sec ago) ***", 
                                filename, (currentTime - lastTime) / 1000);
                            return;
                        }
                    }
                    
                    // Update tracking
                    lastSaveTime.put(filename, currentTime);
                    lastSaveHash.put(filename, dataHash);
                    
                    // Handle DeepLearning4J models with stream bridge
                    if (data.getClass().getSimpleName().equals("MultiLayerNetwork") || filename.endsWith(".zip")) {
                        // Check stop flag before expensive model save
                        if (shouldStopIO()) {
                            logger.info("*** ASYNC I/O: DeepLearning4J training save cancelled - training stopped or shutdown in progress ***");
                            return;
                        }
                        saveDeepLearning4JModel(filename, data);
                        return;
                    }
                    
                    // Handle Java serializable objects (like GeneticAlgorithm population)
                    if (data instanceof java.io.Serializable && !data.getClass().equals(String.class)) {
                        // Check stop flag before expensive serialization
                        if (shouldStopIO()) {
                            logger.info("*** ASYNC I/O: Training serialization cancelled - training stopped or shutdown in progress ***");
                            return;
                        }
                        saveSerializableObject(filename, data);
                        return;
                    }
                    
                    // Create parent directories if needed
                    Path filePath = Paths.get(filename);
                    if (filePath.getParent() != null) {
                        java.nio.file.Files.createDirectories(filePath.getParent());
                    }
                    
                    AsynchronousFileChannel channel = getOrCreateChannel(filename, 
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    
                    ByteBuffer buffer = ByteBuffer.wrap(data.toString().getBytes());
                    
                    CompletableFuture<Integer> writeFuture = new CompletableFuture<>();
                    channel.write(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            dirtyFlags.computeIfAbsent(filename, k -> new AtomicBoolean()).set(false);
                            String aiName = filename.replace(".dat", "");
                            logger.info("*** ASYNC I/O: {} saved using NIO.2 AsynchronousFileChannel ({} bytes) - RACE CONDITION PROTECTED ***", aiName, result);
                            writeFuture.complete(result);
                        }
                        
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            String aiName = filename.replace(".dat", "");
                            logger.error("*** ASYNC I/O: {} save FAILED using NIO.2 - {} ***", aiName, exc.getMessage());
                            writeFuture.completeExceptionally(exc);
                        }
                    });
                    
                    long startTime = System.currentTimeMillis();
                    writeFuture.join();
                    long duration = System.currentTimeMillis() - startTime;
                    
                    String aiName = filename.replace(".dat", "");
                    metrics.recordSaveTime(aiName, duration);
                } catch (Exception e) {
                    String aiName = filename.replace(".dat", "");
                    metrics.recordError(aiName);
                    throw new RuntimeException(e);
                } finally {
                    activeIOOperations.decrementAndGet();
                }
            }
        }, ioExecutor);
    }
    
    private void saveSerializableObject(String filename, Object data) {
        // CRITICAL FIX: Synchronized per-file to prevent concurrent saves to same file
        Object fileLock = fileLocks.computeIfAbsent(filename, k -> new Object());
        
        synchronized (fileLock) {
            try {
                Path filePath = Paths.get(filename);
                
                // Create parent directories
                if (filePath.getParent() != null) {
                    java.nio.file.Files.createDirectories(filePath.getParent());
                }
                
                // CRITICAL FIX: Create snapshot of data before serialization to prevent corruption
                Object dataSnapshot;
                if (data instanceof java.util.Map) {
                    // For maps (like Q-Learning tables), create defensive copy
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> originalMap = (java.util.Map<String, Object>) data;
                    dataSnapshot = new java.util.concurrent.ConcurrentHashMap<>(originalMap);
                } else if (data instanceof java.util.List) {
                    // For lists (like GA populations), create defensive copy
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> originalList = (java.util.List<Object>) data;
                    dataSnapshot = new java.util.ArrayList<>(originalList);
                } else {
                    // For other objects, use as-is (assuming immutable or thread-safe)
                    dataSnapshot = data;
                }
                
                AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, 
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                
                // Serialize snapshot to byte array (compression disabled due to corruption issues)
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
                    oos.writeObject(dataSnapshot);
                }
                byte[] serializedData = baos.toByteArray();
                
                // Write to channel
                ByteBuffer buffer = ByteBuffer.wrap(serializedData);
                CompletableFuture<Integer> writeFuture = new CompletableFuture<>();
                
                channel.write(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        try {
                            channel.close();
                            String aiName = filename.replace(".dat", "").replace("ga_models/", "");
                            logger.info("*** ASYNC I/O: {} saved using NIO.2 AsynchronousFileChannel ({} bytes) - RACE CONDITION PROTECTED ***", aiName, result);
                            writeFuture.complete(result);
                        } catch (Exception e) {
                            writeFuture.completeExceptionally(e);
                        }
                    }
                    
                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        try {
                            channel.close();
                        } catch (Exception e) {}
                        String aiName = filename.replace(".dat", "").replace("ga_models/", "");
                        logger.error("*** ASYNC I/O: {} save FAILED using NIO.2 - {} ***", aiName, exc.getMessage());
                        writeFuture.completeExceptionally(exc);
                    }
                });
                
                writeFuture.join();
                
            } catch (Exception e) {
                String aiName = filename.replace(".dat", "").replace("ga_models/", "");
                logger.error("*** ASYNC I/O: {} save FAILED - {} ***", aiName, e.getMessage());
                throw new RuntimeException("Serializable object save failed", e);
            } finally {
                activeIOOperations.decrementAndGet();
            }
        }
    }
    
    private void saveDeepLearning4JModel(String filename, Object model) {
        // CRITICAL FIX: File-level synchronization for DeepLearning4J models
        Object fileLock = fileLocks.computeIfAbsent(filename, k -> new Object());
        
        synchronized (fileLock) {
            try {
                Path filePath = Paths.get(filename);
                AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, 
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                
                // Create thread-safe OutputStream bridge from AsynchronousFileChannel
                java.io.OutputStream channelStream = new java.io.OutputStream() {
                    private final java.util.concurrent.atomic.AtomicLong position = new java.util.concurrent.atomic.AtomicLong(0);
                    
                    @Override
                    public void write(int b) throws java.io.IOException {
                        write(new byte[]{(byte) b});
                    }
                    
                    @Override
                    public synchronized void write(byte[] b, int off, int len) throws java.io.IOException {
                        ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
                        try {
                            long pos = position.get();
                            channel.write(buffer, pos).get();
                            position.addAndGet(len);
                        } catch (Exception e) {
                            throw new java.io.IOException("NIO.2 write failed", e);
                        }
                    }
                };
                
                // Use direct DL4J API calls
                if (model instanceof org.deeplearning4j.nn.multilayer.MultiLayerNetwork) {
                    org.deeplearning4j.util.ModelSerializer.writeModel(
                        (org.deeplearning4j.nn.multilayer.MultiLayerNetwork) model, channelStream, true);
                } else if (model instanceof org.deeplearning4j.nn.graph.ComputationGraph) {
                    org.deeplearning4j.util.ModelSerializer.writeModel(
                        (org.deeplearning4j.nn.graph.ComputationGraph) model, channelStream, true);
                } else {
                    // Generic model interface
                    org.deeplearning4j.util.ModelSerializer.writeModel(
                        (org.deeplearning4j.nn.api.Model) model, channelStream, true);
                }
                
                channel.close();
                
                // Identify which AI system based on filename
                String aiName = "Unknown";
                if (filename.contains("deeplearning_model")) aiName = "DeepLearning";
                else if (filename.contains("cnn_model")) aiName = "CNN";
                else if (filename.contains("dqn_model")) aiName = "DQN";
                else if (filename.contains("dqn_target")) aiName = "DQN-Target";
                
                logger.info("*** ASYNC I/O: {} model saved using NIO.2 stream bridge - RACE CONDITION PROTECTED ***", aiName);
                
            } catch (Exception e) {
                logger.error("*** ASYNC I/O: DeepLearning4J model save FAILED - {} ***", e.getMessage());
                throw new RuntimeException("DeepLearning4J stream bridge save failed", e);
            } finally {
                activeIOOperations.decrementAndGet();
            }
        }
    }
    
    private AsynchronousFileChannel getOrCreateChannel(String filename, StandardOpenOption... options) throws IOException {
        return channels.computeIfAbsent(filename, key -> {
            try {
                Path path = Paths.get(key);
                return AsynchronousFileChannel.open(path, Set.of(options), ioExecutor);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private CompletableFuture<Void> saveAllDirtyData() {
        return CompletableFuture.runAsync(() -> {
            // CRITICAL: Check if training stopped - if so, skip all saves except shutdown
            if (shouldStopIO() && !coordinator.isShuttingDown()) {
                logger.info("*** ASYNC I/O: Training stopped - skipping dirty data save ***");
                // Clear all dirty flags to prevent future saves
                clearAllDirtyFlags();
                return;
            }
            
            int dirtyCount = (int) dirtyFlags.entrySet().stream().mapToLong(e -> e.getValue().get() ? 1 : 0).sum();
            
            // Check if any actual state changes occurred before saving
            if (!shouldMarkDirty() && dirtyCount == 0) {
                logger.info("*** ASYNC I/O: No AI state changes detected - skipping redundant save ***");
                return;
            }
            
            if (dirtyCount == 0) {
                logger.info("*** ASYNC I/O: No dirty data to save ***");
                return;
            }
            
            logger.info("*** ASYNC I/O: Saving all dirty data - {} files marked dirty ***", dirtyCount);
            
            java.util.List<CompletableFuture<Void>> saveTasks = new java.util.ArrayList<>();
            
            dirtyFlags.entrySet().parallelStream()
                .filter(entry -> entry.getValue().get())
                .forEach(entry -> {
                    // CRITICAL: Check stop status for each file
                    if (shouldStopIO() && !coordinator.isShuttingDown()) {
                        logger.info("*** ASYNC I/O: Skipping dirty file {} - training stopped ***", entry.getKey());
                        entry.getValue().set(false); // Mark as clean to prevent future saves
                        return;
                    }
                    
                    String filename = entry.getKey();
                    Object cachedData = dataCache.get(filename);
                    
                    if (cachedData != null) {
                        logger.info("*** ASYNC I/O: Flushing dirty file: {} ***", filename);
                        CompletableFuture<Void> task = writeDataAsyncForShutdown(filename, cachedData)
                            .thenRun(() -> {
                                entry.getValue().set(false);
                                // Clear cache after successful save to prevent memory leaks
                                dataCache.remove(filename);
                            });
                        synchronized(saveTasks) {
                            saveTasks.add(task);
                        }
                    } else {
                        logger.warn("*** ASYNC I/O: No cached data for dirty file: {} - marking clean ***", filename);
                        entry.getValue().set(false);
                    }
                });
            
            // Wait for all saves to complete
            CompletableFuture.allOf(saveTasks.toArray(new CompletableFuture[0])).join();
            logger.info("*** ASYNC I/O: All dirty data flushed successfully ***");
        }, ioExecutor);
    }
    
    private void closeAllChannels() {
        channels.values().parallelStream().forEach(channel -> {
            try {
                channel.close();
            } catch (IOException e) {
                // Log error
            }
        });
        channels.clear();
    }
    
    public AsyncIOMetrics getMetrics() {
        return metrics;
    }
    
    public void logMetrics() {
        metrics.logMetrics();
    }
    
    /**
     * Check if any async I/O operations are currently in progress
     */
    public boolean isIOInProgress() {
        return activeIOOperations.get() > 0;
    }
    
    /**
     * Enable user game data processing mode - allows saves even when training stopped
     */
    public void enableUserGameDataProcessing() {
        userGameDataProcessing = true;
        logger.debug("*** ASYNC I/O: User game data processing ENABLED - saves allowed ***");
    }
    
    /**
     * Disable user game data processing mode
     */
    public void disableUserGameDataProcessing() {
        userGameDataProcessing = false;
        logger.debug("*** ASYNC I/O: User game data processing DISABLED ***");
    }
    
    private CompletableFuture<Object> readDataAsync(String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Handle DeepLearning4J models with stream bridge
                if (filename.endsWith(".zip")) {
                    Object model = loadDeepLearning4JModel(filename);
                    if (model == null) {
                        logger.error("*** ASYNC I/O: DeepLearning4J model load FAILED - null ***");
                        throw new RuntimeException("DeepLearning4J stream bridge load failed");
                    }
                    return model;
                }
                
                // Handle serializable objects (like GeneticAlgorithm population, Q-Learning table, and AlphaZero cache)
                if (filename.contains("/") || filename.contains("population") || filename.contains("qtable") || filename.contains("alphazero_cache")) {
                    return loadSerializableObject(filename);
                }
                
                AsynchronousFileChannel channel = getOrCreateChannel(filename, 
                    StandardOpenOption.READ);
                
                long fileSize = channel.size();
                ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
                
                CompletableFuture<Integer> readFuture = new CompletableFuture<>();
                channel.read(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        String aiName = filename.replace(".dat", "");
                        logger.info("*** ASYNC I/O: {} loaded using NIO.2 AsynchronousFileChannel ({} bytes) ***", aiName, result);
                        readFuture.complete(result);
                    }
                    
                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        String aiName = filename.replace(".dat", "");
                        logger.error("*** ASYNC I/O: {} load FAILED using NIO.2 - {} ***", aiName, exc.getMessage());
                        readFuture.completeExceptionally(exc);
                    }
                });
                
                readFuture.join();
                buffer.flip();
                
                // Convert buffer to string for text-based files
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                return new String(data);
                
            } catch (Exception e) {
                String aiName = filename.replace(".dat", "");
                metrics.recordError(aiName);
                logger.error("*** ASYNC I/O: {} load FAILED using NIO.2 - {} ***", aiName, e.getMessage());
                throw new RuntimeException(e);
            }
        }, ioExecutor);
    }
    
    private Object loadDeepLearning4JModel(String filename) {
        try {
            Path filePath = Paths.get(filename);
            if (!java.nio.file.Files.exists(filePath)) {
                logger.info("*** ASYNC I/O: {} does not exist, returning null ***", filename);
                return null;
            }
            
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
            long fileSize = channel.size();
            
            if (fileSize == 0) {
                channel.close();
                logger.info("*** ASYNC I/O: {} is empty, returning null ***", filename);
                return null;
            }
            
            // Read entire file into memory first for DeepLearning4J compatibility
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            CompletableFuture<Integer> readFuture = new CompletableFuture<>();
            
            channel.read(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    readFuture.complete(result);
                }
                
                @Override
                public void failed(Throwable exc, Void attachment) {
                    readFuture.completeExceptionally(exc);
                }
            });
            
            readFuture.join();
            buffer.flip();
            channel.close();
            
            // Create InputStream from buffer
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            
            // Check if this is a ZIP file (DL4J models are saved as ZIP)
            java.io.InputStream modelStream;
            if (data.length >= 4 && data[0] == 0x50 && data[1] == 0x4B && data[2] == 0x03 && data[3] == 0x04) {
                // This is a ZIP file - extract the model data
                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().equals("coefficients.bin") || entry.getName().equals("configuration.json") || !entry.isDirectory()) {
                            // Found model data - read it
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            byte[] buffer2 = new byte[8192];
                            int len;
                            while ((len = zis.read(buffer2)) > 0) {
                                baos.write(buffer2, 0, len);
                            }
                            // Use the entire ZIP as input stream for DL4J
                            modelStream = new java.io.ByteArrayInputStream(data);
                            break;
                        }
                    }
                    modelStream = new java.io.ByteArrayInputStream(data);
                }
            } else {
                // Raw model data
                modelStream = new java.io.ByteArrayInputStream(data);
            }
            
            // Use DeepLearning4J ModelSerializer with InputStream - try different model types
            Class<?> modelSerializerClass = Class.forName("org.deeplearning4j.util.ModelSerializer");
            Object result = null;
            
            // Determine model type based on filename to avoid backward compatibility issues
            boolean isComputationGraph = filename.contains("dqn");
            
            if (isComputationGraph) {
                // Load as ComputationGraph directly
                result = org.deeplearning4j.util.ModelSerializer.restoreComputationGraph(modelStream, true);
                logger.info("*** ASYNC I/O: Successfully loaded as ComputationGraph ***");
            } else {
                // Load as MultiLayerNetwork directly
                result = org.deeplearning4j.util.ModelSerializer.restoreMultiLayerNetwork(modelStream, true);
                logger.info("*** ASYNC I/O: Successfully loaded as MultiLayerNetwork ***");
            }
            
            logger.info("*** ASYNC I/O: DeepLearning4J model loaded using NIO.2 stream bridge ***");
            return result;
            
        } catch (Exception e) {
            logger.error("*** ASYNC I/O: DeepLearning4J model load FAILED - {} ***", e.getMessage());
            return null;
        }
    }
    
    private Object loadSerializableObject(String filename) {
        try {
            Path filePath = Paths.get(filename);
            if (!java.nio.file.Files.exists(filePath)) {
                logger.info("*** ASYNC I/O: {} does not exist, returning null ***", filename);
                return null;
            }
            
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
            long fileSize = channel.size();
            
            if (fileSize == 0) {
                channel.close();
                logger.info("*** ASYNC I/O: {} is empty, returning null ***", filename);
                return null;
            }
            
            // Read entire file into memory
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            CompletableFuture<Integer> readFuture = new CompletableFuture<>();
            
            channel.read(buffer, 0, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
                    readFuture.complete(result);
                }
                
                @Override
                public void failed(Throwable exc, Void attachment) {
                    readFuture.completeExceptionally(exc);
                }
            });
            
            readFuture.join();
            buffer.flip();
            channel.close();
            
            // Deserialize object (compression disabled)
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(data))) {
                Object result = ois.readObject();
                String aiName = filename.replace(".dat", "").replace("ga_models/", "");
                logger.info("*** ASYNC I/O: {} loaded using NIO.2 AsynchronousFileChannel ({} bytes) ***", aiName, data.length);
                return result;
            }
            
        } catch (Exception e) {
            String aiName = filename.replace(".dat", "").replace("ga_models/", "");
            logger.error("*** ASYNC I/O: {} load FAILED - {} ***", aiName, e.getMessage());
            return null;
        }
    }
}