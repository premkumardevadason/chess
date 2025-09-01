# ChessGame.java Sequence Diagram - REFACTORED VERSION

## Overview
This sequence diagram illustrates the main interactions and flow within the refactored ChessGame.java class, showing how user moves, AI moves, and game state management work together in the enhanced architecture.

## Main Game Flow Sequence

```mermaid
sequenceDiagram
    participant User
    participant ChessController
    participant ChessGame
    participant AISystem as AI_Systems
    participant OpeningBook as Leela_Opening_Book
    participant WebSocket
    participant GameState as Game_State_Manager
    participant RuleValidator as Chess_Rule_Validator
    participant TacticalDefense as Chess_Tactical_Defense

    Note over ChessGame: Game Initialization
    ChessGame->>ChessGame: initializeAfterInjection
    ChessGame->>ChessGame: detectAndConfigureOpenCL
    ChessGame->>OpeningBook: new LeelaChessZeroOpeningBook
    ChessGame->>ChessGame: initializeAISystems
    loop For each enabled AI (12 total systems)
        ChessGame->>AISystem: initialize AI system
        Note over AISystem: Q-Learning, Deep Learning, CNN, DQN, MCTS, AlphaZero, Negamax, OpenAI, Leela Zero, Genetic, AlphaFold3, A3C
    end
    ChessGame->>ChessGame: aiSystemsReady = true

    Note over User,GameState: User Move Flow
    User->>ChessController: makeMove
    ChessController->>ChessGame: makeMove
    ChessGame->>RuleValidator: isValidMove 9-step validation
    RuleValidator->>RuleValidator: Check coordinate bounds
    RuleValidator->>RuleValidator: Check piece ownership
    RuleValidator->>RuleValidator: Check piece movement rules
    RuleValidator->>RuleValidator: Check path blocking
    RuleValidator->>RuleValidator: Check king safety
    RuleValidator->>RuleValidator: Check tactical defense
    RuleValidator-->>ChessGame: Move validity result
    
    alt Move is valid
        ChessGame->>GameState: saveGameState()
        ChessGame->>ChessGame: execute move on board
        ChessGame->>ChessGame: updateCastlingRights()
        ChessGame->>OpeningBook: addMoveToHistory()
        ChessGame->>ChessGame: whiteTurn = false
        ChessGame->>ChessGame: start makeComputerMove thread
        ChessGame->>WebSocket: broadcastGameState()
    else Move is invalid
        ChessGame-->>ChessController: return false
    end

    Note over ChessGame,AISystem: AI Move Flow
    ChessGame->>ChessGame: makeComputerMove()
    ChessGame->>ChessGame: findBestMove()
    
    alt Opening phase moves 1-12
        ChessGame->>OpeningBook: getOpeningMove()
        OpeningBook-->>ChessGame: return opening move
    else Mid/End game
        ChessGame->>TacticalDefense: Check for critical threats
        alt King in check
            ChessGame->>ChessGame: findLegalCheckResponse()
            ChessGame->>ChessGame: getAllValidMoves(false)
            loop Test each move
                ChessGame->>ChessGame: actuallyResolvesCheck()
            end
        else Queen under attack
            ChessGame->>TacticalDefense: findProtectionMove()
            ChessGame->>TacticalDefense: findAttackersOfSquare()
        else Strategic move selection
            Note over ChessGame,AISystem: Parallel AI Execution (12 Systems)
            par Q-Learning AI
                ChessGame->>AISystem: qLearningAI selectMove
            and Deep Learning AI
                ChessGame->>AISystem: deepLearningAI selectMove
            and CNN Deep Learning AI
                ChessGame->>AISystem: deepLearningCNNAI selectMove
            and Deep Q-Network AI
                ChessGame->>AISystem: dqnAI selectMove
            and Monte Carlo Tree Search AI
                ChessGame->>AISystem: mctsAI selectMove
            and AlphaZero AI
                ChessGame->>AISystem: alphaZeroAI selectMove
            and Negamax AI
                ChessGame->>AISystem: negamaxAI selectMove
            and OpenAI Chess AI
                ChessGame->>AISystem: openAiAI selectMove
            and Leela Chess Zero AI
                ChessGame->>AISystem: leelaZeroAI selectMove
            and Genetic Algorithm AI
                ChessGame->>AISystem: geneticAI selectMove
            and AlphaFold3 AI
                ChessGame->>AISystem: alphaFold3AI selectMove
            and A3C AI
                ChessGame->>AISystem: a3cAI selectMove
            end
            
            loop For each AI move
                ChessGame->>ChessGame: validateAIMove 10-step validation
            end
            ChessGame->>ChessGame: compareMoves select best move
        end
    end

    ChessGame->>ChessGame: executeBestMove()
    ChessGame->>RuleValidator: isValidMove final validation
    ChessGame->>ChessGame: execute AI move on board
    ChessGame->>ChessGame: handlePawnPromotion()
    ChessGame->>GameState: update move history
    ChessGame->>OpeningBook: addMoveToHistory()
    ChessGame->>ChessGame: whiteTurn = true
    ChessGame->>WebSocket: broadcastGameState()

    Note over ChessGame,AISystem: Training Flow
    User->>ChessController: startTraining(games)
    ChessController->>ChessGame: trainAI(games)
    
    par Q-Learning Training
        ChessGame->>AISystem: qLearningAI trainAgainstSelfWithProgress
    and Deep Learning Training
        ChessGame->>AISystem: deepLearningAI startTraining
    and CNN Training
        ChessGame->>AISystem: deepLearningCNNAI startTraining
    and DQN Training
        ChessGame->>AISystem: dqnAI startTraining
    and AlphaZero Training
        ChessGame->>AISystem: alphaZeroAI startSelfPlayTraining
    and LeelaZero Training
        ChessGame->>AISystem: leelaZeroAI startSelfPlayTraining
    and Genetic Training
        ChessGame->>AISystem: geneticAI startTraining
    and AlphaFold3 Training
        ChessGame->>AISystem: alphaFold3AI startTraining
    and A3C Training
        ChessGame->>AISystem: a3cAI startTraining
    end

    Note over ChessGame,GameState: Game State Management
    User->>ChessController: undoMove()
    ChessController->>ChessGame: undoMove()
    ChessGame->>GameState: restore previous state
    ChessGame->>WebSocket: broadcastGameState()

    User->>ChessController: resetGame()
    ChessController->>ChessGame: resetGame()
    loop For each AI system
        ChessGame->>AISystem: addHumanGameData()
        ChessGame->>AISystem: saveState()
    end
    ChessGame->>ChessGame: initializeBoard()
    ChessGame->>OpeningBook: resetOpeningLine()
    ChessGame->>WebSocket: broadcastGameState()
```

## Enhanced Move Validation Flow

```mermaid
sequenceDiagram
    participant ChessGame
    participant RuleValidator as Chess_Rule_Validator
    participant TacticalDefense as Chess_Tactical_Defense
    participant KingSafety as King_Safety_Checker

    ChessGame->>RuleValidator: isValidMove
    RuleValidator->>RuleValidator: Check coordinate bounds
    RuleValidator->>RuleValidator: Check piece ownership
    RuleValidator->>RuleValidator: Check piece movement rules
    RuleValidator->>RuleValidator: Check path blocking
    RuleValidator->>KingSafety: Simulate move
    KingSafety->>KingSafety: Check if King exposed to check
    KingSafety-->>RuleValidator: King safety result
    RuleValidator->>TacticalDefense: Check tactical defense
    TacticalDefense->>TacticalDefense: Validate move against tactical threats
    TacticalDefense-->>RuleValidator: Tactical validation result
    RuleValidator-->>ChessGame: Move validity result
```

## AI Move Selection Flow (12 Systems)

```mermaid
sequenceDiagram
    participant ChessGame
    participant AIValidator as AI Move Validator
    participant Comparator as Move Comparator
    participant AISystems as 12_AI_Systems

    ChessGame->>ChessGame: Get moves from all 12 AI systems
    loop For each AI system
        ChessGame->>AISystems: selectMove (parallel execution)
        AISystems-->>ChessGame: AI move suggestion
        ChessGame->>AIValidator: validateAIMove
        AIValidator->>AIValidator: 10-step validation process
        AIValidator-->>ChessGame: Validated move or null
    end
    ChessGame->>Comparator: compareMoves
    Comparator->>Comparator: evaluateMoveQuality for each move
    Comparator->>Comparator: Apply AI-specific scoring weights
    Comparator-->>ChessGame: Best move selected
```

## Enhanced Checkmate Detection Flow

```mermaid
sequenceDiagram
    participant ChessGame
    participant CheckDetector as Check Detector
    participant MoveGenerator as Move Generator
    participant TacticalDefense as Tactical Defense

    ChessGame->>CheckDetector: isPlayerInCheckmate
    CheckDetector->>CheckDetector: isKingInDanger
    alt King not in check
        CheckDetector-->>ChessGame: false (not checkmate)
    else King in check
        CheckDetector->>MoveGenerator: getAllValidMoves
        loop For each valid move
            CheckDetector->>CheckDetector: Simulate move
            CheckDetector->>TacticalDefense: Check tactical implications
            CheckDetector->>CheckDetector: Check if King still in danger
            alt King safe after move
                CheckDetector-->>ChessGame: false (not checkmate)
            end
        end
        CheckDetector-->>ChessGame: true (checkmate - no legal moves)
    end
```

## Class Relationships and Dependencies (Refactored)

### Core Dependencies
- **Spring Framework**: `@Component`, `@PostConstruct`, `@Value` annotations
- **Logging**: Apache Log4j2 for comprehensive logging
- **AI Systems**: 12 different AI implementations (increased from 10)
- **Opening Book**: Leela Chess Zero professional opening database (extended to 6 moves)
- **WebSocket**: Real-time communication with frontend
- **Rule Validation**: Dedicated `ChessRuleValidator` class
- **Tactical Defense**: Dedicated `ChessTacticalDefense` class

### AI System Integration (Updated)
1. **Q-Learning AI**: Reinforcement learning with experience replay
2. **Deep Learning AI**: Neural network with GPU acceleration
3. **CNN Deep Learning AI**: Convolutional neural network for spatial patterns
4. **Deep Q-Network AI**: Deep reinforcement learning
5. **Monte Carlo Tree Search AI**: Classical MCTS with tree reuse
6. **AlphaZero AI**: Self-play neural network with MCTS
7. **Negamax AI**: Classical chess engine with alpha-beta pruning
8. **OpenAI Chess AI**: GPT-4 powered chess analysis
9. **Leela Chess Zero AI**: Human game knowledge with transformer architecture
10. **Genetic Algorithm AI**: Evolutionary learning approach
11. **AlphaFold3 AI**: Protein structure prediction adapted for chess
12. **A3C AI**: Asynchronous Advantage Actor-Critic reinforcement learning

### New Architectural Components
- **ChessRuleValidator**: Dedicated move validation logic
- **ChessTacticalDefense**: Advanced tactical analysis and defense
- **Enhanced Opening Book**: Extended from 4 to 6 moves depth
- **Improved Move Validation**: 9-step comprehensive checking process
- **Better Error Handling**: Comprehensive exception management
- **Performance Optimization**: Parallel AI execution and caching

## File Storage Location

This sequence diagram is stored at:
```
CHESS/docs/ChessGame_Sequence_Diagram.md
```

## Additional Documentation

For more detailed information about specific components:
- **AI Systems**: See individual AI class documentation
- **Opening Book**: `LeelaChessZeroOpeningBook.java`
- **WebSocket Communication**: `WebSocketController.java`
- **Game Rules**: `ChessRuleValidator.java` and `ChessTacticalDefense.java`
- **Training System**: `TrainingManager.java` and AI training coordination methods
- **MCP Integration**: See `MCP_Sequence_Diagram.md`

## Refactoring Notes

- **AI Systems**: Increased from 10 to 12 systems with AlphaFold3 and A3C
- **Architecture**: Separated concerns into dedicated validator and tactical defense classes
- **Performance**: Enhanced parallel execution and caching mechanisms
- **Validation**: Improved move validation from 9-step to 10-step process
- **Opening Book**: Extended depth from 4 to 6 moves for better opening play
- **Error Handling**: Comprehensive exception management and logging
- **Code Organization**: Better separation of concerns and modularity