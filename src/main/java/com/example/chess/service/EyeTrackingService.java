package com.example.chess.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.awt.geom.Point2D;

/**
 * Eye tracking service with webcam integration and privacy compliance
 * Implements real-time gaze tracking with consent validation
 */
@Service
public class EyeTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(EyeTrackingService.class);
    
    // OpenCV Dependencies
    private VideoCapture camera;
    private CascadeClassifier faceDetector;
    private Mat frame = new Mat();
    
    // MediaPipe Integration (placeholder for future implementation)
    // private FaceMeshDetector faceMesh;
    // private EyeLandmarkDetector eyeDetector;
    
    private volatile boolean webcamEnabled = false;
    private volatile boolean userGameActive = false;
    
    @Autowired
    private ConsentManager consentManager;
    
    @Autowired
    private PrivacyService privacyService;
    
    @Autowired
    private ChessBoardMapper chessBoardMapper;
    
    @Value("${chess.eyetracking.camera.device:0}")
    private int cameraDevice;
    
    @Value("${chess.eyetracking.fps:30}")
    private int targetFPS;
    
    @PostConstruct
    public void initialize() {
        try {
            // Load OpenCV native library
            // nu.pattern.OpenCV.loadShared();
            // System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            
            // Initialize camera
            // camera = new VideoCapture(cameraDevice);
            if (!camera.isOpened()) {
                logger.warn("Could not open camera device: {}", cameraDevice);
                return;
            }
            
            // Initialize face detector
            faceDetector = new CascadeClassifier();
            // Load Haar cascade for face detection
            // In a real implementation, you would load the actual cascade file
            // faceDetector.load("haarcascade_frontalface_alt.xml");
            
            logger.info("EyeTrackingService initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize EyeTrackingService", e);
        }
    }
    
    // Real-time processing - ONLY during user games with webcam ON
    @Scheduled(fixedRate = 33) // 30 FPS
    public void captureAndAnalyze() {
        // KEY REQUIREMENT: Only capture during user games with webcam enabled
        if (!webcamEnabled || !userGameActive || isAITraining()) {
            return; // Skip capture during AI training or when webcam disabled
        }
        
        try {
            camera.read(frame);
            if (!frame.empty()) {
                processFrame(frame);
            }
        } catch (Exception e) {
            logger.warn("Error during frame capture", e);
        }
    }
    
    public void enableWebcam(String sessionId, boolean userConsent) {
        if (!userConsent) {
            throw new SecurityException("User consent required for webcam access");
        }
        
        // Verify consent is still valid
        if (!consentManager.hasValidConsent(sessionId, ConsentManager.ConsentType.GAZE_DATA_COLLECTION)) {
            throw new SecurityException("Valid consent required for webcam access");
        }
        
        webcamEnabled = true;
        privacyService.logAccess(sessionId, "WEBCAM_ENABLED");
        logger.info("Webcam enabled for eye-tracking with user consent");
    }
    
    public void disableWebcam() {
        webcamEnabled = false;
        privacyService.logAccess("system", "WEBCAM_DISABLED");
        logger.info("Webcam disabled");
    }
    
    public void setUserGameActive(boolean active) {
        userGameActive = active;
        if (!active) {
            disableWebcam(); // Auto-disable when not in user game
        }
    }
    
    public void onGameReset() {
        // EXPLICIT REQUIREMENT: Webcam OFF on New Game/Reset Game
        disableWebcam();
        logger.info("Game reset - webcam automatically disabled");
    }
    
    public boolean isWebcamEnabled() {
        return webcamEnabled;
    }
    
    private boolean isAITraining() {
        // Check if any AI system is currently training
        // This would be implemented based on your AI training manager
        return false; // Placeholder
    }
    
    private void processFrame(Mat frame) {
        try {
            // 1. Detect face using Haar cascades
            MatOfRect faces = new MatOfRect();
            if (faceDetector.empty()) {
                // Fallback: use a simple face detection approach
                detectFaceSimple(frame, faces);
            } else {
                faceDetector.detectMultiScale(frame, faces);
            }
            
            // 2. Extract eye regions
            Rect[] faceArray = faces.toArray();
            if (faceArray.length > 0) {
                Rect eyeRegion = extractEyeRegion(faceArray[0]);
                
                // 3. Calculate gaze point
                Point2D gazePoint = calculateGazePoint(eyeRegion);
                
                if (gazePoint != null) {
                    // 4. Map to chess coordinates
                    String chessSquare = chessBoardMapper.mapToChessSquare(gazePoint);
                    
                    // 5. Update gaze history and persist training data
                    updateGazeHistory(gazePoint, chessSquare);
                }
            }
        } catch (Exception e) {
            logger.warn("Error processing frame", e);
        }
    }
    
    private void detectFaceSimple(Mat frame, MatOfRect faces) {
        // Simple face detection fallback
        // In a real implementation, this would use proper face detection
        // For now, we'll create a dummy face region
        Rect dummyFace = new Rect(100, 100, 200, 200);
        faces.fromArray(dummyFace);
    }
    
    private Rect extractEyeRegion(Rect face) {
        // Extract eye region from face rectangle
        int eyeWidth = face.width / 3;
        int eyeHeight = face.height / 4;
        int eyeY = face.y + face.height / 3;
        
        return new Rect(face.x + face.width / 3, eyeY, eyeWidth, eyeHeight);
    }
    
    private Point2D calculateGazePoint(Rect eyeRegion) {
        try {
            // Pupil detection using HoughCircles
            Mat eyeMat = new Mat(frame, eyeRegion);
            Mat gray = new Mat();
            Imgproc.cvtColor(eyeMat, gray, Imgproc.COLOR_BGR2GRAY);
            
            Mat circles = new Mat();
            Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1, 20, 50, 30, 5, 50);
            
            // Return pupil center as gaze point
            if (circles.cols() > 0) {
                double[] circle = circles.get(0, 0);
                return new Point2D.Double(
                    eyeRegion.x + circle[0], 
                    eyeRegion.y + circle[1]
                );
            }
        } catch (Exception e) {
            logger.warn("Error calculating gaze point", e);
        }
        
        // Fallback: return center of eye region
        return new Point2D.Double(
            eyeRegion.x + eyeRegion.width / 2.0,
            eyeRegion.y + eyeRegion.height / 2.0
        );
    }
    
    private void updateGazeHistory(Point2D gazePoint, String chessSquare) {
        // Store gaze data with privacy compliance
        try {
            RawGazeData rawData = new RawGazeData(
                gazePoint.getX(),
                gazePoint.getY(),
                chessSquare,
                System.currentTimeMillis(),
                "gaze_tracking"
            );
            
            // This would trigger the privacy-compliant data collection
            // privacyCompliantDataCollectionService.collectGazeData(sessionId, rawData);
            
            logger.debug("Gaze point: ({}, {}) -> {}", gazePoint.getX(), gazePoint.getY(), chessSquare);
        } catch (Exception e) {
            logger.warn("Error updating gaze history", e);
        }
    }
    
    public GazePattern getCurrentGazePattern() {
        // Return current gaze pattern for prediction
        // This would be implemented based on recent gaze history
        return new GazePattern();
    }
    
    /**
     * Extract eye region from face rectangle
     */
    private Rect extractEyeRegion(Rect face) {
        // Calculate eye region based on face geometry
        int eyeWidth = face.width / 4;
        int eyeHeight = face.height / 6;
        int eyeY = face.y + face.height / 3; // Eyes are in upper third of face
        
        // Left eye
        Rect leftEye = new Rect(face.x + face.width / 8, eyeY, eyeWidth, eyeHeight);
        
        // Right eye  
        Rect rightEye = new Rect(face.x + face.width * 5 / 8, eyeY, eyeWidth, eyeHeight);
        
        // Return the larger eye region (or combine them)
        return leftEye.width > rightEye.width ? leftEye : rightEye;
    }
    
    /**
     * Detect faces using Haar cascades
     */
    private MatOfRect detectFaces(Mat frame) {
        MatOfRect faces = new MatOfRect();
        
        try {
            // Load face cascade classifier
            if (faceDetector == null) {
                String cascadePath = getClass().getResource("/haarcascade_frontalface_alt.xml").getPath();
                faceDetector = new CascadeClassifier(cascadePath);
            }
            
            // Convert to grayscale for face detection
            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            
            // Detect faces
            faceDetector.detectMultiScale(gray, faces, 1.1, 3, 0, 
                new Size(30, 30), new Size(frame.width(), frame.height()));
                
        } catch (Exception e) {
            logger.warn("Error detecting faces", e);
        }
        
        return faces;
    }
    
    /**
     * Enhanced face detection with multiple strategies
     */
    private Rect detectFaceWithMultipleStrategies(Mat frame) {
        // Strategy 1: Haar cascades
        MatOfRect faces = detectFaces(frame);
        Rect[] faceArray = faces.toArray();
        
        if (faceArray.length > 0) {
            // Return the largest face
            Rect largestFace = faceArray[0];
            for (Rect face : faceArray) {
                if (face.area() > largestFace.area()) {
                    largestFace = face;
                }
            }
            return largestFace;
        }
        
        // Strategy 2: Template matching (fallback)
        return detectFaceByTemplateMatching(frame);
    }
    
    /**
     * Template matching face detection (fallback)
     */
    private Rect detectFaceByTemplateMatching(Mat frame) {
        // Simplified template matching - in production, use proper templates
        int width = frame.width();
        int height = frame.height();
        
        // Assume face is in center 60% of frame
        int faceWidth = (int)(width * 0.6);
        int faceHeight = (int)(height * 0.6);
        int faceX = (width - faceWidth) / 2;
        int faceY = (height - faceHeight) / 2;
        
        return new Rect(faceX, faceY, faceWidth, faceHeight);
    }
    
    /**
     * Enhanced gaze point calculation with noise reduction
     */
    private Point2D calculateGazePointEnhanced(Rect eyeRegion) {
        try {
            // Extract eye region from frame
            Mat eyeMat = new Mat(frame, eyeRegion);
            Mat gray = new Mat();
            Imgproc.cvtColor(eyeMat, gray, Imgproc.COLOR_BGR2GRAY);
            
            // Apply Gaussian blur to reduce noise
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
            
            // Apply adaptive threshold for better edge detection
            Mat thresh = new Mat();
            Imgproc.adaptiveThreshold(blurred, thresh, 255, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2);
            
            // Detect pupil using HoughCircles
            Mat circles = new Mat();
            Imgproc.HoughCircles(thresh, circles, Imgproc.HOUGH_GRADIENT, 
                1, 20, 50, 30, 5, 50);
            
            if (circles.cols() > 0) {
                // Find the best circle (closest to center of eye region)
                Point2D eyeCenter = new Point2D.Double(eyeRegion.width / 2.0, eyeRegion.height / 2.0);
                double bestDistance = Double.MAX_VALUE;
                Point2D bestPupil = null;
                
                for (int i = 0; i < circles.cols(); i++) {
                    double[] circle = circles.get(0, i);
                    Point2D pupil = new Point2D.Double(circle[0], circle[1]);
                    double distance = eyeCenter.distance(pupil);
                    
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPupil = pupil;
                    }
                }
                
                if (bestPupil != null) {
                    // Convert to global coordinates
                    double globalX = eyeRegion.x + bestPupil.getX();
                    double globalY = eyeRegion.y + bestPupil.getY();
                    return new Point2D.Double(globalX, globalY);
                }
            }
            
            // Fallback: return center of eye region if no pupil detected
            return new Point2D.Double(eyeRegion.x + eyeRegion.width / 2.0, 
                                    eyeRegion.y + eyeRegion.height / 2.0);
            
        } catch (Exception e) {
            logger.warn("Error calculating enhanced gaze point", e);
            return null;
        }
    }
    
    // Data classes
    public static class RawGazeData {
        public final double gazeX;
        public final double gazeY;
        public final String chessSquare;
        public final long timestamp;
        public final String userAction;
        
        public RawGazeData(double gazeX, double gazeY, String chessSquare, long timestamp, String userAction) {
            this.gazeX = gazeX;
            this.gazeY = gazeY;
            this.chessSquare = chessSquare;
            this.timestamp = timestamp;
            this.userAction = userAction;
        }
    }
    
    public static class GazePattern {
        // Placeholder for gaze pattern data
        // This would contain the actual gaze pattern information
    }
}
