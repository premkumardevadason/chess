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
        // Log command line arguments and JVM parameters
        logger.info("Command Line Arguments: {}", java.util.Arrays.toString(args));
        java.lang.management.RuntimeMXBean runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        logger.info("JVM Arguments: {}", runtimeMxBean.getInputArguments());
        
        // Check for MCP mode (--mcp or --dual-mode)
        boolean mcpMode = containsArg(args, "--mcp");
        boolean dualMode = containsArg(args, "--dual-mode");
        
        if (mcpMode || dualMode) {
            if (dualMode || (mcpMode && dualMode)) {
                logger.info("Starting in DUAL MODE (Web interface + MCP server)");
                startDualMode(args);
            } else {
                logger.info("Starting in MCP-only mode");
                startMCPServer(args);
            }
            return;
        }
        
        // Default: Web-only mode
        logger.info("Starting in Web-only mode (default)");
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        applicationContext = context;
        
        // Add shutdown hook with forced execution
        Thread shutdownHook = new Thread(() -> {
            if (!shutdownInProgress && chessGame != null) {
                shutdownInProgress = true;
                logger.debug("*** JVM SHUTDOWN HOOK TRIGGERED ***");
                try {
                    // CRITICAL FIX: Always call chessGame.shutdown() to process user game data
                    chessGame.shutdown();
                    logger.debug("*** CHESS GAME SHUTDOWN COMPLETE (JVM HOOK) ***");
                } catch (Exception e) {
                    logger.error("JVM shutdown hook error: " + e.getMessage(), e);
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
                            logger.debug("*** PERIODIC TRAINING DATA SAVE COMPLETE ***");
                        } catch (Exception e) {
                            logger.error("Periodic save error: " + e.getMessage(), e);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Periodic save error: " + e.getMessage(), e);
                }
            }
        });
        periodicSave.setDaemon(true);
        periodicSave.setName("Periodic-Save-Thread");
        periodicSave.start();
    }
    
    private static void startMCPServer(String[] args) {
        logger.info("Starting Chess MCP Server (MCP-only mode)");
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("spring.profiles.active", "mcp");
        
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        applicationContext = context;
        
        startMCPTransport(context, args);
    }
    
    private static void startDualMode(String[] args) {
        logger.info("Starting Chess Application in Dual Mode (Web + MCP)");
        // Keep web application enabled for dual mode
        System.setProperty("spring.profiles.active", "dev,mcp");
        // CRITICAL FIX: Override MCP profile's web-application-type=none for dual mode
        System.setProperty("spring.main.web-application-type", "servlet");
        
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        applicationContext = context;
        
        // Log web server port
        String webPort = context.getEnvironment().getProperty("server.port", "8081");
        logger.info("Web server started on port: {}", webPort);
        
        // Start MCP transport in addition to web interface
        startMCPTransport(context, args);
    }
    
    private static void startMCPTransport(ConfigurableApplicationContext context, String[] args) {
        try {
            com.example.chess.mcp.MCPTransportService transportService = 
                context.getBean(com.example.chess.mcp.MCPTransportService.class);
            
            String transport = getTransportType(args);
            int port = getPortNumber(args);
            
            logger.info("MCP Transport: {}", transport);
            if ("websocket".equals(transport)) {
                logger.info("MCP WebSocket Port: {}", port);
                transportService.startWebSocketTransport(port);
            } else {
                logger.info("MCP using stdio transport");
                transportService.startStdioTransport();
            }
        } catch (Exception e) {
            logger.error("Failed to start MCP transport: {}", e.getMessage(), e);
        }
    }
    
    private static boolean containsArg(String[] args, String arg) {
        for (String a : args) {
            if (arg.equals(a)) {
                return true;
            }
        }
        return false;
    }
    
    private static String getTransportType(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--transport".equals(args[i])) {
                return args[i + 1];
            }
        }
        // Default to websocket for dual mode, stdio for MCP-only
        return containsArg(args, "--dual-mode") ? "websocket" : "stdio";
    }
    
    private static int getPortNumber(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port number: {}, using default 8082", args[i + 1]);
                }
            }
        }
        return 8082;
    }
    

    
    @Bean(destroyMethod = "")
    public ChessGame chessGame() {
        ChessGame game = new ChessGame();
        chessGame = game; // Set static reference for shutdown hook
        logger.debug("ChessGame bean created and static reference set");
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
            logger.debug("*** SPRING CONTEXT CLOSING - CALLING CHESS GAME SHUTDOWN ***");
            
            try {
                // CRITICAL FIX: Always call chessGame.shutdown() to process user game data
                chessGame.shutdown();
                logger.debug("*** CHESS GAME SHUTDOWN COMPLETE ***");
            } catch (Exception e) {
                logger.error("Chess game shutdown error: " + e.getMessage(), e);
            }
            
            if (useAsyncIO()) {
                logger.debug("*** ASYNC I/O SHUTDOWN ***");
                asyncDataManager.shutdown().join();
            }
            
            logger.debug("*** SPRING SHUTDOWN COMPLETE ***");
        }
    }
    

}