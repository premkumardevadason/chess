package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.awt.geom.Point2D;

@Service
public class LSTMMovePredictionAI {
    
    private MultiLayerNetwork network;
    private Queue<GazeSequence> trainingQueue = new ConcurrentLinkedQueue<>();
    private static final int SEQUENCE_LENGTH = 20;
    private static final int FEATURE_SIZE = 20;
    private static final int OUTPUT_SIZE = 64;
    
    @Autowired
    private com.example.chess.ChessGame chessGame;
    
    @PostConstruct
    public void initializeNetwork() {
        try {
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(12345)
                .updater(new org.nd4j.linalg.learning.config.Adam(0.001))
                .list()
                .layer(new LSTM.Builder()
                    .nIn(FEATURE_SIZE)
                    .nOut(128)
                    .activation(Activation.TANH)
                    .build())
                .layer(new DenseLayer.Builder()
                    .nIn(128)
                    .nOut(256)
                    .activation(Activation.RELU)
                    .dropOut(0.3)
                    .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                    .nIn(256)
                    .nOut(OUTPUT_SIZE)
                    .activation(Activation.SOFTMAX)
                    .build())
                .build();
                
            network = new MultiLayerNetwork(conf);
            network.init();
            network.setListeners(new ScoreIterationListener(10));
            
            System.out.println("LSTM Move Prediction AI initialized");
            
        } catch (Exception e) {
            System.err.println("Error initializing LSTM network: " + e.getMessage());
        }
    }
    
    public MovePrediction predictMove(String sessionId, List<Point2D> gazeSequence, String currentSquare) {
        try {
            if (network == null || gazeSequence.size() < 10) {
                return new MovePrediction(null, 0.0, "Insufficient data");
            }
            
            INDArray features = extractFeatures(gazeSequence, currentSquare);
            if (features == null) {
                return new MovePrediction(null, 0.0, "Feature extraction failed");
            }
            
            INDArray output = network.output(features);
            int bestMoveIndex = Nd4j.argMax(output, 1).getInt(0);
            double confidence = output.getDouble(bestMoveIndex);
            
            String predictedMove = indexToMove(bestMoveIndex, currentSquare);
            
            if (predictedMove != null && isLegalMove(predictedMove)) {
                return new MovePrediction(predictedMove, confidence, "LSTM prediction");
            } else {
                return new MovePrediction(null, 0.0, "Illegal move predicted");
            }
            
        } catch (Exception e) {
            System.err.println("Error in LSTM prediction: " + e.getMessage());
            return new MovePrediction(null, 0.0, "Prediction error");
        }
    }
    
    private INDArray extractFeatures(List<Point2D> gazeSequence, String currentSquare) {
        try {
            int seqLen = Math.min(gazeSequence.size(), SEQUENCE_LENGTH);
            INDArray features = Nd4j.zeros(1, seqLen, FEATURE_SIZE);
            
            for (int t = 0; t < seqLen; t++) {
                Point2D point = gazeSequence.get(gazeSequence.size() - seqLen + t);
                double[] pointFeatures = extractPointFeatures(point, t, currentSquare);
                
                for (int f = 0; f < FEATURE_SIZE && f < pointFeatures.length; f++) {
                    features.putScalar(0, t, f, pointFeatures[f]);
                }
            }
            
            return features;
            
        } catch (Exception e) {
            System.err.println("Error extracting features: " + e.getMessage());
            return null;
        }
    }
    
    private double[] extractPointFeatures(Point2D point, int timeStep, String currentSquare) {
        double[] features = new double[FEATURE_SIZE];
        
        features[0] = point.getX() / 1920.0;
        features[1] = point.getY() / 1080.0;
        features[2] = timeStep / (double)SEQUENCE_LENGTH;
        
        if (currentSquare != null && currentSquare.length() >= 2) {
            features[3] = (currentSquare.charAt(0) - 'a') / 7.0;
            features[4] = (Character.getNumericValue(currentSquare.charAt(1)) - 1) / 7.0;
        }
        
        for (int i = 5; i < FEATURE_SIZE; i++) {
            features[i] = Math.sin(i * point.getX() / 100.0) * 0.1;
        }
        
        return features;
    }
    
    private String indexToMove(int index, String fromSquare) {
        if (fromSquare == null || fromSquare.length() < 2) {
            return null;
        }
        
        try {
            int targetRow = (index / 8) % 8;
            int targetCol = index % 8;
            
            char toFile = (char)('a' + targetCol);
            int toRank = targetRow + 1;
            
            return fromSquare + "" + toFile + toRank;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isLegalMove(String move) {
        try {
            if (chessGame == null || move.length() < 4) {
                return false;
            }
            
            int fromCol = move.charAt(0) - 'a';
            int fromRow = Character.getNumericValue(move.charAt(1)) - 1;
            int toCol = move.charAt(2) - 'a';
            int toRow = Character.getNumericValue(move.charAt(3)) - 1;
            
            int fromRowIdx = 7 - fromRow;
            int toRowIdx = 7 - toRow;
            
            return chessGame.isValidMove(fromRowIdx, fromCol, toRowIdx, toCol);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public static class MovePrediction {
        public final String move;
        public final double confidence;
        public final String method;
        
        public MovePrediction(String move, double confidence, String method) {
            this.move = move;
            this.confidence = confidence;
            this.method = method;
        }
    }
    
    private static class GazeSequence {
        public final String sessionId;
        public final List<Point2D> gazePoints;
        public final String actualMove;
        
        public GazeSequence(String sessionId, List<Point2D> gazePoints, String actualMove) {
            this.sessionId = sessionId;
            this.gazePoints = new ArrayList<>(gazePoints);
            this.actualMove = actualMove;
        }
    }
}