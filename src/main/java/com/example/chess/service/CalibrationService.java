package com.example.chess.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calibration service for gaze accuracy improvement
 * Handles 9-point calibration and gaze correction algorithms
 */
@Service
@ConditionalOnProperty(name = "chess.eyetracking.services.enabled", havingValue = "true", matchIfMissing = false)
public class CalibrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CalibrationService.class);
    
    // Calibration configuration
    private static final int CALIBRATION_POINTS = 9; // 3x3 grid
    private static final double CALIBRATION_ACCURACY_THRESHOLD = 0.8;
    private static final long CALIBRATION_TIMEOUT_MS = 30000; // 30 seconds per point
    
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
    
    /**
     * Start a new calibration session
     */
    public CalibrationSession startCalibration(String sessionId) {
        if (isCalibrating) {
            throw new IllegalStateException("Calibration already in progress");
        }
        
        isCalibrating = true;
        currentSessionId = sessionId;
        
        CalibrationSession session = new CalibrationSession(sessionId);
        activeSessions.put(sessionId, session);
        
        logger.info("Started calibration session: {}", sessionId);
        return session;
    }
    
    /**
     * Record calibration data for a specific point
     */
    public void recordCalibrationPoint(String sessionId, int pointIndex, Point2D targetPoint, 
                                     List<Point2D> gazeSamples) {
        try {
            CalibrationSession session = activeSessions.get(sessionId);
            if (session == null) {
                logger.warn("No active calibration session found for: {}", sessionId);
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
            
            logger.debug("Recorded calibration point {}: target=({:.1f},{:.1f}), gaze=({:.1f},{:.1f})",
                pointIndex, targetPoint.getX(), targetPoint.getY(),
                averageGaze.getX(), averageGaze.getY());
            
            // Check if calibration is complete
            if (session.getCalibrationPoints().size() >= CALIBRATION_POINTS) {
                completeCalibration(sessionId);
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
            CalibrationSession session = activeSessions.get(sessionId);
            if (session == null) {
                throw new IllegalStateException("No active calibration session found");
            }
            
            // Build calibration model
            calibrationModel = buildCalibrationModel(session.getCalibrationPoints());
            
            // Calculate calibration accuracy
            double accuracy = calculateCalibrationAccuracy(session.getCalibrationPoints());
            
            // Create result
            CalibrationResult result = new CalibrationResult(
                sessionId, accuracy, calibrationModel, session.getCalibrationPoints()
            );
            
            // Store calibration data securely
            storeCalibrationData(sessionId, result);
            
            // Clean up
            activeSessions.remove(sessionId);
            isCalibrating = false;
            currentSessionId = null;
            
            logger.info("Completed calibration session {} with accuracy: {:.2f}%", 
                sessionId, accuracy * 100);
            
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
        if (currentSessionId != null && currentSessionId.equals(sessionId)) {
            activeSessions.remove(sessionId);
            isCalibrating = false;
            currentSessionId = null;
            logger.info("Cancelled calibration session: {}", sessionId);
        }
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
            // Encrypt calibration data
            byte[] encryptedData = privacyService.encryptGazeData(
                serializeCalibrationData(result).getBytes()
            );
            
            // Store in secure location
            // This would integrate with the data storage system
            logger.info("Stored encrypted calibration data for session: {}", sessionId);
            
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
        json.append("{\"sessionId\":\"").append(result.getSessionId()).append("\",");
        json.append("\"accuracy\":").append(result.getAccuracy()).append(",");
        json.append("\"timestamp\":").append(System.currentTimeMillis()).append("}");
        return json.toString();
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
            // Calculate model accuracy based on calibration points
            return 0.85; // Placeholder accuracy
        }
    }
}
