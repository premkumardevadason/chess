# Chess MCP Server

Model Context Protocol (MCP) server implementation for the Chess application, enabling external AI agents to play chess against 12 different AI systems.

## Quick Start

### Start MCP Server (stdio)
```bash
java -jar chess-application.jar --mcp --transport=stdio
```

### Start Web Application (default)
```bash
java -jar chess-application.jar
```

## MCP Protocol Support

### Initialize Handshake
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {"tools": {}},
    "clientInfo": {"name": "chess-client", "version": "1.0.0"}
  }
}
```

### Available Tools
- `create_chess_game` - Start new game with AI opponent
- `make_chess_move` - Execute moves in algebraic notation
- `get_board_state` - Get current game state
- `analyze_position` - AI position analysis
- `get_legal_moves` - All valid moves
- `get_move_hint` - AI move suggestions
- `create_tournament` - Play all 12 AI systems simultaneously
- `get_tournament_status` - Tournament progress

### Available Resources
- `chess://ai-systems` - All 12 AI systems info
- `chess://opening-book` - Professional opening database
- `chess://game-sessions` - Active game sessions
- `chess://training-stats` - AI performance metrics
- `chess://tactical-patterns` - Chess tactical database

## AI Systems Available
1. **AlphaZero** - Self-play neural network with MCTS
2. **LeelaChessZero** - Human game knowledge with transformer architecture
3. **AlphaFold3** - Diffusion modeling for chess
4. **A3C** - Asynchronous Advantage Actor-Critic
5. **MCTS** - Monte Carlo Tree Search
6. **Negamax** - Classical chess engine with alpha-beta pruning
7. **OpenAI** - GPT-4 powered chess analysis
8. **QLearning** - Reinforcement learning
9. **DeepLearning** - Neural network position evaluation
10. **CNN** - Convolutional neural network
11. **DQN** - Deep Q-Network
12. **Genetic** - Genetic algorithm evolution

## Example Usage

### Create Game
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "create_chess_game",
    "arguments": {
      "agentId": "my-agent",
      "aiOpponent": "AlphaZero",
      "playerColor": "white",
      "difficulty": 7
    }
  }
}
```

### Make Move
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "make_chess_move",
    "arguments": {
      "sessionId": "chess-session-uuid",
      "move": "e4"
    }
  }
}
```

### Create Tournament (All AI Systems)
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "create_tournament",
    "arguments": {
      "agentId": "my-agent",
      "playerColor": "white",
      "difficulty": 5
    }
  }
}
```

## Features

### Stateful Multi-Agent Support
- Up to 100 concurrent MCP clients
- 10 games per agent, 1,000 total sessions
- Complete session isolation
- Tournament mode for playing all AI systems

### Server-Side Validation
- JSON Schema input validation
- Chess move legality checking
- Security pattern blocking
- Rate limiting (100 req/min, 60 moves/min)

### Performance
- Concurrent AI processing with thread pools
- Load balancing across AI systems
- < 100ms move validation
- < 5s AI response times

## Testing

### Run MCP Tests
```bash
mvn test -Dtest="com.example.chess.mcp.**"
```

### Test Categories
- Protocol compliance tests
- Chess tool functionality tests
- Multi-agent integration tests
- Tournament and concurrent gameplay tests

## Configuration

See `application-mcp.properties` for MCP-specific settings including rate limits, security options, and concurrent session limits.