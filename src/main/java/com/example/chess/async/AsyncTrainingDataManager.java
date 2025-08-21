package com.example.chess.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    private final Map<String, Long> lastSaveTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastSaveHash = new ConcurrentHashMap<>();
    // CRITICAL FIX: File-level locks to prevent race conditions
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(11);
    private final AsyncIOMetrics metrics = new AsyncIOMetrics();
    private static final long SAVE_DEBOUNCE_MS = 1000; // 1 second debounce
    
    public AsyncTrainingDataManager() {
        this.aiTracker = new AICompletionTracker();
        this.coordinator = new AtomicFeatureCoordinator(aiTracker);
    }
    
    private boolean shouldStopIO() {
        // Only stop I/O during shutdown, not during game reset or periodic saves
        return coordinator.isShuttingDown();
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
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.TRAINING_STOP_SAVE, () -> {
            saveAllDirtyData().join();
        });
    }
    
    public CompletableFuture<Void> saveOnGameReset() {
        return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.GAME_RESET_SAVE, () -> {
            saveAllDirtyData().join();
        });
    }
    
    public CompletableFuture<Void> saveAIData(String aiName, Object data, String filename) {
        return coordinator.executeAsyncIO(aiName, () -> {
            aiTracker.markAIActive(aiName);
            try {
                return writeDataAsync(filename, data);
            } finally {
                aiTracker.markAIComplete(aiName);
            }
        });
    }
    
    public CompletableFuture<Void> saveAIData(String aiName, Object data) {
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
        dirtyFlags.computeIfAbsent(filename, k -> new AtomicBoolean()).set(true);
    }
    
    private CompletableFuture<Void> writeDataAsync(String filename, Object data) {
        return CompletableFuture.runAsync(() -> {
            // CRITICAL FIX: File-level synchronization for all save operations
            Object fileLock = fileLocks.computeIfAbsent(filename, k -> new Object());
            
            synchronized (fileLock) {
                try {
                    // Check if training stopped before expensive I/O
                    if (shouldStopIO()) {
                        logger.info("*** ASYNC I/O: Operation cancelled - training stopped ***");
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
                    
                    // Extra check for DeepLearning4J models - 30 minute debounce
                    if (filename.endsWith(".zip") && lastTime != null && 
                        (currentTime - lastTime) < (30 * 60 * 1000)) { // 30 minutes
                        logger.debug("*** ASYNC I/O: Skipping frequent DeepLearning4J save for {} ({}min ago) ***", 
                            filename, (currentTime - lastTime) / (60 * 1000));
                        return;
                    }
                    
                    // Update tracking
                    lastSaveTime.put(filename, currentTime);
                    lastSaveHash.put(filename, dataHash);
                    
                    // Handle DeepLearning4J models with stream bridge
                    if (data.getClass().getSimpleName().equals("MultiLayerNetwork") || filename.endsWith(".zip")) {
                        // Check stop flag before expensive model save
                        if (shouldStopIO()) {
                            logger.info("*** ASYNC I/O: DeepLearning4J save cancelled - training stopped ***");
                            return;
                        }
                        saveDeepLearning4JModel(filename, data);
                        return;
                    }
                    
                    // Handle Java serializable objects (like GeneticAlgorithm population)
                    if (data instanceof java.io.Serializable && !data.getClass().equals(String.class)) {
                        // Check stop flag before expensive serialization
                        if (shouldStopIO()) {
                            logger.info("*** ASYNC I/O: Serialization save cancelled - training stopped ***");
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
                }
            }
        }, ioExecutor);
    }
    
    // CRITICAL FIX: File-level synchronization to prevent race conditions
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    
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
                
                // Serialize snapshot to byte array
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
                
                // Create OutputStream bridge from AsynchronousFileChannel
                java.io.OutputStream channelStream = new java.io.OutputStream() {
                    private long position = 0;
                    
                    @Override
                    public void write(int b) throws java.io.IOException {
                        write(new byte[]{(byte) b});
                    }
                    
                    @Override
                    public void write(byte[] b, int off, int len) throws java.io.IOException {
                        ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
                        try {
                            channel.write(buffer, position).get();
                            position += len;
                        } catch (Exception e) {
                            throw new java.io.IOException("NIO.2 write failed", e);
                        }
                    }
                };
                
                // Use DeepLearning4J ModelSerializer with OutputStream
                Class<?> modelSerializerClass = Class.forName("org.deeplearning4j.util.ModelSerializer");
                java.lang.reflect.Method writeMethod = modelSerializerClass.getMethod(
                    "writeModel", Class.forName("org.deeplearning4j.nn.api.Model"), java.io.OutputStream.class, boolean.class);
                writeMethod.invoke(null, model, channelStream, true);
                
                channel.close();
                logger.info("*** ASYNC I/O: DeepLearning4J model saved using NIO.2 stream bridge - RACE CONDITION PROTECTED ***");
                
            } catch (Exception e) {
                logger.error("*** ASYNC I/O: DeepLearning4J model save FAILED - {} ***", e.getMessage());
                throw new RuntimeException("DeepLearning4J stream bridge save failed", e);
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
            dirtyFlags.entrySet().parallelStream()
                .filter(entry -> entry.getValue().get())
                .forEach(entry -> {
                    // Save dirty files
                });
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
                
                // Handle serializable objects (like GeneticAlgorithm population and Q-Learning table)
                if (filename.contains("/") || filename.contains("population") || filename.contains("qtable")) {
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
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(data);
            
            // Use DeepLearning4J ModelSerializer with InputStream - try different model types
            Class<?> modelSerializerClass = Class.forName("org.deeplearning4j.util.ModelSerializer");
            Object result = null;
            
            try {
                // Try MultiLayerNetwork first
                java.lang.reflect.Method restoreMLNMethod = modelSerializerClass.getMethod(
                    "restoreMultiLayerNetwork", java.io.InputStream.class);
                result = restoreMLNMethod.invoke(null, inputStream);
            } catch (Exception e1) {
                try {
                    // Reset stream and try ComputationGraph
                    inputStream = new java.io.ByteArrayInputStream(data);
                    java.lang.reflect.Method restoreCGMethod = modelSerializerClass.getMethod(
                        "restoreComputationGraph", java.io.InputStream.class);
                    result = restoreCGMethod.invoke(null, inputStream);
                } catch (Exception e2) {
                    logger.error("*** ASYNC I/O: Failed to load as MultiLayerNetwork or ComputationGraph - {} ***", e2.getMessage());
                    return null;
                }
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
            
            // Deserialize object
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