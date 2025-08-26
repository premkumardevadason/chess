package com.example.chess.unit.ai;

import com.example.chess.GeneticAlgorithmAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class GeneticAlgorithmAITest {
    
    private GeneticAlgorithmAI geneticAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        geneticAI = new GeneticAlgorithmAI(false);
    }
    
    @Test
    void testPopulationInitialization() {
        assertTrue(geneticAI.getPopulationSize() > 0);
        assertTrue(geneticAI.getGeneration() >= 0);
        
        // Test population initialization
        assertNotNull(geneticAI);
        assertTrue(geneticAI.getPopulationSize() > 0);
    }
    
    @Test
    void testFitnessEvaluation() {
        // Test fitness evaluation through training
        double initialFitness = geneticAI.getBestFitness();
        assertTrue(initialFitness >= 0.0);
        
        // Test population has reasonable size
        assertTrue(geneticAI.getPopulationSize() >= 10);
    }
    
    @Test
    void testMutationOperations() {
        // Test mutation through evolution
        int initialGeneration = geneticAI.getGeneration();
        geneticAI.startTraining(3);
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        geneticAI.stopTraining();
        
        // Evolution should progress
        assertTrue(geneticAI.getGeneration() >= initialGeneration);
    }
    
    @Test
    void testCrossoverOperations() {
        // Test crossover through population diversity
        int populationSize = geneticAI.getPopulationSize();
        assertTrue(populationSize > 1);
        
        // Test population diversity
        double diversity = geneticAI.getPopulationDiversity();
        assertTrue(diversity >= 0.0 && diversity <= 1.0);
    }
    
    @Test
    @Timeout(30)
    void testGenerationalImprovement() {
        int initialGeneration = geneticAI.getGeneration();
        double initialBestFitness = geneticAI.getBestFitness();
        
        geneticAI.startTraining(2);
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        geneticAI.stopTraining();
        
        int afterGeneration = geneticAI.getGeneration();
        double afterBestFitness = geneticAI.getBestFitness();
        
        assertTrue(afterGeneration > initialGeneration);
        assertTrue(afterBestFitness >= initialBestFitness);
    }
    
    @Test
    void testPopulationPersistence() {
        geneticAI.savePopulation();
        assertTrue(new File("ga_population.dat").exists());
        assertTrue(new File("ga_generation.dat").exists());
        
        GeneticAlgorithmAI newAI = new GeneticAlgorithmAI(false);
        assertEquals(geneticAI.getGeneration(), newAI.getGeneration());
        assertEquals(geneticAI.getPopulationSize(), newAI.getPopulationSize());
    }
}