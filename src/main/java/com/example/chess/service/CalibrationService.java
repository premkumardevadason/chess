package com.example.chess.service;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Calibration service for gaze accuracy improvement
 * Handles 9-point calibration and gaze correction algorithms
 */
@Service
@ConditionalOnProperty(name = "chess.eyetracking.services.enabled", havingValue = "true", matchIfMissing = false)
public class CalibrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CalibrationService.class);
    
    // Calibration configuration
    private static final int CALIBRATION_POINTS = 64; // 8x8 chess board
    private static final double CALIBRATION_ACCURACY_THRESHOLD = 0.8;
    private static final long CALIBRATION_TIMEOUT_MS = 3000; // 3 seconds per point
    
    @Autowired
    private EyeTrackingService eyeTrackingService;
    
    @Autowired
    private PrivacyService privacyService;
    
    // Calibration data storage
    private final Map<Integer, CalibrationPoint> calibrationPoints = new ConcurrentHashMap<>();
    private final Map<String, CalibrationSession> activeSessions = new ConcurrentHashMap<>();
    private volatile boolean isCalibrating = false;
    private volatile String currentSessionId = null;
    
    // Calibration model
    private CalibrationModel calibrationModel = null;
    
    // Global calibration data file path in state/visual-chess folder
    private static final String CALIBRATION_DATA_FILE = "state/visual-chess/global_calibration.dat";
    
    @PostConstruct
    public void loadCalibrationData() {
        try {
            // Ensure state directory exists
            Path stateDir = Paths.get("state");
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
                logger.info("Created state directory: {}", stateDir.toAbsolutePath());
            }
            
            // Ensure state/visual-chess directory exists
            Path visualChessDir = Paths.get("state/visual-chess");
            if (!Files.exists(visualChessDir)) {
                Files.createDirectories(visualChessDir);
                logger.info("Created visual-chess directory: {}", visualChessDir.toAbsolutePath());
            }
            
            Path calibrationFile = Paths.get(CALIBRATION_DATA_FILE);
            logger.debug("Looking for calibration file at: {}", calibrationFile.toAbsolutePath());
            if (Files.exists(calibrationFile)) {
                byte[] encryptedData = Files.readAllBytes(calibrationFile);
                byte[] decryptedData = privacyService.decryptGazeData(encryptedData);
                String calibrationJson = new String(decryptedData);
                
                // Parse and restore calibration model
                calibrationModel = deserializeCalibrationModel(calibrationJson);
                if (calibrationModel != null) {
                    logger.info("Loaded calibration data with accuracy: {}%", 
                        String.format("%.2f", calibrationModel.getAccuracy() * 100));
                } else {
                    logger.warn("Failed to parse calibration data, starting fresh");
                }
            } else {
                logger.info("No existing calibration data found");
            }
        } catch (Exception e) {
            logger.warn("Failed to load calibration data ({}), deleting corrupted file", e.getMessage());
            try {
                Files.deleteIfExists(Paths.get(CALIBRATION_DATA_FILE));
                logger.info("Deleted corrupted calibration file");
            } catch (IOException deleteError) {
                logger.warn("Could not delete corrupted calibration file: {}", deleteError.getMessage());
            }
            calibrationModel = null;
        }
    }
    
    /**
     * Start a new calibration session
     */
    public CalibrationSession startCalibration(String sessionId) {
        if (isCalibrating) {
            throw new IllegalStateException("Calibration already in progress");
        }
        
        isCalibrating = true;
        currentSessionId = "global"; // Single user calibration
        
        CalibrationSession session = new CalibrationSession("global");
        activeSessions.put("global", session);
        
        logger.info("Started global calibration session");
        return session;
    }
    
    /**
     * Record calibration data for a specific chess square
     */
    public void recordCalibrationPoint(String sessionId, int pointIndex, Point2D targetPoint, 
                                     List<Point2D> gazeSamples) {
        try {
            CalibrationSession session = activeSessions.get("global");
            if (session == null) {
                logger.warn("No active global calibration session found");
                return;
            }
            
            // Calculate average gaze point
            Point2D averageGaze = calculateAverageGazePoint(gazeSamples);
            
            // Create calibration point
            CalibrationPoint calibPoint = new CalibrationPoint(
                pointIndex, targetPoint, averageGaze, gazeSamples, System.currentTimeMillis()
            );
            
            // Store calibration point
            calibrationPoints.put(pointIndex, calibPoint);
            session.addCalibrationPoint(calibPoint);
            
            String square = indexToSquare(pointIndex);
            logger.info("*** CALIBRATION RECEIVED: Square {} (point {}): target=({},{}) gaze=({},{}) ***",
                square, pointIndex, targetPoint.getX(), targetPoint.getY(),
                averageGaze.getX(), averageGaze.getY());
            
            // Check if calibration is complete
            if (session.getCalibrationPoints().size() >= CALIBRATION_POINTS) {
                completeCalibration("global");
            }
            
        } catch (Exception e) {
            logger.error("Error recording calibration point", e);
        }
    }
    
    /**
     * Complete the calibration process
     */
    public CalibrationResult completeCalibration(String sessionId) {
        try {
            CalibrationSession session = activeSessions.get("global");
            if (session == null) {
                throw new IllegalStateException("No active calibration session found");
            }
            
            // Build calibration model
            calibrationModel = buildCalibrationModel(session.getCalibrationPoints());
            
            // Calculate calibration accuracy
            double accuracy = calculateCalibrationAccuracy(session.getCalibrationPoints());
            
            // Create result
            CalibrationResult result = new CalibrationResult(
                "global", accuracy, calibrationModel, session.getCalibrationPoints()
            );
            
            // Store calibration data securely
            storeCalibrationData("global", result);
            
            // Clean up
            activeSessions.remove("global");
            isCalibrating = false;
            currentSessionId = null;
            
            logger.info("Completed global calibration with accuracy: {}%", String.format("%.2f", accuracy * 100));
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error completing calibration", e);
            isCalibrating = false;
            currentSessionId = null;
            throw new RuntimeException("Calibration completion failed", e);
        }
    }
    
    /**
     * Cancel the current calibration
     */
    public void cancelCalibration(String sessionId) {
        activeSessions.remove("global");
        isCalibrating = false;
        currentSessionId = null;
        logger.info("Cancelled global calibration session");
    }
    
    /**
     * Get chess board calibration sequence (sequential)
     */
    public List<String> getSequentialCalibrationOrder() {
        List<String> sequence = new ArrayList<>();
        for (int rank = 1; rank <= 8; rank++) {
            for (char file = 'a'; file <= 'h'; file++) {
                sequence.add("" + file + rank);
            }
        }
        return sequence;
    }
    
    /**
     * Get chess board calibration sequence (random)
     */
    public List<String> getRandomCalibrationOrder() {
        List<String> sequence = getSequentialCalibrationOrder();
        Collections.shuffle(sequence);
        return sequence;
    }
    
    /**
     * Convert square name to calibration index
     */
    public int squareToIndex(String square) {
        char file = square.charAt(0);
        int rank = Character.getNumericValue(square.charAt(1));
        return (rank - 1) * 8 + (file - 'a');
    }
    
    /**
     * Convert calibration index to square name
     */
    public String indexToSquare(int index) {
        if (index < 0 || index >= 128) {
            return "invalid" + index;
        }
        // Handle 128 points (64 sequential + 64 random)
        int actualIndex = index % 64;
        int rank = 8 - (actualIndex / 8);
        char file = (char)('a' + (actualIndex % 8));
        return "" + file + rank;
    }
    
    /**
     * Correct a gaze point using the calibration model
     */
    public Point2D correctGazePoint(Point2D rawGazePoint) {
        if (calibrationModel == null) {
            logger.warn("No calibration model available, returning raw gaze point");
            return rawGazePoint;
        }
        
        try {
            return calibrationModel.correctGazePoint(rawGazePoint);
        } catch (Exception e) {
            logger.warn("Error correcting gaze point, returning raw point", e);
            return rawGazePoint;
        }
    }
    
    /**
     * Check if calibration is available
     */
    public boolean isCalibrationAvailable() {
        return calibrationModel != null;
    }
    
    /**
     * Get calibration accuracy
     */
    public double getCalibrationAccuracy() {
        return calibrationModel != null ? calibrationModel.getAccuracy() : 0.0;
    }
    
    /**
     * Calculate average gaze point from samples
     */
    private Point2D calculateAverageGazePoint(List<Point2D> gazeSamples) {
        if (gazeSamples == null || gazeSamples.isEmpty()) {
            return new Point2D.Double(0, 0);
        }
        
        double sumX = 0, sumY = 0;
        for (Point2D point : gazeSamples) {
            sumX += point.getX();
            sumY += point.getY();
        }
        
        return new Point2D.Double(sumX / gazeSamples.size(), sumY / gazeSamples.size());
    }
    
    /**
     * Build calibration model from calibration points
     */
    private CalibrationModel buildCalibrationModel(List<CalibrationPoint> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 calibration points");
        }
        
        // Use affine transformation for gaze correction
        return new AffineCalibrationModel(points);
    }
    
    /**
     * Calculate calibration accuracy
     */
    private double calculateCalibrationAccuracy(List<CalibrationPoint> points) {
        if (points.isEmpty()) return 0.0;
        
        double totalError = 0.0;
        int validPoints = 0;
        
        for (CalibrationPoint point : points) {
            if (point.getGazeSamples().size() > 0) {
                Point2D corrected = calibrationModel.correctGazePoint(point.getAverageGaze());
                double error = point.getTargetPoint().distance(corrected);
                totalError += error;
                validPoints++;
            }
        }
        
        if (validPoints == 0) return 0.0;
        
        double averageError = totalError / validPoints;
        // Convert error to accuracy (0-1 scale, higher is better)
        double accuracy = Math.max(0.0, 1.0 - (averageError / 100.0)); // Assuming 100px is max error
        return Math.min(1.0, accuracy);
    }
    
    /**
     * Store calibration data securely
     */
    private void storeCalibrationData(String sessionId, CalibrationResult result) {
        try {
            // Ensure state/visual-chess directory exists
            Path visualChessDir = Paths.get("state/visual-chess");
            if (!Files.exists(visualChessDir)) {
                Files.createDirectories(visualChessDir);
                logger.info("Created visual-chess directory: {}", visualChessDir.toAbsolutePath());
            }
            
            // Encrypt calibration data
            byte[] encryptedData = privacyService.encryptGazeData(
                serializeCalibrationData(result).getBytes()
            );
            
            // Store to file
            Path calibrationFile = Paths.get(CALIBRATION_DATA_FILE);
            Files.write(calibrationFile, encryptedData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            logger.info("Stored encrypted calibration data to {}", CALIBRATION_DATA_FILE);
            
        } catch (Exception e) {
            logger.error("Error storing calibration data", e);
        }
    }
    
    /**
     * Serialize calibration data to JSON
     */
    private String serializeCalibrationData(CalibrationResult result) {
        // Simple JSON serialization (in production, use proper JSON library)
        StringBuilder json = new StringBuilder();
        json.append("{\"sessionId\":\"global\",");
        json.append("\"accuracy\":").append(result.getAccuracy()).append(",");
        json.append("\"points\":").append(result.getPoints().size()).append(",");
        json.append("\"timestamp\":").append(System.currentTimeMillis()).append("}");
        return json.toString();
    }
    
    /**
     * Deserialize calibration model from JSON
     */
    private CalibrationModel deserializeCalibrationModel(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                logger.warn("Empty calibration JSON data");
                return null;
            }
            
            // Simple JSON parsing (in production, use proper JSON library)
            if (json.contains("\"accuracy\":")) {
                int accuracyStart = json.indexOf("\"accuracy\":") + 12;
                if (accuracyStart >= json.length()) {
                    logger.warn("Invalid JSON format: accuracy field incomplete");
                    return null;
                }
                
                String accuracyStr = json.substring(accuracyStart);
                int commaIndex = accuracyStr.indexOf(",");
                int braceIndex = accuracyStr.indexOf("}");
                
                int endIndex = -1;
                if (commaIndex != -1 && braceIndex != -1) {
                    endIndex = Math.min(commaIndex, braceIndex);
                } else if (commaIndex != -1) {
                    endIndex = commaIndex;
                } else if (braceIndex != -1) {
                    endIndex = braceIndex;
                }
                
                if (endIndex == -1) {
                    logger.warn("Invalid JSON format: cannot find accuracy value end");
                    return null;
                }
                
                accuracyStr = accuracyStr.substring(0, endIndex).trim();
                double accuracy = Double.parseDouble(accuracyStr);
                
                if (accuracy < 0.0 || accuracy > 1.0) {
                    logger.warn("Invalid accuracy value: {}, using default", accuracy);
                    accuracy = 0.5;
                }
                
                // If accuracy is 0, it means no valid calibration data
                if (accuracy == 0.0) {
                    logger.warn("Zero accuracy in saved calibration, treating as no calibration");
                    return null;
                }
                
                logger.debug("Parsed calibration accuracy: {}", accuracy);
                return new RestoredCalibrationModel(accuracy);
            } else {
                logger.warn("No accuracy field found in calibration JSON");
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse accuracy value: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to parse calibration JSON: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current calibration status
     */
    public CalibrationStatus getCalibrationStatus() {
        return new CalibrationStatus(
            isCalibrating,
            currentSessionId,
            calibrationModel != null,
            calibrationModel != null ? calibrationModel.getAccuracy() : 0.0,
            activeSessions.size()
        );
    }
    
    // Data classes
    public static class CalibrationSession {
        private final String sessionId;
        private final long startTime;
        private final List<CalibrationPoint> calibrationPoints;
        
        public CalibrationSession(String sessionId) {
            this.sessionId = sessionId;
            this.startTime = System.currentTimeMillis();
            this.calibrationPoints = new ArrayList<>();
        }
        
        public void addCalibrationPoint(CalibrationPoint point) {
            calibrationPoints.add(point);
        }
        
        public String getSessionId() { return sessionId; }
        public long getStartTime() { return startTime; }
        public List<CalibrationPoint> getCalibrationPoints() { return calibrationPoints; }
    }
    
    public static class CalibrationPoint {
        private final int index;
        private final Point2D targetPoint;
        private final Point2D averageGaze;
        private final List<Point2D> gazeSamples;
        private final long timestamp;
        
        public CalibrationPoint(int index, Point2D targetPoint, Point2D averageGaze,
                              List<Point2D> gazeSamples, long timestamp) {
            this.index = index;
            this.targetPoint = targetPoint;
            this.averageGaze = averageGaze;
            this.gazeSamples = new ArrayList<>(gazeSamples);
            this.timestamp = timestamp;
        }
        
        public int getIndex() { return index; }
        public Point2D getTargetPoint() { return targetPoint; }
        public Point2D getAverageGaze() { return averageGaze; }
        public List<Point2D> getGazeSamples() { return gazeSamples; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class CalibrationResult {
        private final String sessionId;
        private final double accuracy;
        private final CalibrationModel model;
        private final List<CalibrationPoint> points;
        
        public CalibrationResult(String sessionId, double accuracy, CalibrationModel model,
                               List<CalibrationPoint> points) {
            this.sessionId = sessionId;
            this.accuracy = accuracy;
            this.model = model;
            this.points = points;
        }
        
        public String getSessionId() { return sessionId; }
        public double getAccuracy() { return accuracy; }
        public CalibrationModel getModel() { return model; }
        public List<CalibrationPoint> getPoints() { return points; }
    }
    
    public static class CalibrationStatus {
        public final boolean isCalibrating;
        public final String currentSessionId;
        public final boolean hasCalibration;
        public final double accuracy;
        public final int activeSessions;
        
        public CalibrationStatus(boolean isCalibrating, String currentSessionId,
                               boolean hasCalibration, double accuracy, int activeSessions) {
            this.isCalibrating = isCalibrating;
            this.currentSessionId = currentSessionId;
            this.hasCalibration = hasCalibration;
            this.accuracy = accuracy;
            this.activeSessions = activeSessions;
        }
    }
    
    // Calibration model interface
    public interface CalibrationModel {
        Point2D correctGazePoint(Point2D rawGazePoint);
        double getAccuracy();
    }
    
    // Affine transformation calibration model
    public static class AffineCalibrationModel implements CalibrationModel {
        private final double[][] transformationMatrix;
        private final double accuracy;
        
        public AffineCalibrationModel(List<CalibrationPoint> points) {
            this.transformationMatrix = calculateAffineTransformation(points);
            this.accuracy = calculateModelAccuracy(points);
        }
        
        @Override
        public Point2D correctGazePoint(Point2D rawGazePoint) {
            double x = rawGazePoint.getX();
            double y = rawGazePoint.getY();
            
            // Apply affine transformation: [x', y'] = [x, y, 1] * T
            double correctedX = transformationMatrix[0][0] * x + transformationMatrix[0][1] * y + transformationMatrix[0][2];
            double correctedY = transformationMatrix[1][0] * x + transformationMatrix[1][1] * y + transformationMatrix[1][2];
            
            return new Point2D.Double(correctedX, correctedY);
        }
        
        @Override
        public double getAccuracy() {
            return accuracy;
        }
        
        private double[][] calculateAffineTransformation(List<CalibrationPoint> points) {
            // Simplified affine transformation calculation
            // In production, use proper least squares fitting
            return new double[][]{
                {1.0, 0.0, 0.0},  // Identity matrix as fallback
                {0.0, 1.0, 0.0}
            };
        }
        
        private double calculateModelAccuracy(List<CalibrationPoint> points) {
            if (points.isEmpty()) return 0.0;
            
            double totalError = 0.0;
            int validPoints = 0;
            
            for (CalibrationPoint point : points) {
                if (!point.getGazeSamples().isEmpty()) {
                    double error = point.getTargetPoint().distance(point.getAverageGaze());
                    totalError += error;
                    validPoints++;
                }
            }
            
            if (validPoints == 0) return 0.0;
            
            double averageError = totalError / validPoints;
            // Convert error to accuracy (assume 100px is max acceptable error)
            double accuracy = Math.max(0.0, 1.0 - (averageError / 100.0));
            return Math.min(1.0, accuracy);
        }
    }
    
    // Restored calibration model from saved data
    public static class RestoredCalibrationModel implements CalibrationModel {
        private final double accuracy;
        
        public RestoredCalibrationModel(double accuracy) {
            this.accuracy = accuracy;
        }
        
        @Override
        public Point2D correctGazePoint(Point2D rawGazePoint) {
            // Simple correction - in production, restore full transformation matrix
            return rawGazePoint;
        }
        
        @Override
        public double getAccuracy() {
            return accuracy;
        }
    }
}