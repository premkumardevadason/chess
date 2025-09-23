package com.example.chess.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.awt.geom.Point2D;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service
public class BasicEyeTrackingService {
    
    private static final Logger logger = LogManager.getLogger(BasicEyeTrackingService.class);
    
    private CascadeClassifier faceDetector;
    private Map<String, Point2D> gazeHistory = new ConcurrentHashMap<>();
    private Map<String, java.util.List<Point2D>> gazeSequences = new ConcurrentHashMap<>();
    private static final int MAX_SEQUENCE_LENGTH = 20;
    private Point2D lastStableGaze = null;
    private long lastGazeTime = 0;
    private static final long GAZE_STABILITY_THRESHOLD = 500; // 500ms
    private static final double GAZE_MOVEMENT_THRESHOLD = 50.0; // 50 pixels
    
    @Autowired
    private com.example.chess.service.ChessBoardMapper chessBoardMapper;
    
    @Autowired
    private com.example.chess.service.EyeMovementLearningService eyeMovementLearningService;
    
    @PostConstruct
    public void initializeOpenCV() {
        try {
            nu.pattern.OpenCV.loadLocally();
            faceDetector = new CascadeClassifier();
            // Try to load the downloaded cascade files from OpenCV directory
            if (faceDetector.load("OpenCV/haarcascade_frontalface_alt.xml")) {
                logger.info("OpenCV face detection initialized with haarcascade_frontalface_alt.xml");
            } else if (faceDetector.load("OpenCV/haarcascade_frontalface_default.xml")) {
                logger.info("OpenCV face detection initialized with haarcascade_frontalface_default.xml");
            } else {
                logger.error("Could not load any cascade file from OpenCV directory");
                faceDetector = null;
            }
        } catch (Exception e) {
            logger.error("OpenCV initialization failed: {}", e.getMessage());
            faceDetector = null;
        }
    }
    
    public Point2D processVideoFrame(byte[] imageData, int width, int height) {
        try {
            logger.debug("Processing frame: width={}, height={}, faceDetector={}", width, height, (faceDetector != null ? "loaded" : "null"));
            
            // Validate input parameters
            if (imageData == null || width <= 0 || height <= 0) {
                System.err.println("Invalid video frame data: width=" + width + ", height=" + height + ", dataSize=" + (imageData != null ? imageData.length : "null"));
                return null;
            }
            
            // Validate expected data size
            int expectedSize = width * height * 4; // RGBA = 4 bytes per pixel
            if (imageData.length != expectedSize) {
                System.err.println("Image data size mismatch: expected=" + expectedSize + ", actual=" + imageData.length);
                return null;
            }
            
            // Convert byte array to OpenCV Mat
            Mat frame = new Mat(height, width, CvType.CV_8UC4);
            frame.put(0, 0, imageData);
            
            // Convert to grayscale for face detection
            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY);
            
            logger.debug("Frame converted to grayscale: {}x{}", gray.rows(), gray.cols());
            
            if (faceDetector != null) {
                // Use OpenCV face detection with 640x480 optimized parameters
                MatOfRect faces = new MatOfRect();
                // Larger minimum face size for higher resolution
                faceDetector.detectMultiScale(gray, faces, 1.1, 3, 0, new Size(80, 80), new Size());
                
                Rect[] faceArray = faces.toArray();
                logger.debug("Detected {} faces with 640x480 parameters", faceArray.length);
                
                if (faceArray.length == 0) {
                    logger.debug("Trying medium face size...");
                    faceDetector.detectMultiScale(gray, faces, 1.05, 2, 0, new Size(60, 60), new Size());
                    faceArray = faces.toArray();
                    logger.debug("Medium face detection found {} faces", faceArray.length);
                    
                    if (faceArray.length == 0) {
                        logger.debug("Trying small face size...");
                        faceDetector.detectMultiScale(gray, faces, 1.02, 1, 0, new Size(40, 40), new Size());
                        faceArray = faces.toArray();
                        logger.debug("Small face detection found {} faces", faceArray.length);
                    }
                }
                
                if (faceArray.length > 0) {
                    // Use first detected face
                    Rect face = faceArray[0];
                    logger.debug("*** FACE DETECTED: x={}, y={}, width={}, height={} ***", face.x, face.y, face.width, face.height);
                    
                    // Extract eye regions (left and right)
                    logger.debug("Detecting pupils in face region...");
                    Point2D leftEye = detectPupil(gray, face, true);
                    Point2D rightEye = detectPupil(gray, face, false);
                    
                    if (leftEye != null) logger.debug("*** LEFT EYE DETECTED: ({}, {}) ***", leftEye.getX(), leftEye.getY());
                    if (rightEye != null) logger.debug("*** RIGHT EYE DETECTED: ({}, {}) ***", rightEye.getX(), rightEye.getY());
                    
                    // Calculate average gaze point from both eyes
                    Point2D rawGaze = null;
                    if (leftEye != null && rightEye != null) {
                        rawGaze = new Point2D.Double(
                            (leftEye.getX() + rightEye.getX()) / 2.0,
                            (leftEye.getY() + rightEye.getY()) / 2.0
                        );
                    } else if (leftEye != null) {
                        rawGaze = leftEye;
                    } else if (rightEye != null) {
                        rawGaze = rightEye;
                    }
                    
                    // Apply gaze stabilization to reduce jumping
                    Point2D stabilizedGaze = stabilizeGaze(rawGaze);
                    if (stabilizedGaze != null) {
                        logger.debug("*** RETURNING STABILIZED GAZE: ({}, {}) ***", stabilizedGaze.getX(), stabilizedGaze.getY());
                    } else {
                        logger.warn("*** STABILIZED GAZE IS NULL - no stable gaze detected ***");
                    }
                    return stabilizedGaze;
                } else {
                    logger.warn("*** NO FACES DETECTED in {}x{} frame ***", width, height);
                }
            } else {
                logger.warn("*** FACE DETECTOR IS NULL - using center fallback ***");
                Point2D centerPoint = new Point2D.Double(width / 2.0, height / 2.0);
                logger.info("Using center-point fallback: ({}, {})", centerPoint.getX(), centerPoint.getY());
                return centerPoint;
            }
            
        } catch (Exception e) {
            logger.error("Error processing video frame: {}", e.getMessage());
        }
        
        return null;
    }
    
    private Point2D detectPupil(Mat gray, Rect face, boolean isLeftEye) {
        try {
            // Define eye region within face
            int eyeWidth = face.width / 3;
            int eyeHeight = face.height / 4;
            int eyeY = face.y + face.height / 4;
            int eyeX = isLeftEye ? face.x + face.width / 6 : face.x + face.width * 2 / 3;
            
            logger.debug("{} eye region: x={}, y={}, width={}, height={}", (isLeftEye ? "Left" : "Right"), eyeX, eyeY, eyeWidth, eyeHeight);
            
            // Validate eye region bounds
            if (eyeX < 0 || eyeY < 0 || eyeX + eyeWidth > gray.cols() || eyeY + eyeHeight > gray.rows()) {
                logger.warn("Eye region out of bounds - skipping");
                return null;
            }
            
            Rect eyeRegion = new Rect(eyeX, eyeY, eyeWidth, eyeHeight);
            Mat eyeMat = new Mat(gray, eyeRegion);
            
            logger.debug("Eye mat size: {}x{}", eyeMat.rows(), eyeMat.cols());
            
            // Apply Gaussian blur to reduce noise
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(eyeMat, blurred, new Size(5, 5), 0);
            
            // Find darkest point (pupil) using minMaxLoc
            Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(blurred);
            Point pupilCenter = minMaxLoc.minLoc;
            
            logger.debug("Pupil center in eye region: ({}, {}), minVal={}", pupilCenter.x, pupilCenter.y, minMaxLoc.minVal);
            
            // Convert back to full image coordinates
            Point2D result = new Point2D.Double(
                eyeRegion.x + pupilCenter.x,
                eyeRegion.y + pupilCenter.y
            );
            
            logger.debug("Final pupil coordinates: ({}, {})", result.getX(), result.getY());
            return result;
            
        } catch (Exception e) {
            logger.error("Error detecting pupil: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public String mapGazeToChessSquare(Point2D gazePoint) {
        if (gazePoint == null || chessBoardMapper == null) {
            return null;
        }
        
        String square = chessBoardMapper.mapToChessSquare(gazePoint);
        if (square != null) {
            logger.debug("*** GAZE MAPPED TO CHESS SQUARE: {} at ({}, {}) ***", square, gazePoint.getX(), gazePoint.getY());
        }
        return square;
    }
    
    public void recordGazePoint(String sessionId, Point2D gazePoint) {
        if (gazePoint != null) {
            gazeHistory.put(sessionId, gazePoint);
            
            // Add to sequence history
            gazeSequences.computeIfAbsent(sessionId, k -> new java.util.ArrayList<>()).add(gazePoint);
            
            // Keep only last 30 points
            java.util.List<Point2D> sequence = gazeSequences.get(sessionId);
            if (sequence.size() > MAX_SEQUENCE_LENGTH) {
                sequence.remove(0);
            }
        }
    }
    
    public Point2D getLastGazePoint(String sessionId) {
        return gazeHistory.get(sessionId);
    }
    
    public java.util.List<Point2D> getGazeSequence(String sessionId) {
        return gazeSequences.getOrDefault(sessionId, new java.util.ArrayList<>());
    }
    
    public void recordChessMove(String sessionId, Point2D gazePoint, String chessSquare) {
        if (eyeMovementLearningService != null) {
            eyeMovementLearningService.recordChessMove(sessionId, gazePoint, chessSquare);
        }
    }
    
    private Point2D stabilizeGaze(Point2D currentGaze) {
        if (currentGaze == null) {
            return null;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // If no previous gaze, use current
        if (lastStableGaze == null) {
            lastStableGaze = currentGaze;
            lastGazeTime = currentTime;
            return currentGaze;
        }
        
        // Calculate distance from last stable gaze
        double distance = Math.sqrt(
            Math.pow(currentGaze.getX() - lastStableGaze.getX(), 2) +
            Math.pow(currentGaze.getY() - lastStableGaze.getY(), 2)
        );
        
        // If movement is small, keep using stable gaze
        if (distance < GAZE_MOVEMENT_THRESHOLD) {
            return lastStableGaze;
        }
        
        // If movement is large, check if it's been stable for threshold time
        if (currentTime - lastGazeTime > GAZE_STABILITY_THRESHOLD) {
            lastStableGaze = currentGaze;
            lastGazeTime = currentTime;
            return currentGaze;
        }
        
        // Movement detected but not stable enough yet
        return lastStableGaze;
    }
}