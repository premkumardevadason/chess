# Development Guide

## Architecture Overview

The Chess application follows a layered architecture:

### Backend Layers
1. **Controller Layer** (`ChessController.java`) - REST API endpoints
2. **Service Layer** (`ChessGame.java`) - Core game logic
3. **AI Layer** - Multiple AI implementations
4. **Validation Layer** (`ChessRuleValidator.java`) - Move validation

### Key Components

#### ChessGame.java
Core game engine containing:
- Board state management
- Move validation and execution
- Check/checkmate detection
- Game history (undo/redo)
- AI integration

#### AI Systems
- **QLearningAI**: Reinforcement learning using Q-tables
- **DeepLearningAI**: Neural network for position evaluation
- **DeepLearningCNNAI**: Convolutional neural network for spatial pattern recognition
- **DeepQNetworkAI**: Deep Q-Network implementation
- **MonteCarloTreeSearchAI**: Classical MCTS with tree reuse
- **AlphaZeroAI**: Self-play neural network with MCTS
- **NegamaxAI**: Classical chess engine with alpha-beta pruning
- **OpenAiChessAI**: GPT-4 powered chess analysis
- **LeelaChessZeroAI**: Human game knowledge with transformer architecture
- **GeneticAlgorithmAI**: Evolutionary learning with population-based optimization
- **Opening Book**: 100+ professional chess openings from Leela Chess Zero database

#### ChessController.java
REST API providing:
- Game state endpoints
- Move execution
- AI training controls
- Game management

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.6+
- IDE (Eclipse STS recommended)

### Environment Setup
1. Clone the repository
2. Import as Maven project
3. Run `mvn clean compile`
4. Start with `ChessApplication.java`

### Configuration
Edit `application.properties`:
```properties
server.port=8081
chess.debug.enabled=true  # Enable debug logging

# AI Systems Configuration (true=enabled, false=disabled)
chess.ai.qlearning.enabled=true
chess.ai.deeplearning.enabled=true
chess.ai.deeplearningcnn.enabled=true
chess.ai.dqn.enabled=true
chess.ai.mcts.enabled=false
chess.ai.alphazero.enabled=true
chess.ai.negamax.enabled=true
chess.ai.openai.enabled=true
chess.ai.leelazerochess.enabled=true
chess.ai.genetic.enabled=true

# OpenAI API Key
chess.ai.openai=your-openai-api-key-here
```

## Code Structure

### Game Logic Flow
1. User makes move via web interface
2. `ChessController` receives REST request
3. `ChessGame.isValidMove()` validates move
4. `ChessGame.makeMove()` executes move
5. Board state updated and returned

### AI Training Flow
1. Training initiated via `/app/train` (WebSocket)
2. All enabled AI systems run parallel training
3. Learning data saved to files:
   - `chess_qtable.dat` - Q-Learning data
   - `chess_deeplearning_model.zip` - Neural network
   - `chess_cnn_model.zip` - CNN neural network
   - `chess_dqn_model.zip` - DQN model
   - `chess_dqn_target_model.zip` - DQN target network
   - `chess_dqn_experiences.dat` - DQN experience replay
   - `alphazero_cache.dat` - AlphaZero neural network cache
   - `lc0_network.zip` - Leela Chess Zero network
   - `population.dat` - Genetic Algorithm population

## Key Methods

### ChessGame.java
- `isValidMove()` - Validates chess moves
- `makeMove()` - Executes moves
- `isKingInDanger()` - Check detection
- `isPlayerInCheckmate()` - Checkmate detection
- `trainAI()` - Starts AI training

### AI Classes
- `getNextMove()` - AI move selection
- `learn()` - Learning from game outcomes
- `saveModel()` / `loadModel()` - Persistence

## Testing

### Manual Testing
1. Start application
2. Open http://localhost:8081
3. Test game features:
   - Basic moves
   - Special moves (castling, en passant)
   - Check/checkmate scenarios
   - Pawn promotion

### AI Testing Endpoints
- `/api/test-qtable` - Test Q-table functionality
- `/api/test-training` - Quick training test
- `/api/verify-qtable` - Verify persistence

## Adding New Features

### Adding New AI Algorithm
1. Create class implementing AI interface with `selectMove()` method
2. Add configuration property in `application.properties`
3. Initialize in `ChessGame.initializeAISystems()`
4. Add to parallel AI execution in `findBestMove()`
5. Add status reporting in `WebSocketController.getAIStatus()`
6. Add shutdown handling in `ChessGame.shutdown()`

### Adding New Game Rules
1. Update validation in `ChessGame.isValidMove()`
2. Add special case handling in `makeMove()`
3. Update frontend JavaScript if needed

### Adding New API Endpoints
1. Add method to `ChessController.java`
2. Use appropriate HTTP method (`@GetMapping`, `@PostMapping`)
3. Return `ResponseEntity` with proper status codes

## Performance Considerations

### Memory Management
- AI training can consume significant memory
- Implement proper cleanup in shutdown hooks
- Monitor file sizes of training data

### Concurrency
- AI training runs in background threads
- Use proper synchronization for shared state
- Implement graceful shutdown

## Debugging

### Enable Debug Logging
Set `chess.debug.enabled=true` in properties

### Common Issues
1. **AI not learning**: Check file permissions for training data
2. **Memory errors**: Reduce training batch sizes
3. **Move validation errors**: Check coordinate bounds (0-7)

## File Structure
```
chess/
├── src/main/java/com/example/chess/
│   ├── ChessApplication.java           # Main Spring Boot app
│   ├── ChessController.java            # Web interface controller
│   ├── ChessGame.java                  # Core game logic (4,360 lines)
│   ├── ChessRuleValidator.java         # Move validation
│   ├── VirtualChessBoard.java          # AI training board
│   ├── WebSocketController.java        # Real-time communication
│   ├── WebSocketConfig.java            # WebSocket configuration
│   ├── SecurityConfig.java             # Security settings
│   │
│   ├── AI Systems/
│   ├── QLearningAI.java                # Q-Learning AI
│   ├── DeepLearningAI.java             # Neural network AI
│   ├── DeepLearningCNNAI.java          # CNN AI
│   ├── DeepQNetworkAI.java             # DQN AI
│   ├── MonteCarloTreeSearchAI.java     # MCTS AI
│   ├── AlphaZeroAI.java                # AlphaZero AI
│   ├── NegamaxAI.java                  # Negamax AI
│   ├── OpenAiChessAI.java              # OpenAI GPT-4 AI
│   ├── LeelaChessZeroAI.java           # Leela Chess Zero AI
│   ├── GeneticAlgorithmAI.java         # Genetic Algorithm AI
│   └── LeelaChessZeroOpeningBook.java  # Opening database
│   │
├── src/main/resources/
│   ├── static/
│   │   ├── app.js                      # Frontend JavaScript
│   │   └── app.ts                      # TypeScript source
│   ├── templates/
│   │   └── index.html                  # Game interface
│   ├── application.properties          # Configuration
│   └── log4j2.xml                      # Logging configuration
│
└── Training data files (generated):
    ├── chess_qtable.dat                # Q-Learning data
    ├── chess_deeplearning_model.zip    # Neural network
    ├── chess_cnn_model.zip             # CNN model
    ├── chess_dqn_model.zip             # DQN model
    ├── chess_dqn_target_model.zip      # DQN target network
    ├── chess_dqn_experiences.dat       # DQN experience replay
    ├── alphazero_cache.dat             # AlphaZero cache
    ├── lc0_network.zip                 # Leela network
    └── population.dat                  # Genetic population
```

## Best Practices

### Code Style
- Use meaningful variable names
- Add JavaDoc for public methods
- Keep methods focused and small
- Handle exceptions appropriately

### AI Development
- Save training progress regularly
- Implement proper model versioning
- Monitor training metrics
- Use appropriate learning rates

### API Design
- Use proper HTTP status codes
- Validate input parameters
- Return consistent response formats
- Handle errors gracefully