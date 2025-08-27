package com.example.chess.mcp;

import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.example.chess.mcp.tools.ChessToolExecutor;
import com.example.chess.mcp.validation.MCPValidationOrchestrator;
import com.example.chess.mcp.validation.ValidationResult;
import com.example.chess.mcp.agent.MCPAgentRegistry;
import com.example.chess.mcp.resources.ChessResourceProvider;
import com.example.chess.mcp.metrics.MCPMetricsService;
import com.example.chess.mcp.notifications.MCPNotificationService;
import com.example.chess.mcp.ratelimit.MCPRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Component
public class ChessMCPServer {
    
    private static final Logger logger = LogManager.getLogger(ChessMCPServer.class);
    
    @Autowired
    private ChessToolExecutor toolExecutor;
    
    @Autowired
    private MCPValidationOrchestrator validator;
    
    @Autowired
    private MCPAgentRegistry agentRegistry;
    
    @Autowired
    private ChessResourceProvider resourceProvider;
    
    @Autowired
    private MCPMetricsService metricsService;
    
    @Autowired
    private MCPNotificationService notificationService;
    
    @Autowired
    private MCPRateLimiter rateLimiter;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public JsonRpcResponse handleJsonRpcRequest(JsonRpcRequest request, String agentId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Rate limiting
            if (!rateLimiter.allowRequest(agentId, "general")) {
                return JsonRpcResponse.error(request.getId(), -32099, "Rate limit exceeded");
            }
            
            // Update agent activity
            agentRegistry.updateAgentActivity(agentId);
            
            logger.debug("Handling request from agent {}: {}", agentId, request.getMethod());
            
            switch (request.getMethod()) {
                case "initialize":
                    return handleInitialize(request);
                case "tools/list":
                    return handleToolsList(request);
                case "resources/list":
                    return handleResourcesList(request);
                case "tools/call":
                    return handleToolCall(request, agentId);
                case "resources/read":
                    return handleResourceRead(request, agentId);
                default:
                    return JsonRpcResponse.methodNotFound(request.getId());
            }
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage(), e);
            metricsService.recordError(agentId, request.getMethod(), e.getClass().getSimpleName());
            return JsonRpcResponse.internalError(request.getId(), e.getMessage());
        } finally {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsService.recordRequest(agentId, request.getMethod(), responseTime);
        }
    }
    
    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        // Register agent if not already registered
        String clientInfo = "unknown";
        if (request.getParams() != null && request.getParams().containsKey("clientInfo")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> clientInfoMap = (Map<String, Object>) request.getParams().get("clientInfo");
            clientInfo = (String) clientInfoMap.getOrDefault("name", "unknown");
        }
        
        Map<String, Object> result = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "resources", Map.of("subscribe", true, "listChanged", true),
                "notifications", Map.of(
                    "chess/game_state", true,
                    "chess/ai_move", true,
                    "chess/training_progress", true
                )
            ),
            "serverInfo", Map.of(
                "name", "chess-mcp-server",
                "version", "1.0.0",
                "description", "Advanced Chess AI MCP Server with 12 AI Systems"
            )
        );
        
        return JsonRpcResponse.success(request.getId(), result);
    }
    
    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        List<Map<String, Object>> tools = Arrays.asList(
            createToolDefinition("create_chess_game", "Create a new chess game with AI opponent selection",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "agentId", Map.of("type", "string", "description", "Agent identifier"),
                        "aiOpponent", Map.of("type", "string", 
                            "enum", Arrays.asList("AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", "MCTS", "Negamax", "OpenAI", "QLearning", "DeepLearning", "CNN", "DQN", "Genetic"),
                            "description", "AI system to play against"),
                        "playerColor", Map.of("type", "string", "enum", Arrays.asList("white", "black"), "description", "Player's piece color"),
                        "difficulty", Map.of("type", "integer", "minimum", 1, "maximum", 10, "description", "AI difficulty level")
                    ),
                    "required", Arrays.asList("agentId", "aiOpponent", "playerColor")
                )
            ),
            createToolDefinition("make_chess_move", "Execute a chess move and get AI response",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sessionId", Map.of("type", "string", "description", "Game session identifier"),
                        "move", Map.of("type", "string", "description", "Move in algebraic notation"),
                        "promotion", Map.of("type", "string", "enum", Arrays.asList("Q", "R", "B", "N"), "description", "Promotion piece")
                    ),
                    "required", Arrays.asList("sessionId", "move")
                )
            ),
            createToolDefinition("get_board_state", "Get current chess board state and game information",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sessionId", Map.of("type", "string", "description", "Game session identifier")
                    ),
                    "required", Arrays.asList("sessionId")
                )
            ),
            createToolDefinition("analyze_position", "Get AI analysis of current chess position",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sessionId", Map.of("type", "string", "description", "Game session identifier"),
                        "depth", Map.of("type", "integer", "minimum", 1, "maximum", 20, "description", "Analysis depth")
                    ),
                    "required", Arrays.asList("sessionId")
                )
            ),
            createToolDefinition("get_legal_moves", "Get all legal moves for current position",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sessionId", Map.of("type", "string", "description", "Game session identifier")
                    ),
                    "required", Arrays.asList("sessionId")
                )
            ),
            createToolDefinition("get_move_hint", "Get AI move suggestion with explanation",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "sessionId", Map.of("type", "string", "description", "Game session identifier"),
                        "hintLevel", Map.of("type", "string", "enum", Arrays.asList("beginner", "intermediate", "advanced", "master"), "description", "Hint complexity level")
                    ),
                    "required", Arrays.asList("sessionId")
                )
            ),
            createToolDefinition("create_tournament", "Create games against all 12 AI systems simultaneously",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "agentId", Map.of("type", "string", "description", "Agent identifier"),
                        "playerColor", Map.of("type", "string", "enum", Arrays.asList("white", "black"), "description", "Player's piece color"),
                        "difficulty", Map.of("type", "integer", "minimum", 1, "maximum", 10, "description", "AI difficulty level")
                    ),
                    "required", Arrays.asList("agentId", "playerColor")
                )
            ),
            createToolDefinition("get_tournament_status", "Get status of all games in agent's tournament",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "agentId", Map.of("type", "string", "description", "Agent identifier")
                    ),
                    "required", Arrays.asList("agentId")
                )
            )
        );
        
        return JsonRpcResponse.success(request.getId(), Map.of("tools", tools));
    }
    
    private JsonRpcResponse handleResourcesList(JsonRpcRequest request) {
        List<Map<String, Object>> resources = Arrays.asList(
            Map.of(
                "uri", "chess://ai-systems",
                "name", "Available AI Systems",
                "description", "List of all 12 AI chess engines with capabilities",
                "mimeType", "application/json"
            ),
            Map.of(
                "uri", "chess://opening-book",
                "name", "Chess Opening Database",
                "description", "Professional opening book with 100+ grandmaster openings",
                "mimeType", "application/json"
            ),
            Map.of(
                "uri", "chess://game-sessions",
                "name", "Active Game Sessions",
                "description", "Currently active chess game sessions",
                "mimeType", "application/json"
            ),
            Map.of(
                "uri", "chess://training-stats",
                "name", "AI Training Statistics",
                "description", "Performance metrics and training progress for all AI systems",
                "mimeType", "application/json"
            ),
            Map.of(
                "uri", "chess://tactical-patterns",
                "name", "Chess Tactical Patterns",
                "description", "Database of chess tactical motifs and patterns",
                "mimeType", "application/json"
            )
        );
        
        return JsonRpcResponse.success(request.getId(), Map.of("resources", resources));
    }
    
    private JsonRpcResponse handleToolCall(JsonRpcRequest request, String agentId) {
        Map<String, Object> params = request.getParams();
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        
        // Add agentId to arguments if not present
        if (!arguments.containsKey("agentId")) {
            arguments = new HashMap<>(arguments);
            arguments.put("agentId", agentId);
        }
        
        // Validate request
        ValidationResult validation = validator.validateToolCall(agentId, toolName, arguments);
        if (!validation.isValid()) {
            return JsonRpcResponse.error(request.getId(), -32602, validation.getError());
        }
        
        // Execute tool (notifications now handled in ChessGameSession)
        ChessToolExecutor.ToolResult result = toolExecutor.execute(toolName, arguments);
        
        if (result.isSuccess()) {
            try {
                List<Map<String, Object>> content = Arrays.asList(
                    Map.of("type", "text", "text", result.getMessage()),
                    Map.of("type", "resource", "resource", Map.of(
                        "uri", "chess://tool-result/" + toolName,
                        "text", objectMapper.writeValueAsString(result.getData())
                    ))
                );
                
                return JsonRpcResponse.success(request.getId(), Map.of(
                    "content", content,
                    "isError", false
                ));
            } catch (Exception e) {
                logger.error("Error serializing tool result: {}", e.getMessage());
                return JsonRpcResponse.error(request.getId(), -32603, "Error serializing result: " + e.getMessage());
            }
        } else {
            return JsonRpcResponse.error(request.getId(), -32001, result.getMessage());
        }
    }
    
    private JsonRpcResponse handleResourceRead(JsonRpcRequest request, String agentId) {
        try {
            Map<String, Object> params = request.getParams();
            String uri = (String) params.get("uri");
            
            ChessResourceProvider.Resource resource = resourceProvider.getResource(agentId, uri);
            
            return JsonRpcResponse.success(request.getId(), Map.of(
                "contents", Arrays.asList(
                    Map.of(
                        "uri", resource.getUri(),
                        "mimeType", resource.getMimeType(),
                        "text", resource.getContent()
                    )
                )
            ));
        } catch (Exception e) {
            logger.error("Error reading resource: {}", e.getMessage());
            return JsonRpcResponse.error(request.getId(), -32001, "Resource not found: " + e.getMessage());
        }
    }
    
    private Map<String, Object> createToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", inputSchema
        );
    }
    
    public Map<String, Object> getServerStatus() {
        MCPMetricsService.MCPMetrics metrics = metricsService.getMetrics();
        
        return Map.of(
            "activeAgents", agentRegistry.getActiveAgentCount(),
            "totalRequests", metrics.getTotalRequests(),
            "totalErrors", metrics.getTotalErrors(),
            "uptime", java.time.Duration.between(metrics.getStartTime(), java.time.LocalDateTime.now()).toString(),
            "toolCallCounts", metrics.getToolCallCounts(),
            "agentRequestCounts", metrics.getAgentRequestCounts()
        );
    }
    
    public void registerAgent(String agentId, String clientInfo, String transport) {
        agentRegistry.registerAgent(clientInfo, transport);
        logger.info("Agent {} registered via MCP server", agentId);
    }
    
    public void unregisterAgent(String agentId) {
        agentRegistry.removeAgent(agentId);
        logger.info("Agent {} unregistered from MCP server", agentId);
    }
}