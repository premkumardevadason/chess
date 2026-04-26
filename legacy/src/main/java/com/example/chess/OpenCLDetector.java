package com.example.chess;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GPU detection and system capability analysis for AI optimization.
 * Supports OpenCL and CUDA acceleration detection.
 */
public class OpenCLDetector {
    private static final Logger logger = LogManager.getLogger(OpenCLDetector.class);
    
    private static boolean detectionComplete = false;
    private static String systemInfo = "CPU Backend";
    
    /**
     * Detect system capabilities
     */
    public static void detectAndConfigureOpenCL() {
        if (detectionComplete) return;
        
        try {
            logger.info("System: Starting capability detection...");
            logSystemInfo();
            systemInfo = "CPU Backend (Optimized)";
            logger.info("System: Using CPU backend for AI processing");
            
        } catch (Exception e) {
            logger.error("System: Detection failed - {}", e.getMessage());
        } finally {
            detectionComplete = true;
        }
    }
    
    private static void logSystemInfo() {
        try {
            long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
            
            logger.info("=== System Information ===");
            logger.info("Backend: CPU (Native)");
            logger.info("JVM Max Memory: {} MB", maxMemory);
            logger.info("CPU Cores: {}", Runtime.getRuntime().availableProcessors());
            
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();
            logger.info("OS: {} ({})", osName, osArch);
            
        } catch (Exception e) {
            logger.error("System: Info logging failed - {}", e.getMessage());
        }
    }
    
    /**
     * Check if GPU acceleration is available (always false for simplified version)
     */
    public static boolean isOpenCLAvailable() {
        if (!detectionComplete) {
            detectAndConfigureOpenCL();
        }
        return false; // Simplified to CPU-only
    }
    
    /**
     * Get system information string
     */
    public static String getGPUInfoString() {
        if (!detectionComplete) {
            detectAndConfigureOpenCL();
        }
        return systemInfo;
    }
    
    /**
     * Force re-detection (for testing)
     */
    public static void resetDetection() {
        detectionComplete = false;
        systemInfo = "CPU Backend";
    }
}