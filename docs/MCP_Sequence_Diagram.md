# MCP (Model Context Protocol) Sequence Diagram

## Overview
This sequence diagram illustrates the client-server interactions for the Model Context Protocol (MCP) integration in the chess application, showing how AI agents communicate with the chess engine through the MCP server.

## MCP Client-Server Architecture

```mermaid
sequenceDiagram
    participant Client as MCP_Client
    participant MCPTransport as MCP_Transport_Service
    participant MCPServer as Chess_MCP_Server
    participant Validation as MCP_Validation_Orchestrator
    participant RateLimit as MCP_Rate_Limiter
    participant AgentRegistry as MCP_Agent_Registry
    participant ToolExecutor as Chess_Tool_Executor
    participant ResourceProvider as Chess_Resource_Provider
    participant Metrics as MCP_Metrics_Service
    participant Notifications as MCP_Notification_Service
    participant ChessEngine as Chess_Game_Engine

    Note over Client,ChessEngine: MCP Connection Initialization
    Client->>MCPTransport: WebSocket Connection Request
    MCPTransport->>MCPTransport: Validate WebSocket Upgrade
    MCPTransport->>MCPServer: Connection Established
    MCPServer->>AgentRegistry: Register New Agent
    AgentRegistry->>AgentRegistry: Generate Agent ID
    AgentRegistry-->>MCPServer: Agent ID & Session Info
    MCPServer-->>Client: Connection Confirmed

    Note over Client,ChessEngine: MCP Protocol Handshake
    Client->>MCPServer: initialize Request
    MCPServer->>Validation: Validate Request Format
    Validation-->>MCPServer: Validation Result
    MCPServer->>RateLimit: Check Rate Limits
    RateLimit-->>MCPServer: Rate Limit Status
    MCPServer->>Metrics: Record Request
    MCPServer-->>Client: Protocol Capabilities & Server Info

    Note over Client,ChessEngine: Tool Discovery
    Client->>MCPServer: tools/list Request
    MCPServer->>ToolExecutor: Get Available Tools
    ToolExecutor->>ToolExecutor: Enumerate Chess Tools
    ToolExecutor-->>MCPServer: Tool List
    MCPServer->>Metrics: Record Tool Discovery
    MCPServer-->>Client: Available Tools Response

    Note over Client,ChessEngine: Resource Discovery
    Client->>MCPServer: resources/list Request
    MCPServer->>ResourceProvider: Get Available Resources
    ResourceProvider->>ResourceProvider: Enumerate Chess Resources
    ResourceProvider-->>MCPServer: Resource List
    MCPServer->>Metrics: Record Resource Discovery
    MCPServer-->>Client: Available Resources Response

    Note over Client,ChessEngine: Tool Execution Flow
    Client->>MCPServer: tools/call Request
    MCPServer->>Validation: Validate Tool Call
    Validation->>Validation: Check Parameters & Permissions
    Validation-->>MCPServer: Validation Result
    
    alt Tool Call Valid
        MCPServer->>RateLimit: Check Tool Rate Limits
        RateLimit-->>MCPServer: Rate Limit Status
        MCPServer->>ToolExecutor: Execute Tool
        ToolExecutor->>ChessEngine: Perform Chess Operation
        ChessEngine->>ChessEngine: Execute Move/Analysis/Training
        ChessEngine-->>ToolExecutor: Operation Result
        ToolExecutor-->>MCPServer: Tool Execution Result
        MCPServer->>Metrics: Record Tool Execution
        MCPServer->>Notifications: Send Result Notifications
        MCPServer-->>Client: Tool Execution Response
    else Tool Call Invalid
        MCPServer->>Metrics: Record Validation Error
        MCPServer-->>Client: Error Response
    end

    Note over Client,ChessEngine: Resource Access Flow
    Client->>MCPServer: resources/read Request
    MCPServer->>Validation: Validate Resource Access
    Validation->>Validation: Check Resource Permissions
    Validation-->>MCPServer: Validation Result
    
    alt Resource Access Valid
        MCPServer->>RateLimit: Check Resource Rate Limits
        RateLimit-->>MCPServer: Rate Limit Status
        MCPServer->>ResourceProvider: Read Resource
        ResourceProvider->>ChessEngine: Get Resource Data
        ChessEngine-->>ResourceProvider: Resource Content
        ResourceProvider-->>MCPServer: Resource Data
        MCPServer->>Metrics: Record Resource Access
        MCPServer-->>Client: Resource Content Response
    else Resource Access Invalid
        MCPServer->>Metrics: Record Access Error
        MCPServer-->>Client: Error Response
    end

    Note over Client,ChessEngine: Continuous Operations
    loop Ongoing Chess Operations
        Client->>MCPServer: Tool Call (makeMove, analyzePosition, etc.)
        MCPServer->>ToolExecutor: Execute Chess Tool
        ToolExecutor->>ChessEngine: Perform Chess Operation
        ChessEngine-->>ToolExecutor: Operation Result
        ToolExecutor-->>MCPServer: Tool Result
        MCPServer->>Notifications: Broadcast Game Updates
        MCPServer-->>Client: Operation Response
        MCPServer->>Metrics: Record Operation
    end

    Note over Client,ChessEngine: Connection Termination
    Client->>MCPTransport: Close Connection
    MCPTransport->>MCPServer: Connection Closed
    MCPServer->>AgentRegistry: Unregister Agent
    MCPServer->>Metrics: Record Session End
    MCPServer->>Notifications: Send Disconnect Notification
```

## MCP Tool Execution Sequence

```mermaid
sequenceDiagram
    participant Client as MCP_Client
    participant MCPServer as Chess_MCP_Server
    participant ToolExecutor as Chess_Tool_Executor
    participant ChessEngine as Chess_Game_Engine
    participant AISystems as AI_Systems
    participant Validation as MCP_Validation

    Note over Client,ChessEngine: Tool Call Validation
    Client->>MCPServer: tools/call {method: "makeMove", params: {...}}
    MCPServer->>Validation: Validate Tool Call
    Validation->>Validation: Check Method Exists
    Validation->>Validation: Validate Parameters
    Validation->>Validation: Check Agent Permissions
    Validation-->>MCPServer: Validation Result

    alt Validation Successful
        MCPServer->>ToolExecutor: Execute Tool
        ToolExecutor->>ToolExecutor: Parse Parameters
        ToolExecutor->>ChessEngine: makeMove(params)
        
        Note over ChessEngine,AISystems: Chess Move Execution
        ChessEngine->>ChessEngine: Validate Move
        alt Move Valid
            ChessEngine->>ChessEngine: Execute Move
            ChessEngine->>AISystems: Generate AI Response
            AISystems->>AISystems: Calculate Best Move
            AISystems-->>ChessEngine: AI Move
            ChessEngine->>ChessEngine: Execute AI Move
            ChessEngine-->>ToolExecutor: Move Result
        else Move Invalid
            ChessEngine-->>ToolExecutor: Error Response
        end
        
        ToolExecutor-->>MCPServer: Tool Execution Result
        MCPServer-->>Client: Success Response
    else Validation Failed
        MCPServer-->>Client: Error Response
    end
```

## MCP Resource Access Sequence

```mermaid
sequenceDiagram
    participant Client as MCP_Client
    participant MCPServer as Chess_MCP_Server
    participant ResourceProvider as Chess_Resource_Provider
    participant ChessEngine as Chess_Game_Engine
    participant GameState as Game_State_Manager

    Note over Client,ChessEngine: Resource Access Request
    Client->>MCPServer: resources/read {uri: "chess://game/state"}
    MCPServer->>ResourceProvider: Read Resource
    ResourceProvider->>ResourceProvider: Parse Resource URI
    ResourceProvider->>ChessEngine: Get Game State
    ChessEngine->>GameState: Retrieve Current State
    GameState->>GameState: Format State Data
    GameState-->>ChessEngine: Formatted State
    ChessEngine-->>ResourceProvider: Game State Data
    ResourceProvider-->>MCPServer: Resource Content
    MCPServer-->>Client: Resource Content Response

    Note over Client,ChessEngine: Resource Subscription
    Client->>MCPServer: resources/subscribe {uri: "chess://game/updates"}
    MCPServer->>ResourceProvider: Subscribe to Resource
    ResourceProvider->>ResourceProvider: Register Subscription
    ResourceProvider-->>MCPServer: Subscription Confirmed
    MCPServer-->>Client: Subscription Response
    
    Note over ChessEngine,Client: Resource Change Notifications
    ChessEngine->>GameState: Game State Changed
    GameState->>ResourceProvider: Notify State Change
    ResourceProvider->>MCPServer: Resource Changed
    MCPServer->>Notifications: Send Change Notification
    Notifications-->>Client: Resource Update Notification
```

## MCP Error Handling Sequence

```mermaid
sequenceDiagram
    participant Client as MCP_Client
    participant MCPServer as Chess_MCP_Server
    participant Validation as MCP_Validation
    participant RateLimit as MCP_Rate_Limiter
    participant Metrics as MCP_Metrics_Service
    participant ErrorHandler as MCP_Error_Handler

    Note over Client,ChessEngine: Error Scenarios
    alt Rate Limit Exceeded
        Client->>MCPServer: Request
        MCPServer->>RateLimit: Check Limits
        RateLimit-->>MCPServer: Rate Limit Exceeded
        MCPServer->>Metrics: Record Rate Limit
        MCPServer->>ErrorHandler: Handle Rate Limit
        ErrorHandler-->>MCPServer: Error Response
        MCPServer-->>Client: Rate Limit Error
    else Validation Error
        Client->>MCPServer: Invalid Request
        MCPServer->>Validation: Validate
        Validation-->>MCPServer: Validation Failed
        MCPServer->>Metrics: Record Validation Error
        MCPServer->>ErrorHandler: Handle Validation Error
        ErrorHandler-->>MCPServer: Error Response
        MCPServer-->>Client: Validation Error
    else Tool Execution Error
        Client->>MCPServer: Tool Call
        MCPServer->>ToolExecutor: Execute Tool
        ToolExecutor-->>MCPServer: Tool Execution Failed
        MCPServer->>Metrics: Record Tool Error
        MCPServer->>ErrorHandler: Handle Tool Error
        ErrorHandler-->>MCPServer: Error Response
        MCPServer-->>Client: Tool Error
    end
```

## MCP Metrics and Monitoring Sequence

```mermaid
sequenceDiagram
    participant MCPServer as Chess_MCP_Server
    participant Metrics as MCP_Metrics_Service
    participant AgentRegistry as MCP_Agent_Registry
    participant RateLimit as MCP_Rate_Limiter
    participant Monitoring as External_Monitoring

    Note over MCPServer,Monitoring: Metrics Collection
    MCPServer->>Metrics: Record Request
    Metrics->>Metrics: Update Request Count
    Metrics->>Metrics: Update Response Time
    Metrics->>Metrics: Update Success Rate
    
    MCPServer->>AgentRegistry: Update Agent Activity
    AgentRegistry->>AgentRegistry: Track Agent Sessions
    AgentRegistry->>AgentRegistry: Monitor Agent Performance
    
    MCPServer->>RateLimit: Check Rate Limits
    RateLimit->>RateLimit: Track Request Patterns
    RateLimit->>RateLimit: Update Rate Limit Stats
    
    Note over Metrics,Monitoring: Metrics Export
    Metrics->>Metrics: Aggregate Metrics
    Metrics->>Metrics: Generate Performance Report
    Metrics->>Monitoring: Export Metrics Data
    Monitoring->>Monitoring: Store Metrics
    Monitoring->>Monitoring: Generate Alerts
```

## MCP Protocol Flow Summary

### **Connection Lifecycle:**
1. **WebSocket Connection** - Client establishes connection
2. **Agent Registration** - Server assigns unique agent ID
3. **Protocol Handshake** - Exchange capabilities and server info
4. **Tool Discovery** - Client learns available tools
5. **Resource Discovery** - Client learns available resources
6. **Continuous Operations** - Tool calls and resource access
7. **Connection Termination** - Clean shutdown and cleanup

### **Key MCP Components:**
- **MCPTransportService**: WebSocket connection management
- **ChessMCPServer**: Main MCP protocol handler
- **MCPValidationOrchestrator**: Request validation and security
- **MCPRateLimiter**: Rate limiting and throttling
- **MCPAgentRegistry**: Agent session management
- **ChessToolExecutor**: Chess-specific tool execution
- **ChessResourceProvider**: Chess resource access
- **MCPMetricsService**: Performance monitoring
- **MCPNotificationService**: Real-time notifications

### **Supported Operations:**
- **Chess Moves**: makeMove, undoMove, resetGame
- **Game Analysis**: analyzePosition, getLegalMoves
- **AI Training**: startTraining, stopTraining
- **Game State**: getGameState, getMoveHistory
- **Resource Access**: Game state, move history, AI models

### **Security Features:**
- **Rate Limiting**: Per-agent request throttling
- **Input Validation**: Comprehensive parameter validation
- **Agent Isolation**: Separate sessions and permissions
- **Error Handling**: Graceful error responses
- **Metrics Tracking**: Performance and usage monitoring

## File Storage Location

This sequence diagram is stored at:
```
CHESS/docs/MCP_Sequence_Diagram.md
```

## Related Documentation

For more detailed information about MCP integration:
- **MCP Server**: `ChessMCPServer.java`
- **Transport Layer**: `MCPTransportService.java`
- **Tool Execution**: `ChessToolExecutor.java`
- **Resource Management**: `ChessResourceProvider.java`
- **Validation**: `MCPValidationOrchestrator.java`
- **Chess Game Engine**: See `ChessGame_Sequence_Diagram.md`
