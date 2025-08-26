# AI Unit Test Cases - CHESS Project

## Overview

This document provides comprehensive test cases for validating the CHESS project functionality, covering the core game engine, 12 AI systems, NIO.2 async infrastructure, and web interface components.

## Test Structure

### Test Directory Layout
```
src/test/java/com/example/chess/
├── unit/
│   ├── ChessGameTest.java
│   ├── ChessRuleValidatorTest.java
│   ├── ChessTacticalDefenseTest.java
│   └── ai/
│       ├── QLearningAITest.java
│       ├── DeepLearningAITest.java
│       ├── DeepLearningCNNAITest.java
│       ├── DeepQNetworkAITest.java
│       ├── AlphaZeroAITest.java
│       ├── LeelaChessZeroAITest.java
│       ├── AlphaFold3AITest.java
│       ├── AsynchronousAdvantageActorCriticAITest.java
│       ├── MonteCarloTreeSearchAITest.java
│       ├── NegamaxAITest.java
│       ├── OpenAiChessAITest.java
│       └── GeneticAlgorithmAITest.java
├── integration/
│   ├── AIIntegrationTest.java
│   ├── WebSocketIntegrationTest.java
│   ├── AsyncIOIntegrationTest.java
│   └── GameFlowIntegrationTest.java
├── performance/
│   ├── AITrainingPerformanceTest.java
│   └── GamePerformanceTest.java
└── fixtures/
    ├── ChessPositions.java
    ├── TestGameStates.java
    └── AITestData.java
```

## 1. Core Game Engine Tests

### ChessGameTest.java
**Purpose**: Validate core chess game logic (4,360 lines)

**Test Cases:**
- `testInitialBoardSetup()` - Verify correct piece placement
- `testBasicPieceMovement()` - Test all piece movement patterns
- `testSpecialMoves()` - Castling, en passant, pawn promotion
- `testCheckDetection()` - King in check scenarios
- `testCheckmateDetection()` - Scholar's Mate, Fool's Mate, Back Rank Mate
- `testStalemateDetection()` - Draw conditions
- `testUndoRedoFunctionality()` - Game history management
- `testGameStateValidation()` - Board state consistency

### ChessRuleValidatorTest.java
**Purpose**: Validate FIDE chess rule compliance

**Test Cases:**
- `testMoveValidation()` - Legal/illegal move detection
- `testPinnedPieceMovement()` - Pieces pinned to king
- `testDiscoveredCheck()` - Moving piece reveals check
- `testCastlingRules()` - All castling conditions
- `testEnPassantRules()` - En passant capture validation
- `testPawnPromotionRules()` - Promotion piece selection

### ChessTacticalDefenseTest.java
**Purpose**: Validate tactical defense system

**Test Cases:**
- `testCheckmatePreventionScholars()` - Scholar's Mate defense
- `testCheckmatePreventionFools()` - Fool's Mate defense
- `testQueenSafetyDetection()` - Queen threat analysis
- `testForkDefenseStrategies()` - Multi-piece fork handling
- `testCriticalDefenseDetection()` - Defense move prioritization

## 2. AI System Tests

### QLearningAITest.java
**Purpose**: Validate Q-Learning reinforcement learning

**Test Cases:**
- `testQTableInitialization()` - Empty Q-table creation
- `testQTablePersistence()` - Save/load `chess_qtable.dat`
- `testLearningProgression()` - Q-value updates over games
- `testMoveSelection()` - Epsilon-greedy strategy
- `testTrainingPerformance()` - 20-50 games/second target
- `testMemoryManagement()` - Q-table size optimization

### DeepLearningAITest.java
**Purpose**: Validate neural network position evaluation

**Test Cases:**
- `testModelInitialization()` - Neural network creation
- `testModelPersistence()` - Save/load `chess_deeplearning_model.zip`
- `testGPUDetection()` - OpenCL/CUDA availability
- `testBatchTraining()` - 128 batch size processing
- `testPositionEvaluation()` - Board position scoring
- `testModelCorruptionRecovery()` - Backup restoration

### DeepLearningCNNAITest.java
**Purpose**: Validate convolutional neural network

**Test Cases:**
- `testCNNModelInitialization()` - 8x8x12 tensor input
- `testSpatialPatternRecognition()` - Piece pattern detection
- `testModelPersistence()` - Save/load `chess_cnn_model.zip`
- `testConvolutionalLayers()` - Layer functionality
- `testGPUAcceleration()` - OpenCL/CUDA performance
- `testGameDataLearning()` - User vs AI position storage

### DeepQNetworkAITest.java
**Purpose**: Validate Deep Q-Network implementation

**Test Cases:**
- `testDualNetworkInitialization()` - Main/target networks
- `testExperienceReplay()` - Buffer management
- `testModelPersistence()` - Save/load dual models + experiences
- `testTargetNetworkSync()` - Network synchronization
- `testTrainingStability()` - Learning convergence
- `testMemoryEfficiency()` - Experience buffer optimization

### AlphaZeroAITest.java
**Purpose**: Validate self-play neural network with MCTS

**Test Cases:**
- `testSelfPlayTraining()` - Episode-based learning
- `testMCTSIntegration()` - Tree search guidance
- `testNeuralNetworkPersistence()` - Model save/load
- `testPolicyValueOutputs()` - Network predictions
- `testTrainingConvergence()` - Learning progression
- `testTreeReuse()` - MCTS optimization

### LeelaChessZeroAITest.java
**Purpose**: Validate Leela Chess Zero implementation

**Test Cases:**
- `testOpeningBookIntegration()` - 100+ professional openings
- `testTransformerArchitecture()` - Neural network structure
- `testHumanGameKnowledge()` - Grandmaster game learning
- `testMCTSOptimization()` - Chess-specific enhancements
- `testModelPersistence()` - Network state save/load
- `testOpeningSelection()` - Statistical move weighting

### AlphaFold3AITest.java
**Purpose**: Validate diffusion modeling for chess

**Test Cases:**
- `testDiffusionProcess()` - 10-step trajectory refinement
- `testPieceFormerAttention()` - Inter-piece cooperation
- `testContinuousLatentSpace()` - Move interpolation
- `testStatePersistence()` - Compressed state save/load
- `testPositionEvaluation()` - Diffusion-based scoring
- `testTrajectoryMemory()` - Learning from trajectories

### AsynchronousAdvantageActorCriticAITest.java
**Purpose**: Validate A3C multi-worker reinforcement learning

**Test Cases:**
- `testMultiWorkerTraining()` - 6 asynchronous workers
- `testActorCriticNetworks()` - Separate policy/value networks
- `testAdvantageEstimation()` - A3C advantage calculation
- `testGlobalNetworkUpdates()` - Shared network synchronization
- `testModelPersistence()` - Compressed model storage
- `testIndependentTraining()` - No Q-Learning dependency

### MonteCarloTreeSearchAITest.java
**Purpose**: Validate classical MCTS implementation

**Test Cases:**
- `testTreeConstruction()` - Node creation and expansion
- `testUCB1Selection()` - Upper confidence bound formula
- `testSimulationAccuracy()` - Random playout quality
- `testTreeReuse()` - Optimization between moves
- `testPerformanceScaling()` - Simulation count vs strength
- `testStatelessOperation()` - No persistent state requirement

### NegamaxAITest.java
**Purpose**: Validate classical chess engine

**Test Cases:**
- `testAlphaBetaPruning()` - Search tree optimization
- `testIterativeDeepening()` - Depth 1-6 search
- `testPositionEvaluation()` - Static evaluation function
- `testTranspositionTable()` - Move caching effectiveness
- `testTimeBoundedSearch()` - 5-second time limit
- `testMoveOrdering()` - Search efficiency optimization

### OpenAiChessAITest.java
**Purpose**: Validate GPT-4 chess integration

**Test Cases:**
- `testAPIIntegration()` - OpenAI API connectivity
- `testFENProcessing()` - Board position notation
- `testStrategicReasoning()` - Move explanation quality
- `testErrorHandling()` - API failure graceful fallback
- `testTimeoutHandling()` - Async call timeout management
- `testAPIKeyValidation()` - Configuration verification

### GeneticAlgorithmAITest.java
**Purpose**: Validate evolutionary learning

**Test Cases:**
- `testPopulationInitialization()` - Initial chromosome generation
- `testFitnessEvaluation()` - Chromosome scoring
- `testMutationOperations()` - Genetic variation
- `testCrossoverOperations()` - Genetic recombination
- `testGenerationalImprovement()` - Evolution progression
- `testPopulationPersistence()` - Save/load population data

## 3. NIO.2 Async I/O Tests

### AsyncIOIntegrationTest.java
**Purpose**: Validate asynchronous file operations

**Test Cases:**
- `testParallelAsyncSave()` - Concurrent AI data saves
- `testRaceConditionPrevention()` - File-level synchronization
- `testStreamBridgeCompatibility()` - DeepLearning4J integration
- `testAsyncLoadPerformance()` - 20-40% startup improvement
- `testCorruptionHandling()` - File integrity protection
- `testGracefulFallback()` - Sync operation fallback

### AtomicFeatureCoordinatorTest.java
**Purpose**: Validate concurrency coordination

**Test Cases:**
- `testExclusiveAccess()` - Single writer coordination
- `testSharedAccess()` - Multiple reader coordination
- `testDeadlockPrevention()` - Concurrent access safety
- `testPerformanceUnderContention()` - High load behavior

## 4. Web Interface Tests

### WebSocketIntegrationTest.java
**Purpose**: Validate real-time communication

**Test Cases:**
- `testConnectionEstablishment()` - WebSocket handshake
- `testGameStateUpdates()` - Real-time board updates
- `testTrainingProgressBroadcast()` - AI training status
- `testRateLimiting()` - 10 requests/second enforcement
- `testErrorHandling()` - Connection failure recovery
- `testSecurityValidation()` - CSRF protection

### GameFlowIntegrationTest.java
**Purpose**: Validate end-to-end game flow

**Test Cases:**
- `testCompleteGameFlow()` - Start to checkmate
- `testAIVsUserGame()` - Human vs AI gameplay
- `testMultipleAISelection()` - AI opponent switching
- `testGameStateConsistency()` - State synchronization
- `testTrainingDataCollection()` - Position storage for AI learning

## 5. Performance Tests

### AITrainingPerformanceTest.java
**Purpose**: Validate training performance targets

**Test Cases:**
- `testQLearningSpeed()` - 20-50 games/second target
- `testDQNSpeed()` - 10-20 steps/second target
- `testMemoryUsage()` - Heap utilization optimization
- `testGPUAcceleration()` - OpenCL/CUDA performance boost
- `testConcurrentTraining()` - Multiple AI systems parallel training
- `testStartupTime()` - Application initialization < 30 seconds

### GamePerformanceTest.java
**Purpose**: Validate game response times

**Test Cases:**
- `testMoveValidationSpeed()` - < 100ms response time
- `testAIMoveSelection()` - < 5 seconds AI thinking time
- `testBoardStateUpdates()` - < 50ms state synchronization
- `testConcurrentUsers()` - Multiple simultaneous games

## Running Test Cases

### Prerequisites
```bash
# Ensure Java 21 and Maven are installed
java -version
mvn -version

# Navigate to project root
cd CHESS
```

### Execute All Tests
```bash
# Run all test suites
mvn test

# Run with detailed output
mvn test -Dtest.verbose=true

# Run specific test class
mvn test -Dtest=ChessGameTest

# Run specific test method
mvn test -Dtest=ChessGameTest#testInitialBoardSetup
```

### Execute Test Categories
```bash
# Unit tests only
mvn test -Dtest="**/unit/**/*Test"

# Integration tests only
mvn test -Dtest="**/integration/**/*Test"

# Performance tests only
mvn test -Dtest="**/performance/**/*Test"

# AI system tests only
mvn test -Dtest="**/ai/**/*Test"
```

### Test Configuration
```properties
# src/test/resources/application-test.properties
server.port=0
chess.debug.enabled=true
chess.test.mode=true

# Disable external dependencies for testing
chess.ai.openai.enabled=false
chess.gpu.detection.enabled=false

# Use in-memory storage for tests
chess.test.data.path=target/test-data
```

## Evaluating Test Results

### Success Criteria

#### Unit Tests (85%+ Pass Rate Required)
- **PASS**: All assertions succeed, no exceptions thrown
- **FAIL**: Assertion failures, unexpected exceptions, timeouts

#### Integration Tests (90%+ Pass Rate Required)
- **PASS**: Component interactions work correctly
- **FAIL**: Communication failures, state inconsistencies

#### Performance Tests (100% Pass Rate Required)
- **PASS**: All performance targets met
- **FAIL**: Any performance target missed

### Test Result Analysis

#### Maven Test Report
```bash
# Generate detailed test report
mvn surefire-report:report

# View report at: target/site/surefire-report.html
```

#### Key Metrics to Monitor
- **Test Coverage**: Minimum 85% for core components
- **Execution Time**: Unit tests < 30 seconds, Integration tests < 5 minutes
- **Memory Usage**: No memory leaks, heap usage < 2GB during tests
- **AI Training Speed**: Performance targets met consistently

#### Failure Investigation
```bash
# View detailed failure logs
cat target/surefire-reports/TEST-*.xml

# Check application logs during test
tail -f target/test-logs/chess-test.log
```

### Continuous Integration Setup

#### GitHub Actions Workflow
```yaml
# .github/workflows/test.yml
name: Chess Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: mvn test
      - uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: target/surefire-reports/
```

### Test Maintenance

#### Regular Validation Schedule
- **Daily**: Unit tests on code changes
- **Weekly**: Full integration test suite
- **Monthly**: Performance benchmark validation
- **Release**: Complete test suite + manual validation

#### Test Data Management
```bash
# Clean test data
mvn clean

# Reset AI training data for tests
rm -rf target/test-data/*

# Regenerate test fixtures
mvn test -Dtest=TestDataGenerator
```

### Troubleshooting Common Issues

#### Test Failures
1. **Memory Issues**: Increase JVM heap with `-Xmx4g`
2. **Timeout Issues**: Extend test timeouts for slow systems
3. **File Permission Issues**: Ensure write access to test directories
4. **GPU Tests Failing**: Disable GPU tests on systems without acceleration

#### Performance Test Failures
1. **Training Speed**: Verify no blocking sleeps in AI training loops
2. **Memory Usage**: Check for memory leaks in long-running tests
3. **Concurrent Access**: Validate thread safety in multi-threaded tests

## Success/Failure Evaluation Matrix

| Test Category | Success Criteria | Failure Indicators | Action Required |
|---------------|------------------|-------------------|-----------------|
| **Core Game Engine** | All chess rules validated, no illegal moves accepted | Rule violations, incorrect game states | Fix game logic, update validation |
| **AI Systems** | All 12 AI systems functional, training data persists | AI failures, data corruption | Debug AI implementation, fix persistence |
| **Performance** | Training speed targets met, response times < limits | Slow performance, timeouts | Optimize algorithms, remove bottlenecks |
| **Integration** | Components communicate correctly, state synchronized | Communication failures, state drift | Fix integration points, improve error handling |
| **Security** | Input validation works, no security vulnerabilities | Validation bypassed, security issues | Strengthen validation, fix security gaps |

This comprehensive test plan ensures validation of all critical functionality in the CHESS project's 25,000+ lines of code across 60+ Java classes, providing a reliable foundation for future development and maintenance.