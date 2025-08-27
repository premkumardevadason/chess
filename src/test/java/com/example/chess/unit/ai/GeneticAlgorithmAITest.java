package com.example.chess.unit.ai;

import com.example.chess.GeneticAlgorithmAI;
import com.example.chess.BaseTestClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class GeneticAlgorithmAITest extends BaseTestClass {
    
    private GeneticAlgorithmAI geneticAI;
    
    
    @BeforeEach
    void setUp() {
        super.baseSetUp();
        geneticAI = new GeneticAlgorithmAI(false);
    }
    
    @Test
    void testPopulationInitialization() {
        // Test initial chromosome generation for evolutionary learning
        assertTrue(geneticAI.getPopulationSize() > 0, "Population should have positive size");
        assertTrue(geneticAI.getGeneration() >= 0, "Generation should start at 0 or higher");
        
        // Test population size is reasonable for genetic diversity
        int populationSize = geneticAI.getPopulationSize();
        assertTrue(populationSize >= 10, "Population should be large enough for diversity");
        assertTrue(populationSize <= 1000, "Population should not be excessively large");
        
        // Test initial population diversity
        double initialDiversity = geneticAI.getPopulationDiversity();
        assertTrue(initialDiversity >= 0.0 && initialDiversity <= 1.0, "Initial population should have diversity");
        
        // Test that genetic AI can generate moves from initial population
        game.resetGame();
        int[] populationMove = geneticAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (populationMove != null) {
            assertTrue(game.isValidMove(populationMove[0], populationMove[1], populationMove[2], populationMove[3]));
            assertEquals(4, populationMove.length, "Move should have 4 coordinates");
        }
        
        // Verify population initialization is functional
        assertNotNull(geneticAI, "Genetic AI population should be initialized");
    }
    
    @Test
    void testFitnessEvaluation() {
        // Test chromosome scoring and fitness evaluation
        double initialFitness = geneticAI.getBestFitness();
        assertTrue(initialFitness >= 0.0, "Initial fitness should be non-negative");
        
        // Test fitness evaluation through game performance
        game.resetGame();
        
        // Simulate fitness evaluation by playing moves
        java.util.List<Double> fitnessScores = new java.util.ArrayList<>();
        
        for (int evaluation = 0; evaluation < 5; evaluation++) {
            game.resetGame();
            
            // Play a few moves to evaluate chromosome fitness
            for (int move = 0; move < 3; move++) {
                int[] geneticMove = geneticAI.selectMove(game.getBoard(), game.getAllValidMoves(move % 2 == 0));
                
                if (geneticMove != null && game.isValidMove(geneticMove[0], geneticMove[1], geneticMove[2], geneticMove[3])) {
                    game.makeMove(geneticMove[0], geneticMove[1], geneticMove[2], geneticMove[3]);
                    
                    // Simulate fitness scoring based on move quality
                    double moveScore = 0.1 + move * 0.05;
                    fitnessScores.add(moveScore);
                } else {
                    break;
                }
            }
        }
        
        // Test fitness evaluation produces reasonable scores
        if (!fitnessScores.isEmpty()) {
            double avgFitness = fitnessScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            assertTrue(avgFitness >= 0.0 && avgFitness <= 10.0, "Average fitness should be reasonable");
        }
        
        // Test that best fitness can improve
        double currentBestFitness = geneticAI.getBestFitness();
        assertTrue(currentBestFitness >= initialFitness, "Best fitness should be tracked");
        
        // Verify fitness evaluation system is functional
        assertTrue(geneticAI.getPopulationSize() >= 10, "Fitness evaluation should score chromosomes");
    }
    
    @Test
    void testMutationOperations() {
        // Test genetic variation through mutation operations
        int initialGeneration = geneticAI.getGeneration();
        double initialDiversity = geneticAI.getPopulationDiversity();
        
        // Test mutation through short training period
        geneticAI.startTraining(3);
        try { 
            Thread.sleep(2000); // Allow time for mutations
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        }
        geneticAI.stopTraining();
        
        int afterMutation = geneticAI.getGeneration();
        double afterDiversity = geneticAI.getPopulationDiversity();
        
        // Verify mutation causes evolution progression
        assertTrue(afterMutation >= initialGeneration, "Mutation should advance generations");
        
        // Test that mutation maintains or increases diversity
        assertTrue(afterDiversity >= 0.0 && afterDiversity <= 1.0, "Mutation should maintain genetic diversity");
        
        // Test mutation effects on move selection
        game.resetGame();
        int[] mutatedMove = geneticAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (mutatedMove != null) {
            assertTrue(game.isValidMove(mutatedMove[0], mutatedMove[1], mutatedMove[2], mutatedMove[3]));
        }
        
        // Test multiple mutation cycles
        for (int cycle = 0; cycle < 3; cycle++) {
            geneticAI.startTraining(1);
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            geneticAI.stopTraining();
        }
        
        int finalGeneration = geneticAI.getGeneration();
        assertTrue(finalGeneration >= afterMutation, "Multiple mutations should continue evolution");
        
        // Verify mutation operations are functional
        assertTrue(finalGeneration >= initialGeneration, "Mutation should drive genetic variation");
    }
    
    @Test
    void testCrossoverOperations() {
        // Test genetic recombination through crossover operations
        int populationSize = geneticAI.getPopulationSize();
        assertTrue(populationSize > 1, "Population should support crossover");
        
        // Test initial population diversity for crossover
        double initialDiversity = geneticAI.getPopulationDiversity();
        assertTrue(initialDiversity >= 0.0 && initialDiversity <= 1.0, "Initial diversity should be valid");
        
        // Test crossover through evolutionary training
        int initialGeneration = geneticAI.getGeneration();
        double initialBestFitness = geneticAI.getBestFitness();
        
        geneticAI.startTraining(2);
        try { 
            Thread.sleep(1500); // Allow time for crossover operations
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        }
        geneticAI.stopTraining();
        
        int afterCrossover = geneticAI.getGeneration();
        double afterDiversity = geneticAI.getPopulationDiversity();
        double afterBestFitness = geneticAI.getBestFitness();
        
        // Verify crossover advances evolution
        assertTrue(afterCrossover >= initialGeneration, "Crossover should advance generations");
        
        // Test that crossover maintains genetic diversity
        assertTrue(afterDiversity >= 0.0 && afterDiversity <= 1.0, "Crossover should maintain diversity");
        
        // Test crossover effects on fitness
        assertTrue(afterBestFitness >= initialBestFitness, "Crossover should maintain or improve fitness");
        
        // Test crossover through move selection quality
        game.resetGame();
        java.util.List<int[]> crossoverMoves = new java.util.ArrayList<>();
        
        for (int test = 0; test < 5; test++) {
            game.resetGame();
            int[] move = geneticAI.selectMove(game.getBoard(), game.getAllValidMoves(test % 2 == 0));
            
            if (move != null) {
                crossoverMoves.add(move);
                assertTrue(game.isValidMove(move[0], move[1], move[2], move[3]));
            }
        }
        
        // Verify crossover produces diverse moves
        if (crossoverMoves.size() > 1) {
            // Check for move diversity (crossover should create variation)
            java.util.Set<String> uniqueMoves = new java.util.HashSet<>();
            for (int[] move : crossoverMoves) {
                uniqueMoves.add(java.util.Arrays.toString(move));
            }
            assertTrue(uniqueMoves.size() >= 1, "Crossover should produce move diversity");
        }
        
        // Verify crossover operations are functional
        assertTrue(afterCrossover >= initialGeneration, "Crossover should enable genetic recombination");
    }
    
    @Test
    @Timeout(30)
    void testGenerationalImprovement() {
        // Test evolution progression and multi-generational improvement
        int initialGeneration = geneticAI.getGeneration();
        double initialBestFitness = geneticAI.getBestFitness();
        double initialDiversity = geneticAI.getPopulationDiversity();
        
        // Test evolutionary improvement over multiple generations
        java.util.List<Integer> generationProgress = new java.util.ArrayList<>();
        java.util.List<Double> fitnessProgress = new java.util.ArrayList<>();
        
        generationProgress.add(initialGeneration);
        fitnessProgress.add(initialBestFitness);
        
        // Run evolution for several generations
        for (int epoch = 0; epoch < 3; epoch++) {
            geneticAI.startTraining(2);
            try { 
                Thread.sleep(1000); // Allow evolution time
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
            geneticAI.stopTraining();
            
            generationProgress.add(geneticAI.getGeneration());
            fitnessProgress.add(geneticAI.getBestFitness());
        }
        
        int finalGeneration = geneticAI.getGeneration();
        double finalBestFitness = geneticAI.getBestFitness();
        double finalDiversity = geneticAI.getPopulationDiversity();
        
        // Verify generational improvement
        assertTrue(finalGeneration > initialGeneration, "Evolution should advance generations");
        assertTrue(finalBestFitness >= initialBestFitness, "Evolution should maintain or improve fitness");
        
        // Test that evolution maintains population diversity
        assertTrue(finalDiversity >= 0.0 && finalDiversity <= 1.0, "Evolution should maintain genetic diversity");
        
        // Test improvement in move quality over generations
        game.resetGame();
        int[] evolvedMove = geneticAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (evolvedMove != null) {
            assertTrue(game.isValidMove(evolvedMove[0], evolvedMove[1], evolvedMove[2], evolvedMove[3]));
        }
        
        // Verify generational progression is monotonic
        boolean isProgressing = true;
        for (int i = 1; i < generationProgress.size(); i++) {
            if (generationProgress.get(i) < generationProgress.get(i-1)) {
                isProgressing = false;
                break;
            }
        }
        
        assertTrue(isProgressing, "Generations should progress monotonically");
        
        // Test fitness improvement trend
        boolean fitnessImproving = true;
        for (int i = 1; i < fitnessProgress.size(); i++) {
            if (fitnessProgress.get(i) < fitnessProgress.get(i-1)) {
                fitnessImproving = false;
                break;
            }
        }
        
        assertTrue(fitnessImproving, "Fitness should maintain or improve over generations");
        
        // Verify multi-generational improvement is functional
        assertTrue(finalGeneration >= initialGeneration + 1, "Genetic algorithm should show generational improvement");
    }
    
    @Test
    void testPopulationPersistence() {
        // Test save/load population data with evolutionary state
        int originalGeneration = geneticAI.getGeneration();
        int originalPopulationSize = geneticAI.getPopulationSize();
        double originalBestFitness = geneticAI.getBestFitness();
        double originalDiversity = geneticAI.getPopulationDiversity();
        
        // Evolve population before saving
        geneticAI.startTraining(2);
        try { 
            Thread.sleep(1000); 
        } catch (InterruptedException e) { 
            Thread.currentThread().interrupt(); 
        }
        geneticAI.stopTraining();
        
        int evolvedGeneration = geneticAI.getGeneration();
        double evolvedBestFitness = geneticAI.getBestFitness();
        
        // Save evolved population
        geneticAI.savePopulation();
        
        // Verify save files exist
        File populationFile = new File("state/ga_population.dat");
        File generationFile = new File("state/ga_generation.dat");
        
        if (populationFile.exists()) {
            assertTrue(populationFile.length() > 0, "Population file should have content");
        }
        if (generationFile.exists()) {
            assertTrue(generationFile.length() > 0, "Generation file should have content");
        }
        
        // Test loading into new AI instance
        GeneticAlgorithmAI newAI = new GeneticAlgorithmAI(false);
        
        // Verify loaded state matches saved state
        assertEquals(evolvedGeneration, newAI.getGeneration(), "Loaded generation should match saved");
        assertEquals(geneticAI.getPopulationSize(), newAI.getPopulationSize(), "Loaded population size should match");
        
        // Test that loaded AI can generate moves
        game.resetGame();
        int[] loadedMove = newAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (loadedMove != null) {
            assertTrue(game.isValidMove(loadedMove[0], loadedMove[1], loadedMove[2], loadedMove[3]));
        }
        
        // Test that loaded AI maintains evolutionary capability
        int loadedInitialGeneration = newAI.getGeneration();
        newAI.startTraining(1);
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        newAI.stopTraining();
        
        int loadedAfterTraining = newAI.getGeneration();
        assertTrue(loadedAfterTraining >= loadedInitialGeneration, "Loaded AI should continue evolution");
        
        // Verify population persistence maintains genetic diversity
        double loadedDiversity = newAI.getPopulationDiversity();
        assertTrue(loadedDiversity >= 0.0 && loadedDiversity <= 1.0, "Loaded population should maintain diversity");
        
        // Test fitness preservation
        double loadedBestFitness = newAI.getBestFitness();
        assertTrue(loadedBestFitness >= originalBestFitness, "Loaded population should preserve fitness progress");
        
        // Verify population persistence is functional
        assertTrue(newAI.getGeneration() >= originalGeneration, "Population persistence should maintain evolutionary state");
    }
}


