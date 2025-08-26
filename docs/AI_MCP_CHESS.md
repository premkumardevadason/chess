# AI MCP Chess - Model Context Protocol Design

## Overview

This document outlines the design for exposing our Chess application's 12 AI systems through the Model Context Protocol (MCP), enabling external agents to engage in full-fledged chess games with advanced AI opponents using JSON-RPC over stdio or WebSockets.

**The existing Spring Boot Chess application serves as the MCP Server**, providing chess game capabilities to external MCP clients (AI agents, applications, etc.) through standardized JSON-RPC 2.0 protocol communication.

## MCP Chess Protocol Architecture

### Core Principles
- **JSON-RPC 2.0 Compliance**: Strict adherence to JSON-RPC 2.0 specification
- **SOLID Design Patterns**: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion
- **MCP Protocol Flow**: initialize → list_tools → list_resources → call_tool
- **Stateful Multi-Agent Support**: Concurrent chess games for multiple MCP clients
- **Session Isolation**: Each agent maintains independent game state and AI opponent
- **AI System Selection**: Clients can choose from 12 different AI opponents
- **Real-time Interaction**: Immediate move validation and response
- **Training Integration**: AI systems continue learning from MCP games
- **Standard Chess Protocol**: FEN notation and algebraic notation support

## Stateful MCP Design for Concurrent Multi-Agent Support

### Multi-Agent Architecture Overview

**The MCP Chess Server is inherently stateful and designed to support multiple concurrent agents**, each maintaining independent chess game sessions with isolated state management.

#### Key Stateful Characteristics:
- **Per-Agent Sessions**: Each MCP client (agent) can create and manage multiple chess game sessions
- **Session Isolation**: Game state, move history, and AI interactions are completely isolated between agents
- **Concurrent Gameplay**: Multiple agents can play simultaneous games without interference
- **Resource Sharing**: AI systems and chess engine are shared efficiently across all agents
- **State Persistence**: Game sessions persist across MCP reconnections

### Concurrent Multi-Agent Session Management

#### Session Architecture
```java
package com.example.chess.mcp.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class MCPSessionManager {
    
    private static final Logger logger = LogManager.getLogger(MCPSessionManager.class);
    
    // Thread-safe session storage for concurrent access
    private final ConcurrentHashMap<String, ChessGameSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> agentSessions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock();
    
    // Maximum concurrent sessions per agent
    private static final int MAX_SESSIONS_PER_AGENT = 10;
    private static final int MAX_TOTAL_SESSIONS = 1000;
    
    public String createSession(String agentId, String aiOpponent, String playerColor, int difficulty) {
        sessionLock.writeLock().lock();
        try {
            // Validate session limits
            validateSessionLimits(agentId);
            
            String sessionId = generateSessionId(agentId);
            ChessGame game = new ChessGame();
            ChessAI ai = selectAI(aiOpponent, difficulty);
            
            ChessGameSession session = new ChessGameSession(
                sessionId, agentId, game, ai, playerColor, difficulty
            );
            
            activeSessions.put(sessionId, session);
            agentSessions.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            
            logger.info("Created session {} for agent {} with AI {}", sessionId, agentId, aiOpponent);
            return sessionId;
            
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    public ChessGameSession getSession(String sessionId) {
        sessionLock.readLock().lock();
        try {
            return activeSessions.get(sessionId);
        } finally {
            sessionLock.readLock().unlock();
        }
    }
    
    public List<ChessGameSession> getAgentSessions(String agentId) {
        sessionLock.readLock().lock();
        try {
            Set<String> sessionIds = agentSessions.get(agentId);
            if (sessionIds == null) return Collections.emptyList();
            
            return sessionIds.stream()
                .map(activeSessions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            sessionLock.readLock().unlock();
        }
    }
    
    private void validateSessionLimits(String agentId) {
        if (activeSessions.size() >= MAX_TOTAL_SESSIONS) {
            throw new IllegalStateException("Maximum total sessions reached: " + MAX_TOTAL_SESSIONS);
        }
        
        Set<String> agentSessionIds = agentSessions.get(agentId);
        if (agentSessionIds != null && agentSessionIds.size() >= MAX_SESSIONS_PER_AGENT) {
            throw new IllegalStateException("Maximum sessions per agent reached: " + MAX_SESSIONS_PER_AGENT);
        }
    }
}
```

#### Multi-Agent Game Session Model
```java
package com.example.chess.mcp.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

public class ChessGameSession {
    
    private static final Logger logger = LogManager.getLogger(ChessGameSession.class);
    
    private final String sessionId;
    private final String agentId;  // MCP client identifier
    private final ChessGame game;
    private final ChessAI ai;
    private final String playerColor;
    private final int difficulty;
    private final LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    
    // Thread-safe access to game state
    private final ReentrantLock gameLock = new ReentrantLock();
    
    // Session statistics
    private int movesPlayed = 0;
    private double averageThinkingTime = 0.0;
    private String gameStatus = "active";
    
    public ChessGameSession(String sessionId, String agentId, ChessGame game, 
                           ChessAI ai, String playerColor, int difficulty) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.game = game;
        this.ai = ai;
        this.playerColor = playerColor;
        this.difficulty = difficulty;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }
    
    public synchronized MoveResult makeMove(String move) {
        gameLock.lock();
        try {
            logger.debug("Agent {} making move {} in session {}", agentId, move, sessionId);
            
            // Validate and execute player move
            boolean moveValid = game.makeMove(move);
            if (!moveValid) {
                return MoveResult.invalid(move, game.getLegalMoves());
            }
            
            movesPlayed++;
            lastActivity = LocalDateTime.now();
            
            // Check game status after player move
            if (game.isGameOver()) {
                gameStatus = game.getGameStatus();
                return MoveResult.gameOver(move, gameStatus, game.getFEN());
            }
            
            // Get AI response
            long startTime = System.currentTimeMillis();
            String aiMove = ai.getMove(game);
            long thinkingTime = System.currentTimeMillis() - startTime;
            
            // Execute AI move
            game.makeMove(aiMove);
            movesPlayed++;
            
            // Update statistics
            updateThinkingTimeAverage(thinkingTime);
            
            // Check game status after AI move
            if (game.isGameOver()) {
                gameStatus = game.getGameStatus();
                return MoveResult.gameOver(move, aiMove, gameStatus, game.getFEN(), thinkingTime);
            }
            
            return MoveResult.success(move, aiMove, game.getFEN(), thinkingTime);
            
        } finally {
            gameLock.unlock();
        }
    }
    
    public synchronized GameState getGameState() {
        gameLock.lock();
        try {
            return new GameState(
                sessionId, agentId, game.getFEN(), game.getMoveHistory(),
                game.getCurrentTurn(), gameStatus, movesPlayed, averageThinkingTime
            );
        } finally {
            gameLock.unlock();
        }
    }
    
    // Getters and utility methods...
}
```

### Concurrent Agent Management

#### Agent Registry and Tracking
```java
package com.example.chess.mcp.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MCPAgentRegistry {
    
    private static final Logger logger = LogManager.getLogger(MCPAgentRegistry.class);
    
    private final ConcurrentHashMap<String, MCPAgent> activeAgents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> agentLastActivity = new ConcurrentHashMap<>();
    
    public String registerAgent(String clientInfo, String transport) {
        String agentId = generateAgentId();
        MCPAgent agent = new MCPAgent(agentId, clientInfo, transport);
        
        activeAgents.put(agentId, agent);
        agentLastActivity.put(agentId, LocalDateTime.now());
        
        logger.info("Registered new MCP agent: {} via {}", agentId, transport);
        return agentId;
    }
    
    public void updateAgentActivity(String agentId) {
        agentLastActivity.put(agentId, LocalDateTime.now());
    }
    
    public List<MCPAgent> getActiveAgents() {
        return new ArrayList<>(activeAgents.values());
    }
    
    public int getActiveAgentCount() {
        return activeAgents.size();
    }
    
    // Cleanup inactive agents (configurable timeout)
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupInactiveAgents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        
        agentLastActivity.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                String agentId = entry.getKey();
                activeAgents.remove(agentId);
                logger.info("Removed inactive agent: {}", agentId);
                return true;
            }
            return false;
        });
    }
}
```

### Multi-Agent Resource Isolation

#### Agent-Specific Resource Access
```java
package com.example.chess.mcp.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class ChessResourceProvider {
    
    private static final Logger logger = LogManager.getLogger(ChessResourceProvider.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private MCPAgentRegistry agentRegistry;
    
    public Resource getResource(String agentId, String uri) {
        logger.debug("Agent {} requesting resource: {}", agentId, uri);
        
        switch (uri) {
            case "chess://game-sessions":
                return getAgentGameSessions(agentId);
            case "chess://ai-systems":
                return getAvailableAISystems();
            case "chess://opening-book":
                return getOpeningBook();
            case "chess://training-stats":
                return getTrainingStats();
            case "chess://tactical-patterns":
                return getTacticalPatterns();
            default:
                if (uri.startsWith("chess://game-sessions/")) {
                    String sessionId = uri.substring("chess://game-sessions/".length());
                    return getSpecificGameSession(agentId, sessionId);
                }
                throw new IllegalArgumentException("Unknown resource URI: " + uri);
        }
    }
    
    private Resource getAgentGameSessions(String agentId) {
        List<ChessGameSession> sessions = sessionManager.getAgentSessions(agentId);
        
        Map<String, Object> sessionData = sessions.stream()
            .collect(Collectors.toMap(
                ChessGameSession::getSessionId,
                session -> Map.of(
                    "sessionId", session.getSessionId(),
                    "aiOpponent", session.getAI().getClass().getSimpleName(),
                    "playerColor", session.getPlayerColor(),
                    "gameStatus", session.getGameStatus(),
                    "movesPlayed", session.getMovesPlayed(),
                    "createdAt", session.getCreatedAt().toString()
                )
            ));
        
        return new Resource("chess://game-sessions", "application/json", 
                          JsonUtils.toJson(Map.of("sessions", sessionData)));
    }
    
    private Resource getSpecificGameSession(String agentId, String sessionId) {
        ChessGameSession session = sessionManager.getSession(sessionId);
        
        // Verify agent owns this session
        if (session == null || !session.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("Session not found or access denied: " + sessionId);
        }
        
        GameState gameState = session.getGameState();
        return new Resource("chess://game-sessions/" + sessionId, "application/json", 
                          JsonUtils.toJson(gameState));
    }
}
```

### Concurrent Performance Optimization

#### AI System Load Balancing
```java
package com.example.chess.mcp.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ConcurrentAIManager {
    
    private static final Logger logger = LogManager.getLogger(ConcurrentAIManager.class);
    
    // Dedicated thread pools for different AI types
    private final ExecutorService neuralNetworkPool = Executors.newFixedThreadPool(4);
    private final ExecutorService classicalEnginePool = Executors.newFixedThreadPool(8);
    private final ExecutorService machineLearningPool = Executors.newFixedThreadPool(6);
    
    private final ConcurrentHashMap<String, AtomicInteger> aiSystemLoad = new ConcurrentHashMap<>();
    
    public CompletableFuture<String> getAIMoveAsync(ChessAI ai, ChessGame game, String sessionId) {
        String aiType = ai.getClass().getSimpleName();
        
        // Track AI system load
        aiSystemLoad.computeIfAbsent(aiType, k -> new AtomicInteger(0)).incrementAndGet();
        
        ExecutorService executor = selectExecutorForAI(aiType);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("AI {} processing move for session {}", aiType, sessionId);
                long startTime = System.currentTimeMillis();
                
                String move = ai.getMove(game);
                
                long duration = System.currentTimeMillis() - startTime;
                logger.debug("AI {} completed move {} in {}ms for session {}", 
                           aiType, move, duration, sessionId);
                
                return move;
                
            } finally {
                aiSystemLoad.get(aiType).decrementAndGet();
            }
        }, executor);
    }
    
    private ExecutorService selectExecutorForAI(String aiType) {
        if (aiType.contains("Neural") || aiType.contains("AlphaZero") || aiType.contains("Leela")) {
            return neuralNetworkPool;
        } else if (aiType.contains("Negamax") || aiType.contains("MCTS")) {
            return classicalEnginePool;
        } else {
            return machineLearningPool;
        }
    }
    
    public Map<String, Integer> getAISystemLoad() {
        return aiSystemLoad.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
    }
}
```

### Multi-Agent Statistics and Monitoring

#### Concurrent Session Metrics
```java
package com.example.chess.mcp.monitoring;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class MCPMetricsService {
    
    private static final Logger logger = LogManager.getLogger(MCPMetricsService.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private MCPAgentRegistry agentRegistry;
    
    @Autowired
    private ConcurrentAIManager aiManager;
    
    public MCPServerMetrics getServerMetrics() {
        return MCPServerMetrics.builder()
            .activeAgents(agentRegistry.getActiveAgentCount())
            .activeSessions(sessionManager.getActiveSessionCount())
            .totalGamesPlayed(sessionManager.getTotalGamesPlayed())
            .averageSessionDuration(sessionManager.getAverageSessionDuration())
            .aiSystemLoad(aiManager.getAISystemLoad())
            .concurrentGamesPerAI(calculateConcurrentGamesPerAI())
            .build();
    }
    
    private Map<String, Integer> calculateConcurrentGamesPerAI() {
        return sessionManager.getAllActiveSessions().stream()
            .collect(Collectors.groupingBy(
                session -> session.getAI().getClass().getSimpleName(),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
    }
    
    @EventListener
    public void handleGameCreated(GameCreatedEvent event) {
        logger.info("New game created - Agent: {}, Session: {}, AI: {}, Total Active: {}", 
                   event.getAgentId(), event.getSessionId(), event.getAiOpponent(),
                   sessionManager.getActiveSessionCount());
    }
    
    @EventListener
    public void handleGameCompleted(GameCompletedEvent event) {
        logger.info("Game completed - Session: {}, Duration: {}min, Moves: {}, Result: {}", 
                   event.getSessionId(), event.getDurationMinutes(), 
                   event.getMovesPlayed(), event.getResult());
    }
}
```

## JSON-RPC 2.0 Specification Compliance

### Required Fields
- **jsonrpc**: Must be exactly "2.0"
- **method**: String containing method name
- **id**: Unique identifier for request/response correlation
- **params**: Parameters object (optional)
- **result**: Success response data (mutually exclusive with error)
- **error**: Error object with code, message, data (mutually exclusive with result)

### Error Codes
- **-32700**: Parse error (Invalid JSON)
- **-32600**: Invalid Request (Invalid JSON-RPC)
- **-32601**: Method not found
- **-32602**: Invalid params
- **-32603**: Internal error
- **-32000 to -32099**: Server error range (MCP-specific errors)

## MCP Protocol Flow

### 1. Initialize (Handshake & Feature Discovery)

#### Client Initialize Request
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {},
      "resources": {}
    },
    "clientInfo": {
      "name": "chess-ai-client",
      "version": "1.0.0"
    }
  }
}
```

#### Server Initialize Response
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": true
      },
      "resources": {
        "subscribe": true,
        "listChanged": true
      },
      "notifications": {
        "chess/game_state": true,
        "chess/ai_move": true,
        "chess/training_progress": true
      }
    },
    "serverInfo": {
      "name": "chess-mcp-server",
      "version": "1.0.0",
      "description": "Advanced Chess AI MCP Server with 12 AI Systems"
    }
  }
}
```

### 2. List Tools (Available Actions)

#### tools/list Request
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

#### tools/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "create_chess_game",
        "description": "Create a new chess game with AI opponent selection",
        "inputSchema": {
          "type": "object",
          "properties": {
            "aiOpponent": {
              "type": "string",
              "enum": ["AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", "MCTS", "Negamax", "OpenAI", "QLearning", "DeepLearning", "CNN", "DQN", "Genetic"],
              "description": "AI system to play against"
            },
            "playerColor": {
              "type": "string",
              "enum": ["white", "black"],
              "description": "Player's piece color"
            },
            "difficulty": {
              "type": "integer",
              "minimum": 1,
              "maximum": 10,
              "description": "AI difficulty level"
            }
          },
          "required": ["aiOpponent", "playerColor"]
        }
      },
      {
        "name": "make_chess_move",
        "description": "Execute a chess move and get AI response",
        "inputSchema": {
          "type": "object",
          "properties": {
            "sessionId": {"type": "string", "description": "Game session identifier"},
            "move": {"type": "string", "description": "Move in algebraic notation (e.g., e4, Nf3, O-O)"},
            "promotion": {"type": "string", "enum": ["Q", "R", "B", "N"], "description": "Promotion piece for pawn promotion"}
          },
          "required": ["sessionId", "move"]
        }
      },
      {
        "name": "get_board_state",
        "description": "Get current chess board state and game information",
        "inputSchema": {
          "type": "object",
          "properties": {
            "sessionId": {"type": "string", "description": "Game session identifier"}
          },
          "required": ["sessionId"]
        }
      },
      {
        "name": "analyze_position",
        "description": "Get AI analysis of current chess position",
        "inputSchema": {
          "type": "object",
          "properties": {
            "sessionId": {"type": "string", "description": "Game session identifier"},
            "depth": {"type": "integer", "minimum": 1, "maximum": 20, "description": "Analysis depth"}
          },
          "required": ["sessionId"]
        }
      },
      {
        "name": "get_legal_moves",
        "description": "Get all legal moves for current position",
        "inputSchema": {
          "type": "object",
          "properties": {
            "sessionId": {"type": "string", "description": "Game session identifier"}
          },
          "required": ["sessionId"]
        }
      },
      {
        "name": "get_move_hint",
        "description": "Get AI move suggestion with explanation",
        "inputSchema": {
          "type": "object",
          "properties": {
            "sessionId": {"type": "string", "description": "Game session identifier"},
            "hintLevel": {"type": "string", "enum": ["beginner", "intermediate", "advanced", "master"], "description": "Hint complexity level"}
          },
          "required": ["sessionId"]
        }
      },
      {
        "name": "create_tournament",
        "description": "Create games against all 12 AI systems simultaneously",
        "inputSchema": {
          "type": "object",
          "properties": {
            "agentId": {"type": "string", "description": "Agent identifier"},
            "playerColor": {"type": "string", "enum": ["white", "black"], "description": "Player's piece color"},
            "difficulty": {"type": "integer", "minimum": 1, "maximum": 10, "description": "AI difficulty level"}
          },
          "required": ["agentId", "playerColor"]
        }
      },
      {
        "name": "get_tournament_status",
        "description": "Get status of all games in agent's tournament",
        "inputSchema": {
          "type": "object",
          "properties": {
            "agentId": {"type": "string", "description": "Agent identifier"}
          },
          "required": ["agentId"]
        }
      }
    ]
  }
}
```

### 3. List Resources (Available Data)

#### resources/list Request
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "resources/list",
  "params": {}
}
```

#### resources/list Response
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "resources": [
      {
        "uri": "chess://ai-systems",
        "name": "Available AI Systems",
        "description": "List of all 12 AI chess engines with capabilities",
        "mimeType": "application/json"
      },
      {
        "uri": "chess://opening-book",
        "name": "Chess Opening Database",
        "description": "Professional opening book with 100+ grandmaster openings",
        "mimeType": "application/json"
      },
      {
        "uri": "chess://game-sessions",
        "name": "Active Game Sessions",
        "description": "Currently active chess game sessions",
        "mimeType": "application/json"
      },
      {
        "uri": "chess://training-stats",
        "name": "AI Training Statistics",
        "description": "Performance metrics and training progress for all AI systems",
        "mimeType": "application/json"
      },
      {
        "uri": "chess://tactical-patterns",
        "name": "Chess Tactical Patterns",
        "description": "Database of chess tactical motifs and patterns",
        "mimeType": "application/json"
      }
    ]
  }
}
```

### 4. Call Tool (Execute Actions)

#### tools/call Request - Create Game
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "create_chess_game",
    "arguments": {
      "aiOpponent": "AlphaZero",
      "playerColor": "white",
      "difficulty": 7
    }
  }
}
```

#### tools/call Response - Create Game
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Chess game created successfully! You are playing as White against AlphaZero AI (difficulty 7).\n\nSession ID: chess-session-uuid-12345\nStarting Position: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\n\nYour turn! Make your opening move."
      },
      {
        "type": "resource",
        "resource": {
          "uri": "chess://game-sessions/chess-session-uuid-12345",
          "text": "{\"sessionId\": \"chess-session-uuid-12345\", \"gameState\": \"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\", \"aiOpponent\": \"AlphaZero\", \"playerColor\": \"white\", \"status\": \"active\", \"difficulty\": 7}"
        }
      }
    ],
    "isError": false
  }
}
```

#### tools/call Request - Make Move
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "make_chess_move",
    "arguments": {
      "sessionId": "chess-session-uuid-12345",
      "move": "e4"
    }
  }
}
```

#### tools/call Response - Make Move
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Move executed: e4\nAlphaZero responds: e5\n\nCurrent position: rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2\n\nGame Status: Active\nYour turn!"
      },
      {
        "type": "resource",
        "resource": {
          "uri": "chess://game-sessions/chess-session-uuid-12345",
          "text": "{\"sessionId\": \"chess-session-uuid-12345\", \"gameState\": \"rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2\", \"moveHistory\": [\"e4\", \"e5\"], \"currentTurn\": \"white\", \"gameStatus\": \"active\", \"aiResponse\": \"e5\", \"moveTime\": 1.2, \"evaluation\": 0.15}"
        }
      }
    ],
    "isError": false
  }
}
```

## SOLID Design Principles Implementation

### Single Responsibility Principle (SRP)
Each class has one reason to change:
- `MCPProtocolHandler`: Handles JSON-RPC protocol parsing/formatting
- `ChessToolExecutor`: Executes chess-specific tool calls
- `GameSessionManager`: Manages chess game sessions
- `AISystemRegistry`: Manages AI system selection and configuration
- `ResourceProvider`: Provides access to chess resources

### Open/Closed Principle (OCP)
System is open for extension, closed for modification:
- `ChessTool` interface allows new tools without changing existing code
- `AISystem` interface enables new AI implementations
- `ResourceProvider` interface supports new resource types

### Liskov Substitution Principle (LSP)
Subtypes are substitutable for base types:
- All `ChessAI` implementations can be used interchangeably
- All `ChessTool` implementations follow same contract
- All `ResourceProvider` implementations provide consistent interface

### Interface Segregation Principle (ISP)
Clients depend only on interfaces they use:
- `ToolExecutor` interface separate from `ResourceProvider`
- `GameStateProvider` separate from `MoveValidator`
- `NotificationSender` separate from `SessionManager`

### Dependency Inversion Principle (DIP)
High-level modules don't depend on low-level modules:
- `MCPServer` depends on `ToolExecutor` abstraction
- `ChessToolExecutor` depends on `ChessGame` interface
- `AISystemRegistry` depends on `ChessAI` abstraction

## SOLID Architecture Implementation

```java
// Single Responsibility Principle
public interface MCPProtocolHandler {
    JsonRpcResponse handleRequest(JsonRpcRequest request);
}

public interface ToolExecutor {
    ToolResult execute(String toolName, Map<String, Object> arguments);
}

public interface ResourceProvider {
    Resource getResource(String uri);
    List<ResourceInfo> listResources();
}

// Open/Closed Principle
public abstract class ChessTool {
    protected abstract ToolResult executeInternal(Map<String, Object> args);
    protected abstract JsonSchema getInputSchema();
    
    public final ToolResult execute(Map<String, Object> args) {
        validateInput(args);
        return executeInternal(args);
    }
}

public class CreateGameTool extends ChessTool {
    @Override
    protected ToolResult executeInternal(Map<String, Object> args) {
        // Implementation
    }
}

// Interface Segregation Principle
public interface GameStateProvider {
    GameState getGameState(String sessionId);
}

public interface MoveValidator {
    boolean isValidMove(String sessionId, String move);
}

public interface NotificationSender {
    void sendNotification(String method, Object params);
}

// Dependency Inversion Principle
@Component
public class ChessMCPServer implements MCPProtocolHandler {
    private final ToolExecutor toolExecutor;
    private final ResourceProvider resourceProvider;
    private final NotificationSender notificationSender;
    
    public ChessMCPServer(ToolExecutor toolExecutor, 
                         ResourceProvider resourceProvider,
                         NotificationSender notificationSender) {
        this.toolExecutor = toolExecutor;
        this.resourceProvider = resourceProvider;
        this.notificationSender = notificationSender;
    }
}
```

## MCP Notifications

### AI Move Notifications
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/chess/ai_move",
  "params": {
    "sessionId": "uuid",
    "move": "Nf3",
    "gameState": "fen_notation",
    "thinkingTime": 2.1,
    "evaluation": 0.15,
    "gameStatus": "active"
  }
}
```

### Training Progress Notifications
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/chess/training_progress",
  "params": {
    "aiName": "AlphaZero",
    "progress": 0.75,
    "gamesPlayed": 1000,
    "currentElo": 2650
  }
}
```

### Game State Change Notifications
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/chess/game_state",
  "params": {
    "sessionId": "uuid",
    "gameState": "fen_notation",
    "status": "checkmate",
    "winner": "white",
    "reason": "checkmate"
  }
}
```

## Server-Side Validation

### Never Trust the Agent - Always Validate

**Core Principle**: Never trust AI agents blindly. The MCP Chess Server implements comprehensive server-side validation to ensure security, data integrity, and system stability.

#### 1. Input Schema Validation

##### JSON Schema Enforcement
```java
package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class MCPInputValidator {
    
    private static final Logger logger = LogManager.getLogger(MCPInputValidator.class);
    
    public ValidationResult validateToolCall(String toolName, Map<String, Object> arguments) {
        logger.debug("Validating tool call: {} with args: {}", toolName, arguments);
        
        switch (toolName) {
            case "create_chess_game":
                return validateCreateGameInput(arguments);
            case "make_chess_move":
                return validateMakeMoveInput(arguments);
            case "get_board_state":
                return validateGetBoardStateInput(arguments);
            default:
                return ValidationResult.invalid("Unknown tool: " + toolName);
        }
    }
    
    private ValidationResult validateCreateGameInput(Map<String, Object> args) {
        // Validate AI opponent
        String aiOpponent = (String) args.get("aiOpponent");
        if (!isValidAIOpponent(aiOpponent)) {
            return ValidationResult.invalid("Invalid AI opponent: " + aiOpponent);
        }
        
        // Validate player color
        String playerColor = (String) args.get("playerColor");
        if (!"white".equals(playerColor) && !"black".equals(playerColor)) {
            return ValidationResult.invalid("Invalid player color: " + playerColor);
        }
        
        // Validate difficulty
        Integer difficulty = (Integer) args.get("difficulty");
        if (difficulty != null && (difficulty < 1 || difficulty > 10)) {
            return ValidationResult.invalid("Invalid difficulty: " + difficulty);
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMakeMoveInput(Map<String, Object> args) {
        // Validate session ID format
        String sessionId = (String) args.get("sessionId");
        if (!isValidSessionId(sessionId)) {
            return ValidationResult.invalid("Invalid session ID format: " + sessionId);
        }
        
        // Validate move format (algebraic notation)
        String move = (String) args.get("move");
        if (!isValidMoveFormat(move)) {
            return ValidationResult.invalid("Invalid move format: " + move);
        }
        
        // Validate promotion piece
        String promotion = (String) args.get("promotion");
        if (promotion != null && !isValidPromotionPiece(promotion)) {
            return ValidationResult.invalid("Invalid promotion piece: " + promotion);
        }
        
        return ValidationResult.valid();
    }
    
    private boolean isValidAIOpponent(String aiOpponent) {
        Set<String> validAI = Set.of("AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                                   "MCTS", "Negamax", "OpenAI", "QLearning", 
                                   "DeepLearning", "CNN", "DQN", "Genetic");
        return validAI.contains(aiOpponent);
    }
    
    private boolean isValidSessionId(String sessionId) {
        // UUID format validation
        return sessionId != null && sessionId.matches("^[a-fA-F0-9-]{36}$");
    }
    
    private boolean isValidMoveFormat(String move) {
        // Algebraic notation validation (e4, Nf3, O-O, etc.)
        return move != null && move.matches("^[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?$|^O-O(-O)?[+#]?$");
    }
    
    private boolean isValidPromotionPiece(String promotion) {
        return Set.of("Q", "R", "B", "N").contains(promotion);
    }
}
```

#### 2. Move Legality Validation

##### Chess Rule Enforcement
```java
package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ChessMoveValidator {
    
    private static final Logger logger = LogManager.getLogger(ChessMoveValidator.class);
    
    // Dangerous patterns that should never be allowed
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "DROP", "DELETE", "UPDATE", "INSERT", "CREATE", "ALTER", "TRUNCATE",
        "EXEC", "EXECUTE", "SYSTEM", "CMD", "SHELL", "SCRIPT", "EVAL"
    );
    
    public MoveValidationResult validateMove(String sessionId, String move, ChessGame game) {
        logger.debug("Validating move '{}' for session {}", move, sessionId);
        
        // 1. Security validation - prevent injection attacks
        if (containsForbiddenPatterns(move)) {
            logger.warn("Blocked potentially malicious move: {}", move);
            return MoveValidationResult.forbidden("Move contains forbidden patterns");
        }
        
        // 2. Format validation
        if (!isValidChessNotation(move)) {
            return MoveValidationResult.invalid("Invalid chess notation: " + move);
        }
        
        // 3. Game state validation
        if (game.isGameOver()) {
            return MoveValidationResult.invalid("Game is already finished");
        }
        
        // 4. Chess rule validation using existing ChessRuleValidator
        if (!game.isLegalMove(move)) {
            List<String> legalMoves = game.getLegalMoves();
            return MoveValidationResult.illegal(move, legalMoves);
        }
        
        // 5. Additional chess-specific validations
        if (!validateChessSpecificRules(move, game)) {
            return MoveValidationResult.invalid("Move violates chess rules");
        }
        
        return MoveValidationResult.valid();
    }
    
    private boolean containsForbiddenPatterns(String move) {
        String upperMove = move.toUpperCase();
        return FORBIDDEN_PATTERNS.stream().anyMatch(upperMove::contains);
    }
    
    private boolean isValidChessNotation(String move) {
        // Comprehensive chess notation validation
        return move.matches("^[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](=[QRBN])?[+#]?$|^O-O(-O)?[+#]?$");
    }
    
    private boolean validateChessSpecificRules(String move, ChessGame game) {
        // Additional chess rule validations
        // - Piece movement patterns
        // - Check/checkmate conditions
        // - Castling availability
        // - En passant validity
        return true; // Implemented by existing ChessRuleValidator
    }
}
```

#### 3. Resource Scope Validation

##### Agent Access Control
```java
package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ResourceScopeValidator {
    
    private static final Logger logger = LogManager.getLogger(ResourceScopeValidator.class);
    
    public AccessValidationResult validateResourceAccess(String agentId, String resourceUri) {
        logger.debug("Validating resource access for agent {} to {}", agentId, resourceUri);
        
        // 1. URI format validation
        if (!isValidResourceUri(resourceUri)) {
            return AccessValidationResult.invalid("Invalid resource URI format");
        }
        
        // 2. Agent-specific resource access
        if (resourceUri.startsWith("chess://game-sessions/")) {
            return validateGameSessionAccess(agentId, resourceUri);
        }
        
        // 3. Global resource access (allowed for all agents)
        if (isGlobalResource(resourceUri)) {
            return AccessValidationResult.allowed();
        }
        
        // 4. Forbidden resource patterns
        if (isForbiddenResource(resourceUri)) {
            logger.warn("Agent {} attempted to access forbidden resource: {}", agentId, resourceUri);
            return AccessValidationResult.forbidden("Access to system resources not allowed");
        }
        
        return AccessValidationResult.denied("Resource not found or access denied");
    }
    
    private boolean isValidResourceUri(String uri) {
        return uri != null && uri.startsWith("chess://") && !uri.contains("..") && !uri.contains("//");
    }
    
    private AccessValidationResult validateGameSessionAccess(String agentId, String resourceUri) {
        String sessionId = extractSessionId(resourceUri);
        
        // Verify agent owns this session
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return AccessValidationResult.denied("Session not found");
        }
        
        if (!session.getAgentId().equals(agentId)) {
            logger.warn("Agent {} attempted to access session {} owned by {}", 
                       agentId, sessionId, session.getAgentId());
            return AccessValidationResult.denied("Access denied - session belongs to different agent");
        }
        
        return AccessValidationResult.allowed();
    }
    
    private boolean isGlobalResource(String uri) {
        Set<String> globalResources = Set.of(
            "chess://ai-systems",
            "chess://opening-book", 
            "chess://training-stats",
            "chess://tactical-patterns"
        );
        return globalResources.contains(uri);
    }
    
    private boolean isForbiddenResource(String uri) {
        // Prevent access to system resources
        Set<String> forbiddenPatterns = Set.of(
            "system://", "file://", "http://", "https://", "ftp://",
            "database", "config", "admin", "root", "system"
        );
        
        String lowerUri = uri.toLowerCase();
        return forbiddenPatterns.stream().anyMatch(lowerUri::contains);
    }
}
```

#### 4. Rate Limiting & DoS Protection

##### Request Throttling
```java
package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class MCPRateLimiter {
    
    private static final Logger logger = LogManager.getLogger(MCPRateLimiter.class);
    
    // Rate limiting configuration
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final int MAX_MOVES_PER_MINUTE = 60;
    private static final int MAX_SESSIONS_PER_HOUR = 20;
    private static final int BURST_LIMIT = 10; // Max requests in 10 seconds
    
    // Per-agent rate tracking
    private final ConcurrentHashMap<String, AgentRateTracker> agentTrackers = new ConcurrentHashMap<>();
    
    public RateLimitResult checkRateLimit(String agentId, String operation) {
        AgentRateTracker tracker = agentTrackers.computeIfAbsent(agentId, k -> new AgentRateTracker());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Clean old entries
        tracker.cleanOldEntries(now);
        
        // Check different rate limits based on operation
        switch (operation) {
            case "make_chess_move":
                return checkMoveRateLimit(tracker, now);
            case "create_chess_game":
                return checkSessionCreationLimit(tracker, now);
            default:
                return checkGeneralRateLimit(tracker, now);
        }
    }
    
    private RateLimitResult checkGeneralRateLimit(AgentRateTracker tracker, LocalDateTime now) {
        // Check burst limit (10 requests in 10 seconds)
        long recentRequests = tracker.getRequestsInWindow(now, Duration.ofSeconds(10));
        if (recentRequests >= BURST_LIMIT) {
            logger.warn("Agent {} exceeded burst limit: {} requests in 10 seconds", 
                       tracker.getAgentId(), recentRequests);
            return RateLimitResult.exceeded("Burst limit exceeded", Duration.ofSeconds(10));
        }
        
        // Check per-minute limit
        long requestsPerMinute = tracker.getRequestsInWindow(now, Duration.ofMinutes(1));
        if (requestsPerMinute >= MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Agent {} exceeded rate limit: {} requests per minute", 
                       tracker.getAgentId(), requestsPerMinute);
            return RateLimitResult.exceeded("Rate limit exceeded", Duration.ofMinutes(1));
        }
        
        tracker.recordRequest(now);
        return RateLimitResult.allowed();
    }
    
    private RateLimitResult checkMoveRateLimit(AgentRateTracker tracker, LocalDateTime now) {
        long movesPerMinute = tracker.getMovesInWindow(now, Duration.ofMinutes(1));
        if (movesPerMinute >= MAX_MOVES_PER_MINUTE) {
            logger.warn("Agent {} exceeded move rate limit: {} moves per minute", 
                       tracker.getAgentId(), movesPerMinute);
            return RateLimitResult.exceeded("Move rate limit exceeded", Duration.ofMinutes(1));
        }
        
        tracker.recordMove(now);
        return RateLimitResult.allowed();
    }
    
    private RateLimitResult checkSessionCreationLimit(AgentRateTracker tracker, LocalDateTime now) {
        long sessionsPerHour = tracker.getSessionsInWindow(now, Duration.ofHours(1));
        if (sessionsPerHour >= MAX_SESSIONS_PER_HOUR) {
            logger.warn("Agent {} exceeded session creation limit: {} sessions per hour", 
                       tracker.getAgentId(), sessionsPerHour);
            return RateLimitResult.exceeded("Session creation limit exceeded", Duration.ofHours(1));
        }
        
        tracker.recordSessionCreation(now);
        return RateLimitResult.allowed();
    }
    
    // Cleanup inactive trackers
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupInactiveTrackers() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        agentTrackers.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
    }
}

class AgentRateTracker {
    private final String agentId;
    private final List<LocalDateTime> requests = new ArrayList<>();
    private final List<LocalDateTime> moves = new ArrayList<>();
    private final List<LocalDateTime> sessions = new ArrayList<>();
    private LocalDateTime lastActivity;
    
    public AgentRateTracker(String agentId) {
        this.agentId = agentId;
        this.lastActivity = LocalDateTime.now();
    }
    
    public synchronized void recordRequest(LocalDateTime time) {
        requests.add(time);
        lastActivity = time;
    }
    
    public synchronized void recordMove(LocalDateTime time) {
        moves.add(time);
        recordRequest(time);
    }
    
    public synchronized void recordSessionCreation(LocalDateTime time) {
        sessions.add(time);
        recordRequest(time);
    }
    
    public synchronized long getRequestsInWindow(LocalDateTime now, Duration window) {
        LocalDateTime cutoff = now.minus(window);
        return requests.stream().filter(time -> time.isAfter(cutoff)).count();
    }
    
    public synchronized long getMovesInWindow(LocalDateTime now, Duration window) {
        LocalDateTime cutoff = now.minus(window);
        return moves.stream().filter(time -> time.isAfter(cutoff)).count();
    }
    
    public synchronized long getSessionsInWindow(LocalDateTime now, Duration window) {
        LocalDateTime cutoff = now.minus(window);
        return sessions.stream().filter(time -> time.isAfter(cutoff)).count();
    }
    
    public synchronized void cleanOldEntries(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(2);
        requests.removeIf(time -> time.isBefore(cutoff));
        moves.removeIf(time -> time.isBefore(cutoff));
        sessions.removeIf(time -> time.isBefore(cutoff));
    }
}
```

#### 5. Comprehensive Validation Pipeline

##### Validation Orchestrator
```java
package com.example.chess.mcp.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class MCPValidationOrchestrator {
    
    private static final Logger logger = LogManager.getLogger(MCPValidationOrchestrator.class);
    
    @Autowired
    private MCPInputValidator inputValidator;
    
    @Autowired
    private ChessMoveValidator moveValidator;
    
    @Autowired
    private ResourceScopeValidator resourceValidator;
    
    @Autowired
    private MCPRateLimiter rateLimiter;
    
    public ValidationResult validateToolCall(String agentId, String toolName, Map<String, Object> arguments) {
        logger.debug("Validating tool call from agent {}: {}", agentId, toolName);
        
        // 1. Rate limiting check
        RateLimitResult rateLimit = rateLimiter.checkRateLimit(agentId, toolName);
        if (!rateLimit.isAllowed()) {
            logger.warn("Rate limit exceeded for agent {}: {}", agentId, rateLimit.getReason());
            return ValidationResult.rateLimited(rateLimit.getReason(), rateLimit.getRetryAfter());
        }
        
        // 2. Input schema validation
        ValidationResult inputValidation = inputValidator.validateToolCall(toolName, arguments);
        if (!inputValidation.isValid()) {
            logger.warn("Input validation failed for agent {}: {}", agentId, inputValidation.getError());
            return inputValidation;
        }
        
        // 3. Tool-specific validation
        if ("make_chess_move".equals(toolName)) {
            return validateMoveToolCall(agentId, arguments);
        }
        
        return ValidationResult.valid();
    }
    
    public ValidationResult validateResourceAccess(String agentId, String resourceUri) {
        logger.debug("Validating resource access for agent {}: {}", agentId, resourceUri);
        
        // 1. Rate limiting check
        RateLimitResult rateLimit = rateLimiter.checkRateLimit(agentId, "resource_access");
        if (!rateLimit.isAllowed()) {
            return ValidationResult.rateLimited(rateLimit.getReason(), rateLimit.getRetryAfter());
        }
        
        // 2. Resource scope validation
        AccessValidationResult accessValidation = resourceValidator.validateResourceAccess(agentId, resourceUri);
        if (!accessValidation.isAllowed()) {
            logger.warn("Resource access denied for agent {}: {}", agentId, accessValidation.getReason());
            return ValidationResult.accessDenied(accessValidation.getReason());
        }
        
        return ValidationResult.valid();
    }
    
    private ValidationResult validateMoveToolCall(String agentId, Map<String, Object> arguments) {
        String sessionId = (String) arguments.get("sessionId");
        String move = (String) arguments.get("move");
        
        // Get game session
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ValidationResult.invalid("Session not found: " + sessionId);
        }
        
        // Verify agent owns session
        if (!session.getAgentId().equals(agentId)) {
            logger.warn("Agent {} attempted to make move in session {} owned by {}", 
                       agentId, sessionId, session.getAgentId());
            return ValidationResult.accessDenied("Session belongs to different agent");
        }
        
        // Chess move validation
        MoveValidationResult moveValidation = moveValidator.validateMove(sessionId, move, session.getGame());
        if (!moveValidation.isValid()) {
            return ValidationResult.invalid(moveValidation.getError());
        }
        
        return ValidationResult.valid();
    }
}
```

### Validation Configuration

#### application.properties
```properties
# Server-side validation configuration
mcp.validation.enabled=true
mcp.validation.strict-mode=true
mcp.validation.log-violations=true

# Rate limiting configuration
mcp.rate-limit.requests-per-minute=100
mcp.rate-limit.moves-per-minute=60
mcp.rate-limit.sessions-per-hour=20
mcp.rate-limit.burst-limit=10

# Security configuration
mcp.security.forbidden-patterns=DROP,DELETE,UPDATE,INSERT,EXEC,SYSTEM
mcp.security.resource-access-control=true
mcp.security.session-isolation=true
```

## Security & Rate Limiting

### Authentication
- **API Keys**: Per-client authentication tokens
- **Session Tokens**: Temporary game session authentication
- **Rate Limiting**: 100 requests/minute per client with burst protection
- **Move Validation**: Server-side chess rule enforcement with security checks

### Data Protection
- **Game Privacy**: Session isolation between clients
- **Training Data**: Anonymized game data for AI improvement
- **API Security**: HTTPS/WSS encryption, comprehensive input validation
- **Access Control**: Agent-specific resource access validation

## Spring Boot MCP Server Architecture

### Chess Application as MCP Server

The existing Spring Boot Chess application (ChessApplication.java) is extended to function as an MCP Server:

- **Existing Web Interface**: Continues to serve the browser-based chess game on port 8081
- **New MCP Interface**: Adds JSON-RPC 2.0 protocol support for external AI agents
- **Dual Operation**: Both interfaces can operate simultaneously
- **Shared Resources**: Both interfaces use the same chess engine, AI systems, and game logic

### Integration with Existing Chess Engine

#### 1. MCP Server Implementation
```java
@Component
public class ChessMCPServer {
    
    @Autowired
    private ChessGame chessGame;
    
    @Autowired
    private Map<String, ChessAI> aiSystems;
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private ChessToolExecutor toolExecutor;
    
    @Autowired
    private ChessResourceProvider resourceProvider;
    
    public JsonRpcResponse handleJsonRpcRequest(JsonRpcRequest request) {
        try {
            switch (request.getMethod()) {
                case "initialize":
                    return handleInitialize(request);
                case "tools/list":
                    return handleToolsList(request);
                case "resources/list":
                    return handleResourcesList(request);
                case "tools/call":
                    return handleToolCall(request);
                case "resources/read":
                    return handleResourceRead(request);
                default:
                    return JsonRpcResponse.methodNotFound(request.getId());
            }
        } catch (Exception e) {
            return JsonRpcResponse.internalError(request.getId(), e.getMessage());
        }
    }
}
```

#### 2. Spring Boot Application Extension
```java
@SpringBootApplication
public class ChessApplication {
    
    public static void main(String[] args) {
        // Check if MCP mode is requested
        if (args.length > 0 && "--mcp".equals(args[0])) {
            startMCPServer(args);
        } else {
            // Standard web application startup
            SpringApplication.run(ChessApplication.class, args);
        }
    }
    
    private static void startMCPServer(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ChessApplication.class, args);
        MCPTransportService transportService = context.getBean(MCPTransportService.class);
        
        String transport = getTransportType(args);
        if ("stdio".equals(transport)) {
            transportService.startStdioTransport();
        } else if ("websocket".equals(transport)) {
            transportService.startWebSocketTransport();
        }
    }
}
```

#### 3. JSON-RPC Transport Layer
```java
@Service
public class MCPTransportService {
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    // stdio transport for direct process communication
    public void startStdioTransport() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonRpcRequest request = JsonRpcParser.parse(line);
                JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request);
                writer.println(response.toJson());
            }
        } catch (IOException e) {
            System.err.println("MCP stdio transport error: " + e.getMessage());
        }
    }
    
    // WebSocket transport for network communication
    @ServerEndpoint("/mcp")
    public void startWebSocketTransport() {
        // WebSocket server implementation
    }
    
    @OnMessage
    public void handleWebSocketMessage(String message, Session session) {
        try {
            JsonRpcRequest request = JsonRpcParser.parse(message);
            JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request);
            session.getAsyncRemote().sendText(response.toJson());
        } catch (Exception e) {
            JsonRpcResponse error = JsonRpcResponse.parseError(null);
            session.getAsyncRemote().sendText(error.toJson());
        }
    }
}
```

#### 4. Tool Implementation Integration
```java
@Component
public class ChessToolExecutor implements ToolExecutor {
    
    @Autowired
    private ChessGame chessGame;
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private Map<String, ChessAI> aiSystems;
    
    @Override
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        switch (toolName) {
            case "create_chess_game":
                return createChessGame(arguments);
            case "make_chess_move":
                return makeChessMove(arguments);
            case "get_board_state":
                return getBoardState(arguments);
            case "analyze_position":
                return analyzePosition(arguments);
            case "get_legal_moves":
                return getLegalMoves(arguments);
            case "get_move_hint":
                return getMoveHint(arguments);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }
    
    private ToolResult createChessGame(Map<String, Object> args) {
        String aiOpponent = (String) args.get("aiOpponent");
        String playerColor = (String) args.get("playerColor");
        Integer difficulty = (Integer) args.get("difficulty");
        
        // Create new chess game using existing ChessGame.java
        ChessGame game = new ChessGame();
        ChessAI ai = aiSystems.get(aiOpponent);
        
        String sessionId = sessionManager.createSession(game, ai, playerColor, difficulty);
        
        return ToolResult.success(
            "Chess game created successfully! Session ID: " + sessionId,
            Map.of(
                "sessionId", sessionId,
                "gameState", game.getFEN(),
                "aiOpponent", aiOpponent,
                "playerColor", playerColor,
                "status", "active"
            )
        );
    }
    
    private ToolResult makeChessMove(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        String move = (String) args.get("move");
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        // Use existing ChessGame.java move validation and execution
        ChessGame game = session.getGame();
        boolean moveValid = game.makeMove(move);
        
        if (!moveValid) {
            return ToolResult.error("Invalid move: " + move, 
                Map.of("legalMoves", game.getLegalMoves()));
        }
        
        // Get AI response using existing AI system
        ChessAI ai = session.getAI();
        String aiMove = ai.getMove(game);
        game.makeMove(aiMove);
        
        return ToolResult.success(
            "Move executed: " + move + "\nAI responds: " + aiMove,
            Map.of(
                "gameState", game.getFEN(),
                "aiResponse", aiMove,
                "gameStatus", game.getGameStatus()
            )
        );
    }
}
```

### Startup Configuration

#### application.properties
```properties
# Existing web server configuration
server.port=8081

# MCP Server configuration
mcp.enabled=true
mcp.transport=stdio,websocket
mcp.websocket.port=8082
mcp.stdio.enabled=true

# Chess AI configuration (existing)
chess.ai.alphazero.enabled=true
chess.ai.leelazerochess.enabled=true
# ... other AI configurations
```

#### Command Line Usage
```bash
# Start as web application (existing behavior)
java -jar chess-application.jar

# Start as MCP server via stdio
java -jar chess-application.jar --mcp --transport=stdio

# Start as MCP server via WebSocket
java -jar chess-application.jar --mcp --transport=websocket --port=8082

# Start both web and MCP interfaces
java -jar chess-application.jar --mcp --dual-mode
```age, Session session) {
        JsonRpcRequest request = parseJsonRpc(message);
        JsonRpcResponse response = processMCPRequest(request);
        session.getAsyncRemote().sendText(response.toJson());
    }
}
```

#### 3. Tool Implementation
```java
@Component
public class ChessToolProvider {
    
    public List<MCPTool> getAvailableTools() {
        return Arrays.asList(
            MCPTool.builder()
                .name("chess_create_game")
                .description("Create a new chess game with AI opponent")
                .inputSchema(createGameSchema())
                .handler(this::handleCreateGame)
                .build(),
            MCPTool.builder()
                .name("chess_make_move")
                .description("Make a chess move")
                .inputSchema(makeMoveSchema())
                .handler(this::handleMakeMove)
                .build()
        );
    }
    
    private ToolResult handleCreateGame(Map<String, Object> params) {
        String aiOpponent = (String) params.get("aiOpponent");
        String playerColor = (String) params.get("playerColor");
        Integer difficulty = (Integer) params.get("difficulty");
        
        String sessionId = sessionManager.createSession(aiOpponent, playerColor, difficulty);
        
        return ToolResult.success(Map.of(
            "sessionId", sessionId,
            "gameState", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "status", "active"
        ));
    }
}
```

## Performance Considerations

### Scalability
- **Concurrent Sessions**: Support 1000+ simultaneous games
- **AI Load Balancing**: Distribute AI requests across available systems
- **Memory Management**: Efficient game state storage
- **Database Integration**: Persistent session storage for long games

### Response Times
- **Move Validation**: < 50ms
- **AI Move Generation**: 1-5 seconds (configurable)
- **Position Analysis**: < 2 seconds
- **Session Creation**: < 100ms

## Training Data Integration

### Game Data Collection
```json
{
  "sessionId": "uuid",
  "gameResult": "1-0|0-1|1/2-1/2",
  "moves": ["e4", "e5", "Nf3"],
  "positions": ["fen1", "fen2", "fen3"],
  "aiOpponent": "AlphaZero",
  "playerStrength": 1800,
  "gameLength": 45,
  "endReason": "checkmate|resignation|draw"
}
```

### AI Training Enhancement
- **Real Game Data**: MCP games contribute to AI training datasets
- **Player Strength Analysis**: Adapt AI difficulty based on player performance
- **Opening Repertoire**: Learn from popular MCP game openings
- **Tactical Patterns**: Identify common tactical motifs from MCP games

## Error Handling

### JSON-RPC 2.0 Error Responses

#### Invalid Move Error
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "error": {
    "code": -32001,
    "message": "Invalid chess move",
    "data": {
      "chessError": "INVALID_MOVE",
      "position": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "attemptedMove": "Ke1",
      "legalMoves": ["e4", "d4", "Nf3", "Nc3", "f4", "g4", "h4", "a4", "b4", "c4"]
    }
  }
}
```

#### Session Not Found Error
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "error": {
    "code": -32002,
    "message": "Chess game session not found",
    "data": {
      "chessError": "SESSION_NOT_FOUND",
      "sessionId": "invalid-session-id",
      "activeSessions": ["chess-session-uuid-12345", "chess-session-uuid-67890"]
    }
  }
}
```

#### AI System Unavailable Error
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "error": {
    "code": -32003,
    "message": "AI system temporarily unavailable",
    "data": {
      "chessError": "AI_UNAVAILABLE",
      "requestedAI": "AlphaZero",
      "reason": "Training in progress",
      "availableAI": ["LeelaChessZero", "Negamax", "MCTS"],
      "estimatedAvailableTime": "2024-01-15T15:30:00Z"
    }
  }
}
```

### MCP Chess Error Codes (JSON-RPC 2.0 Compliant)
- **-32700**: Parse error - Invalid JSON received
- **-32600**: Invalid Request - JSON-RPC format error
- **-32601**: Method not found - Unknown MCP method
- **-32602**: Invalid params - Parameter validation failed
- **-32603**: Internal error - Server-side error
- **-32001**: Invalid chess move (INVALID_MOVE)
- **-32002**: Session not found (SESSION_NOT_FOUND)
- **-32003**: AI system unavailable (AI_UNAVAILABLE)
- **-32004**: Game already ended (GAME_ENDED)
- **-32005**: Rate limit exceeded (RATE_LIMITED)
- **-32006**: Invalid session state (INVALID_STATE)
- **-32007**: Resource not found (RESOURCE_NOT_FOUND)
- **-32008**: Tool execution failed (TOOL_EXECUTION_ERROR)

## Monitoring & Analytics

### Game Metrics
- **Active Sessions**: Real-time session count
- **AI Performance**: Win rates, average game length
- **Popular Openings**: Most played opening variations
- **Player Engagement**: Session duration, return rate

### System Health
- **AI Response Times**: Performance monitoring per AI system
- **Error Rates**: Track and alert on error frequency
- **Resource Usage**: Memory, CPU utilization
- **Training Progress**: AI improvement metrics

## Future Enhancements

### Advanced Features
- **Tournament Mode**: Multi-player tournaments via MCP
- **Puzzle Solving**: Chess tactical puzzle API
- **Game Analysis**: Post-game analysis with AI commentary
- **Opening Explorer**: Opening database with statistics
- **Endgame Tablebase**: Perfect endgame play integration

### AI Capabilities
- **Multi-AI Consultation**: Combine multiple AI opinions
- **Explanation Engine**: Natural language move explanations
- **Style Adaptation**: AI adapts to player's playing style
- **Weakness Detection**: Identify and exploit player weaknesses

## Implementation Timeline

### Phase 1: Spring Boot MCP Integration (2 weeks)
- Extend ChessApplication.java to support MCP mode
- JSON-RPC 2.0 transport layer (stdio/WebSocket)
- SOLID architecture implementation
- MCP initialize/list_tools/list_resources/call_tool flow
- Integration with existing ChessGame.java and AI systems

### Phase 2: Chess Tool Implementation (1.5 weeks)
- Core chess tools leveraging existing chess engine
- Session management with existing game state
- AI system integration (all 12 existing AI systems)
- Move validation using existing ChessRuleValidator.java

### Phase 3: Advanced Features (1 week)
- Analysis tools using existing AI analysis capabilities
- Resource providers (AI systems, opening book, training stats)
- Real-time notifications
- Error handling and validation

### Phase 4: Production Ready (0.5 weeks)
- Dual-mode operation (web + MCP)
- Performance optimization
- Testing with existing test suite
- Documentation and deployment

## Conclusion

The MCP Chess API will transform our sophisticated chess engine into a powerful platform for AI-driven chess interaction. By exposing our 12 advanced AI systems through a standardized protocol, we enable external agents to engage in meaningful chess games while contributing to our AI training datasets.

The design prioritizes:
- **Simplicity**: Clean, intuitive API design
- **Performance**: Sub-second response times
- **Scalability**: Support for thousands of concurrent games
- **Intelligence**: Integration with cutting-edge AI systems
- **Extensibility**: Foundation for future chess AI innovations

## MCP Testing Framework

### Package Structure

#### Production Code
```
src/main/java/com/example/chess/mcp/
├── ChessMCPServer.java              # Main MCP server
├── MCPTransportService.java         # Transport layer (stdio/WebSocket)
├── protocol/
│   ├── JsonRpcRequest.java          # JSON-RPC request model
│   ├── JsonRpcResponse.java         # JSON-RPC response model
│   └── MCPProtocolHandler.java      # Protocol handling
├── tools/
│   ├── ChessToolExecutor.java       # Tool execution coordinator
│   ├── CreateGameTool.java          # Create chess game tool
│   ├── MakeMoveTool.java            # Make move tool
│   ├── GetBoardStateTool.java       # Get board state tool
│   ├── AnalyzePositionTool.java     # Position analysis tool
│   ├── GetLegalMovesTool.java       # Legal moves tool
│   └── GetMoveHintTool.java         # Move hint tool
├── resources/
│   ├── ChessResourceProvider.java   # Resource provider
│   ├── AISystemsResource.java       # AI systems resource
│   ├── OpeningBookResource.java     # Opening book resource
│   ├── GameSessionsResource.java    # Game sessions resource
│   └── TrainingStatsResource.java   # Training statistics resource
├── session/
│   ├── MCPSessionManager.java       # Session management
│   └── ChessGameSession.java        # Game session model
└── config/
    ├── MCPConfiguration.java        # MCP configuration
    └── MCPProperties.java           # MCP properties
```

#### Test Code
```
src/test/java/com/example/chess/mcp/
├── ChessMCPServerTest.java          # Main server tests
├── MCPTransportServiceTest.java     # Transport layer tests
├── protocol/
│   ├── JsonRpcProtocolTest.java     # JSON-RPC protocol tests
│   └── MCPProtocolHandlerTest.java  # Protocol handler tests
├── tools/
│   ├── ChessToolExecutorTest.java   # Tool executor tests
│   ├── CreateGameToolTest.java      # Create game tool tests
│   ├── MakeMoveToolTest.java        # Make move tool tests
│   ├── GetBoardStateToolTest.java   # Board state tool tests
│   ├── AnalyzePositionToolTest.java # Analysis tool tests
│   ├── GetLegalMovesToolTest.java   # Legal moves tool tests
│   └── GetMoveHintToolTest.java     # Move hint tool tests
├── resources/
│   ├── ChessResourceProviderTest.java # Resource provider tests
│   ├── AISystemsResourceTest.java   # AI systems resource tests
│   ├── OpeningBookResourceTest.java # Opening book resource tests
│   ├── GameSessionsResourceTest.java # Game sessions resource tests
│   └── TrainingStatsResourceTest.java # Training stats resource tests
├── session/
│   ├── MCPSessionManagerTest.java   # Session manager tests
│   └── ChessGameSessionTest.java    # Game session tests
├── integration/
│   ├── MCPIntegrationTest.java      # End-to-end MCP tests
│   ├── MCPProtocolFlowTest.java     # Protocol flow tests
│   ├── MCPChessGameFlowTest.java    # Chess game flow tests
│   └── MCPErrorHandlingTest.java    # Error handling tests
└── fixtures/
    ├── MCPTestFixtures.java         # Test data fixtures
    ├── JsonRpcTestData.java         # JSON-RPC test data
    └── ChessGameTestData.java       # Chess game test data
```

### Comprehensive MCP Testing Strategy

#### 1. Protocol Level Tests

##### JSON-RPC 2.0 Compliance Tests
```java
package com.example.chess.mcp.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class JsonRpcProtocolTest {
    
    private static final Logger logger = LogManager.getLogger(JsonRpcProtocolTest.class);
    
    @Test
    public void testJsonRpcRequestParsing() {
        logger.info("Testing JSON-RPC request parsing");
        // Test valid JSON-RPC 2.0 request parsing
        // Test invalid JSON handling
        // Test missing required fields
    }
    
    @Test
    public void testJsonRpcResponseGeneration() {
        logger.info("Testing JSON-RPC response generation");
        // Test success response format
        // Test error response format
        // Test notification format
    }
    
    @Test
    public void testErrorCodeCompliance() {
        logger.info("Testing JSON-RPC error code compliance");
        // Test standard error codes (-32700 to -32603)
        // Test custom error codes (-32001 to -32099)
    }
}
```

##### MCP Protocol Flow Tests
```java
package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MCPProtocolFlowTest {
    
    private static final Logger logger = LogManager.getLogger(MCPProtocolFlowTest.class);
    
    @Test
    public void testCompleteProtocolFlow() {
        logger.info("Testing complete MCP protocol flow");
        // 1. Test initialize handshake
        // 2. Test tools/list discovery
        // 3. Test resources/list discovery
        // 4. Test tools/call execution
        // 5. Test notifications
    }
    
    @Test
    public void testInitializeHandshake() {
        logger.info("Testing MCP initialize handshake");
        // Test client capabilities negotiation
        // Test server capabilities response
        // Test protocol version compatibility
    }
    
    @Test
    public void testToolDiscovery() {
        logger.info("Testing MCP tool discovery");
        // Test tools/list returns all 6 chess tools
        // Test tool schema validation
        // Test tool availability
    }
    
    @Test
    public void testResourceDiscovery() {
        logger.info("Testing MCP resource discovery");
        // Test resources/list returns all 5 chess resources
        // Test resource URI format
        // Test resource accessibility
    }
}
```

#### 2. Chess Tool Tests

##### Create Game Tool Tests
```java
package com.example.chess.mcp.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CreateGameToolTest {
    
    private static final Logger logger = LogManager.getLogger(CreateGameToolTest.class);
    
    @Test
    public void testCreateGameWithAllAISystems() {
        logger.info("Testing create game with all 12 AI systems");
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                             "MCTS", "Negamax", "OpenAI", "QLearning", 
                             "DeepLearning", "CNN", "DQN", "Genetic"};
        
        for (String ai : aiSystems) {
            // Test game creation with each AI system
            // Verify session ID generation
            // Verify initial board state
            // Verify AI system assignment
        }
    }
    
    @Test
    public void testCreateGameParameterValidation() {
        logger.info("Testing create game parameter validation");
        // Test invalid AI opponent
        // Test invalid player color
        // Test invalid difficulty level
        // Test missing required parameters
    }
    
    @Test
    public void testCreateGameResponseFormat() {
        logger.info("Testing create game response format");
        // Test success response structure
        // Test content array format
        // Test resource URI generation
    }
}
```

##### Make Move Tool Tests
```java
package com.example.chess.mcp.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MakeMoveToolTest {
    
    private static final Logger logger = LogManager.getLogger(MakeMoveToolTest.class);
    
    @Test
    public void testValidMoveExecution() {
        logger.info("Testing valid move execution");
        // Test standard moves (e4, Nf3, etc.)
        // Test captures (exd5, Nxf7, etc.)
        // Test special moves (O-O, O-O-O, en passant)
        // Test pawn promotion
    }
    
    @Test
    public void testInvalidMoveHandling() {
        logger.info("Testing invalid move handling");
        // Test illegal moves
        // Test moves in wrong notation
        // Test moves for non-existent sessions
        // Test moves in finished games
    }
    
    @Test
    public void testAIResponseGeneration() {
        logger.info("Testing AI response generation");
        // Test AI move generation for all 12 AI systems
        // Test AI response timing
        // Test position evaluation
    }
}
```

#### 3. Resource Provider Tests

##### AI Systems Resource Tests
```java
package com.example.chess.mcp.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AISystemsResourceTest {
    
    private static final Logger logger = LogManager.getLogger(AISystemsResourceTest.class);
    
    @Test
    public void testAISystemsResourceContent() {
        logger.info("Testing AI systems resource content");
        // Test all 12 AI systems are listed
        // Test AI system metadata (strength, type, description)
        // Test AI system availability status
        // Test training statistics
    }
    
    @Test
    public void testAISystemsResourceFormat() {
        logger.info("Testing AI systems resource format");
        // Test JSON format compliance
        // Test required fields presence
        // Test data type validation
    }
}
```

#### 4. Integration Tests

##### End-to-End Chess Game Flow
```java
package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MCPChessGameFlowTest {
    
    private static final Logger logger = LogManager.getLogger(MCPChessGameFlowTest.class);
    
    @Test
    public void testCompleteChessGameFlow() {
        logger.info("Testing complete chess game flow via MCP");
        // 1. Initialize MCP connection
        // 2. Discover tools and resources
        // 3. Create chess game
        // 4. Play complete game (20+ moves)
        // 5. Test game ending scenarios
        // 6. Verify training data collection
    }
    
    @Test
    public void testMultipleSimultaneousGames() {
        logger.info("Testing multiple simultaneous chess games");
        // Test concurrent game sessions
        // Test session isolation
        // Test resource management
    }
    
    @Test
    public void testAllAISystemsGameplay() {
        logger.info("Testing gameplay with all AI systems");
        String[] aiSystems = {"AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", 
                             "MCTS", "Negamax", "OpenAI", "QLearning", 
                             "DeepLearning", "CNN", "DQN", "Genetic"};
        
        for (String ai : aiSystems) {
            logger.info("Testing gameplay with AI system: {}", ai);
            // Create game with specific AI
            // Play 10 moves
            // Verify AI responses
            // Test position analysis
            // Test move hints
        }
    }
}
```

#### 5. Transport Layer Tests

##### stdio Transport Tests
```java
package com.example.chess.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MCPTransportServiceTest {
    
    private static final Logger logger = LogManager.getLogger(MCPTransportServiceTest.class);
    
    @Test
    public void testStdioTransport() {
        logger.info("Testing stdio transport layer");
        // Test JSON-RPC message parsing from stdin
        // Test JSON-RPC response writing to stdout
        // Test error handling for malformed input
    }
    
    @Test
    public void testWebSocketTransport() {
        logger.info("Testing WebSocket transport layer");
        // Test WebSocket connection establishment
        // Test JSON-RPC message handling over WebSocket
        // Test connection cleanup
    }
    
    @Test
    public void testTransportErrorHandling() {
        logger.info("Testing transport error handling");
        // Test connection failures
        // Test message parsing errors
        // Test timeout handling
    }
}
```

#### 6. Error Handling Tests

##### MCP Error Handling Tests
```java
package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MCPErrorHandlingTest {
    
    private static final Logger logger = LogManager.getLogger(MCPErrorHandlingTest.class);
    
    @Test
    public void testJsonRpcErrorCodes() {
        logger.info("Testing JSON-RPC error codes");
        // Test -32700 (Parse error)
        // Test -32600 (Invalid Request)
        // Test -32601 (Method not found)
        // Test -32602 (Invalid params)
        // Test -32603 (Internal error)
    }
    
    @Test
    public void testChessSpecificErrors() {
        logger.info("Testing chess-specific error codes");
        // Test -32001 (Invalid move)
        // Test -32002 (Session not found)
        // Test -32003 (AI unavailable)
        // Test -32004 (Game ended)
        // Test -32005 (Rate limited)
    }
    
    @Test
    public void testErrorResponseFormat() {
        logger.info("Testing error response format");
        // Test error object structure
        // Test error data inclusion
        // Test helpful error messages
    }
}
```

### Test Execution Strategy

#### Maven Test Configuration
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
        <systemPropertyVariables>
            <log4j.configurationFile>src/test/resources/log4j2-test.xml</log4j.configurationFile>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

#### Test Categories
```bash
# Run all MCP tests
mvn test -Dtest="com.example.chess.mcp.**"

# Run protocol tests only
mvn test -Dtest="com.example.chess.mcp.protocol.**"

# Run tool tests only
mvn test -Dtest="com.example.chess.mcp.tools.**"

# Run integration tests only
mvn test -Dtest="com.example.chess.mcp.integration.**"

# Run specific AI system tests
mvn test -Dtest="MCPChessGameFlowTest#testAllAISystemsGameplay"
```

#### Test Coverage Goals
- **Protocol Compliance**: 100% JSON-RPC 2.0 specification coverage
- **Tool Coverage**: All 6 chess tools tested with valid/invalid inputs
- **Resource Coverage**: All 5 chess resources tested for content and format
- **AI Integration**: All 12 AI systems tested via MCP interface
- **Error Handling**: All error codes and scenarios tested
- **Transport Layer**: Both stdio and WebSocket transports tested
- **End-to-End**: Complete chess games played via MCP protocol

### Log4j Integration

#### Test Logging Configuration (log4j2-test.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
        <File name="MCPTestFile" fileName="logs/mcp-tests.log">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </File>
    </Appenders>
    <Loggers>
        <Logger name="com.example.chess.mcp" level="DEBUG" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="MCPTestFile"/>
        </Logger>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

## Stateful Multi-Agent Scalability

### Concurrent Session Limits and Performance

#### Scalability Targets
- **Maximum Concurrent Agents**: 100 simultaneous MCP clients
- **Sessions Per Agent**: Up to 10 concurrent chess games per agent
- **Total Active Sessions**: Up to 1,000 simultaneous chess games
- **AI System Load Balancing**: Dedicated thread pools for different AI types
- **Response Time Goals**: < 100ms for moves, < 5s for AI responses

#### Resource Management
```java
// Configuration for concurrent multi-agent support
mcp.concurrent.max-agents=100
mcp.concurrent.max-sessions-per-agent=10
mcp.concurrent.max-total-sessions=1000
mcp.concurrent.ai-thread-pools.neural-network=4
mcp.concurrent.ai-thread-pools.classical-engine=8
mcp.concurrent.ai-thread-pools.machine-learning=6
mcp.concurrent.session-timeout-minutes=30
mcp.concurrent.cleanup-interval-minutes=5
```

### Multi-Agent Testing Strategy

#### Concurrent Agent Tests
```java
package com.example.chess.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MCPConcurrentAgentTest {
    
    private static final Logger logger = LogManager.getLogger(MCPConcurrentAgentTest.class);
    
    @Test
    public void testConcurrentMultipleAgents() {
        logger.info("Testing concurrent multiple MCP agents");
        
        int agentCount = 10;
        int gamesPerAgent = 3;
        
        List<CompletableFuture<Void>> agentTasks = IntStream.range(0, agentCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                String agentId = "test-agent-" + i;
                simulateAgentGameplay(agentId, gamesPerAgent);
            }))
            .collect(Collectors.toList());
        
        // Wait for all agents to complete
        CompletableFuture.allOf(agentTasks.toArray(new CompletableFuture[0])).join();
        
        // Verify no session interference
        verifySessionIsolation();
    }
    
    @Test
    public void testSessionIsolationBetweenAgents() {
        logger.info("Testing session isolation between agents");
        
        // Create games for multiple agents
        String agent1 = createAgentWithGame("AlphaZero", "white");
        String agent2 = createAgentWithGame("LeelaChessZero", "black");
        String agent3 = createAgentWithGame("Negamax", "white");
        
        // Make moves in parallel
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> playGameMoves(agent1, Arrays.asList("e4", "Nf3", "Bc4"))),
            CompletableFuture.runAsync(() -> playGameMoves(agent2, Arrays.asList("d4", "c4", "Nc3"))),
            CompletableFuture.runAsync(() -> playGameMoves(agent3, Arrays.asList("f4", "e4", "Nf3")))
        ).join();
        
        // Verify each agent's game state is independent
        verifyIndependentGameStates(agent1, agent2, agent3);
    }
    
    @Test
    public void testAISystemLoadBalancing() {
        logger.info("Testing AI system load balancing under concurrent load");
        
        // Create 50 concurrent games with different AI systems
        List<String> aiSystems = Arrays.asList("AlphaZero", "LeelaChessZero", "Negamax", "MCTS", "QLearning");
        
        List<CompletableFuture<Void>> gameTasks = IntStream.range(0, 50)
            .mapToObj(i -> {
                String aiSystem = aiSystems.get(i % aiSystems.size());
                return CompletableFuture.runAsync(() -> {
                    String agentId = "load-test-agent-" + i;
                    createAndPlayGame(agentId, aiSystem, 10); // 10 moves
                });
            })
            .collect(Collectors.toList());
        
        CompletableFuture.allOf(gameTasks.toArray(new CompletableFuture[0])).join();
        
        // Verify AI system performance under load
        verifyAISystemPerformance();
    }
}
```

### Multi-Agent Notification System

#### Agent-Specific Notifications
```java
package com.example.chess.mcp.notifications;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class MCPNotificationService {
    
    private static final Logger logger = LogManager.getLogger(MCPNotificationService.class);
    
    private final ConcurrentHashMap<String, NotificationChannel> agentChannels = new ConcurrentHashMap<>();
    
    public void sendAgentNotification(String agentId, String method, Object params) {
        NotificationChannel channel = agentChannels.get(agentId);
        if (channel != null) {
            JsonRpcNotification notification = new JsonRpcNotification(method, params);
            channel.send(notification);
            
            logger.debug("Sent notification {} to agent {}", method, agentId);
        }
    }
    
    public void broadcastToAllAgents(String method, Object params) {
        JsonRpcNotification notification = new JsonRpcNotification(method, params);
        
        agentChannels.values().parallelStream().forEach(channel -> {
            try {
                channel.send(notification);
            } catch (Exception e) {
                logger.warn("Failed to send broadcast notification: {}", e.getMessage());
            }
        });
        
        logger.info("Broadcast notification {} to {} agents", method, agentChannels.size());
    }
    
    // Agent-specific game notifications
    public void notifyGameStateChange(String agentId, String sessionId, GameState gameState) {
        sendAgentNotification(agentId, "notifications/chess/game_state", Map.of(
            "sessionId", sessionId,
            "gameState", gameState.getFEN(),
            "status", gameState.getStatus(),
            "movesPlayed", gameState.getMovesPlayed()
        ));
    }
    
    public void notifyAIMove(String agentId, String sessionId, String move, double thinkingTime) {
        sendAgentNotification(agentId, "notifications/chess/ai_move", Map.of(
            "sessionId", sessionId,
            "move", move,
            "thinkingTime", thinkingTime,
            "timestamp", System.currentTimeMillis()
        ));
    }
}
```

This MCP implementation extends our existing Spring Boot Chess application to serve as a **stateful MCP Server supporting concurrent multi-agent gameplay**, following JSON-RPC 2.0 specification and SOLID design principles. The application can operate in dual mode (web interface + MCP server) or dedicated MCP mode, establishing our chess engine as the premier platform for AI chess interaction and research through standardized protocol communication.

### Key Benefits
- **Stateful Multi-Agent Support**: Up to 100 concurrent MCP clients with 1,000 simultaneous games
- **Session Isolation**: Complete independence between agent game sessions
- **Concurrent AI Processing**: Load-balanced AI systems with dedicated thread pools
- **Minimal Code Changes**: Leverages existing chess engine and AI systems
- **Dual Operation**: Web interface and MCP server can run simultaneously
- **Standard Compliance**: Full JSON-RPC 2.0 and MCP protocol adherence
- **SOLID Architecture**: Clean, extensible, maintainable design
- **Rich AI Integration**: All 12 AI systems available via MCP with concurrent access
- **Production Ready**: Built on proven Spring Boot chess application
- **Comprehensive Testing**: 100% MCP capability coverage including multi-agent scenarios
- **Standard Logging**: Integrated log4j logging for debugging and monitoring
- **Real-time Notifications**: Agent-specific notifications for game state changes
- **Performance Monitoring**: Detailed metrics for concurrent session management

### **Production-Ready Concurrency Example**
```java
// Example: 4 agents playing simultaneously with full AI system utilization
Agent A: Creates game with AlphaZero, plays e4, Nf3, Bc4
Agent B: Creates game with LeelaChessZero, plays d4, c4, Nc3  
Agent C: Creates game with Negamax, plays f4, e4, Nf3
Agent D: Creates 12 games (one against each AI system):
  - Session D1: vs AlphaZero (e4, d4, Nf3)
  - Session D2: vs LeelaChessZero (d4, Nf6, c4)
  - Session D3: vs AlphaFold3 (Nf3, d5, e3)
  - Session D4: vs A3C (c4, e6, Nc3)
  - Session D5: vs MCTS (g3, d5, Bg2)
  - Session D6: vs Negamax (e4, c5, Nf3)
  - Session D7: vs OpenAI (d4, d5, c4)
  - Session D8: vs QLearning (Nf3, Nf6, g3)
  - Session D9: vs DeepLearning (e4, e5, Bc4)
  - Session D10: vs CNN (c4, c5, Nc3)
  - Session D11: vs DQN (f4, d5, e3)
  - Session D12: vs Genetic (b3, e5, Bb2)

// Total: 15 concurrent games across 4 agents
// All 12 AI systems actively engaged
// Complete session isolation maintained
// Load-balanced AI processing across thread pools
```

#### Multi-AI Tournament Tool
```java
package com.example.chess.mcp.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CreateTournamentTool extends ChessTool {
    
    private static final Logger logger = LogManager.getLogger(CreateTournamentTool.class);
    
    private static final String[] ALL_AI_SYSTEMS = {
        "AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", "MCTS", "Negamax",
        "OpenAI", "QLearning", "DeepLearning", "CNN", "DQN", "Genetic"
    };
    
    @Override
    protected ToolResult executeInternal(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        String playerColor = (String) args.get("playerColor");
        Integer difficulty = (Integer) args.getOrDefault("difficulty", 5);
        
        logger.info("Creating tournament for agent {} against all 12 AI systems", agentId);
        
        List<String> sessionIds = new ArrayList<>();
        Map<String, String> tournamentResults = new HashMap<>();
        
        // Create game against each AI system
        for (String aiSystem : ALL_AI_SYSTEMS) {
            try {
                String sessionId = sessionManager.createSession(agentId, aiSystem, playerColor, difficulty);
                sessionIds.add(sessionId);
                tournamentResults.put(aiSystem, sessionId);
                
                logger.debug("Created session {} for agent {} vs {}", sessionId, agentId, aiSystem);
                
            } catch (Exception e) {
                logger.warn("Failed to create session for agent {} vs {}: {}", agentId, aiSystem, e.getMessage());
                tournamentResults.put(aiSystem, "FAILED: " + e.getMessage());
            }
        }
        
        String tournamentId = "tournament-" + UUID.randomUUID().toString().substring(0, 8);
        
        return ToolResult.success(
            String.format("Tournament created! Agent %s now playing against all 12 AI systems.\n" +
                         "Tournament ID: %s\n" +
                         "Active Sessions: %d\n" +
                         "Use get_tournament_status tool to monitor progress.", 
                         agentId, tournamentId, sessionIds.size()),
            Map.of(
                "tournamentId", tournamentId,
                "agentId", agentId,
                "totalGames", sessionIds.size(),
                "sessions", tournamentResults,
                "playerColor", playerColor,
                "difficulty", difficulty
            )
        );
    }
}
```

#### Agent D Tournament Usage Example
```java
// Agent D creates tournament against all AI systems
tools/call: create_tournament
{
  "agentId": "agent-d",
  "playerColor": "white",
  "difficulty": 7
}

// Response: 12 concurrent sessions created
{
  "tournamentId": "tournament-abc123",
  "totalGames": 12,
  "sessions": {
    "AlphaZero": "session-d1-uuid",
    "LeelaChessZero": "session-d2-uuid",
    "AlphaFold3": "session-d3-uuid",
    // ... all 12 AI systems
  }
}

// Agent D can now make moves in any/all games
make_chess_move: {"sessionId": "session-d1-uuid", "move": "e4"}
make_chess_move: {"sessionId": "session-d2-uuid", "move": "d4"}
make_chess_move: {"sessionId": "session-d3-uuid", "move": "Nf3"}
// ... continue with other sessions

// Monitor tournament progress
get_tournament_status: {"agentId": "agent-d"}
// Returns: wins/losses/draws against each AI, active games, completion status
```

**Agent D's tournament capability demonstrates the system's ability to support an agent playing against all 12 AI systems concurrently**, showcasing the full scalability and concurrent processing power of the MCP Chess Server architecture.