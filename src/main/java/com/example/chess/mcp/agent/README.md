# MCP Chess Agent

A standalone Java application that creates dual-session AI vs AI training by connecting to the MCP Chess server.

## Overview

The MCP Chess Agent establishes two concurrent connections to the MCP Chess server:
1. **White Session** - Plays as white with specified AI
2. **Black Session** - Plays as black with specified AI

Moves are relayed between sessions, creating continuous AI vs AI training.

## Usage

### Basic Usage
```bash
java -cp target/classes com.example.chess.mcp.agent.MCPChessAgent
```

### With Options
```bash
java -cp target/classes com.example.chess.mcp.agent.MCPChessAgent \
  --host localhost \
  --port 8082 \
  --transport websocket \
  --white AlphaZero \
  --black LeelaChessZero \
  --games 100 \
  --difficulty 8
```

### Tournament Mode
```bash
java -cp target/classes com.example.chess.mcp.agent.MCPChessAgent \
  --tournament \
  --games 1000
```

## Command Line Options

- `--host <host>` - MCP server host (default: localhost)
- `--port <port>` - MCP server port (default: 8082)  
- `--transport <type>` - Transport type: websocket|stdio (default: websocket)
- `--games <count>` - Games per session (default: 100)
- `--difficulty <level>` - AI difficulty 1-10 (default: 8)
- `--white <ai>` - White AI system (default: AlphaZero)
- `--black <ai>` - Black AI system (default: LeelaChessZero)
- `--tournament` - Run tournament mode
- `--help` - Show help

## Available AI Systems

- AlphaZero
- LeelaChessZero  
- AlphaFold3
- A3C
- MCTS
- Negamax
- OpenAI
- QLearning
- DeepLearning
- DeepLearningCNN
- DQN
- Genetic

## Prerequisites

1. MCP Chess server must be running
2. Server must be in MCP mode:
   ```bash
   java -jar chess-application.jar --mcp --transport=websocket --port=8082
   ```

## Architecture

- **MCPChessAgent** - Main application entry point
- **MCPConnectionManager** - Manages WebSocket connections
- **DualSessionOrchestrator** - Coordinates dual sessions
- **ChessSessionProxy** - Individual session management
- **AgentConfiguration** - Configuration management

## Example Output

```
MCP Chess Agent initialized
Server: localhost:8082
Transport: websocket
Dual-session training started
Initializing dual chess sessions...
Sessions initialized:
  White: AlphaZero
  Black: LeelaChessZero
Starting training loop for 100 games...
Move 1: White plays e4
Move 2: Black plays e5
Move 3: White plays Nf3
...
Game 1 completed
Game result: White wins (moves: 45)
Game 2 completed
...
Training loop completed. Games played: 100
```

This creates a continuous training environment where the server's AI systems learn from playing against each other.