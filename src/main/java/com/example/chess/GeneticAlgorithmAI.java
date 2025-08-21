package com.example.chess;

import java.util.*;

import java.io.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.chess.async.TrainingDataIOWrapper;

/**
 * Genetic Algorithm Chess AI with evolutionary learning.
 * Features population-based optimization and chromosome persistence.
 */
public class GeneticAlgorithmAI {
    
    // Records for better data structures
    public record EvolutionStats(int generation, double bestFitness, double avgFitness) {}
    public record ChromosomeData(double[] genes, double fitness, int generation) {}
    public record GameResult(double fitness, int moves, boolean completed) {}
    public record MultiObjective(double material, double position, double mobility, double safety) {}
    public record SpeciesInfo(int id, List<Chromosome> members, double avgFitness) {}
    private static final Logger logger = LogManager.getLogger(GeneticAlgorithmAI.class);
    
    private boolean debugEnabled;
    private LeelaChessZeroOpeningBook openingBook;
    private List<Chromosome> population;
    private volatile boolean isTraining = false;
    private volatile boolean trainingStopRequested = false;
    private volatile boolean stopTrainingFlag = false;
    private Thread trainingThread;
    
    // GA Parameters
    private static final int POPULATION_SIZE = 50;
    private double baseMutationRate = 0.1;  // Adaptive mutation base rate
    private static final double CROSSOVER_RATE = 0.8;
    private static final int ELITE_SIZE = 5;
    private static final int CHROMOSOME_LENGTH = 64; // 8x8 position weights
    
    // Multi-objective optimization weights
    private static final double[] OBJECTIVE_WEIGHTS = {0.4, 0.3, 0.2, 0.1}; // material, position, mobility, safety
    
    // Niching/Speciation parameters
    private static final double SPECIES_THRESHOLD = 0.3;
    private static final int MIN_SPECIES_SIZE = 3;
    private List<Species> species = new ArrayList<>();
    
    // Coevolution - separate populations for game phases
    private List<Chromosome> openingPopulation = new ArrayList<>();
    private List<Chromosome> middlegamePopulation = new ArrayList<>();
    private List<Chromosome> endgamePopulation = new ArrayList<>();
    private GamePhase currentPhase = GamePhase.OPENING;
    
    // Training state
    private int generation = 0;
    private double bestFitness = 0.0;
    private String trainingStatus = "Ready";
    private double populationDiversity = 1.0;
    
    // Phase 3: Async I/O capability
    private TrainingDataIOWrapper ioWrapper;
    
    // Game phase enum for coevolution
    public enum GamePhase { OPENING, MIDDLEGAME, ENDGAME }
    
    public GeneticAlgorithmAI(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        this.population = new ArrayList<>();
        this.ioWrapper = new TrainingDataIOWrapper();
        
        loadPopulation();
        loadGenerationData(); // Always load generation data
        initializeCoevolutionPopulations();
        logger.info("*** Advanced GA AI: Initialized with adaptive mutation, multi-objective optimization, niching, and coevolution ***");
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Use opening book for early moves
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, false);
        LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), false);
        if (openingResult != null) {
            return openingResult.move;
        }
        
        // Determine game phase and use appropriate coevolved population
        GamePhase phase = determineGamePhase(board);
        Chromosome best = getBestChromosomeForPhase(phase);
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
        
        // Multi-objective evaluation
        MultiObjective objectives = evaluateMultiObjective(newBoard, move);
        return chromosome.evaluatePositionMultiObjective(newBoard, objectives);
    }
    
    public void startTraining(int generations) {
        // Reset stop flags to allow new training
        trainingStopRequested = false;
        stopTrainingFlag = false;
        
        // Wait for previous training thread to complete
        if (trainingThread != null && trainingThread.isAlive()) {
            logger.info("*** GA AI: Waiting for previous training to complete ***");
            try {
                trainingThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (isTraining) {
            logger.warn("GA AI: Training already in progress");
            return;
        }
        
        // ENHANCED: Ensure sufficient generations for population improvement
        int effectiveGenerations = Math.max(generations, 50); // Minimum 50 generations
        if (generations < 50) {
            logger.info("*** GA AI: Increasing generations from {} to {} for population improvement ***", generations, effectiveGenerations);
        }
        
        // Check current generation and recommend more if needed
        if (generation < 100) {
            int recommendedTotal = Math.max(effectiveGenerations, 100);
            logger.info("*** GA AI: Current generation: {}, Running {} more (Recommended total: 200+ for effectiveness) ***", 
                generation, recommendedTotal);
            effectiveGenerations = recommendedTotal;
        }
        
        isTraining = true;
        final int finalGenerations = effectiveGenerations;
        trainingThread = Thread.ofVirtual().name("GA-Training-Thread").start(() -> runEvolution(finalGenerations));
        // Virtual threads are already daemon threads by default
        
        logger.info("*** GA AI: Started evolution for {} generations ***", effectiveGenerations);
    }
    
    private void runEvolution(int maxGenerations) {
        trainingStatus = "Evolving population";
        long startTime = System.currentTimeMillis();
        double initialBestFitness = bestFitness;
        
        try {
            for (int gen = 0; gen < maxGenerations && isTraining && !stopTrainingFlag; gen++) {
                if (stopTrainingFlag) {
                    logger.info("*** GA AI: STOP DETECTED at generation {} - Exiting evolution ***", gen);
                    return;
                }
                generation++;
                
                // Calculate population diversity for adaptive mutation
                populationDiversity = calculatePopulationDiversity();
                
                // Check stop flag before expensive fitness evaluation
                if (stopTrainingFlag || !isTraining) {
                    logger.info("*** GA AI: STOP DETECTED before fitness evaluation - Exiting evolution ***");
                    break;
                }
                
                // Evaluate fitness through self-play with multi-objective optimization
                evaluatePopulationFitness();
                
                // Check stop flag after fitness evaluation
                if (stopTrainingFlag || !isTraining) {
                    logger.info("*** GA AI: Training stopped after fitness evaluation ***");
                    break;
                }
                
                // Perform speciation (niching)
                performSpeciation();
                
                // Sort by fitness
                population.sort((a, b) -> Double.compare(b.fitness, a.fitness));
                
                // Track best fitness and improvement
                double previousBest = bestFitness;
                bestFitness = population.get(0).fitness;
                double improvement = bestFitness - previousBest;
                
                // Coevolve separate populations
                coevolvePhasePopulations();
                
                // Enhanced progress reporting with improvement tracking
                if (gen % 10 == 0) {
                    double avgFitness = population.stream().mapToDouble(c -> c.fitness).average().orElse(0.0);
                    logger.info("*** GA AI: Generation {} - Best: {:.3f} (+{:.3f}), Avg: {:.3f}, Diversity: {:.3f} ***", 
                        generation, bestFitness, improvement, avgFitness, populationDiversity);
                    
                    // Assess population improvement
                    assessPopulationImprovement(gen, maxGenerations);
                }
                
                // Check stop flag after each generation
                if (stopTrainingFlag || !isTraining) {
                    logger.info("*** GA AI: Training stopped at generation {} ***", generation);
                    break;
                }
                
                // Create next generation with enhanced genetic operators
                List<Chromosome> newPopulation = new ArrayList<>();
                
                // Keep elite with diversity preservation
                for (int i = 0; i < ELITE_SIZE; i++) {
                    newPopulation.add(population.get(i).copy());
                }
                
                // Generate offspring with improved selection pressure
                while (newPopulation.size() < POPULATION_SIZE) {
                    Chromosome parent1 = selectParentWithPressure();
                    Chromosome parent2 = selectParentWithPressure();
                    
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
                
                // Apply adaptive mutation with generation-based scaling
                double adaptiveMutationRate = calculateAdaptiveMutationRate();
                double generationFactor = Math.min(1.0, generation / 100.0); // Scale with generations
                
                for (int i = ELITE_SIZE; i < newPopulation.size(); i++) {
                    if (Math.random() < adaptiveMutationRate * generationFactor) {
                        newPopulation.get(i).mutateAdaptive(populationDiversity);
                    }
                }
                
                population = newPopulation;
                
                // Save progress periodically (check stop flag first)
                if (gen % 10 == 0 && !stopTrainingFlag && isTraining) {
                    savePopulation();
                }
                
                // Final stop flag check before next generation
                if (stopTrainingFlag || !isTraining) {
                    logger.info("*** GA AI: Training stopped before next generation ***");
                    break;
                }
            }
            
            // Final save after training completion with statistics
            savePopulation();
            
            long endTime = System.currentTimeMillis();
            double trainingTimeMinutes = (endTime - startTime) / (1000.0 * 60.0);
            double totalImprovement = bestFitness - initialBestFitness;
            
            logger.info("*** GA AI: Evolution completed - Final statistics ***");
            logger.info("Generations: {}, Time: {:.1f} min, Best fitness: {:.3f} (+{:.3f})", 
                generation, trainingTimeMinutes, bestFitness, totalImprovement);
            
            // Assess final effectiveness
            assessEvolutionEffectiveness(generation, totalImprovement);
            
        } catch (Exception e) {
            logger.error("*** GA AI: Evolution error - {} ***", e.getMessage());
        } finally {
            isTraining = false;
            trainingStatus = "Ready";
        }
    }
    
    private void assessPopulationImprovement(int currentGen, int maxGenerations) {
        try {
            double progress = (double) currentGen / maxGenerations;
            
            if (populationDiversity < 0.1) {
                logger.warn("*** GA AI: Low population diversity ({:.3f}) - consider increasing mutation rate ***", populationDiversity);
            }
            
            if (currentGen >= 50 && bestFitness < 0.1) {
                logger.warn("*** GA AI: Slow fitness improvement after {} generations - may need more generations ***", currentGen);
            }
            
            if (progress > 0.5 && generation < 100) {
                logger.info("*** GA AI: Recommendation - Train for 200+ total generations for better population evolution ***");
            }
            
        } catch (Exception e) {
            logger.debug("Failed to assess population improvement: {}", e.getMessage());
        }
    }
    
    private void assessEvolutionEffectiveness(int totalGenerations, double totalImprovement) {
        try {
            String effectiveness;
            String recommendation;
            
            if (totalGenerations < 50) {
                effectiveness = "INSUFFICIENT";
                recommendation = "Train with at least 50 generations for basic evolution";
            } else if (totalGenerations < 100) {
                effectiveness = "BASIC";
                recommendation = "Train with 100+ generations for improved population fitness";
            } else if (totalGenerations < 200) {
                effectiveness = "GOOD";
                recommendation = "Train with 200+ generations for strong population evolution";
            } else if (totalGenerations < 500) {
                effectiveness = "STRONG";
                recommendation = "Train with 500+ generations for expert-level evolution";
            } else {
                effectiveness = "EXPERT";
                recommendation = "Continue evolution for incremental improvements";
            }
            
            logger.info("*** GA AI Evolution Assessment ***");
            logger.info("Total generations: {}", totalGenerations);
            logger.info("Fitness improvement: {:.3f}", totalImprovement);
            logger.info("Effectiveness level: {}", effectiveness);
            logger.info("Recommendation: {}", recommendation);
            logger.info("Population diversity: {:.3f}", populationDiversity);
            
        } catch (Exception e) {
            logger.warn("Failed to assess evolution effectiveness: {}", e.getMessage());
        }
    }
    
    private Chromosome selectParentWithPressure() {
        // Enhanced tournament selection with generation-based pressure
        int tournamentSize = Math.min(5, Math.max(3, generation / 20)); // Increase pressure over time
        Chromosome best = population.get((int)(Math.random() * population.size()));
        
        for (int i = 1; i < tournamentSize; i++) {
            Chromosome candidate = population.get((int)(Math.random() * population.size()));
            if (candidate.fitness > best.fitness) {
                best = candidate;
            }
        }
        
        return best;
    }
    
    private void evaluatePopulationFitness() {
        for (int i = 0; i < population.size(); i++) {
            // Check stop flag every 5 chromosomes for faster response
            if (i % 5 == 0 && (stopTrainingFlag || !isTraining)) {
                logger.info("*** GA AI: STOP DETECTED during fitness evaluation at chromosome {} ***", i);
                return;
            }
            
            Chromosome chromosome = population.get(i);
            chromosome.fitness = evaluateChromosomeFitness(chromosome);
        }
    }
    
    private double evaluateChromosomeFitness(Chromosome chromosome) {
        double totalFitness = 0.0;
        int games = 5; // Play 5 games per chromosome
        
        for (int game = 0; game < games; game++) {
            // Check stop flag before each game
            if (stopTrainingFlag || !isTraining) {
                logger.info("*** GA AI: STOP DETECTED during fitness game {} ***", game);
                return totalFitness / Math.max(game, 1); // Return partial fitness
            }
            
            totalFitness += playFitnessGameMultiObjective(chromosome);
        }
        
        return totalFitness / games;
    }
    
    private double playFitnessGameMultiObjective(Chromosome chromosome) {
        VirtualChessBoard virtualBoard = new VirtualChessBoard(openingBook);
        boolean isWhiteTurn = true;
        int moveCount = 0;
        int maxMoves = 100;
        double cumulativeFitness = 0.0;
        
        while (moveCount < maxMoves && !isGameOver(virtualBoard.getBoard())) {
            List<int[]> validMoves = generateValidMoves(virtualBoard.getBoard(), isWhiteTurn);
            if (validMoves.isEmpty()) break;
            
            // Determine current game phase
            GamePhase phase = determineGamePhase(virtualBoard.getBoard());
            
            int[] move;
            if (moveCount < 10 && openingBook != null) {
                LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = 
                    openingBook.getOpeningMove(virtualBoard.getBoard(), validMoves, virtualBoard.getRuleValidator(), isWhiteTurn);
                move = (openingResult != null) ? openingResult.move : 
                       selectMoveWithChromosome(virtualBoard.getBoard(), validMoves, chromosome);
            } else {
                move = selectMoveWithChromosome(virtualBoard.getBoard(), validMoves, chromosome);
            }
            
            // Evaluate move quality with multi-objective criteria
            MultiObjective objectives = evaluateMultiObjective(virtualBoard.getBoard(), move);
            cumulativeFitness += chromosome.evaluatePositionMultiObjective(virtualBoard.getBoard(), objectives);
            
            virtualBoard.makeMove(move[0], move[1], move[2], move[3]);
            isWhiteTurn = !isWhiteTurn;
            moveCount++;
        }
        
        double gameResult = evaluateGameResultMultiObjective(virtualBoard.getBoard());
        return (cumulativeFitness / Math.max(moveCount, 1)) + gameResult;
    }
    
    private double evaluateGameResultMultiObjective(String[][] board) {
        MultiObjective objectives = evaluateMultiObjective(board, null);
        
        return OBJECTIVE_WEIGHTS[0] * objectives.material +
               OBJECTIVE_WEIGHTS[1] * objectives.position +
               OBJECTIVE_WEIGHTS[2] * objectives.mobility +
               OBJECTIVE_WEIGHTS[3] * objectives.safety;
    }
    
    private double getPieceValue(String piece) {
        return switch (piece) {
            case "♛" -> 9.0; case "♜" -> 5.0; case "♝" -> 3.0;
            case "♞" -> 3.0; case "♟" -> 1.0;
            case "♕" -> -9.0; case "♖" -> -5.0; case "♗" -> -3.0;
            case "♘" -> -3.0; case "♙" -> -1.0;
            default -> 0.0;
        };
    }
    
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
    
    private Chromosome getBestChromosome() {
        if (population.isEmpty()) {
            initializePopulation();
        }
        
        // Safe bounds checking - ensure population is not empty
        if (population.isEmpty()) {
            logger.warn("*** GA AI: Population is empty after initialization - creating default chromosome ***");
            return new Chromosome();
        }
        
        return population.stream()
            .max(Comparator.comparingDouble(c -> c.fitness))
            .orElse(population.get(0));
    }
    
    private Chromosome getBestChromosomeForPhase(GamePhase phase) {
        List<Chromosome> phasePopulation = switch (phase) {
            case OPENING -> openingPopulation.isEmpty() ? population : openingPopulation;
            case MIDDLEGAME -> middlegamePopulation.isEmpty() ? population : middlegamePopulation;
            case ENDGAME -> endgamePopulation.isEmpty() ? population : endgamePopulation;
        };
        
        return phasePopulation.stream()
            .max(Comparator.comparingDouble(c -> c.fitness))
            .orElse(getBestChromosome());
    }
    
    private void initializePopulation() {
        population.clear();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            population.add(new Chromosome());
        }
        
        logger.info("*** GA AI: Initialized random population ***");
    }
    
    private final Object savePopulationLock = new Object();
    
    public void savePopulation() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            ioWrapper.saveAIData("GeneticAlgorithm", population, "ga_models/population.dat");
        } else {
            // SYNCHRONIZED: Prevent concurrent saves that corrupt data
            synchronized (savePopulationLock) {
                try {
                    File dir = new File("ga_models");
                    if (!dir.exists()) dir.mkdirs();
                    
                    // Create snapshot to prevent race conditions during serialization
                    List<Chromosome> populationSnapshot = new ArrayList<>();
                    for (Chromosome c : population) {
                        populationSnapshot.add(c.copy());
                    }
                    int generationSnapshot = generation;
                    double bestFitnessSnapshot = bestFitness;
                    
                    // Save to temporary file first for atomic operation
                    File tempFile = new File(dir, "population.dat.tmp");
                    try (ObjectOutputStream oos = new ObjectOutputStream(
                            new FileOutputStream(tempFile))) {
                        oos.writeObject(populationSnapshot);
                        oos.writeInt(generationSnapshot);
                        oos.writeDouble(bestFitnessSnapshot);
                    }
                    
                    // Save additional generation data
                    saveGenerationData();
                    
                    // Atomic rename
                    File finalFile = new File(dir, "population.dat");
                    if (finalFile.exists()) finalFile.delete();
                    if (tempFile.renameTo(finalFile)) {
                        logger.info("*** GA AI: Population saved - Generation {} ***", generationSnapshot);
                    } else {
                        logger.error("*** GA AI: Failed to rename temp file to final file ***");
                    }
                    
                } catch (Exception e) {
                    logger.error("*** GA AI: Error saving population - {} ***", e.getMessage());
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadPopulation() {
        // Phase 3: Dual-path implementation
        if (ioWrapper.isAsyncEnabled()) {
            try {
                logger.info("*** ASYNC I/O: GeneticAlgorithm loading population using NIO.2 async LOAD path ***");
                Object data = ioWrapper.loadAIData("GeneticAlgorithm", "ga_models/population.dat");
                if (data != null) {
                    logger.info("*** GA AI: Loaded population using async I/O ***");
                    return;
                }
            } catch (Exception e) {
                logger.warn("*** GA AI: Async load failed, falling back to sync - {} ***", e.getMessage());
            }
        }
        
        // Existing synchronous code - unchanged
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
    
    public void stopTraining() {
        logger.info("*** GA AI: STOP REQUEST RECEIVED - Setting training flags ***");
        trainingStopRequested = true;
        stopTrainingFlag = true;
        isTraining = false;
        savePopulation();
        
        logger.info("*** GA AI: STOP FLAGS SET - Training will stop on next check ***");
    }
    
    public void resetTraining() {
        stopTraining();
        generation = 0;
        bestFitness = 0.0;
        initializePopulation();
        
        logger.info("*** GA AI: Training reset ***");
    }
    
    public void deleteTrainingData() {
        stopTraining();
        
        File dir = new File("ga_models");
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        }
        
        resetTraining();
        
        logger.info("*** GA AI: Training data deleted ***");
    }
    
    public String getTrainingStatus() {
        String effectiveness;
        if (generation < 50) {
            effectiveness = "INSUFFICIENT (need 50+ generations)";
        } else if (generation < 100) {
            effectiveness = "BASIC (recommend 100+ generations)";
        } else if (generation < 200) {
            effectiveness = "GOOD (recommend 200+ generations)";
        } else {
            effectiveness = "STRONG";
        }
        
        return String.format("%s - Generation: %d, Fitness: %.3f (%s)", 
            trainingStatus, generation, bestFitness, effectiveness);
    }
    
    public int getGeneration() {
        return generation;
    }
    
    public double getBestFitness() {
        return bestFitness;
    }
    
    public boolean isEvolutionEffective() {
        return generation >= 50;
    }
    
    public int getRecommendedAdditionalGenerations() {
        if (generation < 50) return 50 - generation;
        if (generation < 100) return 100 - generation;
        if (generation < 200) return 200 - generation;
        return 0; // Already well-evolved
    }
    
    public double getPopulationDiversity() {
        return populationDiversity;
    }
    
    public Map<String, Object> getEvolutionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("generation", generation);
        stats.put("bestFitness", bestFitness);
        stats.put("populationSize", population.size());
        stats.put("populationDiversity", populationDiversity);
        stats.put("isTraining", isTraining);
        stats.put("isEffective", isEvolutionEffective());
        stats.put("recommendedAdditionalGenerations", getRecommendedAdditionalGenerations());
        
        if (!population.isEmpty()) {
            double avgFitness = population.stream().mapToDouble(c -> c.fitness).average().orElse(0.0);
            double minFitness = population.stream().mapToDouble(c -> c.fitness).min().orElse(0.0);
            stats.put("averageFitness", avgFitness);
            stats.put("minFitness", minFitness);
        }
        
        return stats;
    }
    
    private void saveGenerationData() {
        try {
            java.io.File genFile = new java.io.File("ga_generation.dat");
            try (java.io.FileWriter writer = new java.io.FileWriter(genFile)) {
                writer.write(String.valueOf(generation));
            }
        } catch (Exception e) {
            logger.debug("GA AI: Failed to save generation data - {}", e.getMessage());
        }
    }
    
    private void loadGenerationData() {
        try {
            java.io.File genFile = new java.io.File("ga_generation.dat");
            if (genFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(genFile))) {
                    String line = reader.readLine();
                    if (line != null) {
                        int loadedGeneration = Integer.parseInt(line.trim());
                        if (loadedGeneration > generation) {
                            generation = loadedGeneration;
                            logger.info("GA AI: Loaded generation data: {}", generation);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("GA AI: Failed to load generation data - {}", e.getMessage());
        }
    }
    
    public boolean isTraining() {
        return isTraining;
    }
    
    public int getPopulationSize() {
        return population.size();
    }
    
    public int getCurrentGeneration() {
        return generation;
    }
    
    // Utility methods
    private boolean isGameOver(String[][] board) {
        boolean hasWhiteKing = false, hasBlackKing = false;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j].equals("♔")) hasWhiteKing = true;
                if (board[i][j].equals("♚")) hasBlackKing = true;
            }
        }
        
        return !hasWhiteKing || !hasBlackKing;
    }
    
    private List<int[]> generateValidMoves(String[][] board, boolean isWhiteTurn) {
        // AI vs User: Use ChessGame's ChessRuleValidator
        // AI vs AI Training: Use VirtualChessBoard's ChessRuleValidator
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, isWhiteTurn);
        List<int[]> validMoves = virtualBoard.getAllValidMoves(isWhiteTurn);
        
        // Limit for GA performance - safe bounds checking
        if (validMoves.isEmpty()) {
            return validMoves; // Return empty list
        }
        return validMoves.size() > 30 ? validMoves.subList(0, 30) : validMoves;
    }
    
    // Chromosome class
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
        
        public double evaluatePositionMultiObjective(String[][] board, MultiObjective objectives) {
            double baseScore = evaluatePosition(board);
            
            // Combine with multi-objective scores
            return baseScore + 
                   OBJECTIVE_WEIGHTS[0] * objectives.material +
                   OBJECTIVE_WEIGHTS[1] * objectives.position +
                   OBJECTIVE_WEIGHTS[2] * objectives.mobility +
                   OBJECTIVE_WEIGHTS[3] * objectives.safety;
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
        
        public void mutate() {
            int mutationPoint = (int)(Math.random() * CHROMOSOME_LENGTH);
            genes[mutationPoint] += (Math.random() - 0.5) * 0.2; // Small mutation
            genes[mutationPoint] = Math.max(-1.0, Math.min(1.0, genes[mutationPoint])); // Clamp
        }
        
        public void mutateAdaptive(double diversity) {
            // Adaptive mutation: higher mutation rate when diversity is low
            double mutationStrength = 0.1 + (1.0 - diversity) * 0.3;
            int numMutations = (int)(Math.random() * 3) + 1; // 1-3 mutations
            
            for (int i = 0; i < numMutations; i++) {
                int mutationPoint = (int)(Math.random() * CHROMOSOME_LENGTH);
                genes[mutationPoint] += (Math.random() - 0.5) * mutationStrength;
                genes[mutationPoint] = Math.max(-1.0, Math.min(1.0, genes[mutationPoint]));
            }
        }
        
        public Chromosome copy() {
            Chromosome copy = new Chromosome();
            System.arraycopy(this.genes, 0, copy.genes, 0, CHROMOSOME_LENGTH);
            copy.fitness = this.fitness;
            return copy;
        }
    }
    
    /**
     * Add human game data to Genetic Algorithm learning
     */
    public void addHumanGameData(String[][] finalBoard, List<String> moveHistory, boolean blackWon) {
        logger.debug("*** GA AI: Processing human game data ***");
        
        try {
            // Check if we have sufficient generations for effective learning
            if (generation < 20) {
                logger.warn("*** GA AI: Only {} generations evolved - human game data may not be effective ***", generation);
                logger.warn("*** Recommendation: Evolve for at least 50 generations first ***");
            }
            
            // Use human game outcome to adjust best chromosome fitness
            Chromosome bestChromosome = getBestChromosome();
            
            // Evaluate the final position with current best chromosome
            double positionScore = bestChromosome.evaluatePosition(finalBoard);
            
            // Adjust fitness based on game outcome and generation maturity
            double gameOutcome = blackWon ? -1.0 : 1.0;
            double maturityFactor = Math.min(1.0, generation / 50.0); // Scale with generations
            double fitnessAdjustment = gameOutcome * 0.1 * maturityFactor; // Scaled adjustment
            
            bestChromosome.fitness += fitnessAdjustment;
            
            // Create a new chromosome based on this game outcome
            Chromosome humanGameChromosome = bestChromosome.copy();
            
            // Enhanced mutation based on game result and population maturity
            if ((gameOutcome > 0 && positionScore > 0) || (gameOutcome < 0 && positionScore < 0)) {
                // Good correlation - scaled positive mutation
                int mutationCount = Math.max(3, Math.min(10, generation / 10)); // More mutations with experience
                for (int i = 0; i < mutationCount; i++) {
                    int geneIndex = (int)(Math.random() * CHROMOSOME_LENGTH);
                    double mutationStrength = gameOutcome * 0.05 * maturityFactor;
                    humanGameChromosome.genes[geneIndex] += mutationStrength;
                    humanGameChromosome.genes[geneIndex] = Math.max(-1.0, Math.min(1.0, humanGameChromosome.genes[geneIndex]));
                }
            } else {
                // Poor correlation - exploratory mutation
                for (int i = 0; i < 3; i++) {
                    int geneIndex = (int)(Math.random() * CHROMOSOME_LENGTH);
                    humanGameChromosome.genes[geneIndex] += (Math.random() - 0.5) * 0.1 * maturityFactor;
                    humanGameChromosome.genes[geneIndex] = Math.max(-1.0, Math.min(1.0, humanGameChromosome.genes[geneIndex]));
                }
            }
            
            // Replace worst chromosome with human-game influenced one
            if (population.size() >= POPULATION_SIZE) {
                population.sort((a, b) -> Double.compare(a.fitness, b.fitness));
                population.set(0, humanGameChromosome); // Replace worst
            } else {
                population.add(humanGameChromosome);
            }
            
            // Save population after human game data
            savePopulation();
            
            logger.debug("*** GA AI: Added human game data ({} moves, generation {}) and saved population ***", 
                moveHistory.size(), generation);
            
            // Suggest more evolution if needed
            if (generation < 100) {
                logger.info("*** GA AI: Suggestion - Evolve {} more generations for better human game learning ***", 
                    100 - generation);
            }
            
        } catch (Exception e) {
            logger.error("*** GA AI: Error processing human game data - {} ***", e.getMessage());
        }
    }
    
    // Advanced GA optimization methods
    
    private double calculatePopulationDiversity() {
        if (population.size() < 2) return 1.0;
        
        double totalDistance = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < population.size(); i++) {
            for (int j = i + 1; j < population.size(); j++) {
                totalDistance += calculateGeneticDistance(population.get(i), population.get(j));
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDistance / comparisons : 1.0;
    }
    
    private double calculateGeneticDistance(Chromosome c1, Chromosome c2) {
        double distance = 0.0;
        for (int i = 0; i < CHROMOSOME_LENGTH; i++) {
            distance += Math.abs(c1.genes[i] - c2.genes[i]);
        }
        return distance / CHROMOSOME_LENGTH;
    }
    
    private double calculateAdaptiveMutationRate() {
        // Higher mutation when diversity is low, lower when diversity is high
        return baseMutationRate + (1.0 - populationDiversity) * 0.2;
    }
    
    private MultiObjective evaluateMultiObjective(String[][] board, int[] move) {
        double material = calculateMaterialBalance(board);
        double position = calculatePositionalValue(board);
        double mobility = calculateMobility(board);
        double safety = calculateKingSafety(board);
        
        return new MultiObjective(
            Math.tanh(material / 10.0) + 0.5,
            Math.tanh(position / 5.0) + 0.5,
            Math.tanh(mobility / 20.0) + 0.5,
            Math.tanh(safety / 3.0) + 0.5
        );
    }
    
    private double calculateMaterialBalance(String[][] board) {
        double balance = 0.0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                balance += getPieceValue(board[i][j]);
            }
        }
        return balance;
    }
    
    private double calculatePositionalValue(String[][] board) {
        double value = 0.0;
        // Center control bonus
        for (int i = 3; i <= 4; i++) {
            for (int j = 3; j <= 4; j++) {
                if (!board[i][j].isEmpty()) {
                    value += getPieceValue(board[i][j]) > 0 ? 0.5 : -0.5;
                }
            }
        }
        return value;
    }
    
    private double calculateMobility(String[][] board) {
        // Simplified mobility calculation
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
        int whiteMoves = virtualBoard.getAllValidMoves(true).size();
        virtualBoard = new VirtualChessBoard(board, false);
        int blackMoves = virtualBoard.getAllValidMoves(false).size();
        return whiteMoves - blackMoves;
    }
    
    private double calculateKingSafety(String[][] board) {
        double safety = 0.0;
        // Find kings and evaluate surrounding squares
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♔".equals(board[i][j]) || "♚".equals(board[i][j])) {
                    boolean isWhiteKing = "♔".equals(board[i][j]);
                    int protectedSquares = 0;
                    
                    // Check surrounding squares
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            int ni = i + di, nj = j + dj;
                            if (ni >= 0 && ni < 8 && nj >= 0 && nj < 8) {
                                String piece = board[ni][nj];
                                if (!piece.isEmpty()) {
                                    boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
                                    if (isPieceWhite == isWhiteKing) protectedSquares++;
                                }
                            }
                        }
                    }
                    
                    safety += isWhiteKing ? protectedSquares : -protectedSquares;
                }
            }
        }
        return safety;
    }
    
    private GamePhase determineGamePhase(String[][] board) {
        int pieceCount = 0;
        boolean hasQueens = false;
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!board[i][j].isEmpty()) {
                    pieceCount++;
                    if ("♕♛".contains(board[i][j])) hasQueens = true;
                }
            }
        }
        
        if (pieceCount <= 12) return GamePhase.ENDGAME;
        if (pieceCount <= 20 || !hasQueens) return GamePhase.MIDDLEGAME;
        return GamePhase.OPENING;
    }
    
    private void performSpeciation() {
        species.clear();
        List<Chromosome> unassigned = new ArrayList<>(population);
        
        while (!unassigned.isEmpty()) {
            Chromosome representative = unassigned.remove(0);
            Species newSpecies = new Species(species.size(), representative);
            
            Iterator<Chromosome> iter = unassigned.iterator();
            while (iter.hasNext()) {
                Chromosome candidate = iter.next();
                if (calculateGeneticDistance(representative, candidate) < SPECIES_THRESHOLD) {
                    newSpecies.addMember(candidate);
                    iter.remove();
                }
            }
            
            if (newSpecies.size() >= MIN_SPECIES_SIZE) {
                species.add(newSpecies);
            }
        }
        
        // Fitness sharing within species
        for (Species s : species) {
            s.applyFitnessSharing();
        }
    }
    
    private void coevolvePhasePopulations() {
        // Separate population into phase-specific groups
        openingPopulation.clear();
        middlegamePopulation.clear();
        endgamePopulation.clear();
        
        // Distribute top chromosomes across phases
        for (int i = 0; i < Math.min(population.size(), 15); i++) {
            Chromosome c = population.get(i);
            openingPopulation.add(c.copy());
            middlegamePopulation.add(c.copy());
            endgamePopulation.add(c.copy());
        }
        
        // Phase-specific mutations
        mutatePhasePopulation(openingPopulation, 0.05); // Conservative opening mutations
        mutatePhasePopulation(middlegamePopulation, 0.1); // Standard middlegame mutations
        mutatePhasePopulation(endgamePopulation, 0.15); // Aggressive endgame mutations
    }
    
    private void mutatePhasePopulation(List<Chromosome> phasePopulation, double mutationRate) {
        for (Chromosome c : phasePopulation) {
            if (Math.random() < mutationRate) {
                c.mutateAdaptive(populationDiversity);
            }
        }
    }
    
    private void initializeCoevolutionPopulations() {
        // Initialize phase-specific populations with copies from main population
        if (population.isEmpty()) {
            logger.warn("*** GA AI: Main population is empty - initializing fresh population for coevolution ***");
            initializePopulation(); // Create fresh population instead of failing
        }
        
        for (Chromosome c : population) {
            openingPopulation.add(c.copy());
            middlegamePopulation.add(c.copy());
            endgamePopulation.add(c.copy());
        }
        
        logger.info("*** GA AI: Coevolution populations initialized - Opening: {}, Middlegame: {}, Endgame: {} ***", 
            openingPopulation.size(), middlegamePopulation.size(), endgamePopulation.size());
    }
    
    // Species class for niching/speciation
    private static class Species {
        private int id;
        private Chromosome representative;
        private List<Chromosome> members;
        
        public Species(int id, Chromosome representative) {
            this.id = id;
            this.representative = representative;
            this.members = new ArrayList<>();
            this.members.add(representative);
        }
        
        public void addMember(Chromosome member) {
            members.add(member);
        }
        
        public int size() {
            return members.size();
        }
        
        public void applyFitnessSharing() {
            // Reduce fitness based on species size to maintain diversity
            double sharingFactor = 1.0 / members.size();
            for (Chromosome member : members) {
                member.fitness *= sharingFactor;
            }
        }
        
        public List<Chromosome> getMembers() {
            return members;
        }
    }
}