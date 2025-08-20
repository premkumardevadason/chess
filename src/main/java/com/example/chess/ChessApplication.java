package com.example.chess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.chess.async.AsyncTrainingDataManager;


/**
 * Main Spring Boot application for the Chess Web Game.
 * Features graceful shutdown handling for AI training systems.
 */
@SpringBootApplication
public class ChessApplication {
    private static final Logger logger = LogManager.getLogger(ChessApplication.class);
    
    private static ChessGame chessGame;
    public static volatile boolean shutdownInProgress = false;
    
    private AsyncTrainingDataManager asyncDataManager;
    
    private boolean useAsyncIO() {
        if (asyncDataManager == null) {
            asyncDataManager = new AsyncTrainingDataManager();
            // Start async system immediately when first accessed
            asyncDataManager.startup().join();
            logger.info("*** ASYNC I/O: NIO.2 system started early for AI initialization ***");
        }
        return true;
    }
    
    private static ChessApplication instance;
    
    public ChessApplication() {
        instance = this;
        // Initialize async system immediately
        if (useAsyncIO()) {
            logger.info("*** ASYNC I/O: NIO.2 system initialized early for AI startup ***");
        }
    }
    
    public static AsyncTrainingDataManager getAsyncManager() {
        if (instance != null && instance.asyncDataManager != null) {
            return instance.asyncDataManager;
        }
        return null;
    }    
    public static void main(String[] args) {
        // Log JVM command line parameters
        java.lang.management.RuntimeMXBean runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        logger.info("JVM Arguments: {}", runtimeMxBean.getInputArguments());
        
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        
        // Add shutdown hook with forced execution
        Thread shutdownHook = new Thread(() -> {
            if (!shutdownInProgress && chessGame != null) {
                shutdownInProgress = true;
                System.out.println("*** JVM SHUTDOWN HOOK TRIGGERED ***");
                System.out.flush();
                if (chessGame.hasStateChanged()) {
                    try {
                        chessGame.saveTrainingData();
                        System.out.println("*** TRAINING DATA SAVED ON SHUTDOWN ***");
                    } catch (Exception e) {
                        System.err.println("Shutdown save error: " + e.getMessage());
                    }
                } else {
                    System.out.println("*** NO STATE CHANGES, SKIPPING SAVE ***");
                }
            }
        });
        shutdownHook.setName("ChessGame-JVM-Shutdown-Hook");
        shutdownHook.setDaemon(false);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        
        // Add periodic save as backup
        Thread periodicSave = new Thread(() -> {
            while (!shutdownInProgress) {
                try {
                    Thread.sleep(300000); // Save every 5 minutes
                    if (chessGame != null && !shutdownInProgress && chessGame.hasStateChanged()) {
                        chessGame.saveTrainingData();
                        System.out.println("*** PERIODIC TRAINING DATA SAVE COMPLETE ***");
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Periodic save error: " + e.getMessage());
                }
            }
        });
        periodicSave.setDaemon(true);
        periodicSave.setName("Periodic-Save-Thread");
        periodicSave.start();
    }
    

    
    @Bean(destroyMethod = "")
    public ChessGame chessGame() {
        ChessGame game = new ChessGame();
        chessGame = game; // Set static reference for shutdown hook
        System.out.println("ChessGame bean created and static reference set");
        return game;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (useAsyncIO()) {
            logger.info("CHESS APPLICATION READY - Async I/O enabled");
        } else {
            logger.info("CHESS APPLICATION READY - Using synchronous I/O");
        }
    }
    
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        if (!shutdownInProgress && chessGame != null) {
            shutdownInProgress = true;
            if (useAsyncIO()) {
                System.out.println("*** SPRING CONTEXT CLOSING - ASYNC SHUTDOWN ***");
                asyncDataManager.shutdown().join();
            } else if (chessGame.hasStateChanged()) {
                System.out.println("*** SPRING CONTEXT CLOSING - SAVING TRAINING DATA ***");
                System.out.flush();
                try {
                    chessGame.shutdown();
                    System.out.println("*** SPRING SHUTDOWN COMPLETE ***");
                    System.out.flush();
                } catch (Exception e) {
                    System.err.println("Spring shutdown error: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("*** SPRING CONTEXT CLOSING - NO STATE CHANGES, SKIPPING SAVE ***");
            }
        }
    }
    

}