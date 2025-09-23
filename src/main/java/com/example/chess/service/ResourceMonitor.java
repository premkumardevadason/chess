package com.example.chess.service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resource monitoring service for system resource tracking
 * Monitors CPU usage, memory usage, and system load
 */
@Component
public class ResourceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceMonitor.class);
    
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Cached values for performance
    private volatile double cachedCpuUsage = 0.0;
    private volatile double cachedMemoryUsage = 0.0;
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    
    private static final long UPDATE_INTERVAL_MS = 1000; // Update every second
    
    /**
     * Get current CPU usage as a percentage (0.0 to 1.0)
     */
    public double getCpuUsage() {
        updateCachedValues();
        return cachedCpuUsage;
    }
    
    /**
     * Get current memory usage as a percentage (0.0 to 1.0)
     */
    public double getMemoryUsage() {
        updateCachedValues();
        return cachedMemoryUsage;
    }
    
    /**
     * Get system load average
     */
    public double getSystemLoad() {
        return osBean.getSystemLoadAverage();
    }
    
    /**
     * Get available processors
     */
    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }
    
    /**
     * Check if system is under high load
     */
    public boolean isHighLoad() {
        return getCpuUsage() > 0.8 || getMemoryUsage() > 0.85 || getSystemLoad() > 0.8;
    }
    
    /**
     * Check if system resources are sufficient for additional AI computation
     */
    public boolean canStartNewComputation() {
        return getCpuUsage() < 0.7 && getMemoryUsage() < 0.8 && getSystemLoad() < 0.7;
    }
    
    /**
     * Get detailed resource information
     */
    public ResourceInfo getResourceInfo() {
        updateCachedValues();
        
        long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long freeMemory = totalMemory - usedMemory;
        
        return new ResourceInfo(
            cachedCpuUsage,
            cachedMemoryUsage,
            getSystemLoad(),
            totalMemory,
            usedMemory,
            freeMemory,
            getAvailableProcessors()
        );
    }
    
    /**
     * Update cached resource values if needed
     */
    private void updateCachedValues() {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastUpdateTime.get();
        
        if (currentTime - lastUpdate > UPDATE_INTERVAL_MS) {
            if (lastUpdateTime.compareAndSet(lastUpdate, currentTime)) {
                updateCpuUsage();
                updateMemoryUsage();
            }
        }
    }
    
    /**
     * Update CPU usage calculation
     */
    private void updateCpuUsage() {
        try {
            // Simple CPU usage estimation based on system load
            double systemLoad = osBean.getSystemLoadAverage();
            int processors = osBean.getAvailableProcessors();
            
            if (systemLoad >= 0) {
                double cpuUsage = Math.min(systemLoad / processors, 1.0);
                cachedCpuUsage = cpuUsage;
            } else {
                // Fallback to a conservative estimate
                cachedCpuUsage = 0.5;
            }
        } catch (Exception e) {
            logger.warn("Error calculating CPU usage", e);
            cachedCpuUsage = 0.5; // Conservative fallback
        }
    }
    
    /**
     * Update memory usage calculation
     */
    private void updateMemoryUsage() {
        try {
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            
            if (maxMemory > 0) {
                double memoryUsage = (double) usedMemory / maxMemory;
                cachedMemoryUsage = memoryUsage;
            } else {
                cachedMemoryUsage = 0.5; // Conservative fallback
            }
        } catch (Exception e) {
            logger.warn("Error calculating memory usage", e);
            cachedMemoryUsage = 0.5; // Conservative fallback
        }
    }
    
    /**
     * Resource information data class
     */
    public static class ResourceInfo {
        public final double cpuUsage;
        public final double memoryUsage;
        public final double systemLoad;
        public final long totalMemory;
        public final long usedMemory;
        public final long freeMemory;
        public final int availableProcessors;
        
        public ResourceInfo(double cpuUsage, double memoryUsage, double systemLoad,
                          long totalMemory, long usedMemory, long freeMemory, int availableProcessors) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.systemLoad = systemLoad;
            this.totalMemory = totalMemory;
            this.usedMemory = usedMemory;
            this.freeMemory = freeMemory;
            this.availableProcessors = availableProcessors;
        }
        
        @Override
        public String toString() {
            return String.format("CPU: %.1f%%, Memory: %.1f%%, Load: %.2f, Processors: %d",
                cpuUsage * 100, memoryUsage * 100, systemLoad, availableProcessors);
        }
    }
}
