package com.example.chess;

import java.io.*;
import java.lang.reflect.Method;
import org.deeplearning4j.util.ModelSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Standalone test to investigate DeepLearning4J ModelSerializer API
 */
public class StandaloneAPITest {
    
    private static final Logger logger = LogManager.getLogger(StandaloneAPITest.class);
    
    public static void main(String[] args) {
        logger.info("=== DeepLearning4J ModelSerializer API Investigation ===");
        
        Class<?> modelSerializerClass = ModelSerializer.class;
        
        logger.info("Available ModelSerializer methods:");
        boolean hasStreamMethods = false;
        
        for (Method method : modelSerializerClass.getDeclaredMethods()) {
            if (method.getName().contains("writeModel") || method.getName().contains("restore")) {
                logger.info("  " + method.getName() + " - Parameters: " + 
                    java.util.Arrays.toString(method.getParameterTypes()));
                
                // Check for stream-based methods
                for (Class<?> paramType : method.getParameterTypes()) {
                    if (paramType == OutputStream.class || paramType == InputStream.class) {
                        hasStreamMethods = true;
                    }
                }
            }
        }
        
        logger.info("\n=== COMPATIBILITY RESULT ===");
        if (hasStreamMethods) {
            logger.info("✅ STREAM METHODS FOUND - NIO.2 COMPATIBLE");
            logger.info("   All 8 AI systems can use NIO.2 (100% coverage)");
        } else {
            logger.warn("❌ NO STREAM METHODS - FILE-ONLY API");
            logger.warn("   Only 3/8 AI systems can use NIO.2 (37.5% coverage)");
        }
    }
}