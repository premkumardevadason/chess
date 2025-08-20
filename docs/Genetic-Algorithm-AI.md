# Genetic Algorithm AI Documentation

## Overview
Genetic Algorithm AI uses evolutionary learning to develop chess playing ability. It maintains a population of chromosomes representing different chess evaluation strategies, evolves them through selection, crossover, and mutation, and learns through fitness evaluation based on game outcomes.

## How It Works in Chess

### Core Algorithm
- **Population**: Collection of chromosomes representing chess strategies
- **Selection**: Tournament selection of best performing chromosomes
- **Crossover**: Combining successful strategies to create offspring
- **Mutation**: Random changes to introduce variation
- **Fitness Evaluation**: Playing games to measure chromosome performance

### Key Features
1. **Population-Based Learning**: Maintains 50 different chess strategies
2. **Evolutionary Optimization**: Improves through natural selection principles
3. **Chromosome Persistence**: Saves and loads population between sessions
4. **Opening Book Integration**: Uses Leela Chess Zero opening database

## Code Implementation

### Main Class Structure
```java
public class GeneticAlgorithmAI {
    private boolean debugEnabled;
    private LeelaChessZeroOpeningBook openingBook;
    private List<Chromosome> population;
    private volatile boolean isTraining = false;
    private Thread trainingThread;
    
    // GA Parameters
    private static final int POPULATION_SIZE = 50;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private static final int ELITE_SIZE = 5;
    private static final int CHROMOSOME_LENGTH = 64; // 8x8 position weights
    
    // Training state
    private int generation = 0;
    private double bestFitness = 0.0;
    private String trainingStatus = "Ready";
}
```

### Chromosome Representation
```java
private static class Chromosome implements Serializable {
    private static final long serialVersionUID = 1L;
    
    double[] genes = new double[CHROMOSOME_LENGTH];
    double fitness = 0.0;
    
    public Chromosome() {
        for (int i = 0; i < CHROMOSOME_LENGTH; i++) {
            genes[i] = (Math.random() - 0.5) * 2.0; // Range [-1, 1]
        }
    }
    
    public double evaluatePosition(String[][] board) {
        double score = 0.0;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int geneIndex = i * 8 + j;
                    double pieceValue = getPieceValue(piece);
                    score += pieceValue * genes[geneIndex];
                }
            }
        }
        
        return score;
    }
}
```

### Move Selection Process
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    if (validMoves.size() == 1) return validMoves.get(0);
    
    // Use opening book for early moves
    LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves);
    if (openingResult != null) {
        return openingResult.move;
    }
    
    // Use best chromosome from current population
    Chromosome best = getBestChromosome();
    return selectMoveWithChromosome(board, validMoves, best);
}

private int[] selectMoveWithChromosome(String[][] board, List<int[]> validMoves, Chromosome chromosome) {
    int[] bestMove = validMoves.get(0);
    double bestScore = Double.NEGATIVE_INFINITY;
    
    for (int[] move : validMoves) {
        double score = evaluateMoveWithChromosome(board, move, chromosome);
        if (score > bestScore) {
            bestScore = score;
            bestMove = move;
        }
    }
    
    return bestMove;
}

private double evaluateMoveWithChromosome(String[][] board, int[] move, Chromosome chromosome) {
    // Create virtual board to test move
    VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
    virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
    String[][] newBoard = virtualBoard.getBoard();
    
    return chromosome.evaluatePosition(newBoard);
}
```

## Chess Strategy

### Position Evaluation
Each chromosome represents a unique chess evaluation strategy:

```java
public double evaluatePosition(String[][] board) {
    double score = 0.0;
    
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            String piece = board[i][j];
            if (!piece.isEmpty()) {
                int geneIndex = i * 8 + j;
                double pieceValue = getPieceValue(piece);
                score += pieceValue * genes[geneIndex];
            }
        }
    }
    
    return score;
}

private double getPieceValue(String piece) {
    switch (piece) {
        case "♛": return 9.0; case "♜": return 5.0; case "♝": return 3.0;
        case "♞": return 3.0; case "♟": return 1.0;
        case "♕": return -9.0; case "♖": return -5.0; case "♗": return -3.0;
        case "♘": return -3.0; case "♙": return -1.0;
        default: return 0.0;
    }
}
```

### Evolutionary Learning
The GA learns by evolving successful strategies:

1. **Fitness Evaluation**: Each chromosome plays multiple games
2. **Selection**: Tournament selection favors better performers
3. **Crossover**: Combines successful strategies
4. **Mutation**: Introduces random variations
5. **Elitism**: Preserves best chromosomes across generations

## Training Process

### Evolution Loop
```java
public void startTraining(int generations) {
    if (isTraining) {
        logger.warn("GA AI: Training already in progress");
        return;
    }
    
    isTraining = true;
    trainingThread = Thread.ofVirtual().name("GA-Training-Thread").start(() -> runEvolution(generations));
    
    logger.info("*** GA AI: Started evolution for {} generations ***", generations);
}

private void runEvolution(int maxGenerations) {
    trainingStatus = "Evolving population";
    
    try {
        for (int gen = 0; gen < maxGenerations && isTraining; gen++) {
            generation++;
            
            // Evaluate fitness through self-play
            evaluatePopulationFitness();
            
            // Sort by fitness
            population.sort((a, b) -> Double.compare(b.fitness, a.fitness));
            
            // Track best fitness
            bestFitness = population.get(0).fitness;
            
            if (gen % 10 == 0) {
                logger.info("*** GA AI: Generation {} - Best fitness: {:.3f} ***", generation, bestFitness);
            }
            
            // Create next generation
            List<Chromosome> newPopulation = new ArrayList<>();
            
            // Keep elite
            for (int i = 0; i < ELITE_SIZE; i++) {
                newPopulation.add(population.get(i).copy());
            }
            
            // Generate offspring
            while (newPopulation.size() < POPULATION_SIZE) {
                Chromosome parent1 = selectParent();
                Chromosome parent2 = selectParent();
                
                if (Math.random() < CROSSOVER_RATE) {
                    Chromosome[] offspring = crossover(parent1, parent2);
                    newPopulation.add(offspring[0]);
                    if (newPopulation.size() < POPULATION_SIZE) {
                        newPopulation.add(offspring[1]);
                    }
                } else {
                    newPopulation.add(parent1.copy());
                    if (newPopulation.size() < POPULATION_SIZE) {
                        newPopulation.add(parent2.copy());
                    }
                }
            }
            
            // Apply mutation
            for (int i = ELITE_SIZE; i < newPopulation.size(); i++) {
                if (Math.random() < MUTATION_RATE) {
                    newPopulation.get(i).mutate();
                }
            }
            
            population = newPopulation;
            
            // Save progress periodically
            if (gen % 50 == 0) {
                savePopulation();
            }
        }
        
        savePopulation();
        
    } catch (Exception e) {
        logger.error("*** GA AI: Evolution error - {} ***", e.getMessage());
    } finally {
        isTraining = false;
        trainingStatus = "Ready";
        logger.info("*** GA AI: Evolution completed - Generation {} ***", generation);
    }
}
```

### Fitness Evaluation
```java
private void evaluatePopulationFitness() {
    for (Chromosome chromosome : population) {
        chromosome.fitness = evaluateChromosomeFitness(chromosome);
    }
}

private double evaluateChromosomeFitness(Chromosome chromosome) {
    double totalFitness = 0.0;
    int games = 5; // Play 5 games per chromosome
    
    for (int game = 0; game < games; game++) {
        totalFitness += playFitnessGame(chromosome);
    }
    
    return totalFitness / games;
}

private double playFitnessGame(Chromosome chromosome) {
    VirtualChessBoard virtualBoard = new VirtualChessBoard();
    boolean isWhiteTurn = true;
    int moveCount = 0;
    int maxMoves = 100;
    
    while (moveCount < maxMoves && !isGameOver(virtualBoard.getBoard())) {
        List<int[]> validMoves = generateValidMoves(virtualBoard.getBoard(), isWhiteTurn);
        if (validMoves.isEmpty()) break;
        
        int[] move;
        if (moveCount < 10 && openingBook != null) {
            LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = 
                openingBook.getOpeningMove(virtualBoard.getBoard(), validMoves);
            move = (openingResult != null) ? openingResult.move : 
                   selectMoveWithChromosome(virtualBoard.getBoard(), validMoves, chromosome);
        } else {
            move = selectMoveWithChromosome(virtualBoard.getBoard(), validMoves, chromosome);
        }
        
        virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
        isWhiteTurn = !isWhiteTurn;
        moveCount++;
    }
    
    return evaluateGameResult(virtualBoard.getBoard());
}
```

## Genetic Operations

### Selection (Tournament Selection)
```java
private Chromosome selectParent() {
    // Tournament selection
    int tournamentSize = 3;
    Chromosome best = population.get((int)(Math.random() * population.size()));
    
    for (int i = 1; i < tournamentSize; i++) {
        Chromosome candidate = population.get((int)(Math.random() * population.size()));
        if (candidate.fitness > best.fitness) {
            best = candidate;
        }
    }
    
    return best;
}
```

### Crossover (Single-Point Crossover)
```java
private Chromosome[] crossover(Chromosome parent1, Chromosome parent2) {
    int crossoverPoint = (int)(Math.random() * CHROMOSOME_LENGTH);
    
    Chromosome offspring1 = new Chromosome();
    Chromosome offspring2 = new Chromosome();
    
    for (int i = 0; i < CHROMOSOME_LENGTH; i++) {
        if (i < crossoverPoint) {
            offspring1.genes[i] = parent1.genes[i];
            offspring2.genes[i] = parent2.genes[i];
        } else {
            offspring1.genes[i] = parent2.genes[i];
            offspring2.genes[i] = parent1.genes[i];
        }
    }
    
    return new Chromosome[]{offspring1, offspring2};
}
```

### Mutation
```java
public void mutate() {
    int mutationPoint = (int)(Math.random() * CHROMOSOME_LENGTH);
    genes[mutationPoint] += (Math.random() - 0.5) * 0.2; // Small mutation
    genes[mutationPoint] = Math.max(-1.0, Math.min(1.0, genes[mutationPoint])); // Clamp
}
```

## Performance Characteristics

### Strengths
- **Adaptive Learning**: Evolves strategies based on performance
- **Population Diversity**: Maintains multiple different approaches
- **No Domain Knowledge**: Learns chess evaluation from scratch
- **Robust**: Handles various position types through evolution

### Considerations
- **Slow Convergence**: Requires many generations for good performance
- **Computational Cost**: Fitness evaluation requires many games
- **Local Optima**: May get stuck in suboptimal strategies
- **Parameter Sensitivity**: Performance depends on GA parameters

## Integration Features

### Population Persistence
```java
private void savePopulation() {
    try {
        File dir = new File("ga_models");
        if (!dir.exists()) dir.mkdirs();
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(new File(dir, "population.dat")))) {
            oos.writeObject(population);
            oos.writeInt(generation);
            oos.writeDouble(bestFitness);
        }
        
        logger.info("*** GA AI: Population saved - Generation {} ***", generation);
        
    } catch (Exception e) {
        logger.error("*** GA AI: Error saving population - {} ***", e.getMessage());
    }
}

@SuppressWarnings("unchecked")
private void loadPopulation() {
    try {
        File populationFile = new File("ga_models/population.dat");
        if (!populationFile.exists()) {
            initializePopulation();
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(populationFile))) {
            population = (List<Chromosome>) ois.readObject();
            generation = ois.readInt();
            bestFitness = ois.readDouble();
        }
        
        logger.info("*** GA AI: Loaded population - Generation {} ***", generation);
        
    } catch (Exception e) {
        logger.warn("*** GA AI: Could not load population, initializing new one ***");
        initializePopulation();
    }
}
```

### Training Management
```java
public void stopTraining() {
    isTraining = false;
    if (trainingThread != null && trainingThread.isAlive()) {
        trainingThread.interrupt();
    }
    savePopulation();
    
    logger.info("*** GA AI: Training stopped and saved ***");
}

public void resetTraining() {
    stopTraining();
    generation = 0;
    bestFitness = 0.0;
    initializePopulation();
    
    logger.info("*** GA AI: Training reset ***");
}
```

## Configuration

### GA Parameters
```java
private static final int POPULATION_SIZE = 50;      // Number of chromosomes
private static final double MUTATION_RATE = 0.1;   // Probability of mutation
private static final double CROSSOVER_RATE = 0.8;  // Probability of crossover
private static final int ELITE_SIZE = 5;           // Number of elite chromosomes
private static final int CHROMOSOME_LENGTH = 64;   // 8x8 position weights
```

### Fitness Evaluation
- **Games per Chromosome**: 5 games for fitness evaluation
- **Maximum Moves**: 100 moves per game
- **Opening Integration**: Uses opening book for first 10 moves
- **Result Evaluation**: Based on final material balance

## Usage Examples

### Basic Setup
```java
GeneticAlgorithmAI ga = new GeneticAlgorithmAI(true);
```

### Training
```java
ga.startTraining(1000); // Evolve for 1000 generations
boolean training = ga.isTraining();
int generation = ga.getGeneration();
double fitness = ga.getBestFitness();
```

### Move Selection
```java
int[] move = ga.selectMove(board, validMoves);
```

### Training Management
```java
ga.stopTraining();
ga.resetTraining();
ga.deleteTrainingData();
```

## Technical Details

### Records for Data Structures
```java
public record EvolutionStats(int generation, double bestFitness, double avgFitness) {}
public record ChromosomeData(double[] genes, double fitness, int generation) {}
public record GameResult(double fitness, int moves, boolean completed) {}
```

### Virtual Thread Usage
- **Training Thread**: Uses virtual threads for evolution process
- **Concurrent Evaluation**: Could parallelize fitness evaluation
- **Resource Efficient**: Lower memory overhead than platform threads

### Error Handling
- **Population Corruption**: Graceful handling of corrupted population files
- **Training Interruption**: Safe shutdown with population saving
- **File I/O Errors**: Robust file handling with fallbacks
- **Memory Management**: Automatic cleanup of old generations

### Game Result Evaluation
```java
private double evaluateGameResult(String[][] board) {
    double materialBalance = 0.0;
    
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            String piece = board[i][j];
            materialBalance += getPieceValue(piece);
        }
    }
    
    return Math.tanh(materialBalance / 10.0) + 0.5; // Normalize to [0, 1]
}
```

This Genetic Algorithm implementation demonstrates how evolutionary computation can be applied to chess, learning effective evaluation strategies through natural selection principles without requiring explicit chess knowledge.