package com.example.chess.mcp.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for the MCP Chess Agent
 * Creates dual-session training environment for AI vs AI gameplay
 */
public class MCPChessAgent {
    
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
            System.err.println("Agent failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            agent.shutdown();
        }
    }
    
    public void initialize(AgentConfiguration config) {
        this.config = config;
        this.threadPool = Executors.newFixedThreadPool(4);
        this.connectionManager = new MCPConnectionManager(config);
        this.orchestrator = new DualSessionOrchestrator(connectionManager, config);
        
        System.out.println("MCP Chess Agent initialized");
        System.out.println("Server: " + config.getServerHost() + ":" + config.getServerPort());
        System.out.println("Transport: " + config.getTransportType());
        System.out.println("Games per session: " + config.getGamesPerSession());
        
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
            
            System.out.println("\n=== REGISTERED MCP TOOLS ===");
            
            if (response.has("result") && response.get("result").has("tools")) {
                com.fasterxml.jackson.databind.JsonNode tools = response.get("result").get("tools");
                int toolCount = 0;
                
                for (com.fasterxml.jackson.databind.JsonNode tool : tools) {
                    String name = tool.get("name").asText();
                    String description = tool.has("description") ? tool.get("description").asText() : "No description";
                    System.out.println(name + " - " + description);
                    toolCount++;
                }
                
                System.out.println("Total tools: " + toolCount + "\n");
            } else {
                System.out.println("No tools found in server response\n");
            }
            
            // Close temporary connection
            connectionManager.closeConnection("temp-tools-fetch");
            
        } catch (Exception e) {
            System.err.println("Failed to fetch MCP tools dynamically, using fallback: " + e.getMessage());
            displayFallbackTools();
        }
    }
    
    private void displayFallbackTools() {
        System.out.println("\n=== MCP TOOLS (FALLBACK) ===");
        System.out.println("create_chess_game - Create a new chess game with AI opponent selection");
        System.out.println("make_chess_move - Execute a chess move and get AI response");
        System.out.println("get_board_state - Get current chess board state and game information");
        System.out.println("analyze_position - Get AI analysis of current chess position");
        System.out.println("get_legal_moves - Get all legal moves for current position");
        System.out.println("get_move_hint - Get AI move suggestion with explanation");
        System.out.println("create_tournament - Create games against all 12 AI systems simultaneously");
        System.out.println("get_tournament_status - Get status of all games in agent's tournament");
        System.out.println("fetch_current_board - Get visual representation of current chess board");
        System.out.println("Total tools: 9 (fallback)\n");
    }
    
    public void startDualSessionTraining() {
        running = true;
        
        threadPool.submit(() -> {
            try {
                System.out.println("Dual-session training started");
                System.out.println("Attempting to connect to MCP server at: " + config.getServerUrl());
                orchestrator.initializeSessions();
                orchestrator.startTrainingLoop();
            } catch (java.net.ConnectException e) {
                System.err.println("\n❌ CONNECTION FAILED: Cannot connect to MCP server");
                System.err.println("Server URL: " + config.getServerUrl());
                System.err.println("\n💡 SOLUTION: Start the MCP server first:");
                System.err.println("   java -jar chess-application.jar --mcp --transport=websocket --port=8082");
                System.err.println("   OR");
                System.err.println("   mvn spring-boot:run -Dspring-boot.run.arguments=\"--mcp --transport=websocket --port=8082\"");
                System.err.println("\nError details: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Training loop failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    running = false;
                    notifyAll();
                }
            }
        });
    }
    
    public void shutdown() {
        System.out.println("Shutting down MCP Chess Agent...");
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
        
        System.out.println("MCP Chess Agent shutdown complete");
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
        System.out.println("MCP Chess Agent Usage:");
        System.out.println("  --host <host>        MCP server host (default: localhost)");
        System.out.println("  --port <port>        MCP server port (default: 8082)");
        System.out.println("  --transport <type>   Transport type: websocket|stdio (default: websocket)");
        System.out.println("  --games <count>      Games per session (default: 1)");
        System.out.println("  --difficulty <level> AI difficulty 1-10 (default: 8)");
        System.out.println("  --white <ai>         White AI system (default: AlphaZero)");
        System.out.println("  --black <ai>         Black AI system (default: LeelaChessZero)");
        System.out.println("  --tournament         Run tournament mode");
        System.out.println("  --help               Show this help");
    }
}