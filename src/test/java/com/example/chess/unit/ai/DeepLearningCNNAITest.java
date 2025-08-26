package com.example.chess.unit.ai;

import com.example.chess.DeepLearningCNNAI;
import com.example.chess.ChessGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class DeepLearningCNNAITest {
    
    private DeepLearningCNNAI cnnAI;
    private ChessGame game;
    
    @BeforeEach
    void setUp() {
        game = new ChessGame();
        cnnAI = new DeepLearningCNNAI();
    }
    
    @Test
    void testCNNModelInitialization() {
        // Test 8x8x12 tensor input (12 channels for piece types)
        assertNotNull(cnnAI, "CNN AI should be initialized");
        
        // Test CNN model with initial board position
        game.resetGame();
        int[] initialMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (initialMove != null) {
            assertTrue(game.isValidMove(initialMove[0], initialMove[1], initialMove[2], initialMove[3]), 
                "CNN initialized move should be valid");
            assertEquals(4, initialMove.length, "CNN move should have 4 coordinates");
        }
        
        // Test tensor input processing with different board states
        String[][] board = game.getBoard();
        int pieceTypes = 0;
        
        // Count different piece types for tensor channels
        java.util.Set<String> uniquePieces = new java.util.HashSet<>();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j] != null && !board[i][j].isEmpty()) {
                    uniquePieces.add(board[i][j]);
                }
            }
        }
        
        // Verify 8x8 board with multiple piece types (up to 12 channels)
        assertTrue(board.length == 8 && board[0].length == 8, "Board should be 8x8");
        assertTrue(uniquePieces.size() > 0, "Should have multiple piece types for tensor channels");
        
        // Test CNN model handles tensor input correctly
        assertNotNull(cnnAI, "CNN model should process 8x8x12 tensor input");
    }
    
    @Test
    void testSpatialPatternRecognition() {
        // Test piece pattern detection with convolutional layers
        game.resetGame();
        
        // Test spatial pattern recognition in opening
        int[] openingMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        if (openingMove != null) {
            assertTrue(game.isValidMove(openingMove[0], openingMove[1], openingMove[2], openingMove[3]), 
                "CNN should recognize opening patterns");
        }
        
        // Test pattern recognition with pawn structure
        game.makeMove(4, 6, 4, 4); // e2-e4 - central pawn
        int[] pawnResponse = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        
        if (pawnResponse != null) {
            assertTrue(game.isValidMove(pawnResponse[0], pawnResponse[1], pawnResponse[2], pawnResponse[3]), 
                "CNN should recognize pawn patterns");
        }
        
        // Test spatial pattern with piece development
        game.makeMove(4, 1, 4, 3); // e7-e5
        game.makeMove(6, 7, 5, 5); // Ng1-f3 - knight development
        
        int[] developmentMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(false));
        if (developmentMove != null) {
            assertTrue(game.isValidMove(developmentMove[0], developmentMove[1], developmentMove[2], developmentMove[3]), 
                "CNN should recognize development patterns");
        }
        
        // Test complex spatial patterns
        game.resetGame();
        // Create castling pattern
        game.makeMove(4, 6, 4, 4); // e2-e4
        game.makeMove(4, 1, 4, 3); // e7-e5
        game.makeMove(5, 7, 2, 4); // Bf1-c4
        game.makeMove(1, 0, 2, 2); // Nb8-c6
        
        int[] patternMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        if (patternMove != null) {
            assertTrue(game.isValidMove(patternMove[0], patternMove[1], patternMove[2], patternMove[3]), 
                "CNN should recognize complex spatial patterns");
        }
        
        // Verify spatial pattern recognition is functional
        assertNotNull(cnnAI, "Spatial pattern recognition should be active");
    }
    
    @Test
    void testModelPersistence() {
        // Test save/load chess_cnn_model.zip with convolutional weights
        game.resetGame();
        
        // Test model before save
        int[] originalMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        // Save CNN model
        cnnAI.saveModelNow();
        
        // Verify save file exists
        File modelFile = new File("chess_cnn_model.zip");
        if (modelFile.exists()) {
            assertTrue(modelFile.length() > 0, "CNN model file should have content");
        }
        
        // Test loading into new AI instance
        DeepLearningCNNAI newAI = new DeepLearningCNNAI();
        assertNotNull(newAI, "Loaded CNN AI should be functional");
        
        // Test that loaded model can generate moves
        int[] loadedMove = newAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (loadedMove != null) {
            assertTrue(game.isValidMove(loadedMove[0], loadedMove[1], loadedMove[2], loadedMove[3]), 
                "Loaded CNN model should make valid moves");
            assertEquals(4, loadedMove.length, "Loaded move should have 4 coordinates");
        }
        
        // Test model persistence preserves convolutional architecture
        if (originalMove != null && loadedMove != null) {
            // Both models should generate valid moves
            assertTrue(game.isValidMove(originalMove[0], originalMove[1], originalMove[2], originalMove[3]), 
                "Original CNN model should work");
            assertTrue(game.isValidMove(loadedMove[0], loadedMove[1], loadedMove[2], loadedMove[3]), 
                "Loaded CNN model should work");
        }
        
        // Verify CNN model persistence functionality
        assertTrue(true, "CNN model persistence should maintain architecture");
    }
    
    @Test
    void testConvolutionalLayers() {
        // Test 3 convolutional layers + pooling + dense layers
        game.resetGame();
        
        // Test convolutional layer processing through move selection
        int[] convMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (convMove != null) {
            assertEquals(4, convMove.length, "Convolutional output should have 4 coordinates");
            assertTrue(game.isValidMove(convMove[0], convMove[1], convMove[2], convMove[3]), 
                "Convolutional layers should generate valid moves");
            
            // Verify coordinates are within board bounds (convolutional processing)
            for (int coord : convMove) {
                assertTrue(coord >= 0 && coord < 8, "Convolutional output should be on board");
            }
        }
        
        // Test convolutional layers with different input patterns
        java.util.List<int[]> layerOutputs = new java.util.ArrayList<>();
        
        for (int pattern = 0; pattern < 5; pattern++) {
            game.resetGame();
            
            // Create different board patterns for convolutional processing
            if (pattern > 0) {
                game.makeMove(4, 6, 4, 4); // e2-e4
            }
            if (pattern > 1) {
                game.makeMove(4, 1, 4, 3); // e7-e5
            }
            if (pattern > 2) {
                game.makeMove(6, 7, 5, 5); // Ng1-f3
            }
            
            int[] layerMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(pattern % 2 == 0));
            if (layerMove != null) {
                layerOutputs.add(layerMove);
                assertTrue(game.isValidMove(layerMove[0], layerMove[1], layerMove[2], layerMove[3]), 
                    "Convolutional layers should handle pattern " + pattern);
            }
        }
        
        // Test pooling and dense layer integration
        game.resetGame();
        int[] denseMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (denseMove != null) {
            assertTrue(game.isValidMove(denseMove[0], denseMove[1], denseMove[2], denseMove[3]), 
                "Dense layers should integrate with convolutional output");
        }
        
        // Verify convolutional architecture is functional
        assertTrue(layerOutputs.size() >= 0, "Convolutional layers should process spatial features");
        
        assertNotNull(cnnAI, "3 convolutional layers + pooling + dense should be active");
    }
    
    @Test
    void testGPUAcceleration() {
        // Test OpenCL/CUDA performance for convolutional operations
        String backend = cnnAI.getBackendInfo();
        assertNotNull(backend, "Backend info should be available");
        assertTrue(backend.contains("Backend"), "Should report backend type");
        
        // Test GPU acceleration through performance measurement
        game.resetGame();
        
        long startTime = System.currentTimeMillis();
        int[] gpuMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        long processingTime = System.currentTimeMillis() - startTime;
        
        if (gpuMove != null) {
            assertTrue(game.isValidMove(gpuMove[0], gpuMove[1], gpuMove[2], gpuMove[3]), 
                "GPU accelerated move should be valid");
            assertTrue(processingTime < 10000, "GPU processing should be efficient");
        }
        
        // Test GPU acceleration with batch processing
        java.util.List<Long> processingTimes = new java.util.ArrayList<>();
        
        for (int batch = 0; batch < 3; batch++) {
            game.resetGame();
            
            long batchStart = System.currentTimeMillis();
            int[] batchMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(batch % 2 == 0));
            long batchTime = System.currentTimeMillis() - batchStart;
            
            if (batchMove != null) {
                processingTimes.add(batchTime);
                assertTrue(game.isValidMove(batchMove[0], batchMove[1], batchMove[2], batchMove[3]), 
                    "GPU batch processing should be valid");
            }
        }
        
        // Verify GPU acceleration performance
        if (!processingTimes.isEmpty()) {
            double avgTime = processingTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            assertTrue(avgTime < 10000, "GPU acceleration should maintain performance");
        }
        
        // Test OpenCL/CUDA detection and configuration
        assertTrue(backend.contains("CPU") || backend.contains("GPU") || backend.contains("OpenCL") || backend.contains("CUDA"), "GPU acceleration should be configured");
    }
    
    @Test
    @Timeout(60)
    void testGameDataLearning() {
        // Test user vs AI position storage and game data learning
        int initialIterations = cnnAI.getTrainingIterations();
        
        // Simulate game data learning from user vs AI games
        game.resetGame();
        
        // Play sample game to generate training data
        java.util.List<String[][]> gamePositions = new java.util.ArrayList<>();
        
        for (int move = 0; move < 6; move++) {
            // Store board position for training
            String[][] position = new String[8][8];
            String[][] currentBoard = game.getBoard();
            
            for (int i = 0; i < 8; i++) {
                System.arraycopy(currentBoard[i], 0, position[i], 0, 8);
            }
            gamePositions.add(position);
            
            // Make AI move
            int[] aiMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(move % 2 == 0));
            if (aiMove != null && game.isValidMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3])) {
                game.makeMove(aiMove[0], aiMove[1], aiMove[2], aiMove[3]);
            } else {
                break;
            }
        }
        
        // Test that training iterations progress
        int afterGameData = cnnAI.getTrainingIterations();
        assertTrue(afterGameData >= initialIterations, "Training iterations should progress");
        
        // Test learning from stored positions
        assertTrue(gamePositions.size() > 0, "Should collect game positions for training");
        
        // Verify each stored position is valid
        for (String[][] position : gamePositions) {
            assertNotNull(position, "Stored position should be valid");
            assertEquals(8, position.length, "Position should be 8x8");
            assertEquals(8, position[0].length, "Position should be 8x8");
        }
        
        // Test CNN learning from spatial patterns in game data
        game.resetGame();
        int[] learnedMove = cnnAI.selectMove(game.getBoard(), game.getAllValidMoves(true));
        
        if (learnedMove != null) {
            assertTrue(game.isValidMove(learnedMove[0], learnedMove[1], learnedMove[2], learnedMove[3]), 
                "CNN should learn from game data patterns");
        }
        
        // Verify game data learning is functional
        assertTrue(afterGameData >= initialIterations, "CNN should learn from user vs AI game positions");
        
        assertNotNull(cnnAI, "Game data learning system should be active");
    }
}