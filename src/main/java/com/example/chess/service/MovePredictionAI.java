package com.example.chess.service;

import com.example.chess.ChessGame;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LSTM-based Move Prediction AI with Attention Mechanism
 * Predicts user chess moves based on gaze sequences and chess context
 */
@Component
public class MovePredictionAI {
    
    private static final Logger logger = LoggerFactory.getLogger(MovePredictionAI.class);
    
    // LSTM Configuration Constants
    private static final int SEQUENCE_LENGTH = 30; // 1 second at 30 FPS
    private static final int GAZE_FEATURES = 20; // Gaze coordinate features
    private static final int CHESS_CONTEXT_FEATURES = 40; // Chess game context
    private static final int LSTM_HIDDEN_UNITS = 128;
    private static final int ATTENTION_HEADS = 8;
    private static final int MAX_LEGAL_MOVES = 50; // Maximum legal moves in chess
    private static final double LEARNING_RATE = 0.001;
    private static final int BATCH_SIZE = 32;
    
    private MultiLayerNetwork network;
    private boolean isInitialized = false;
    private final AtomicInteger trainingIterations = new AtomicInteger(0);
    
    @Autowired
    private GazeFeatureExtractor featureExtractor;
    
    @Autowired
    private ChessGame chessGame;
    
    // Training data storage
    private final List<TrainingExample> trainingData = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> moveToIndexMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> indexToMoveMap = new ConcurrentHashMap<>();
    
    /**
     * Initialize the LSTM network with attention mechanism
     */
    public void initializeNetwork() {
        try {
            logger.info("Initializing LSTM Move Prediction AI...");
            
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Adam(LEARNING_RATE))
                .weightInit(WeightInit.XAVIER)
                .list()
                
                // Input layer: Sequential gaze data (30 timesteps x 20 features)
                .layer(new LSTM.Builder()
                    .nIn(GAZE_FEATURES)
                    .nOut(LSTM_HIDDEN_UNITS)
                    .activation(Activation.TANH)
                    .gateActivationFunction(Activation.SIGMOID)
                    .dropOut(0.2)
                    .build())
                
                // Second LSTM layer for deeper sequence understanding
                .layer(new LSTM.Builder()
                    .nIn(LSTM_HIDDEN_UNITS)
                    .nOut(LSTM_HIDDEN_UNITS / 2)
                    .activation(Activation.TANH)
                    .gateActivationFunction(Activation.SIGMOID)
                    .dropOut(0.2)
                    .build())
                
                // Dense layer for chess context integration
                .layer(new DenseLayer.Builder()
                    .nIn(LSTM_HIDDEN_UNITS / 2 + CHESS_CONTEXT_FEATURES)
                    .nOut(256)
                    .activation(Activation.RELU)
                    .dropOut(0.3)
                    .build())
                
                // Output layer: Legal move probabilities
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                    .nIn(256)
                    .nOut(MAX_LEGAL_MOVES)
                    .activation(Activation.SOFTMAX)
                    .build())
                
                .build();
            
            network = new MultiLayerNetwork(conf);
            network.init();
            network.setListeners(new ScoreIterationListener(100));
            
            // Initialize move mappings
            initializeMoveMappings();
            
            isInitialized = true;
            logger.info("LSTM Move Prediction AI initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize LSTM network", e);
            throw new RuntimeException("LSTM initialization failed", e);
        }
    }
    
    /**
     * Predict the most likely move based on gaze sequence
     */
    public MovePrediction predictMove(GazeSequence gazeSequence) {
        if (!isInitialized) {
            initializeNetwork();
        }
        
        try {
            // Convert to GazeFeatureExtractor.GazeSequence
            GazeFeatureExtractor.GazeSequence convertedSequence = convertToFeatureExtractorSequence(gazeSequence);
            
            // Extract features from gaze sequence
            INDArray sequenceInput = featureExtractor.extractSequence(convertedSequence);
            INDArray chessContext = featureExtractor.extractChessContext(convertedSequence);
            
            // Reshape for LSTM input: [batchSize=1, sequenceLength, features]
            INDArray reshapedSequence = sequenceInput.reshape(1, SEQUENCE_LENGTH, GAZE_FEATURES);
            
            // Get LSTM output
            List<INDArray> feedForwardOutput = network.feedForward(reshapedSequence);
            INDArray lstmOutput = feedForwardOutput.get(2); // Second LSTM layer output
            
            // Combine LSTM output with chess context
            INDArray combinedInput = Nd4j.concat(1, lstmOutput, chessContext.reshape(1, CHESS_CONTEXT_FEATURES));
            
            // Get final prediction
            INDArray output = network.output(combinedInput);
            
            // Find top 3 predictions
            List<MovePrediction> predictions = getTopPredictions(output, 3);
            
            if (predictions.isEmpty()) {
                return new MovePrediction("unknown", 0.0, null);
            }
            
            MovePrediction bestPrediction = predictions.get(0);
            logger.debug("Predicted move: {} with confidence: {:.2f}", 
                bestPrediction.move, bestPrediction.confidence);
            
            return bestPrediction;
            
        } catch (Exception e) {
            logger.error("Error predicting move", e);
            return new MovePrediction("error", 0.0, null);
        }
    }
    
    /**
     * Train the network on a successful prediction
     */
    public void trainOnUserMove(GazeSequence gazeSequence, String actualMove) {
        if (!isInitialized) {
            initializeNetwork();
        }
        
        try {
            // Create training example
            TrainingExample example = new TrainingExample(gazeSequence, actualMove);
            trainingData.add(example);
            
            // Online learning with mini-batches
            if (trainingData.size() % BATCH_SIZE == 0) {
                trainBatch();
            }
            
            logger.debug("Added training example for move: {}", actualMove);
            
        } catch (Exception e) {
            logger.error("Error training on user move", e);
        }
    }
    
    /**
     * Train the network on a batch of examples
     */
    private void trainBatch() {
        try {
            if (trainingData.size() < BATCH_SIZE) {
                return;
            }
            
            // Get recent batch
            List<TrainingExample> batch = trainingData.subList(
                Math.max(0, trainingData.size() - BATCH_SIZE), trainingData.size());
            
            // Prepare training data
            INDArray sequences = Nd4j.zeros(BATCH_SIZE, SEQUENCE_LENGTH, GAZE_FEATURES);
            INDArray labels = Nd4j.zeros(BATCH_SIZE, MAX_LEGAL_MOVES);
            
            for (int i = 0; i < batch.size(); i++) {
                TrainingExample example = batch.get(i);
                
                // Convert and extract sequence features
                GazeFeatureExtractor.GazeSequence convertedSequence = convertToFeatureExtractorSequence(example.gazeSequence);
                INDArray sequenceFeatures = featureExtractor.extractSequence(convertedSequence);
                sequences.putRow(i, sequenceFeatures);
                
                // Create one-hot encoded label
                Integer moveIndex = moveToIndexMap.get(example.actualMove);
                if (moveIndex != null) {
                    labels.putScalar(i, moveIndex, 1.0);
                }
            }
            
            // Create dataset and train
            DataSet dataSet = new DataSet(sequences, labels);
            // Create a simple iterator for single dataset
            DataSetIterator iterator = new DataSetIterator() {
                private boolean hasNext = true;
                
                @Override
                public DataSet next() {
                    hasNext = false;
                    return dataSet;
                }
                
                @Override
                public boolean hasNext() {
                    return hasNext;
                }
                
                @Override
                public DataSet next(int num) {
                    return next();
                }
                
                @Override
                public boolean asyncSupported() {
                    return false;
                }
                
                @Override
                public void reset() {
                    hasNext = true;
                }
                
                @Override
                public int batch() {
                    return BATCH_SIZE;
                }
                
                @Override
                public void setPreProcessor(org.nd4j.linalg.dataset.api.DataSetPreProcessor preProcessor) {
                    // Not implemented
                }
                
                @Override
                public org.nd4j.linalg.dataset.api.DataSetPreProcessor getPreProcessor() {
                    return null;
                }
                
                @Override
                public boolean resetSupported() {
                    return true;
                }
                
                public boolean hasAsync() {
                    return false;
                }
                
                public void shutdown() {
                    // Not implemented
                }
                
                @Override
                public List<String> getLabels() {
                    return new ArrayList<>(); // Return empty list for now
                }
                
                @Override
                public int totalOutcomes() {
                    return MAX_LEGAL_MOVES; // Return the number of possible move outcomes
                }
                
                @Override
                public int inputColumns() {
                    return GAZE_FEATURES; // Return the number of input features
                }
            };
            
            network.fit(iterator);
            trainingIterations.incrementAndGet();
            
            logger.debug("Trained batch, total iterations: {}", trainingIterations.get());
            
        } catch (Exception e) {
            logger.error("Error training batch", e);
        }
    }
    
    /**
     * Get top N predictions from network output
     */
    private List<MovePrediction> getTopPredictions(INDArray output, int topN) {
        List<MovePrediction> predictions = new ArrayList<>();
        
        // Get sorted indices manually since argsort is not available
        List<Map.Entry<Integer, Double>> indexedValues = new ArrayList<>();
        for (int i = 0; i < output.length(); i++) {
            indexedValues.add(new AbstractMap.SimpleEntry<>(i, output.getDouble(i)));
        }
        
        // Sort by confidence (descending)
        indexedValues.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        for (int i = 0; i < Math.min(topN, MAX_LEGAL_MOVES); i++) {
            int moveIndex = indexedValues.get(i).getKey();
            double confidence = indexedValues.get(i).getValue();
            String move = indexToMoveMap.get(moveIndex);
            
            if (move != null && confidence > 0.01) { // Minimum confidence threshold
                predictions.add(new MovePrediction(move, confidence, null));
            }
        }
        
        return predictions;
    }
    
    /**
     * Initialize move mappings for legal chess moves
     */
    private void initializeMoveMappings() {
        // Generate all possible legal moves (simplified)
        List<String> legalMoves = generateLegalMoves();
        
        for (int i = 0; i < Math.min(legalMoves.size(), MAX_LEGAL_MOVES); i++) {
            String move = legalMoves.get(i);
            moveToIndexMap.put(move, i);
            indexToMoveMap.put(i, move);
        }
        
        logger.info("Initialized move mappings for {} legal moves", legalMoves.size());
    }
    
    /**
     * Generate list of legal chess moves (simplified)
     */
    private List<String> generateLegalMoves() {
        List<String> moves = new ArrayList<>();
        
        // Generate basic moves for each piece type
        String[] pieces = {"P", "R", "N", "B", "Q", "K"};
        String[] files = {"a", "b", "c", "d", "e", "f", "g", "h"};
        String[] ranks = {"1", "2", "3", "4", "5", "6", "7", "8"};
        
        for (String piece : pieces) {
            for (String file : files) {
                for (String rank : ranks) {
                    // Add moves for each piece
                    moves.add(piece + file + rank);
                }
            }
        }
        
        return moves;
    }
    
    /**
     * Get current training statistics
     */
    public TrainingStats getTrainingStats() {
        return new TrainingStats(
            trainingData.size(),
            trainingIterations.get(),
            isInitialized,
            moveToIndexMap.size()
        );
    }
    
    /**
     * Save the trained model
     */
    public void saveModel(String filePath) {
        try {
            if (network != null) {
                network.save(new java.io.File(filePath));
                logger.info("Model saved to: {}", filePath);
            }
        } catch (Exception e) {
            logger.error("Error saving model", e);
        }
    }
    
    /**
     * Load a pre-trained model
     */
    public void loadModel(String filePath) {
        try {
            network = MultiLayerNetwork.load(new java.io.File(filePath), true);
            isInitialized = true;
            logger.info("Model loaded from: {}", filePath);
        } catch (Exception e) {
            logger.error("Error loading model", e);
        }
    }
    
    // Data classes
    public static class GazeSequence {
        public List<GazePoint> gazePoints;
        public double gamePhase;
        public double materialBalance;
        public double kingSafety;
        public double centerControl;
        public double developmentScore;
        public double pawnStructure;
        public double pieceActivity;
        public double tacticalThreats;
        public double positionalAdvantage;
        public double timeRemaining;
        public int moveNumber;
        public double repetitionRisk;
        public double complexityScore;
        public int numberOfLegalMoves;
        public boolean isInCheck;
        public boolean canCastle;
        public boolean enPassantAvailable;
        public boolean promotionPossible;
        public double playerSkillLevel;
        public double historicalAccuracy;
        public double attentionSpread;
        public double focusIntensity;
        public double scanPathLength;
        public double backtrackCount;
        public double hesitationTime;
        public double confirmationLooks;
        public double alternativeConsiderations;
        public double cognitiveLoad;
        public double gazeStability;
        public double movementSmoothness;
        public double boundaryProximity;
        public double centerBias;
        public double diagonalPreference;
        public double horizontalMovement;
        public double verticalMovement;
        public double knightMovePattern;
        public double castlingPattern;
        public double capturePattern;
        public double defensivePattern;
        public double timeToDecision;
        
        public GazeSequence() {
            this.gazePoints = new ArrayList<>();
        }
    }
    
    public static class GazePoint {
        public double x, y;
        public double chessSquareX, chessSquareY;
        public double gazeVelocityX, gazeVelocityY;
        public double fixationDuration;
        public double saccadeAmplitude;
        public double timestamp;
        public double deltaTime;
        public boolean isFixation;
        public boolean blinkDetected;
        public double confidenceScore;
        public double pieceType;
        public boolean isLegalMoveTarget;
        public double threatLevel;
        public boolean isPlayerPiece;
        public double moveNumber;
        public double timeRemaining;
        public String chessSquare;
        
        public GazePoint() {}
    }
    
    public static class MovePrediction {
        public final String move;
        public final double confidence;
        public final INDArray attentionWeights;
        
        public MovePrediction(String move, double confidence, INDArray attentionWeights) {
            this.move = move;
            this.confidence = confidence;
            this.attentionWeights = attentionWeights;
        }
    }
    
    public static class TrainingExample {
        public final GazeSequence gazeSequence;
        public final String actualMove;
        
        public TrainingExample(GazeSequence gazeSequence, String actualMove) {
            this.gazeSequence = gazeSequence;
            this.actualMove = actualMove;
        }
    }
    
    public static class TrainingStats {
        public final int trainingExamples;
        public final int iterations;
        public final boolean isInitialized;
        public final int legalMovesCount;
        
        public TrainingStats(int trainingExamples, int iterations, boolean isInitialized, int legalMovesCount) {
            this.trainingExamples = trainingExamples;
            this.iterations = iterations;
            this.isInitialized = isInitialized;
            this.legalMovesCount = legalMovesCount;
        }
    }
    
    /**
     * Convert MovePredictionAI.GazeSequence to GazeFeatureExtractor.GazeSequence
     */
    private GazeFeatureExtractor.GazeSequence convertToFeatureExtractorSequence(GazeSequence gazeSequence) {
        GazeFeatureExtractor.GazeSequence converted = new GazeFeatureExtractor.GazeSequence();
        
        // Copy common fields
        converted.gamePhase = gazeSequence.gamePhase;
        converted.materialBalance = gazeSequence.materialBalance;
        converted.kingSafety = gazeSequence.kingSafety;
        converted.centerControl = gazeSequence.centerControl;
        converted.developmentScore = gazeSequence.developmentScore;
        converted.pawnStructure = gazeSequence.pawnStructure;
        converted.pieceActivity = gazeSequence.pieceActivity;
        converted.tacticalThreats = gazeSequence.tacticalThreats;
        converted.positionalAdvantage = gazeSequence.positionalAdvantage;
        converted.timeRemaining = gazeSequence.timeRemaining;
        converted.moveNumber = (double) gazeSequence.moveNumber;
        converted.repetitionRisk = gazeSequence.repetitionRisk;
        converted.complexityScore = gazeSequence.complexityScore;
        converted.numberOfLegalMoves = (double) gazeSequence.numberOfLegalMoves;
        
        return converted;
    }
}
