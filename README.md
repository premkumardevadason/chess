# Chess Web Game

A sophisticated browser-based Chess game built with Spring Boot, featuring multiple AI opponents with advanced machine learning capabilities including AlphaZero, Leela Chess Zero, AlphaFold3-inspired diffusion modeling, and classical chess engines.

## Features

- **Complete Chess Implementation**: Full FIDE chess rules including castling, en passant, pawn promotion, check/checkmate detection
- **Advanced AI Systems**: 
  - **AlphaZero AI**: Self-play neural network with MCTS (episodes-based training)
  - **Leela Chess Zero AI**: Human game knowledge with transformer architecture
  - **AlphaFold3 AI**: Diffusion modeling with pairwise attention for piece cooperation
  - **Monte Carlo Tree Search**: Classical MCTS with tree reuse optimization
  - **Negamax AI**: Classical chess engine with alpha-beta pruning
  - **Q-Learning AI**: Reinforcement learning with experience replay
  - **Deep Learning AI**: Neural network position evaluation
  - **CNN Deep Learning AI**: Convolutional neural network for spatial pattern recognition
  - **Deep Q-Network (DQN)**: Deep reinforcement learning
  - **Genetic Algorithm AI**: Evolutionary learning with population-based optimization
  - **OpenAI Chess AI**: GPT-4 powered chess analysis
- **Professional Opening Book**: Leela Chess Zero opening database with 100+ grandmaster openings
- **Real-time Web Interface**: WebSocket-based gameplay with live AI training visualization
- **GPU Acceleration**: OpenCL support for AMD GPUs, CUDA for NVIDIA
- **Training System**: Self-play training with progress monitoring and user game data collection
- **Board Position Storage**: Saves user vs AI game positions for enhanced training datasets
- **Move History**: Complete undo/redo with game state management
- **Advanced Features**: King safety detection, piece threat analysis, position evaluation

## Technology Stack

- **Backend**: Spring Boot 3.2.0, Java 21
- **Frontend**: Thymeleaf, JavaScript/TypeScript, WebSocket
- **AI/ML**: DeepLearning4J, ND4J, OpenCL, CUDA
- **External APIs**: OpenAI GPT-4 integration
- **Build Tool**: Maven
- **Security**: CSRF protection, rate limiting, input validation

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- Optional: AMD GPU with OpenCL or NVIDIA GPU with CUDA for acceleration
- Optional: OpenAI API key for GPT-4 chess AI

### Running the Game

#### Using Maven
```bash
mvn spring-boot:run
```

#### From Eclipse STS
Right-click `ChessApplication.java` → Run As → Spring Boot App

#### Access the Game
Open your browser to: http://localhost:8081

## Architecture Overview

### Core Classes

#### Game Engine
- **ChessApplication.java** - Spring Boot main application with graceful shutdown
- **ChessGame.java** - Core game logic, move validation, AI coordination (4,360 lines)
- **ChessController.java** - Web controller for game interface
- **ChessRuleValidator.java** - Chess rule validation and training data verification
- **VirtualChessBoard.java** - Isolated board for AI training with opening book integration

#### AI Systems

##### Advanced AI (Neural Networks)
- **AlphaZeroAI.java** - Self-play neural network with MCTS search
- **AlphaZeroMCTS.java** - Monte Carlo Tree Search for AlphaZero
- **AlphaZeroNeuralNetwork.java** - Neural network for position/policy evaluation
- **AlphaZeroTrainer.java** - Self-play training system

- **LeelaChessZeroAI.java** - Leela Chess Zero implementation
- **LeelaChessZeroMCTS.java** - Enhanced MCTS with chess optimizations
- **LeelaChessZeroNetwork.java** - Transformer-based neural architecture
- **LeelaChessZeroTrainer.java** - Human game knowledge training

- **AlphaFold3AI.java** - AlphaFold3-inspired diffusion modeling for chess
- **Diffuser** - Stochastic trajectory refinement with Markov chain sampling
- **PieceFormer** - Pairwise attention mechanism for piece cooperation
- **Evaluator** - Position evaluation for checkmate proximity
- **Sampler** - Legal move transitions in continuous latent space

##### Classical AI
- **MonteCarloTreeSearchAI.java** - Pure MCTS with tree reuse and AI integration
- **NegamaxAI.java** - Classical chess engine with alpha-beta pruning, iterative deepening
- **OpenAiChessAI.java** - GPT-4 powered chess analysis with strategic reasoning

##### Machine Learning AI
- **QLearningAI.java** - Reinforcement learning with comprehensive chess evaluation
- **DeepLearningAI.java** - Neural network position evaluation with GPU support
- **DeepLearningCNNAI.java** - Convolutional neural network for spatial pattern recognition
- **DeepQNetworkAI.java** - Deep Q-Network with experience replay
- **GeneticAlgorithmAI.java** - Evolutionary AI with population-based learning

#### Supporting Systems
- **LeelaChessZeroOpeningBook.java** - Professional opening database (100+ openings)
- **OpenCLDetector.java** - GPU detection and configuration
- **WebSocketController.java** - Real-time communication with rate limiting
- **WebSocketConfig.java** - WebSocket configuration with security
- **SecurityConfig.java** - Security headers and CSRF protection

#### Utilities
- **TrainingDataMigration.java** - Training data migration utilities
- **TrainingDataReset.java** - Training data cleanup and validation
- **CheckTrainingScenarios.java** - Structured training scenarios
- **WebSocketExceptionHandler.java** - WebSocket error handling
- **WebSocketSecurityInterceptor.java** - WebSocket security

## Project Structure

```
src/main/java/com/example/chess/
├── ChessApplication.java           # Spring Boot main application
├── ChessController.java            # Web interface controller
├── ChessGame.java                  # Core game engine (4,360 lines)
├── ChessRuleValidator.java         # Rule validation system
├── VirtualChessBoard.java          # AI training board
│
├── AI Systems/
│   ├── AlphaZeroAI.java           # AlphaZero implementation
│   ├── AlphaZeroMCTS.java         # AlphaZero MCTS
│   ├── AlphaZeroNeuralNetwork.java # AlphaZero neural network
│   ├── AlphaZeroTrainer.java      # AlphaZero training
│   ├── LeelaChessZeroAI.java      # Leela Chess Zero
│   ├── LeelaChessZeroMCTS.java    # Leela MCTS
│   ├── LeelaChessZeroNetwork.java # Leela neural network
│   ├── LeelaChessZeroTrainer.java # Leela training
│   ├── AlphaFold3AI.java          # AlphaFold3 diffusion modeling
│   ├── MonteCarloTreeSearchAI.java # Classical MCTS
│   ├── NegamaxAI.java             # Classical chess engine
│   ├── OpenAiChessAI.java         # GPT-4 chess AI
│   ├── QLearningAI.java           # Q-Learning system
│   ├── DeepLearningAI.java        # Deep learning AI
│   ├── DeepLearningCNNAI.java     # CNN deep learning AI
│   ├── DeepQNetworkAI.java        # Deep Q-Network
│   └── GeneticAlgorithmAI.java    # Genetic Algorithm AI
│
├── Support Systems/
│   ├── LeelaChessZeroOpeningBook.java # Opening database
│   ├── OpenCLDetector.java            # GPU detection
│   ├── WebSocketController.java       # Real-time communication
│   ├── WebSocketConfig.java           # WebSocket setup
│   ├── SecurityConfig.java            # Security configuration
│   └── Training utilities...
│
src/main/resources/
├── static/
│   ├── app.js                     # Frontend JavaScript
│   └── app.ts                     # TypeScript source
├── templates/
│   └── index.html                 # Game interface
├── application.properties         # Configuration
└── log4j2.xml                     # Logging configuration
```

## API Endpoints

### WebSocket Endpoints (Real-time)
- `WS /ws` - WebSocket connection endpoint
- `/app/move` - Make a chess move
- `/app/newgame` - Start new game
- `/app/undo` - Undo last move
- `/app/redo` - Redo move
- `/app/validate` - Validate move
- `/app/board` - Get current board state
- `/app/train` - Start AI training
- `/app/stop-training` - Stop AI training
- `/app/ai-status` - Get AI system status
- `/app/training-progress` - Get training progress
- `/app/delete-training` - Delete training data

### WebSocket Topics (Subscriptions)
- `/topic/gameState` - Game state updates
- `/topic/training` - Training status messages
- `/topic/trainingProgress` - Real-time training progress
- `/topic/trainingBoard` - Live training board visualization
- `/topic/aiStatus` - AI system status updates
- `/topic/validation` - Move validation results

## Configuration

Edit `application.properties` to customize:
```properties
server.port=8081
chess.debug.enabled=false
openai.api.key=your-openai-api-key-here

# AI Systems Configuration
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
chess.ai.alphafold3.enabled=true
```

## AI Systems Overview

### Advanced Neural Network AI

1. **AlphaZero AI**
   - Self-play neural network training
   - Monte Carlo Tree Search guided by neural network
   - Policy and value network architecture
   - Learns from scratch without human games

2. **Leela Chess Zero AI**
   - Based on human grandmaster games
   - Transformer-based neural architecture
   - Enhanced MCTS with chess-specific optimizations
   - Professional opening book integration

### Classical Chess AI

3. **Monte Carlo Tree Search**
   - Pure MCTS implementation
   - Tree reuse optimization
   - Integration with other AI systems for move evaluation
   - Adaptive performance based on success rate

4. **Negamax Engine**
   - Classical chess engine with alpha-beta pruning
   - Iterative deepening (depth 1-6)
   - Advanced position evaluation
   - Transposition table for move caching
   - Time-bounded search (5 seconds)

5. **OpenAI Chess AI**
   - GPT-4 powered chess analysis
   - Natural language chess understanding
   - Strategic reasoning via large language model
   - FEN notation processing

### Machine Learning AI

6. **Q-Learning AI**
   - Reinforcement learning with Q-tables
   - Comprehensive chess position evaluation
   - Experience replay and move history tracking
   - Balanced attack/defense strategy

7. **Deep Learning AI**
   - Neural network position evaluation
   - GPU acceleration support (OpenCL/CUDA)
   - Batch training optimization
   - Real-time training with progress monitoring

8. **CNN Deep Learning AI**
   - Convolutional neural network for spatial pattern recognition
   - 8x8x12 tensor input (12 channels for piece types)
   - 3 convolutional layers + pooling + dense layers
   - GPU acceleration support (OpenCL/CUDA)
   - Game data learning from AI vs User games with board position storage

9. **Deep Q-Network (DQN)**
   - Deep reinforcement learning
   - Experience replay buffer
   - Target network for stable training
   - Integration with opening book

10. **Genetic Algorithm AI**
   - Evolutionary learning approach
   - Population-based optimization
   - Chromosome persistence and mutation
   - Multi-generational improvement

11. **AlphaFold3 AI**
   - Diffusion modeling inspired by AlphaFold3
   - Stochastic trajectory refinement with 10-step diffusion process
   - PieceFormer attention mechanism for inter-piece cooperation
   - Continuous latent space for move interpolation
   - Persistent learning from user games and self-play training
   - Position evaluation and trajectory memory

### Training Features

- **Self-Play Training**: All AI systems support self-play training
- **User Game Learning**: AI systems learn from user vs AI game positions and outcomes
- **Board Position Storage**: Automatic saving of game positions for training datasets
- **Real-time Monitoring**: Live training progress via WebSocket
- **GPU Acceleration**: Automatic detection and configuration
- **Opening Book Integration**: Professional opening database
- **Training Data Management**: Save/load/delete training data
- **Graceful Shutdown**: Safe training interruption and data saving

## Advanced Features

### GPU Acceleration
- **OpenCL Support**: Automatic AMD GPU detection and configuration
- **CUDA Support**: NVIDIA GPU acceleration
- **Fallback**: Optimized CPU backend when GPU unavailable
- **Memory Management**: Efficient memory usage for large neural networks

### Opening Book System
- **Professional Database**: 100+ grandmaster openings from Leela Chess Zero
- **Statistical Selection**: Weighted move selection based on game frequency
- **Training Integration**: Opening diversity for AI training with user game position data
- **Real-time Lookup**: Fast opening move suggestions during gameplay

### Security Features
- **Rate Limiting**: 10 requests per second per WebSocket session
- **Input Validation**: Chess coordinate validation and sanitization
- **CSRF Protection**: Security headers and content security policy
- **Error Handling**: Graceful error handling with sanitized messages

### Performance Optimizations
- **Batch Processing**: Efficient neural network training
- **Memory Reuse**: Optimized array allocation
- **Threading**: Asynchronous AI processing
- **Caching**: Transposition tables and move caching
- **Tree Reuse**: MCTS tree preservation between moves

## Development

### Building
```bash
mvn clean compile
```

### Running Tests
```bash
mvn test
```

### Packaging
```bash
mvn clean package
```

### GPU Setup (Optional)

#### AMD GPU (OpenCL)
1. Install AMD GPU drivers
2. Install OpenCL runtime
3. Application will auto-detect and configure

#### NVIDIA GPU (CUDA)
1. Install NVIDIA drivers
2. Install CUDA toolkit
3. Application will auto-detect and configure

### OpenAI Integration (Optional)
1. Get OpenAI API key from https://platform.openai.com/
2. Add to `application.properties`: `openai.api.key=your-key-here`
3. GPT-4 chess AI will be available

## Troubleshooting

### Common Issues

1. **Out of Memory**: Increase JVM heap size with `-Xmx4g`
2. **GPU Not Detected**: Check driver installation and OpenCL/CUDA setup
3. **Training Slow**: Enable GPU acceleration or reduce batch size
4. **WebSocket Errors**: Check firewall settings for port 8081

### Performance Tuning

- **Memory**: Allocate 4-8GB heap for large neural networks
- **CPU**: Use all available cores for MCTS simulations
- **GPU**: Enable GPU acceleration for 10-100x training speedup
- **Storage**: Use SSD for faster model loading/saving

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Code Style
- Follow Java naming conventions
- Add comprehensive JavaDoc comments
- Include error handling and logging
- Write unit tests for new features

## License

This project is open source and available under the MIT License.

## Class Documentation

### Core Engine Classes

| Class | Lines | Purpose |
|-------|-------|----------|
| ChessGame.java | 4,360 | Main game engine with complete chess rules, AI coordination, move validation |
| ChessController.java | 113 | Web interface controller with WebSocket integration |
| ChessRuleValidator.java | 150 | Chess rule validation and training data verification |
| VirtualChessBoard.java | 142 | Isolated chess board for AI training with opening book |

### AI System Classes

| Class | Lines | Purpose |
|-------|-------|----------|
| AlphaZeroAI.java | 174 | AlphaZero self-play neural network implementation |
| LeelaChessZeroAI.java | 245 | Leela Chess Zero with human game knowledge |
| AlphaFold3AI.java | 420 | AlphaFold3-inspired diffusion modeling with persistent learning |
| MonteCarloTreeSearchAI.java | 638 | Classical MCTS with tree reuse and AI integration |
| NegamaxAI.java | 436 | Classical chess engine with alpha-beta pruning |
| OpenAiChessAI.java | 227 | GPT-4 powered chess analysis |
| QLearningAI.java | 1,891 | Comprehensive Q-Learning with chess evaluation |
| DeepLearningAI.java | 821 | Neural network AI with GPU acceleration |
| DeepLearningCNNAI.java | 722 | CNN AI with spatial pattern recognition |
| DeepQNetworkAI.java | 503 | Deep Q-Network with experience replay |
| GeneticAlgorithmAI.java | 464 | Genetic Algorithm with evolutionary learning |

### Support System Classes

| Class | Lines | Purpose |
|-------|-------|----------|
| LeelaChessZeroOpeningBook.java | 335 | Professional opening database with 100+ openings |
| OpenCLDetector.java | 80 | GPU detection and system capability analysis |
| WebSocketController.java | 501 | Real-time communication with security and rate limiting |
| SecurityConfig.java | 37 | Security configuration and headers |

### Total Project Statistics
- **Total Classes**: 38 Java classes
- **Total Lines of Code**: 14,944 lines
- **AI Systems**: 11 different AI implementations
- **Opening Database**: 100+ professional chess openings
- **Features**: Complete chess rules, GPU acceleration, real-time training, parallel AI execution, diffusion modeling

This comprehensive chess application demonstrates advanced software engineering with multiple AI paradigms, real-time web interfaces, GPU acceleration, and professional chess knowledge integration.

## Recent Enhancements

### Latest Bug Fixes & Improvements
- **AlphaFold3 AI Integration**: Added new diffusion modeling AI with persistent learning capabilities
- **Persistent State Management**: AlphaFold3AI now saves/loads position evaluations and learned trajectories
- **User Game Learning**: AlphaFold3AI learns from user vs AI games with automatic state persistence
- **Compilation Fix**: Resolved duplicate `copyBoard` method in `DeepLearningCNNAI.java` causing Maven build failures
- **Checkmate Detection**: Fixed AI not detecting checkmate when protected pieces give check - updated `isPlayerInCheckmate` to use proper legal move validation
- **Training Broadcasting**: Clarified that only Q-Learning AI broadcasts live training moves to UI, other AIs train silently on virtual boards
- **Virtual Board Usage**: `VirtualChessBoard` in `addHumanGameData` methods reconstructs actual User vs AI games by replaying move history sequentially
- **Log Cleanup**: Removed unnecessary startup log messages for cleaner application initialization

### AlphaFold3 AI Integration
- **Diffusion Modeling**: Novel approach treating chessboard as dynamic state lattice with continuous piece values
- **Stochastic Refinement**: 10-step diffusion process evolving random trajectories toward strategic configurations
- **PieceFormer Attention**: Pairwise attention mechanism modeling inter-piece cooperation (Knight+Queen, Bishop pairs)
- **Persistent Learning**: Position evaluations and successful trajectories saved to `alphafold3_state.dat`
- **User Game Integration**: Learns from human vs AI games with automatic outcome-based evaluation updates
- **Biased Trajectory Generation**: 70% probability of using learned patterns vs random exploration
- **Parallel Execution**: Integrated with existing 10 AI systems for move comparison

### CNN Deep Learning AI Integration
- **New AI System**: Added convolutional neural network for spatial pattern recognition
- **8x8x12 Tensor Input**: 12 channels representing different piece types for better positional understanding
- **Architecture**: 3 convolutional layers + max pooling + dense layers for position evaluation
- **Training Integration**: Uses same Lc0 opening book and learns from AI vs User game data
- **Parallel Execution**: Integrated with existing AI systems for move comparison

### Enhanced AI Status Reporting
- **Comprehensive Display**: Shows all 11 AI systems with enabled/disabled status
- **Detailed Metrics**: Training iterations, model status, backend information, cache sizes
- **GPU Status**: AMD OpenCL and NVIDIA CUDA detection and configuration
- **Terminology Fix**: AlphaZero now correctly shows "Episodes" instead of "Iterations"
- **AlphaFold3 Status**: Shows training episodes and current status (Ready/Training)
- **Summary Count**: "X/11 AI SYSTEMS ENABLED" for quick overview

### Improved Training System
- **Unified Training**: All AI systems train concurrently with single command
- **WebSocket Integration**: Real-time training progress and status updates (Q-Learning AI only)
- **Thread Management**: Proper startup, shutdown, and interruption handling
- **Data Persistence**: Automatic saving of all AI training data during game reset
- **Configuration Control**: Individual AI systems can be enabled/disabled via properties
- **Virtual Board Training**: Most AIs train silently using `VirtualChessBoard` for game replay
- **User Game Data**: AI systems learn from actual user vs AI games with saved board positions

### Architecture Improvements
- **SOLID Principles**: Selected AI systems refactored following SOLID design principles
- **Parallel AI Execution**: All enabled AIs run simultaneously for move selection
- **Move Validation**: 9-step comprehensive validation prevents illegal moves
- **Checkmate Logic**: Enhanced legal move checking for accurate game state detection
- **Race Condition Protection**: Thread-safe AI initialization and ready state management
- **Memory Optimization**: Efficient tensor operations and model loading
- **Error Handling**: Graceful degradation when AI systems fail or timeout
- **User Game Integration**: Board positions from user games automatically saved for AI training