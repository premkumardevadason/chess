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

@Service
public class BasicEyeTrackingService {
    
    private CascadeClassifier faceDetector;
    private Map<String, Point2D> gazeHistory = new ConcurrentHashMap<>();
    
    @Autowired
    private com.example.chess.service.ChessBoardMapper chessBoardMapper;
    
    @PostConstruct
    public void initializeOpenCV() {
        try {
            nu.pattern.OpenCV.loadLocally();
            faceDetector = new CascadeClassifier();
            // Try to load the downloaded cascade files from OpenCV directory
            if (faceDetector.load("OpenCV/haarcascade_frontalface_alt.xml")) {
                System.out.println("[EYE-TRACKING] OpenCV face detection initialized with haarcascade_frontalface_alt.xml");
            } else if (faceDetector.load("OpenCV/haarcascade_frontalface_default.xml")) {
                System.out.println("[EYE-TRACKING] OpenCV face detection initialized with haarcascade_frontalface_default.xml");
            } else {
                System.err.println("[EYE-TRACKING] Could not load any cascade file from OpenCV directory");
                faceDetector = null;
            }
        } catch (Exception e) {
            System.err.println("[EYE-TRACKING] OpenCV initialization failed: " + e.getMessage());
            faceDetector = null;
        }
    }
    
    public Point2D processVideoFrame(byte[] imageData, int width, int height) {
        try {
            System.out.println("[EYE-TRACKING] Processing frame: width=" + width + ", height=" + height + ", faceDetector=" + (faceDetector != null ? "loaded" : "null"));
            
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
            
            System.out.println("[FACE-DETECTION] Frame converted to grayscale: " + gray.rows() + "x" + gray.cols());
            
            if (faceDetector != null) {
                // Use OpenCV face detection with detailed logging
                MatOfRect faces = new MatOfRect();
                faceDetector.detectMultiScale(gray, faces, 1.1, 3, 0, new Size(30, 30), new Size());
                
                Rect[] faceArray = faces.toArray();
                System.out.println("[FACE-DETECTION] Detected " + faceArray.length + " faces with standard parameters");
                
                if (faceArray.length == 0) {
                    // Try with more relaxed parameters
                    System.out.println("[FACE-DETECTION] Trying relaxed parameters...");
                    faceDetector.detectMultiScale(gray, faces, 1.05, 2, 0, new Size(20, 20), new Size());
                    faceArray = faces.toArray();
                    System.out.println("[FACE-DETECTION] Relaxed detection found " + faceArray.length + " faces");
                }
                
                if (faceArray.length > 0) {
                    // Use first detected face
                    Rect face = faceArray[0];
                    
                    // Extract eye regions (left and right)
                    Point2D leftEye = detectPupil(gray, face, true);
                    Point2D rightEye = detectPupil(gray, face, false);
                    
                    // Calculate average gaze point from both eyes
                    if (leftEye != null && rightEye != null) {
                        return new Point2D.Double(
                            (leftEye.getX() + rightEye.getX()) / 2.0,
                            (leftEye.getY() + rightEye.getY()) / 2.0
                        );
                    } else if (leftEye != null) {
                        return leftEye;
                    } else if (rightEye != null) {
                        return rightEye;
                    }
                }
            } else {
                System.out.println("[FACE-DETECTION] Face detector is null - using center fallback");
                // Fallback: use center of frame as gaze point for testing
                Point2D centerPoint = new Point2D.Double(width / 2.0, height / 2.0);
                System.out.println("[EYE-TRACKING] Using center-point fallback: (" + centerPoint.getX() + ", " + centerPoint.getY() + ")");
                return centerPoint;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing video frame: " + e.getMessage());
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
            
            Rect eyeRegion = new Rect(eyeX, eyeY, eyeWidth, eyeHeight);
            Mat eyeMat = new Mat(gray, eyeRegion);
            
            // Apply Gaussian blur to reduce noise
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(eyeMat, blurred, new Size(5, 5), 0);
            
            // Find darkest point (pupil) using minMaxLoc
            Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(blurred);
            Point pupilCenter = minMaxLoc.minLoc;
            
            // Convert back to full image coordinates
            return new Point2D.Double(
                eyeRegion.x + pupilCenter.x,
                eyeRegion.y + pupilCenter.y
            );
            
        } catch (Exception e) {
            System.err.println("Error detecting pupil: " + e.getMessage());
            return null;
        }
    }
    
    public String mapGazeToChessSquare(Point2D gazePoint) {
        if (gazePoint == null || chessBoardMapper == null) {
            return null;
        }
        
        return chessBoardMapper.mapToChessSquare(gazePoint);
    }
    
    public void recordGazePoint(String sessionId, Point2D gazePoint) {
        if (gazePoint != null) {
            gazeHistory.put(sessionId, gazePoint);
        }
    }
    
    public Point2D getLastGazePoint(String sessionId) {
        return gazeHistory.get(sessionId);
    }
}