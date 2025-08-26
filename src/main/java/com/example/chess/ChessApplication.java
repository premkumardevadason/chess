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
    private static ConfigurableApplicationContext applicationContext;
    
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
    
    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }    
    public static void main(String[] args) {
        // Check if MCP mode is requested
        if (args.length > 0 && "--mcp".equals(args[0])) {
            startMCPServer(args);
            return;
        }
        
        // Log JVM command line parameters
        java.lang.management.RuntimeMXBean runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        logger.info("JVM Arguments: {}", runtimeMxBean.getInputArguments());
        
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        applicationContext = context;
        
        // Add shutdown hook with forced execution
        Thread shutdownHook = new Thread(() -> {
            if (!shutdownInProgress && chessGame != null) {
                shutdownInProgress = true;
                System.out.println("*** JVM SHUTDOWN HOOK TRIGGERED ***");
                System.out.flush();
                try {
                    // CRITICAL FIX: Always call chessGame.shutdown() to process user game data
                    chessGame.shutdown();
                    System.out.println("*** CHESS GAME SHUTDOWN COMPLETE (JVM HOOK) ***");
                } catch (Exception e) {
                    System.err.println("JVM shutdown hook error: " + e.getMessage());
                    e.printStackTrace();
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
                        // Use direct AI saves for periodic backup (training may be active)
                        try {
                            chessGame.saveAllAIDirectly();
                            System.out.println("*** PERIODIC TRAINING DATA SAVE COMPLETE ***");
                        } catch (Exception e) {
                            System.err.println("Periodic save error: " + e.getMessage());
                        }
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
    
    private static void startMCPServer(String[] args) {
        logger.info("Starting Chess MCP Server");
        System.setProperty("spring.main.web-application-type", "none");
        
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        applicationContext = context;
        
        try {
            com.example.chess.mcp.MCPTransportService transportService = 
                context.getBean(com.example.chess.mcp.MCPTransportService.class);
            
            String transport = getTransportType(args);
            if ("stdio".equals(transport)) {
                transportService.startStdioTransport();
            } else {
                transportService.startStdioTransport();
            }
        } catch (Exception e) {
            logger.error("Failed to start MCP server: {}", e.getMessage(), e);
        }
    }
    
    private static String getTransportType(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--transport".equals(args[i])) {
                return args[i + 1];
            }
        }
        return "stdio";
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
            System.out.println("*** SPRING CONTEXT CLOSING - CALLING CHESS GAME SHUTDOWN ***");
            System.out.flush();
            
            try {
                // CRITICAL FIX: Always call chessGame.shutdown() to process user game data
                chessGame.shutdown();
                System.out.println("*** CHESS GAME SHUTDOWN COMPLETE ***");
                System.out.flush();
            } catch (Exception e) {
                System.err.println("Chess game shutdown error: " + e.getMessage());
                e.printStackTrace();
            }
            
            if (useAsyncIO()) {
                System.out.println("*** ASYNC I/O SHUTDOWN ***");
                asyncDataManager.shutdown().join();
            }
            
            System.out.println("*** SPRING SHUTDOWN COMPLETE ***");
            System.out.flush();
        }
    }
    

}