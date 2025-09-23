package com.example.chess.service;

import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ResourceMonitor {
    
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private volatile double cpuUsage = 0.0;
    private volatile double memoryUsage = 0.0;
    private volatile int activeFrameProcessing = 0;
    private volatile long totalFramesProcessed = 0;
    private volatile long lastFrameTime = 0;
    
    private final AtomicInteger frameRate = new AtomicInteger(30);
    private final AtomicLong frameProcessingTime = new AtomicLong(0);
    
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::updateMetrics, 0, 1, TimeUnit.SECONDS);
        System.out.println("Resource monitoring started");
    }
    
    private void updateMetrics() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                cpuUsage = sunOsBean.getProcessCpuLoad();
            } else {
                cpuUsage = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
            }
            
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            memoryUsage = (double) usedMemory / maxMemory;
            
            adjustFrameRate();
            
        } catch (Exception e) {
            System.err.println("Error updating resource metrics: " + e.getMessage());
        }
    }
    
    private void adjustFrameRate() {
        int currentRate = frameRate.get();
        
        if (cpuUsage > 0.8 || memoryUsage > 0.8) {
            int newRate = Math.max(10, currentRate - 5);
            frameRate.set(newRate);
            System.out.println("Reduced frame rate to " + newRate + " FPS due to high system load");
        } else if (cpuUsage < 0.5 && memoryUsage < 0.5 && currentRate < 30) {
            int newRate = Math.min(30, currentRate + 5);
            frameRate.set(newRate);
            System.out.println("Increased frame rate to " + newRate + " FPS");
        }
    }
    
    public boolean canProcessFrame() {
        return cpuUsage < 0.9 && memoryUsage < 0.9 && activeFrameProcessing < 3;
    }
    
    public void startFrameProcessing() {
        activeFrameProcessing++;
        lastFrameTime = System.currentTimeMillis();
    }
    
    public void endFrameProcessing() {
        activeFrameProcessing = Math.max(0, activeFrameProcessing - 1);
        totalFramesProcessed++;
        
        long processingTime = System.currentTimeMillis() - lastFrameTime;
        frameProcessingTime.addAndGet(processingTime);
    }
    
    public double getCpuUsage() {
        return Math.max(0.0, Math.min(1.0, cpuUsage));
    }
    
    public double getMemoryUsage() {
        return Math.max(0.0, Math.min(1.0, memoryUsage));
    }
    
    public int getRecommendedFrameRate() {
        return frameRate.get();
    }
    
    public int getFrameInterval() {
        return 1000 / frameRate.get();
    }
    
    public int getActiveFrameProcessing() {
        return activeFrameProcessing;
    }
    
    public long getTotalFramesProcessed() {
        return totalFramesProcessed;
    }
    
    public double getAverageFrameProcessingTime() {
        return totalFramesProcessed > 0 ? 
            (double) frameProcessingTime.get() / totalFramesProcessed : 0.0;
    }
    
    public String getResourceStatus() {
        return String.format(
            "CPU: %.1f%%, Memory: %.1f%%, Frame Rate: %d FPS, Active Processing: %d, Total Frames: %d, Avg Processing: %.1fms",
            cpuUsage * 100, memoryUsage * 100, frameRate.get(), 
            activeFrameProcessing, totalFramesProcessed, getAverageFrameProcessingTime()
        );
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}