package com.example.chess.mcp.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main application class for the MCP Chess Agent
 * Creates dual-session training environment for AI vs AI gameplay
 */
public class MCPChessAgent {
    
    private static final Logger logger = LogManager.getLogger(MCPChessAgent.class);
    
    private MCPConnectionManager connectionManager;
    private DualSessionOrchestrator orchestrator;
    private AgentConfiguration config;
    private ExecutorService threadPool;
    private volatile boolean running = false;
    
    public static void main(String[] args) {
        MCPChessAgent agent = new MCPChessAgent();
        
        // Parse command line arguments
        AgentConfiguration config = parseArguments(args);
        
        try {
            agent.initialize(config);
            agent.startDualSessionTraining();
            
            // Keep running until interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(agent::shutdown));
            
            // Wait for completion
            synchronized (agent) {
                while (agent.running) {
                    agent.wait();
                }
            }
            
        } catch (Exception e) {
            logger.error("Agent failed: " + e.getMessage(), e);
        } finally {
            agent.shutdown();
        }
    }
    
    public void initialize(AgentConfiguration config) {
        this.config = config;
        this.threadPool = Executors.newFixedThreadPool(4);
        this.connectionManager = new MCPConnectionManager(config);
        this.orchestrator = new DualSessionOrchestrator(connectionManager, config);
        
        logger.info("MCP Chess Agent initialized");
        logger.info("Server: " + config.getServerHost() + ":" + config.getServerPort());
        logger.info("Transport: " + config.getTransportType());
        logger.info("Games per session: " + config.getGamesPerSession());
        
        // Display available MCP tools
        displayMCPTools();
    }
    
    private void displayMCPTools() {
        try {
            // Create temporary connection to fetch tools
            MCPConnection tempConnection = connectionManager.createConnection("temp-tools-fetch");
            
            // Send tools/list request
            JsonRpcRequest toolsRequest = new JsonRpcRequest(
                1L,
                "tools/list",
                java.util.Map.of()
            );
            
            com.fasterxml.jackson.databind.JsonNode response = connectionManager
                .sendRequest("temp-tools-fetch", toolsRequest)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            logger.info("\n=== REGISTERED MCP TOOLS ===");
            
            if (response.has("result") && response.get("result").has("tools")) {
                com.fasterxml.jackson.databind.JsonNode tools = response.get("result").get("tools");
                int toolCount = 0;
                
                for (com.fasterxml.jackson.databind.JsonNode tool : tools) {
                    String name = tool.get("name").asText();
                    String description = tool.has("description") ? tool.get("description").asText() : "No description";
                    logger.info(name + " - " + description);
                    toolCount++;
                }
                
                logger.info("Total tools: " + toolCount + "\n");
            } else {
                logger.warn("No tools found in server response\n");
            }
            
            // Close temporary connection
            connectionManager.closeConnection("temp-tools-fetch");
            
        } catch (Exception e) {
            logger.error("Failed to fetch MCP tools dynamically, using fallback: " + e.getMessage(), e);
            displayFallbackTools();
        }
    }
    
    private void displayFallbackTools() {
        logger.info("\n=== MCP TOOLS (FALLBACK) ===");
        logger.info("create_chess_game - Create a new chess game with AI opponent selection");
        logger.info("make_chess_move - Execute a chess move and get AI response");
        logger.info("get_board_state - Get current chess board state and game information");
        logger.info("analyze_position - Get AI analysis of current chess position");
        logger.info("get_legal_moves - Get all legal moves for current position");
        logger.info("get_move_hint - Get AI move suggestion with explanation");
        logger.info("create_tournament - Create games against all 12 AI systems simultaneously");
        logger.info("get_tournament_status - Get status of all games in agent's tournament");
        logger.info("fetch_current_board - Get visual representation of current chess board");
        logger.info("Total tools: 9 (fallback)\n");
    }
    
    public void startDualSessionTraining() {
        running = true;
        
        threadPool.submit(() -> {
            try {
                logger.info("Dual-session training started");
                logger.info("Attempting to connect to MCP server at: " + config.getServerUrl());
                orchestrator.initializeSessions();
                orchestrator.startTrainingLoop();
            } catch (java.net.ConnectException e) {
                logger.error("\n❌ CONNECTION FAILED: Cannot connect to MCP server");
                logger.error("Server URL: " + config.getServerUrl());
                logger.error("\n💡 SOLUTION: Start the MCP server first:");
                logger.error("   java -jar chess-application.jar --mcp --transport=websocket --port=8082");
                logger.error("   OR");
                logger.error("   mvn spring-boot:run -Dspring-boot.run.arguments=\"--mcp --transport=websocket --port=8082\"");
                logger.error("\nError details: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Training loop failed: " + e.getMessage(), e);
            } finally {
                synchronized (this) {
                    running = false;
                    notifyAll();
                }
            }
        });
    }
    
    public void shutdown() {
        logger.info("Shutting down MCP Chess Agent...");
        running = false;
        
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
        
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
        
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }
        
        logger.info("MCP Chess Agent shutdown complete");
    }
    
    private static AgentConfiguration parseArguments(String[] args) {
        AgentConfiguration config = new AgentConfiguration();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) config.setServerHost(args[++i]);
                    break;
                case "--port":
                    if (i + 1 < args.length) config.setServerPort(Integer.parseInt(args[++i]));
                    break;
                case "--transport":
                    if (i + 1 < args.length) config.setTransportType(args[++i]);
                    break;
                case "--games":
                    if (i + 1 < args.length) config.setGamesPerSession(Integer.parseInt(args[++i]));
                    break;
                case "--difficulty":
                    if (i + 1 < args.length) config.setAiDifficulty(Integer.parseInt(args[++i]));
                    break;
                case "--white":
                    if (i + 1 < args.length) config.setWhiteAI(args[++i]);
                    break;
                case "--black":
                    if (i + 1 < args.length) config.setBlackAI(args[++i]);
                    break;
                case "--tournament":
                    config.setTournamentMode(true);
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
            }
        }
        
        return config;
    }
    
    private static void printUsage() {
        logger.info("MCP Chess Agent Usage:");
        logger.info("  --host <host>        MCP server host (default: localhost)");
        logger.info("  --port <port>        MCP server port (default: 8082)");
        logger.info("  --transport <type>   Transport type: websocket|stdio (default: websocket)");
        logger.info("  --games <count>      Games per session (default: 1)");
        logger.info("  --difficulty <level> AI difficulty 1-10 (default: 8)");
        logger.info("  --white <ai>         White AI system (default: AlphaZero)");
        logger.info("  --black <ai>         Black AI system (default: LeelaChessZero)");
        logger.info("  --tournament         Run tournament mode");
        logger.info("  --help               Show this help");
    }
}