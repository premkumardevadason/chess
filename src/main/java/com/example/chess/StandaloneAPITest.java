package com.example.chess;

import java.io.*;
import java.lang.reflect.Method;
import org.deeplearning4j.util.ModelSerializer;

/**
 * Standalone test to investigate DeepLearning4J ModelSerializer API
 */
public class StandaloneAPITest {
    
    public static void main(String[] args) {
        System.out.println("=== DeepLearning4J ModelSerializer API Investigation ===");
        
        Class<?> modelSerializerClass = ModelSerializer.class;
        
        System.out.println("Available ModelSerializer methods:");
        boolean hasStreamMethods = false;
        
        for (Method method : modelSerializerClass.getDeclaredMethods()) {
            if (method.getName().contains("writeModel") || method.getName().contains("restore")) {
                System.out.println("  " + method.getName() + " - Parameters: " + 
                    java.util.Arrays.toString(method.getParameterTypes()));
                
                // Check for stream-based methods
                for (Class<?> paramType : method.getParameterTypes()) {
                    if (paramType == OutputStream.class || paramType == InputStream.class) {
                        hasStreamMethods = true;
                    }
                }
            }
        }
        
        System.out.println("\n=== COMPATIBILITY RESULT ===");
        if (hasStreamMethods) {
            System.out.println("✅ STREAM METHODS FOUND - NIO.2 COMPATIBLE");
            System.out.println("   All 8 AI systems can use NIO.2 (100% coverage)");
        } else {
            System.out.println("❌ NO STREAM METHODS - FILE-ONLY API");
            System.out.println("   Only 3/8 AI systems can use NIO.2 (37.5% coverage)");
        }
    }
}