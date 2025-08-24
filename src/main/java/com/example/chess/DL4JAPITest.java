package com.example.chess;

import java.lang.reflect.Method;
import org.deeplearning4j.util.ModelSerializer;

public class DL4JAPITest {
    public static void main(String[] args) {
        System.out.println("=== DL4J ModelSerializer Available Methods ===");
        
        Method[] methods = ModelSerializer.class.getMethods();
        for (Method method : methods) {
            if (method.getName().contains("writeModel") || method.getName().contains("restoreMultiLayerNetwork")) {
                System.out.println("Method: " + method.getName());
                System.out.println("  Parameters: ");
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    System.out.println("    [" + i + "] " + params[i].getSimpleName());
                }
                System.out.println("  Full signature: " + method.toString());
                System.out.println();
            }
        }
    }
}