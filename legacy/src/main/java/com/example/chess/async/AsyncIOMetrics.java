package com.example.chess.async;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performance monitoring for async I/O operations.
 */
public class AsyncIOMetrics {
    private static final Logger logger = LogManager.getLogger(AsyncIOMetrics.class);
    
    private final Map<String, AtomicLong> saveTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> saveCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCount = new ConcurrentHashMap<>();
    
    public void recordSaveTime(String aiName, long milliseconds) {
        saveTimes.computeIfAbsent(aiName, k -> new AtomicLong()).addAndGet(milliseconds);
        saveCount.computeIfAbsent(aiName, k -> new AtomicLong()).incrementAndGet();
        
        if (logger.isDebugEnabled()) {
            logger.debug("Async save time for {}: {}ms", aiName, milliseconds);
        }
    }
    
    public void recordError(String aiName) {
        errorCount.computeIfAbsent(aiName, k -> new AtomicLong()).incrementAndGet();
        logger.warn("Async I/O error recorded for {}", aiName);
    }
    
    public double getAverageSaveTime(String aiName) {
        AtomicLong totalTime = saveTimes.get(aiName);
        AtomicLong count = saveCount.get(aiName);
        
        if (totalTime == null || count == null || count.get() == 0) {
            return 0.0;
        }
        
        return (double) totalTime.get() / count.get();
    }
    
    public long getTotalSaves(String aiName) {
        AtomicLong count = saveCount.get(aiName);
        return count != null ? count.get() : 0;
    }
    
    public long getErrorCount(String aiName) {
        AtomicLong errors = errorCount.get(aiName);
        return errors != null ? errors.get() : 0;
    }
    
    public String getMetricsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== ASYNC I/O METRICS ===\n");
        
        for (String aiName : saveTimes.keySet()) {
            double avgTime = getAverageSaveTime(aiName);
            long saves = getTotalSaves(aiName);
            long errors = getErrorCount(aiName);
            
            summary.append(String.format("%s: %.2fms avg, %d saves, %d errors\n", 
                aiName, avgTime, saves, errors));
        }
        
        return summary.toString();
    }
    
    public void logMetrics() {
        logger.info(getMetricsSummary());
    }
}