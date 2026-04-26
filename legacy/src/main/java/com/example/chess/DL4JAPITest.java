package com.example.chess;

import java.lang.reflect.Method;
import org.deeplearning4j.util.ModelSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DL4JAPITest {
    
    private static final Logger logger = LogManager.getLogger(DL4JAPITest.class);
    public static void main(String[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("=== DL4J ModelSerializer Available Methods ===");
        }
        
        Method[] methods = ModelSerializer.class.getMethods();
        for (Method method : methods) {
            if (method.getName().contains("writeModel") || method.getName().contains("restoreMultiLayerNetwork")) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Method: " + method.getName());
                    logger.debug("  Parameters: ");
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        logger.debug("    [" + i + "] " + params[i].getSimpleName());
                    }
                    logger.debug("  Full signature: " + method.toString());
                    logger.debug("");
                }
            }
        }
    }
}