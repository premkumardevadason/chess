package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Adam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.*;
import java.nio.file.*;

@Service
public class EyeMovementLearningService {
    
    private static final Logger logger = LoggerFactory.getLogger(EyeMovementLearningService.class);
    
    private MultiLayerNetwork eyeMovementNetwork;
    private final Queue<EyeMovementSample> trainingQueue = new ConcurrentLinkedQueue<>();
    private static final String MODEL_FILE = "eye_movement_model.zip";
    private static final int BATCH_SIZE = 32;
    private static final int INPUT_SIZE = 6; // x, y, timestamp, face_width, face_height, confidence
    private static final int OUTPUT_SIZE = 2; // corrected_x, corrected_y
    
    @PostConstruct
    public void initializeNetwork() {
        try {
            // Try to load existing model
            Path modelPath = Paths.get(MODEL_FILE);
            if (Files.exists(modelPath)) {
                eyeMovementNetwork = MultiLayerNetwork.load(modelPath.toFile(), true);
                logger.info("Loaded existing eye movement model from {}", MODEL_FILE);
            } else {
                // Create new network
                createNewNetwork();
                logger.info("Created new eye movement learning network");
            }
        } catch (Exception e) {
            logger.warn("Failed to load model, creating new one: {}", e.getMessage());
            createNewNetwork();
        }
    }
    
    private void createNewNetwork() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .weightInit(WeightInit.XAVIER)
            .updater(new Adam(0.001))
            .list()
            .layer(0, new DenseLayer.Builder()
                .nIn(INPUT_SIZE)
                .nOut(64)
                .activation(Activation.RELU)
                .build())
            .layer(1, new DenseLayer.Builder()
                .nIn(64)
                .nOut(32)
                .activation(Activation.RELU)
                .build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .nIn(32)
                .nOut(OUTPUT_SIZE)
                .activation(Activation.IDENTITY)
                .build())
            .build();
            
        eyeMovementNetwork = new MultiLayerNetwork(conf);
        eyeMovementNetwork.init();
        eyeMovementNetwork.setListeners(new ScoreIterationListener(100));
    }
    
    public void recordEyeMovement(String sessionId, Point2D rawGaze, Point2D actualTarget, 
                                 double faceWidth, double faceHeight, double confidence) {
        EyeMovementSample sample = new EyeMovementSample(
            rawGaze, actualTarget, System.currentTimeMillis(), faceWidth, faceHeight, confidence
        );
        
        trainingQueue.offer(sample);
        logger.debug("Recorded eye movement: raw=({:.1f},{:.1f}) -> target=({:.1f},{:.1f})", 
            rawGaze.getX(), rawGaze.getY(), actualTarget.getX(), actualTarget.getY());
        
        // Train when we have enough samples
        if (trainingQueue.size() >= BATCH_SIZE) {
            trainOnBatch();
        }
    }
    
    public Point2D correctEyeMovement(Point2D rawGaze, double faceWidth, double faceHeight, double confidence) {
        if (eyeMovementNetwork == null) {
            return rawGaze;
        }
        
        try {
            // Prepare input
            INDArray input = Nd4j.create(new double[][]{
                {rawGaze.getX(), rawGaze.getY(), System.currentTimeMillis(), faceWidth, faceHeight, confidence}
            });
            
            // Get prediction
            INDArray output = eyeMovementNetwork.output(input);
            double correctedX = output.getDouble(0, 0);
            double correctedY = output.getDouble(0, 1);
            
            return new Point2D.Double(correctedX, correctedY);
            
        } catch (Exception e) {
            logger.warn("Error correcting eye movement: {}", e.getMessage());
            return rawGaze;
        }
    }
    
    private void trainOnBatch() {
        try {
            List<EyeMovementSample> batch = new ArrayList<>();
            for (int i = 0; i < BATCH_SIZE && !trainingQueue.isEmpty(); i++) {
                batch.add(trainingQueue.poll());
            }
            
            if (batch.isEmpty()) return;
            
            // Prepare training data
            double[][] inputs = new double[batch.size()][INPUT_SIZE];
            double[][] outputs = new double[batch.size()][OUTPUT_SIZE];
            
            for (int i = 0; i < batch.size(); i++) {
                EyeMovementSample sample = batch.get(i);
                inputs[i] = new double[]{
                    sample.rawGaze.getX(), sample.rawGaze.getY(), sample.timestamp,
                    sample.faceWidth, sample.faceHeight, sample.confidence
                };
                outputs[i] = new double[]{
                    sample.actualTarget.getX(), sample.actualTarget.getY()
                };
            }
            
            INDArray inputArray = Nd4j.create(inputs);
            INDArray outputArray = Nd4j.create(outputs);
            DataSet dataSet = new DataSet(inputArray, outputArray);
            
            // Train
            eyeMovementNetwork.fit(dataSet);
            
            // Save model periodically
            saveModel();
            
            logger.debug("Trained on batch of {} eye movement samples", batch.size());
            
        } catch (Exception e) {
            logger.error("Error training eye movement model: {}", e.getMessage());
        }
    }
    
    private void saveModel() {
        try {
            eyeMovementNetwork.save(new File(MODEL_FILE), true);
            logger.debug("Saved eye movement model to {}", MODEL_FILE);
        } catch (Exception e) {
            logger.warn("Failed to save eye movement model: {}", e.getMessage());
        }
    }
    
    public void recordChessMove(String sessionId, Point2D gazePoint, String chessSquare) {
        // Convert chess square to pixel coordinates for training
        Point2D targetPixel = convertChessSquareToPixel(chessSquare);
        if (targetPixel != null) {
            recordEyeMovement(sessionId, gazePoint, targetPixel, 100, 100, 0.8);
        }
    }
    
    private Point2D convertChessSquareToPixel(String square) {
        if (square == null || square.length() != 2) return null;
        
        char file = square.charAt(0);
        char rank = square.charAt(1);
        
        // Convert to pixel coordinates (assuming 800x800 board)
        double x = (file - 'a') * 100 + 50; // Center of square
        double y = (8 - (rank - '1')) * 100 + 50;
        
        return new Point2D.Double(x, y);
    }
    
    public int getTrainingQueueSize() {
        return trainingQueue.size();
    }
    
    // Data class for eye movement samples
    private static class EyeMovementSample {
        final Point2D rawGaze;
        final Point2D actualTarget;
        final long timestamp;
        final double faceWidth;
        final double faceHeight;
        final double confidence;
        
        EyeMovementSample(Point2D rawGaze, Point2D actualTarget, long timestamp,
                         double faceWidth, double faceHeight, double confidence) {
            this.rawGaze = rawGaze;
            this.actualTarget = actualTarget;
            this.timestamp = timestamp;
            this.faceWidth = faceWidth;
            this.faceHeight = faceHeight;
            this.confidence = confidence;
        }
    }
}