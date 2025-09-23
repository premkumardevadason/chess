# Eye-Tracking Predictive AI Architecture for Chess

## Executive Summary

This document outlines the integration of eye-tracking technology with the Chess application's 12 AI systems to create a **Predictive Multi-Agent Chess Engine**. By analyzing user eye movements and gaze patterns, the system will anticipate user moves 2-5 seconds in advance, enabling all AI agents to pre-compute optimal responses.

### **Target Interface: Original Thymeleaf Chess Board**

**IMPORTANT**: Eye-tracking is designed for the **original Thymeleaf-based chess interface** (http://localhost:8081), NOT the React frontend. The system analyzes gaze patterns on the existing HTML/CSS chess board that users interact with via mouse clicks.

## Core Benefits of Move Anticipation

### **IMPORTANT: Eye-Tracking is Prediction Only**

**Eye-tracking does NOT replace mouse interaction**. The system works as follows:

1. **Eye Movement**: User looks at chess pieces (prediction input)
2. **AI Precomputation**: System predicts likely moves and pre-calculates AI responses
3. **Mouse Click**: User still makes the actual move using mouse (unchanged)
4. **Instant Response**: AI responds immediately using precomputed move

```
Complete Flow:
User Gaze → Predict Move → Precompute AI Responses → User Mouse Click → Instant AI Response
     ↑                                                        ↑
  (Prediction)                                        (Actual Move)
```

### 1. **Instant AI Response System**
```
Traditional Flow:
User Mouse Click → AI Thinks (2-5s) → AI Response

Predictive Flow:
User Gaze → Predict Move (70% confidence) → All 12 AIs Pre-compute → User Mouse Click → Instant Response (<100ms)
```

### 2. **Multi-Agent Parallel Processing**
When eye-tracking predicts a move with 70%+ confidence:
- **AlphaZero**: Calculates strategic response (0.2s)
- **Leela Chess Zero**: Computes human-like response (0.3s)
- **AlphaFold3**: Analyzes tactical patterns (0.4s)
- **All 12 AIs**: Simultaneously prepare responses
- **Result**: 95% reduction in response time

## Development Requirements

### **REQUIREMENT 5: Git Branch Management**
- **Branch Name**: `VISUAL-CHESS`
- **Purpose**: All eye-tracking implementation changes isolated from main branch
- **Commands**:
```bash
git checkout -b VISUAL-CHESS
git push -u origin VISUAL-CHESS
```

### **REQUIREMENT 6: Raw Gaze Data Collection & Backend Training**
- **UI Sends**: Raw gaze coordinates, timestamps, user actions via WebSocket
- **Backend Receives**: Raw data and converts to training features
- **Training Location**: 100% server-side processing and ML training
- **Storage Location**: `state/visual-training/` directory
- **Data Format**: Binary format (.dat) for performance with large datasets
- **File Structure**:
```
state/
├── visual-training/
│   ├── gaze-patterns.dat
│   ├── move-predictions.dat
│   └── user-sessions/
│       ├── session-{uuid}.dat
│       └── ...
```

## Technical Implementation Specifications

### 1. **EyeTrackingService - Webcam Integration**

```java
@Service
public class EyeTrackingService {
    
    // OpenCV Dependencies
    private VideoCapture camera;
    private CascadeClassifier faceDetector;
    private Mat frame = new Mat();
    
    // MediaPipe Integration
    private FaceMeshDetector faceMesh;
    private EyeLandmarkDetector eyeDetector;
    
    // Real-time processing
    @Scheduled(fixedRate = 33) // 30 FPS
    public void captureAndAnalyze() {
        camera.read(frame);
        if (!frame.empty()) {
            processFrame(frame);
        }
    }
    
    private void processFrame(Mat frame) {
        // 1. Detect face using Haar cascades
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(frame, faces);
        
        // 2. Extract eye regions
        Rect[] faceArray = faces.toArray();
        if (faceArray.length > 0) {
            Rect eyeRegion = extractEyeRegion(faceArray[0]);
            
            // 3. Calculate gaze point
            Point2D gazePoint = calculateGazePoint(eyeRegion);
            
            // 4. Map to chess coordinates
            String chessSquare = mapToChessSquare(gazePoint);
            
            // 5. Update gaze history and persist training data
            updateGazeHistory(gazePoint, chessSquare);
        }
    }
    
    private Point2D calculateGazePoint(Rect eyeRegion) {
        // Pupil detection using HoughCircles
        Mat eyeMat = new Mat(frame, eyeRegion);
        Mat gray = new Mat();
        Imgproc.cvtColor(eyeMat, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1, 20, 50, 30, 5, 50);
        
        // Return pupil center as gaze point
        if (circles.cols() > 0) {
            float[] circle = circles.get(0, 0);
            return new Point2D.Double(circle[0], circle[1]);
        }
        return null;
    }
}
```

### 2. **ChessBoardMapper - Dynamic Screen Detection & Square Highlighting**

```java
@Component
public class ChessBoardMapper {
    
    private Rectangle chessBoardBounds;
    private Rectangle[][] squareBounds = new Rectangle[8][8];
    private String currentHighlightedSquare = null;
    private long highlightStartTime = 0;
    private static final long HIGHLIGHT_DURATION = 3000; // 3 seconds
    
    @Autowired
    private WebSocketController webSocketController;
    
    @PostConstruct
    public void initializeMapping() {
        // Continuously detect chess board position (handles browser movement)
        startDynamicDetection();
    }
    
    private void startDynamicDetection() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            detectChessBoard();
            calculateSquareBounds();
        }, 0, 500, TimeUnit.MILLISECONDS); // Update every 500ms
    }
    
    private void detectChessBoard() {
        // REQUIREMENT 1: Precisely locate chess board anywhere on screen
        Mat template = Imgcodecs.imread("thymeleaf_chess_board_template.png");
        Mat screen = captureScreen();
        
        Mat result = new Mat();
        Imgproc.matchTemplate(screen, template, result, Imgproc.TM_CCOEFF_NORMED);
        
        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
        Point topLeft = mmr.maxLoc;
        
        // Update chess board position dynamically
        Rectangle newBounds = new Rectangle(
            (int)topLeft.x, (int)topLeft.y, 
            template.cols(), template.rows()
        );
        
        if (!newBounds.equals(chessBoardBounds)) {
            chessBoardBounds = newBounds;
            logger.info("Chess board position updated: {}", chessBoardBounds);
        }
    }
    
    private void calculateSquareBounds() {
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squareBounds[row][col] = new Rectangle(
                    chessBoardBounds.x + col * squareWidth,
                    chessBoardBounds.y + row * squareHeight,
                    squareWidth, squareHeight
                );
            }
        }
    }
    
    public String mapToChessSquare(Point2D gazePoint) {
        // TOLERANCE: Built-in error handling for eye-tracking inaccuracy
        return mapToChessSquareWithTolerance(gazePoint);
    }
    
    private String mapToChessSquareWithTolerance(Point2D gazePoint) {
        // Primary detection: Exact square boundaries
        String exactSquare = getExactSquare(gazePoint);
        if (exactSquare != null) {
            handleSquareHighlight(exactSquare);
            return exactSquare;
        }
        
        // TOLERANCE LEVEL 1: Expand square boundaries by 15% for edge cases
        String tolerantSquare = getSquareWithExpansion(gazePoint, 0.15);
        if (tolerantSquare != null) {
            logger.debug("Gaze mapped with 15% tolerance: {}", tolerantSquare);
            handleSquareHighlight(tolerantSquare);
            return tolerantSquare;
        }
        
        // TOLERANCE LEVEL 2: Find nearest square within 25% of square size
        String nearestSquare = getNearestSquare(gazePoint, 0.25);
        if (nearestSquare != null) {
            logger.debug("Gaze mapped to nearest square: {}", nearestSquare);
            handleSquareHighlight(nearestSquare);
            return nearestSquare;
        }
        
        // TOLERANCE LEVEL 3: Probabilistic mapping with confidence scoring
        return getProbabilisticSquare(gazePoint);
    }
    
    private String getExactSquare(Point2D gazePoint) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (squareBounds[row][col].contains(gazePoint)) {
                    return getSquareName(row, col);
                }
            }
        }
        return null;
    }
    
    private String getSquareWithExpansion(Point2D gazePoint, double expansionFactor) {
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        int expandX = (int)(squareWidth * expansionFactor);
        int expandY = (int)(squareHeight * expansionFactor);
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle expanded = new Rectangle(
                    squareBounds[row][col].x - expandX,
                    squareBounds[row][col].y - expandY,
                    squareBounds[row][col].width + 2 * expandX,
                    squareBounds[row][col].height + 2 * expandY
                );
                if (expanded.contains(gazePoint)) {
                    return getSquareName(row, col);
                }
            }
        }
        return null;
    }
    
    private String getNearestSquare(Point2D gazePoint, double maxDistanceFactor) {
        int squareWidth = chessBoardBounds.width / 8;
        int squareHeight = chessBoardBounds.height / 8;
        double maxDistance = Math.min(squareWidth, squareHeight) * maxDistanceFactor;
        
        double minDistance = Double.MAX_VALUE;
        String nearestSquare = null;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle square = squareBounds[row][col];
                Point2D center = new Point2D.Double(
                    square.getCenterX(), square.getCenterY());
                
                double distance = gazePoint.distance(center);
                if (distance < maxDistance && distance < minDistance) {
                    minDistance = distance;
                    nearestSquare = getSquareName(row, col);
                }
            }
        }
        
        return nearestSquare;
    }
    
    private String getProbabilisticSquare(Point2D gazePoint) {
        // Calculate probability for each square based on distance
        Map<String, Double> squareProbabilities = new HashMap<>();
        double totalWeight = 0;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle square = squareBounds[row][col];
                Point2D center = new Point2D.Double(
                    square.getCenterX(), square.getCenterY());
                
                double distance = gazePoint.distance(center);
                double weight = 1.0 / (1.0 + distance); // Inverse distance weighting
                
                String squareName = getSquareName(row, col);
                squareProbabilities.put(squareName, weight);
                totalWeight += weight;
            }
        }
        
        // Return square with highest probability if above threshold
        String bestSquare = null;
        double maxProbability = 0;
        
        for (Map.Entry<String, Double> entry : squareProbabilities.entrySet()) {
            double probability = entry.getValue() / totalWeight;
            if (probability > maxProbability) {
                maxProbability = probability;
                bestSquare = entry.getKey();
            }
        }
        
        // Only return if confidence is above minimum threshold (20%)
        if (maxProbability > 0.20) {
            logger.debug("Probabilistic mapping: {} (confidence: {:.2f})", 
                bestSquare, maxProbability);
            handleSquareHighlight(bestSquare);
            return bestSquare;
        }
        
        logger.debug("Gaze point outside tolerance range: ({}, {})", 
            gazePoint.getX(), gazePoint.getY());
        return null;
    }
    
    private String getSquareName(int row, int col) {
        char file = (char)('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }
    
    private void handleSquareHighlight(String square) {
        long currentTime = System.currentTimeMillis();
        
        if (square.equals(currentHighlightedSquare)) {
            // REQUIREMENT 3: Continue looking at same square - re-highlight
            if (currentTime - highlightStartTime >= HIGHLIGHT_DURATION) {
                highlightSquare(square);
                highlightStartTime = currentTime;
            }
        } else {
            // REQUIREMENT 2: New square focused - highlight in BLUE
            highlightSquare(square);
            currentHighlightedSquare = square;
            highlightStartTime = currentTime;
        }
        
        // Schedule highlight removal after 3 seconds
        scheduleHighlightRemoval(square, currentTime);
    }
    
    private void highlightSquare(String square) {
        // Send WebSocket message to highlight square in BLUE
        Map<String, Object> highlightData = new HashMap<>();
        highlightData.put("square", square);
        highlightData.put("color", "blue");
        highlightData.put("duration", HIGHLIGHT_DURATION);
        
        webSocketController.sendToAll("/topic/squareHighlight", highlightData);
        logger.info("Highlighting square {} in BLUE for 3 seconds", square);
    }
    
    private void scheduleHighlightRemoval(String square, long startTime) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            if (square.equals(currentHighlightedSquare) && 
                startTime == highlightStartTime) {
                removeHighlight(square);
            }
        }, HIGHLIGHT_DURATION, TimeUnit.MILLISECONDS);
    }
    
    private void removeHighlight(String square) {
        Map<String, Object> removeData = new HashMap<>();
        removeData.put("square", square);
        removeData.put("action", "remove");
        
        webSocketController.sendToAll("/topic/squareHighlight", removeData);
        logger.info("Removing highlight from square {}", square);
        
        if (square.equals(currentHighlightedSquare)) {
            currentHighlightedSquare = null;
            highlightStartTime = 0;
        }
    }
    
    /**
     * Validates that we're tracking the correct Thymeleaf interface
     */
    public boolean isThymeleafBoardActive() {
        // Check if browser is showing localhost:8081 (Thymeleaf interface)
        // Not localhost:8080/react (React interface)
        return getCurrentBrowserURL().contains(":8081") && 
               !getCurrentBrowserURL().contains("/react");
    }
    
    private String getCurrentBrowserURL() {
        // Implementation to detect current browser URL
        // Could use browser automation tools or system APIs
        return "http://localhost:8081"; // Default assumption
    }
}
```

### 3. **VisualTrainingDataManager - STATE Folder Persistence**

```java
@Service
public class VisualTrainingDataManager {
    
    private static final String VISUAL_TRAINING_DIR = "state/visual-training/";
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void initializeStorage() {
        createDirectoryIfNotExists(VISUAL_TRAINING_DIR);
        createDirectoryIfNotExists(VISUAL_TRAINING_DIR + "user-sessions/");
    }
    
    @EventListener
    public void handleVisualTrainingData(VisualTrainingEvent event) {
        // REQUIREMENT 6: Save visual training data to STATE folder
        try {
            String sessionFile = VISUAL_TRAINING_DIR + "user-sessions/session-" + 
                event.getSessionId() + ".json";
            
            List<GazeDataPoint> sessionData = loadSessionData(sessionFile);
            sessionData.add(event.getGazeDataPoint());
            
            // Use binary format for better performance with large gaze datasets
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(sessionFile))) {
                oos.writeObject(sessionData);
            }
            
            // Also append to main gaze patterns file
            appendToGazePatternsFile(event.getGazeDataPoint());
            
        } catch (IOException e) {
            logger.error("Failed to persist visual training data", e);
        }
    }
    
    private void appendToGazePatternsFile(GazeDataPoint dataPoint) throws IOException {
        String gazeFile = VISUAL_TRAINING_DIR + "gaze-patterns.json";
        List<GazeDataPoint> allData = loadGazePatterns(gazeFile);
        allData.add(dataPoint);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(gazeFile))) {
            oos.writeObject(allData);
        }
    }
    
    public void saveMovePrediction(String predictedMove, String actualMove, double confidence) {
        // Save prediction accuracy data
        try {
            String predictionFile = VISUAL_TRAINING_DIR + "move-predictions.json";
            List<MovePredictionResult> predictions = loadMovePredictions(predictionFile);
            
            predictions.add(new MovePredictionResult(
                predictedMove, actualMove, confidence, System.currentTimeMillis()));
                
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(predictionFile))) {
                oos.writeObject(predictions);
            }
        } catch (IOException e) {
            logger.error("Failed to save move prediction data", e);
        }
    }
}
```

### 4. **WebSocket Visual Training Handler**

```java
@MessageMapping("/rawGazeData")
public void handleRawGazeData(@Payload Map<String, Object> rawData) {
    // REQUIREMENT 6: Receive RAW gaze data from UI (not processed training data)
    String sessionId = (String) rawData.get("sessionId");
    Double gazeX = (Double) rawData.get("gazeX");
    Double gazeY = (Double) rawData.get("gazeY");
    String square = (String) rawData.get("square");
    Long timestamp = (Long) rawData.get("timestamp");
    String userAction = (String) rawData.get("userAction");
    
    // Backend processes raw data into training features
    RawGazePoint rawPoint = new RawGazePoint(
        new Point2D.Double(gazeX, gazeY), square, timestamp, userAction);
    
    // All training processing happens in backend
    applicationEventPublisher.publishEvent(
        new RawGazeDataEvent(sessionId, rawPoint));
}
```

### 5. **PieceIntentionAnalyzer - User Thinking Pattern Detection**

```java
@Component
public class PieceIntentionAnalyzer {
    
    @Autowired
    private ChessGame chessGame;
    
    /**
     * REQUIREMENT 4: Determine which piece user is thinking about
     * and predict their strategic intention
     */
    public PieceIntention analyzePieceIntention(String focusedSquare, GazePattern pattern) {
        String[][] board = chessGame.getBoard();
        String piece = board[getRow(focusedSquare)][getCol(focusedSquare)];
        
        if (piece == null || piece.isEmpty()) {
            return new PieceIntention(focusedSquare, "empty", "none", new ArrayList<>());
        }
        
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        boolean isUserTurn = chessGame.isWhiteTurn();
        
        if (isWhitePiece && isUserTurn) {
            // User looking at their own white piece - predict possible moves
            return analyzeUserPieceIntention(focusedSquare, piece, pattern);
        } else if (!isWhitePiece && !isUserTurn) {
            // User looking at AI's black piece - predict where AI might move
            return analyzeAIPieceIntention(focusedSquare, piece, pattern);
        }
        
        return new PieceIntention(focusedSquare, piece, "observation", new ArrayList<>());
    }
    
    private PieceIntention analyzeUserPieceIntention(String square, String piece, GazePattern pattern) {
        List<String> possibleMoves = chessGame.getLegalMovesForPiece(square);
        
        // Analyze gaze pattern to predict most likely moves
        List<String> predictedMoves = new ArrayList<>();
        
        for (String move : possibleMoves) {
            String targetSquare = extractTargetSquare(move);
            double moveConfidence = calculateMoveConfidence(square, targetSquare, pattern);
            
            if (moveConfidence > 0.6) {
                predictedMoves.add(move);
            }
        }
        
        // Sort by confidence
        predictedMoves.sort((m1, m2) -> {
            double conf1 = calculateMoveConfidence(square, extractTargetSquare(m1), pattern);
            double conf2 = calculateMoveConfidence(square, extractTargetSquare(m2), pattern);
            return Double.compare(conf2, conf1);
        });
        
        String intention = predictedMoves.isEmpty() ? "considering" : "planning_move";
        
        logger.info("User thinking about {} piece at {}: {} (predicted moves: {})", 
            piece, square, intention, predictedMoves.size());
            
        return new PieceIntention(square, piece, intention, predictedMoves);
    }
    
    private PieceIntention analyzeAIPieceIntention(String square, String piece, GazePattern pattern) {
        // User looking at AI piece - predict where AI might move it
        List<String> aiPossibleMoves = chessGame.getLegalMovesForPiece(square);
        
        // Use current AI to predict its most likely moves with this piece
        String selectedAI = chessGame.getSelectedAI();
        List<String> aiPredictedMoves = predictAIMoves(selectedAI, square, aiPossibleMoves);
        
        String intention = "anticipating_ai_move";
        
        logger.info("User anticipating AI {} piece at {}: {} possible moves", 
            piece, square, aiPossibleMoves.size());
            
        return new PieceIntention(square, piece, intention, aiPredictedMoves);
    }
    
    private List<String> predictAIMoves(String aiName, String square, List<String> possibleMoves) {
        // Quick evaluation of AI's likely moves with this piece
        List<String> predictedMoves = new ArrayList<>();
        
        for (String move : possibleMoves) {
            double aiMoveScore = evaluateAIMoveScore(aiName, move);
            if (aiMoveScore > 0.7) {
                predictedMoves.add(move);
            }
        }
        
        return predictedMoves.subList(0, Math.min(3, predictedMoves.size()));
    }
    
    private double calculateMoveConfidence(String fromSquare, String toSquare, GazePattern pattern) {
        // Analyze gaze transitions between source and target squares
        double transitionScore = pattern.getTransitionScore(fromSquare, toSquare);
        double fixationScore = pattern.getFixationScore(toSquare);
        double temporalScore = pattern.getTemporalScore();
        
        return (transitionScore * 0.4 + fixationScore * 0.4 + temporalScore * 0.2);
    }
    
    private double evaluateAIMoveScore(String aiName, String move) {
        // Quick heuristic evaluation of how likely AI is to make this move
        // This could be enhanced with actual AI evaluation calls
        return 0.5 + Math.random() * 0.5; // Placeholder
    }
}

public class PieceIntention {
    public final String square;
    public final String piece;
    public final String intention; // "planning_move", "considering", "anticipating_ai_move", "observation"
    public final List<String> predictedMoves;
    
    public PieceIntention(String square, String piece, String intention, List<String> predictedMoves) {
        this.square = square;
        this.piece = piece;
        this.intention = intention;
        this.predictedMoves = predictedMoves;
    }
}
```

### 4. **MovePredictionAI - LSTM with Attention for Visual Sequences**

```java
@Component
public class MovePredictionAI {
    
    private MultiLayerNetwork network;
    private GazeFeatureExtractor featureExtractor;
    private List<TrainingExample> trainingData = new ArrayList<>();
    private static final int SEQUENCE_LENGTH = 30; // 1 second at 30 FPS
    
    @PostConstruct
    public void initializeNetwork() {
        // OPTIMAL DL4J METHOD: LSTM with Attention for visual cue sequences
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(12345)
            .updater(new Adam(0.001))
            .list()
            // Input layer: Sequential gaze data (30 timesteps x 20 features)
            .layer(new LSTM.Builder()
                .nIn(20) // Gaze coordinates + temporal features
                .nOut(128)
                .activation(Activation.TANH)
                .gateActivationFunction(Activation.SIGMOID)
                .build())
            // Bidirectional LSTM for forward/backward gaze analysis
            .layer(new Bidirectional(new LSTM.Builder()
                .nIn(128)
                .nOut(64)
                .activation(Activation.TANH)
                .build()))
            // Attention mechanism for focusing on relevant gaze patterns
            .layer(new SelfAttentionLayer.Builder()
                .nIn(128) // Bidirectional output
                .nOut(128)
                .nHeads(8) // Multi-head attention
                .build())
            // Dense layer for chess context integration
            .layer(new DenseLayer.Builder()
                .nIn(128 + 40) // Attention output + chess context
                .nOut(256)
                .activation(Activation.RELU)
                .dropOut(0.3)
                .build())
            // Output layer: Move probabilities
            .layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .nIn(256)
                .nOut(4096)
                .activation(Activation.SOFTMAX)
                .build())
            .build();
            
        network = new MultiLayerNetwork(conf);
        network.init();
    }
    
    public MovePrediction predictMove(GazeSequence gazeSequence) {
        // LSTM requires sequential input: [batchSize, sequenceLength, features]
        INDArray sequenceInput = featureExtractor.extractSequence(gazeSequence);
        INDArray chessContext = featureExtractor.extractChessContext(gazeSequence);
        
        // Combine LSTM output with chess context
        INDArray combinedInput = Nd4j.concat(1, sequenceInput, chessContext);
        INDArray output = network.output(combinedInput);
        
        // Get top 3 predictions with attention weights
        INDArray sorted = Nd4j.sort(output, false);
        List<MovePrediction> predictions = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            int moveIndex = sorted.getInt(i);
            double confidence = output.getDouble(moveIndex);
            String move = indexToMove(moveIndex);
            
            // Extract attention weights for interpretability
            INDArray attentionWeights = getAttentionWeights(gazeSequence);
            predictions.add(new MovePrediction(move, confidence, attentionWeights));
        }
        
        return predictions.get(0); // Return highest confidence
    }
    
    public void trainOnUserMove(GazeSequence gazeSequence, String actualMove) {
        // LSTM training with sequence data
        TrainingExample example = new TrainingExample(gazeSequence, actualMove);
        trainingData.add(example);
        
        // Online learning with mini-batches for LSTM stability
        if (trainingData.size() % 32 == 0) { // Batch size 32 for LSTM
            trainLSTMBatch();
        }
    }
    
    private void trainLSTMBatch() {
        // Prepare sequential training data for LSTM
        List<TrainingExample> batch = trainingData.subList(
            Math.max(0, trainingData.size() - 32), trainingData.size());
            
        INDArray sequences = Nd4j.zeros(32, SEQUENCE_LENGTH, 20);
        INDArray labels = Nd4j.zeros(32, 4096);
        
        for (int i = 0; i < batch.size(); i++) {
            TrainingExample example = batch.get(i);
            sequences.putRow(i, featureExtractor.extractSequence(example.gazeSequence));
            labels.putScalar(i, moveToIndex(example.actualMove), 1.0);
        }
        
        DataSet dataSet = new DataSet(sequences, labels);
        network.fit(dataSet);
    }
}
```

### 4. **MultiAgentPrecomputationService - Dynamic Cancellation System**

```java
@Service
public class MultiAgentPrecomputationService {
    
    // All 12 AI systems
    @Autowired private QLearningAI qLearningAI;
    @Autowired private DeepLearningAI deepLearningAI;
    @Autowired private DeepLearningCNNAI deepLearningCNNAI;
    @Autowired private DeepQNetworkAI dqnAI;
    @Autowired private MonteCarloTreeSearchAI mctsAI;
    @Autowired private AlphaZeroAI alphaZeroAI;
    @Autowired private NegamaxAI negamaxAI;
    @Autowired private OpenAiChessAI openAiAI;
    @Autowired private LeelaChessZeroAI leelaZeroAI;
    @Autowired private GeneticAlgorithmAI geneticAI;
    @Autowired private AlphaFold3AI alphaFold3AI;
    @Autowired private AsynchronousAdvantageActorCriticAI a3cAI;
    
    // Thread pool for parallel computation
    private ExecutorService aiExecutor = Executors.newFixedThreadPool(12);
    
    // Cache for pre-computed responses
    private Map<String, Map<String, PrecomputedResponse>> responseCache = new ConcurrentHashMap<>();
    
    // Active computation tracking for cancellation
    private volatile String currentPredictedMove = null;
    private volatile CompletableFuture<Void> activeComputation = null;
    private final Object computationLock = new Object();
    
    public void preComputeAllResponses(String predictedMove, String currentPosition) {
        synchronized (computationLock) {
            // Cancel previous computation if different move predicted
            if (activeComputation != null && !predictedMove.equals(currentPredictedMove)) {
                logger.info("Canceling previous precomputation for: {} (new: {})", 
                    currentPredictedMove, predictedMove);
                activeComputation.cancel(true);
                activeComputation = null;
            }
            
            String cacheKey = currentPosition + ":" + predictedMove;
            
            if (responseCache.containsKey(cacheKey)) {
                return; // Already computed
            }
            
            // Skip if same move already being computed
            if (predictedMove.equals(currentPredictedMove) && activeComputation != null) {
                return;
            }
            
            currentPredictedMove = predictedMove;
            Map<String, PrecomputedResponse> responses = new ConcurrentHashMap<>();
            
            // Create cancellable AI computation tasks
            activeComputation = startCancellableComputation(predictedMove, responses, cacheKey);
        }
    }
    
    private CompletableFuture<Void> startCancellableComputation(
            String predictedMove, Map<String, PrecomputedResponse> responses, String cacheKey) {
        
        List<CompletableFuture<Void>> futures = Arrays.asList(
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = qLearningAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("QLearning", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = deepLearningAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("DeepLearning", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = alphaZeroAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("AlphaZero", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = leelaZeroAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("LeelaChessZero", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = alphaFold3AI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("AlphaFold3", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = a3cAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("A3C", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = mctsAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("MCTS", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = negamaxAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("Negamax", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = openAiAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("OpenAI", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = deepLearningCNNAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("CNN", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = dqnAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("DQN", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor),
            
            CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    String response = geneticAI.getBestMove(simulateMove(predictedMove));
                    if (!Thread.currentThread().isInterrupted()) {
                        responses.put("Genetic", new PrecomputedResponse(response, System.currentTimeMillis()));
                    }
                }
            }, aiExecutor)
        );
        
        // Combine all futures with cancellation support
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
            
        // Return the combined future for cancellation tracking
        return allFutures.whenComplete((result, throwable) -> {
            synchronized (computationLock) {
                if (throwable == null && !allFutures.isCancelled()) {
                    responseCache.put(cacheKey, responses);
                    logger.info("Precomputation completed for move: {} ({} responses)", 
                        predictedMove, responses.size());
                } else if (allFutures.isCancelled()) {
                    logger.info("Precomputation cancelled for move: {}", predictedMove);
                } else {
                    logger.warn("Precomputation failed for move: {}", predictedMove, throwable);
                }
                
                // Clear active computation if this was the current one
                if (activeComputation == allFutures) {
                    activeComputation = null;
                    currentPredictedMove = null;
                }
            }
        }).orTimeout(2, TimeUnit.SECONDS);
    }
    
    public String getPrecomputedResponse(String aiName, String predictedMove, String currentPosition) {
        String cacheKey = currentPosition + ":" + predictedMove;
        Map<String, PrecomputedResponse> responses = responseCache.get(cacheKey);
        
        if (responses != null && responses.containsKey(aiName)) {
            PrecomputedResponse response = responses.get(aiName);
            // Check if response is still valid (within 5 seconds)
            if (System.currentTimeMillis() - response.timestamp < 5000) {
                return response.move;
            }
        }
        
        return null; // No precomputed response available
    }
}
```

### 5. **GazeFeatureExtractor - Sequential Pattern Analysis for LSTM**

```java
@Component
public class GazeFeatureExtractor {
    
    /**
     * Extract sequential features for LSTM input
     * Shape: [sequenceLength, features] = [30, 20]
     */
    public INDArray extractSequence(GazeSequence gazeSequence) {
        List<GazePoint> points = gazeSequence.getGazePoints();
        int seqLen = Math.min(points.size(), 30); // Last 30 points (1 second)
        
        INDArray sequence = Nd4j.zeros(seqLen, 20);
        
        for (int t = 0; t < seqLen; t++) {
            GazePoint point = points.get(points.size() - seqLen + t);
            double[] features = extractPointFeatures(point, t);
            sequence.putRow(t, Nd4j.create(features));
        }
        
        return sequence;
    }
    
    private double[] extractPointFeatures(GazePoint point, int timeStep) {
        return new double[] {
            // Spatial features (8 dimensions)
            point.x / 1920.0,           // Normalized screen X
            point.y / 1080.0,           // Normalized screen Y
            point.chessSquareX / 8.0,   // Chess board X (0-7)
            point.chessSquareY / 8.0,   // Chess board Y (0-7)
            point.gazeVelocityX,        // Velocity X
            point.gazeVelocityY,        // Velocity Y
            point.fixationDuration,     // How long looking at this point
            point.saccadeAmplitude,     // Jump distance from previous
            
            // Temporal features (6 dimensions)
            timeStep / 30.0,            // Normalized time in sequence
            point.timestamp,            // Absolute timestamp
            point.deltaTime,            // Time since previous point
            point.isFixation ? 1.0 : 0.0, // Fixation vs saccade
            point.blinkDetected ? 1.0 : 0.0, // Blink detection
            point.confidenceScore,      // Eye tracking confidence
            
            // Chess context features (6 dimensions)
            point.pieceType,            // What piece is being looked at
            point.isLegalMoveTarget ? 1.0 : 0.0, // Valid move destination
            point.threatLevel,          // Tactical importance of square
            point.isPlayerPiece ? 1.0 : 0.0,     // Own vs opponent piece
            point.moveNumber / 100.0,   // Game progress
            point.timeRemaining / 600.0 // Normalized time pressure
        };
    }
    
    /**
     * Extract chess context features for dense layer
     * Shape: [40] features
     */
    public INDArray extractChessContext(GazeSequence gazeSequence) {
        double[] context = new double[40];
        int idx = 0;
        
        // Game state features (20 dimensions)
        context[idx++] = gazeSequence.gamePhase;        // Opening/Middle/Endgame
        context[idx++] = gazeSequence.materialBalance;  // Piece advantage
        context[idx++] = gazeSequence.kingSafety;       // King safety score
        context[idx++] = gazeSequence.centerControl;    // Center control
        context[idx++] = gazeSequence.developmentScore; // Piece development
        context[idx++] = gazeSequence.pawnStructure;    // Pawn structure score
        context[idx++] = gazeSequence.pieceActivity;    // Piece mobility
        context[idx++] = gazeSequence.tacticalThreats;  // Immediate threats
        context[idx++] = gazeSequence.positionalAdvantage; // Long-term advantage
        context[idx++] = gazeSequence.timeRemaining;    // Clock pressure
        context[idx++] = gazeSequence.moveNumber;       // Game progress
        context[idx++] = gazeSequence.repetitionRisk;   // Draw risk
        context[idx++] = gazeSequence.complexityScore;  // Position complexity
        context[idx++] = gazeSequence.numberOfLegalMoves; // Move options
        context[idx++] = gazeSequence.isInCheck ? 1.0 : 0.0; // Check status
        context[idx++] = gazeSequence.canCastle ? 1.0 : 0.0;  // Castling rights
        context[idx++] = gazeSequence.enPassantAvailable ? 1.0 : 0.0; // En passant
        context[idx++] = gazeSequence.promotionPossible ? 1.0 : 0.0;  // Promotion
        context[idx++] = gazeSequence.playerSkillLevel; // User skill estimate
        context[idx++] = gazeSequence.historicalAccuracy; // Past prediction accuracy
        
        // Attention pattern features (20 dimensions)
        context[idx++] = gazeSequence.attentionSpread;     // How scattered is gaze
        context[idx++] = gazeSequence.focusIntensity;      // Concentration level
        context[idx++] = gazeSequence.scanPathLength;      // Total gaze distance
        context[idx++] = gazeSequence.backtrackCount;      // Revisiting squares
        context[idx++] = gazeSequence.hesitationTime;      // Decision uncertainty
        context[idx++] = gazeSequence.confirmationLooks;   // Double-checking
        context[idx++] = gazeSequence.alternativeConsiderations; // Options explored
        context[idx++] = gazeSequence.cognitiveLoad;       // Mental effort
        context[idx++] = gazeSequence.gazeStability;       // Steadiness
        context[idx++] = gazeSequence.movementSmoothness;  // Smooth vs jerky
        context[idx++] = gazeSequence.boundaryProximity;   // Edge vs center focus
        context[idx++] = gazeSequence.centerBias;          // Center preference
        context[idx++] = gazeSequence.diagonalPreference;  // Diagonal patterns
        context[idx++] = gazeSequence.horizontalMovement;  // Horizontal scanning
        context[idx++] = gazeSequence.verticalMovement;    // Vertical scanning
        context[idx++] = gazeSequence.knightMovePattern;   // L-shaped patterns
        context[idx++] = gazeSequence.castlingPattern;     // Castling consideration
        context[idx++] = gazeSequence.capturePattern;      // Capture focus
        context[idx++] = gazeSequence.defensivePattern;    // Defensive attention
        context[idx++] = gazeSequence.timeToDecision;      // Decision speed
        
        return Nd4j.create(context);
    }
        
        // Spatial features (20 dimensions)
        features[idx++] = pattern.sourceSquareX;
        features[idx++] = pattern.sourceSquareY;
        features[idx++] = pattern.targetSquareX;
        features[idx++] = pattern.targetSquareY;
        features[idx++] = pattern.gazeSpread;
        features[idx++] = pattern.movementDistance;
        features[idx++] = pattern.directionConsistency;
        features[idx++] = pattern.spatialAccuracy;
        features[idx++] = pattern.boundaryProximity;
        features[idx++] = pattern.centerBias;
        features[idx++] = pattern.edgeAvoidance;
        features[idx++] = pattern.diagonalPreference;
        features[idx++] = pattern.horizontalMovement;
        features[idx++] = pattern.verticalMovement;
        features[idx++] = pattern.knightMovePattern;
        features[idx++] = pattern.castlingPattern;
        features[idx++] = pattern.enPassantPattern;
        features[idx++] = pattern.promotionPattern;
        features[idx++] = pattern.capturePattern;
        features[idx++] = pattern.defensivePattern;
        
        // Chess context features (20 dimensions)
        features[idx++] = encodeGamePhase(pattern.gamePhase);
        features[idx++] = pattern.numberOfLegalMoves;
        features[idx++] = pattern.isInCheck ? 1.0 : 0.0;
        features[idx++] = pattern.materialBalance;
        features[idx++] = pattern.kingSafety;
        features[idx++] = pattern.centerControl;
        features[idx++] = pattern.developmentScore;
        features[idx++] = pattern.pawnStructure;
        features[idx++] = pattern.pieceActivity;
        features[idx++] = pattern.tacticalThreats;
        features[idx++] = pattern.positionalAdvantage;
        features[idx++] = pattern.timeRemaining;
        features[idx++] = pattern.moveNumber;
        features[idx++] = pattern.repetitionRisk;
        features[idx++] = pattern.drawProbability;
        features[idx++] = pattern.winProbability;
        features[idx++] = pattern.complexityScore;
        features[idx++] = pattern.uncertaintyLevel;
        features[idx++] = pattern.playerSkillLevel;
        features[idx++] = pattern.historicalAccuracy;
        
        return Nd4j.create(features);
    }
}
```

## Dynamic Gaze Tracking and Prediction Management

### **Problem**: Changing Eye Movement Predictions
As users scan the chess board, their eye movements may initially suggest one move (e.g., "e2-e4") but then shift to consider another move (e.g., "d2-d4"). The system must efficiently cancel previous precomputations and start new ones.

### **Solution**: Real-time Prediction Cancellation System

#### 1. **GazePredictionManager - Central Coordination**

```java
@Service
public class GazePredictionManager {
    
    private volatile MovePrediction currentPrediction = null;
    private volatile long lastPredictionTime = 0;
    private final Object predictionLock = new Object();
    
    // Confidence decay parameters
    private static final double CONFIDENCE_DECAY_RATE = 0.1; // 10% per 100ms
    private static final long PREDICTION_TIMEOUT_MS = 1000; // 1 second
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;
    
    @Autowired
    private MultiAgentPrecomputationService precomputationService;
    
    @Autowired
    private MovePredictionAI predictionAI;
    
    @Autowired
    private PieceIntentionAnalyzer pieceIntentionAnalyzer;
    
    @Autowired
    private WebSocketController webSocketController;
    
    /**
     * Processes new gaze data and manages prediction lifecycle
     * Enhanced with piece intention analysis
     */
    public void processGazeUpdate(GazePattern newPattern) {
        synchronized (predictionLock) {
            // REQUIREMENT 4: Analyze piece intention first
            String focusedSquare = newPattern.getFocusedSquare();
            if (focusedSquare != null) {
                PieceIntention intention = pieceIntentionAnalyzer.analyzePieceIntention(focusedSquare, newPattern);
                
                // Send intention analysis to frontend
                Map<String, Object> intentionData = new HashMap<>();
                intentionData.put("square", intention.square);
                intentionData.put("piece", intention.piece);
                intentionData.put("intention", intention.intention);
                intentionData.put("predictedMoves", intention.predictedMoves);
                
                webSocketController.sendToAll("/topic/pieceIntention", intentionData);
            }
            
            MovePrediction newPrediction = predictionAI.predictMove(newPattern);
            long currentTime = System.currentTimeMillis();
            
            // Check if prediction has changed significantly
            if (shouldUpdatePrediction(newPrediction, currentTime)) {
                
                // Cancel previous precomputation if different move
                if (currentPrediction != null && 
                    !newPrediction.move.equals(currentPrediction.move)) {
                    
                    logger.info("Gaze shifted: {} → {} (confidence: {:.2f})", 
                        currentPrediction.move, newPrediction.move, newPrediction.confidence);
                    
                    // Cancel ongoing precomputation
                    precomputationService.cancelCurrentComputation();
                }
                
                // Start new precomputation if confidence is high enough
                if (newPrediction.confidence > MIN_CONFIDENCE_THRESHOLD) {
                    currentPrediction = newPrediction;
                    lastPredictionTime = currentTime;
                    
                    // Trigger new precomputation
                    precomputationService.preComputeAllResponses(
                        newPrediction.move, getCurrentPosition());
                }
            } else {
                // Update confidence with decay if same move
                updatePredictionConfidence(currentTime);
            }
        }
    }
    
    private boolean shouldUpdatePrediction(MovePrediction newPrediction, long currentTime) {
        if (currentPrediction == null) {
            return true; // First prediction
        }
        
        // Different move with sufficient confidence
        if (!newPrediction.move.equals(currentPrediction.move) && 
            newPrediction.confidence > MIN_CONFIDENCE_THRESHOLD) {
            return true;
        }
        
        // Same move but significantly higher confidence
        if (newPrediction.move.equals(currentPrediction.move) && 
            newPrediction.confidence > currentPrediction.confidence + 0.1) {
            return true;
        }
        
        // Prediction timeout - need refresh
        if (currentTime - lastPredictionTime > PREDICTION_TIMEOUT_MS) {
            return true;
        }
        
        return false;
    }
    
    private void updatePredictionConfidence(long currentTime) {
        if (currentPrediction != null) {
            long timeDelta = currentTime - lastPredictionTime;
            double decayFactor = Math.exp(-CONFIDENCE_DECAY_RATE * timeDelta / 100.0);
            
            currentPrediction.confidence *= decayFactor;
            
            // Cancel if confidence drops too low
            if (currentPrediction.confidence < MIN_CONFIDENCE_THRESHOLD) {
                logger.info("Prediction confidence decayed below threshold: {:.2f}", 
                    currentPrediction.confidence);
                precomputationService.cancelCurrentComputation();
                currentPrediction = null;
            }
        }
    }
    
    public MovePrediction getCurrentPrediction() {
        synchronized (predictionLock) {
            return currentPrediction;
        }
    }
}
```

#### 2. **Enhanced MultiAgentPrecomputationService with Cancellation**

```java
@Service
public class MultiAgentPrecomputationService {
    
    // ... existing code ...
    
    /**
     * Cancels current computation and clears state
     */
    public void cancelCurrentComputation() {
        synchronized (computationLock) {
            if (activeComputation != null) {
                logger.info("Cancelling active precomputation for: {}", currentPredictedMove);
                
                // Cancel all running AI computations
                activeComputation.cancel(true);
                
                // Clear state
                activeComputation = null;
                currentPredictedMove = null;
                
                // Interrupt all AI computation threads
                interruptAIComputations();
            }
        }
    }
    
    private void interruptAIComputations() {
        // Send interrupt signals to all AI systems
        try {
            qLearningAI.interruptComputation();
            deepLearningAI.interruptComputation();
            alphaZeroAI.interruptComputation();
            leelaZeroAI.interruptComputation();
            alphaFold3AI.interruptComputation();
            a3cAI.interruptComputation();
            mctsAI.interruptComputation();
            negamaxAI.interruptComputation();
            openAiAI.interruptComputation();
            deepLearningCNNAI.interruptComputation();
            dqnAI.interruptComputation();
            geneticAI.interruptComputation();
        } catch (Exception e) {
            logger.warn("Error interrupting AI computations", e);
        }
    }
    
    /**
     * Checks if computation is still valid for the current prediction
     */
    public boolean isComputationValid(String predictedMove) {
        synchronized (computationLock) {
            return predictedMove.equals(currentPredictedMove) && 
                   activeComputation != null && 
                   !activeComputation.isCancelled();
        }
    }
}
```

#### 3. **AI System Interruption Interface**

```java
public interface InterruptibleAI {
    /**
     * Interrupts current computation gracefully
     */
    void interruptComputation();
    
    /**
     * Checks if computation should continue
     */
    boolean shouldContinueComputation();
}

// Example implementation in AlphaZeroAI
@Component
public class AlphaZeroAI implements InterruptibleAI {
    
    private volatile boolean computationInterrupted = false;
    
    @Override
    public String getBestMove(String[][] board) {
        computationInterrupted = false;
        
        // MCTS iterations with interruption checks
        for (int i = 0; i < maxIterations && !computationInterrupted; i++) {
            if (Thread.currentThread().isInterrupted() || computationInterrupted) {
                logger.info("AlphaZero computation interrupted at iteration {}", i);
                break;
            }
            
            // Perform MCTS iteration
            performMCTSIteration(board);
            
            // Check interruption every 10 iterations
            if (i % 10 == 0 && shouldContinueComputation()) {
                continue;
            }
        }
        
        return getBestMoveFromTree();
    }
    
    @Override
    public void interruptComputation() {
        computationInterrupted = true;
    }
    
    @Override
    public boolean shouldContinueComputation() {
        return !computationInterrupted && !Thread.currentThread().isInterrupted();
    }
}
```

#### 4. **Gaze Pattern Stability Detection**

```java
@Component
public class GazeStabilityDetector {
    
    private CircularBuffer<GazePoint> gazeHistory = new CircularBuffer<>(30); // 1 second at 30 FPS
    private static final double STABILITY_THRESHOLD = 0.8;
    
    /**
     * Determines if gaze pattern is stable enough for prediction
     */
    public boolean isGazeStable(GazePattern pattern) {
        gazeHistory.add(new GazePoint(pattern.currentGaze, pattern.targetSquare));
        
        if (gazeHistory.size() < 15) { // Need at least 0.5 seconds of data
            return false;
        }
        
        // Calculate gaze consistency over last 0.5 seconds
        List<GazePoint> recent = gazeHistory.getLast(15);
        String dominantSquare = findDominantSquare(recent);
        
        if (dominantSquare == null) {
            return false;
        }
        
        // Check if dominant square appears in >80% of recent samples
        long dominantCount = recent.stream()
            .mapToLong(gp -> dominantSquare.equals(gp.chessSquare) ? 1 : 0)
            .sum();
            
        double stability = (double) dominantCount / recent.size();
        return stability >= STABILITY_THRESHOLD;
    }
    
    private String findDominantSquare(List<GazePoint> points) {
        Map<String, Integer> squareCounts = new HashMap<>();
        
        for (GazePoint point : points) {
            if (point.chessSquare != null) {
                squareCounts.merge(point.chessSquare, 1, Integer::sum);
            }
        }
        
        return squareCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
```

### **Prediction Lifecycle Management**

```
Gaze Movement Flow:

1. Eye looks at e2 → Prediction: "e2-e4" (60% confidence) → Below threshold, no precomputation
2. Eye fixates on e2 → Prediction: "e2-e4" (75% confidence) → Start precomputation
3. Eye moves to d2 → Prediction: "d2-d4" (80% confidence) → Cancel e2-e4, start d2-d4
4. Eye returns to e2 → Prediction: "e2-e4" (85% confidence) → Cancel d2-d4, start e2-e4
5. Eye confirms e4 → Prediction: "e2-e4" (95% confidence) → Maintain precomputation
6. User clicks e2-e4 → Instant AI response from precomputed cache
```

### **Performance Optimizations**

#### 1. **Prediction Debouncing**
```java
// Prevent rapid prediction changes
private static final long MIN_PREDICTION_INTERVAL = 200; // 200ms minimum between changes

if (currentTime - lastPredictionTime < MIN_PREDICTION_INTERVAL) {
    return; // Skip rapid changes
}
```

#### 2. **Confidence Hysteresis**
```java
// Different thresholds for starting vs stopping precomputation
private static final double START_THRESHOLD = 0.7;
private static final double STOP_THRESHOLD = 0.5;

if (newPrediction.confidence > START_THRESHOLD) {
    startPrecomputation();
} else if (currentPrediction.confidence < STOP_THRESHOLD) {
    stopPrecomputation();
}
```

#### 3. **Resource Management**
```java
// Limit concurrent precomputations
private static final int MAX_CONCURRENT_PRECOMPUTATIONS = 1;

// Priority-based AI selection for limited resources
if (systemLoad > 0.8) {
    // Only precompute with fastest AIs
    precomputeWithAIs(Arrays.asList("Negamax", "QLearning", "MCTS"));
} else {
    // Full precomputation with all AIs
    precomputeWithAllAIs();
}
```

## Move Execution and Validation

### **Eye-Tracking vs Mouse Interaction**

The eye-tracking system operates as a **prediction layer** that runs parallel to the existing mouse-based move system:

```java
@Component
public class ChessGameController {
    
    @Autowired
    private GazePredictionManager gazePredictionManager;
    
    @Autowired
    private MultiAgentPrecomputationService precomputationService;
    
    /**
     * Existing mouse-based move method (UNCHANGED)
     * User still clicks to make actual moves
     */
    @MessageMapping("/move")
    public void makeMove(@Payload MoveRequest moveRequest) {
        String userMove = moveRequest.getMove();
        
        // Validate move legality (existing validation)
        if (!isValidMove(userMove)) {
            sendError("Invalid move: " + userMove);
            return;
        }
        
        // Execute user move (existing logic)
        executeMove(userMove);
        
        // Check if we have precomputed AI response
        String precomputedResponse = precomputationService.getPrecomputedResponse(
            selectedAI, userMove, getCurrentPosition());
            
        if (precomputedResponse != null) {
            // Use precomputed response (INSTANT)
            logger.info("Using precomputed AI response: {}", precomputedResponse);
            executeAIMove(precomputedResponse);
            
            // Train prediction model on successful prediction
            gazePredictionManager.recordSuccessfulPrediction(userMove);
        } else {
            // Fallback to normal AI computation (existing logic)
            logger.info("No precomputed response, using normal AI computation");
            String aiMove = computeAIMove(); // 2-5 seconds
            executeAIMove(aiMove);
            
            // Train prediction model on missed prediction
            gazePredictionManager.recordMissedPrediction(userMove);
        }
    }
    
    /**
     * Eye-tracking prediction runs in background (NEW)
     * Does NOT affect user interaction
     */
    @Scheduled(fixedRate = 100) // Every 100ms
    private void updateGazePrediction() {
        if (isUserTurn() && eyeTrackingEnabled) {
            GazePattern currentGaze = eyeTrackingService.getCurrentGazePattern();
            gazePredictionManager.processGazeUpdate(currentGaze);
        }
    }
}
```

### **Prediction Accuracy Validation**

```java
@Service
public class PredictionValidationService {
    
    private Map<String, PredictionAttempt> activePredictions = new ConcurrentHashMap<>();
    
    /**
     * Records when a prediction is made
     */
    public void recordPrediction(String predictedMove, double confidence) {
        PredictionAttempt attempt = new PredictionAttempt(
            predictedMove, confidence, System.currentTimeMillis());
        activePredictions.put("current", attempt);
    }
    
    /**
     * Validates prediction when user makes actual move
     */
    public PredictionResult validatePrediction(String actualMove) {
        PredictionAttempt attempt = activePredictions.remove("current");
        
        if (attempt == null) {
            return new PredictionResult(false, 0.0, "No active prediction");
        }
        
        boolean correct = attempt.predictedMove.equals(actualMove);
        long predictionTime = System.currentTimeMillis() - attempt.timestamp;
        
        // Update prediction statistics
        updatePredictionStats(correct, attempt.confidence, predictionTime);
        
        return new PredictionResult(correct, attempt.confidence, 
            correct ? "Prediction successful" : "Prediction failed");
    }
    
    /**
     * Gets current prediction accuracy metrics
     */
    public PredictionMetrics getCurrentMetrics() {
        return new PredictionMetrics(
            totalPredictions,
            correctPredictions,
            averageConfidence,
            averagePredictionTime
        );
    }
}
```

### **Interface Validation and Fallback Strategy**

```java
@Service
public class InterfaceValidationService {
    
    /**
     * Ensures eye-tracking targets the correct Thymeleaf interface
     */
    public boolean validateTargetInterface() {
        // Check if user is on the correct interface
        String currentURL = getCurrentBrowserURL();
        
        if (!currentURL.contains(":8081")) {
            logger.warn("Eye-tracking requires Thymeleaf interface (localhost:8081), current: {}", currentURL);
            return false;
        }
        
        if (currentURL.contains("/react")) {
            logger.warn("Eye-tracking not supported on React interface, switch to localhost:8081");
            return false;
        }
        
        return true;
    }
}

/**
 * Robust fallback system ensures normal gameplay even if eye-tracking fails
 */
public String getAIResponse(String userMove) {
    // 0. Validate interface before using eye-tracking predictions
    if (!interfaceValidationService.validateTargetInterface()) {
        logger.info("Interface validation failed, using normal AI computation");
        return selectedAI.getBestMove(getCurrentBoard());
    }
    
    // 1. Try precomputed response (eye-tracking prediction)
    String precomputed = precomputationService.getPrecomputedResponse(
        selectedAI, userMove, getCurrentPosition());
        
    if (precomputed != null) {
        logger.info("Eye-tracking prediction HIT on Thymeleaf board: {} → {}", userMove, precomputed);
        return precomputed; // <100ms response
    }
    
    // 2. Fallback to normal AI computation
    logger.info("Eye-tracking prediction MISS: {}, computing normally", userMove);
    return selectedAI.getBestMove(getCurrentBoard()); // 2-5s response
}
```

## Integration with Existing ChessGame

### Enhanced ChessGame Class

```java
@Component
public class ChessGame {
    
    // Existing AI systems...
    
    /** Eye-tracking prediction system */
    @Autowired
    private EyeTrackingPredictionEngine eyeTrackingEngine;
    
    /** Multi-agent precomputation service */
    @Autowired
    private MultiAgentPrecomputationService precomputationService;
    
    @Value("${chess.eyetracking.enabled:false}")
    private boolean eyeTrackingEnabled;
    
    @PostConstruct
    private void initializeEyeTracking() {
        if (eyeTrackingEnabled) {
            eyeTrackingEngine.startTracking();
            startPredictionLoop();
        }
    }
    
    private void startPredictionLoop() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (whiteTurn && !gameOver) { // Only predict for user moves
                MovePrediction prediction = eyeTrackingEngine.predictNextMove();
                if (prediction.confidence > 0.7) {
                    String currentFEN = getCurrentFEN();
                    precomputationService.preComputeAllResponses(prediction.move, currentFEN);
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public String makeAIMove() {
        // Check if we have a precomputed response
        String lastUserMove = getLastMove();
        String currentFEN = getCurrentFEN();
        String precomputedMove = precomputationService.getPrecomputedResponse(
            selectedAIForGame, lastUserMove, currentFEN);
            
        if (precomputedMove != null) {
            logger.info("Using precomputed response: {}", precomputedMove);
            return precomputedMove;
        }
        
        // Fallback to normal AI computation
        return super.makeAIMove();
    }
}
```

## Configuration Properties

```properties
# Eye Tracking Configuration
chess.eyetracking.enabled=false
chess.eyetracking.camera.device=0
chess.eyetracking.fps=30
chess.eyetracking.prediction.confidence.threshold=0.7
chess.eyetracking.precomputation.timeout=2000ms

# Target Interface Configuration
chess.eyetracking.target.interface=thymeleaf
chess.eyetracking.target.url=http://localhost:8081
chess.eyetracking.board.template=thymeleaf_chess_board_template.png
chess.eyetracking.exclude.react=true

# Dynamic Board Detection (REQUIREMENT 1)
chess.eyetracking.board.detection.interval=500ms
chess.eyetracking.board.detection.accuracy=0.8
chess.eyetracking.board.position.tracking=true

# Eye-Tracking Tolerance Configuration
chess.eyetracking.tolerance.expansion.factor=0.15
chess.eyetracking.tolerance.nearest.distance=0.25
chess.eyetracking.tolerance.probability.threshold=0.20
chess.eyetracking.tolerance.calibration.enabled=true
chess.eyetracking.tolerance.adaptive.learning=true

# Square Highlighting (REQUIREMENTS 2 & 3)
chess.eyetracking.highlight.enabled=true
chess.eyetracking.highlight.color=blue
chess.eyetracking.highlight.duration=3000ms
chess.eyetracking.highlight.repeat.enabled=true

# Piece Intention Analysis (REQUIREMENT 4)
chess.eyetracking.intention.analysis.enabled=true
chess.eyetracking.intention.confidence.threshold=0.6
chess.eyetracking.intention.move.prediction.count=3

# Performance Settings
chess.eyetracking.threads=4
chess.eyetracking.gpu.enabled=true
chess.eyetracking.memory.limit=1GB

# Training Configuration - LSTM Optimized
chess.eyetracking.training.enabled=true
chess.eyetracking.training.method=LSTM_ATTENTION
chess.eyetracking.training.sequence.length=30
chess.eyetracking.training.batch.size=32
chess.eyetracking.training.learning.rate=0.001
chess.eyetracking.training.attention.heads=8
chess.eyetracking.training.lstm.units=128
chess.eyetracking.training.bidirectional=true

# Privacy Settings
chess.eyetracking.data.retention.days=7
chess.eyetracking.auto.delete=true
chess.eyetracking.consent.required=true
```

## Maven Dependencies

```xml
<!-- OpenCV for computer vision -->
<dependency>
    <groupId>org.openpnp</groupId>
    <artifactId>opencv</artifactId>
    <version>4.9.0-0</version>
</dependency>

<!-- JavaCV for video processing -->
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacv-platform</artifactId>
    <version>1.5.9</version>
</dependency>

<!-- MediaPipe for face detection -->
<dependency>
    <groupId>com.google.mediapipe</groupId>
    <artifactId>mediapipe_java</artifactId>
    <version>0.10.7</version>
</dependency>
```

## Performance Targets

### Prediction Accuracy
- **Beginner Players**: 60% accuracy
- **Intermediate Players**: 70% accuracy  
- **Expert Players**: 80% accuracy

### Response Time Improvements
- **Traditional AI**: 2-5 seconds
- **Predictive AI**: <100ms (95% improvement)
- **Total Speedup**: 20-50x faster gameplay

### System Requirements
- **CPU**: 4+ cores for parallel AI processing
- **RAM**: 8GB+ for neural networks and caching
- **GPU**: Optional for accelerated computer vision
- **Webcam**: 720p+ resolution, 30 FPS

## Implementation Timeline

### Phase 1: Eye Tracking Foundation (6 weeks)
- OpenCV webcam integration
- Face and eye detection
- **REQUIREMENT 1**: Dynamic chess board detection and position tracking
- Basic gaze point calculation

### Phase 2: Visual Feedback System (4 weeks)
- **REQUIREMENT 2**: Square highlighting in BLUE for 3 seconds
- **REQUIREMENT 3**: Continuous highlighting for sustained gaze
- WebSocket integration for real-time highlighting
- Frontend highlighting animations

### Phase 3: Piece Intention Analysis (6 weeks)
- **REQUIREMENT 4**: Piece thinking pattern detection
- User move prediction for white pieces
- AI move anticipation for black pieces
- Strategic intention classification

### Phase 4: Move Prediction AI (8 weeks)
- Neural network architecture
- Feature extraction pipeline
- Training data collection
- Initial model training

### Phase 5: Multi-Agent Integration (6 weeks)
- Parallel AI precomputation
- Response caching system
- Performance optimization
- Integration testing

### Phase 6: Production Features (4 weeks)
- User interface enhancements
- Privacy controls
- Performance monitoring
- Documentation and deployment

**Total Implementation Time**: 28 weeks

## User Experience: Unchanged Interaction, Enhanced Performance

### **From User Perspective**

**Target Interface: Original Thymeleaf Chess Board (localhost:8081)**

**What Stays the Same:**
- **Mouse Interaction**: Users still click pieces and squares on the Thymeleaf board to make moves
- **Move Validation**: All existing chess rules and validation remain unchanged
- **Game Interface**: No changes to the original HTML/CSS chess board or controls
- **Turn-based Play**: Users still take turns making moves as before on the Thymeleaf interface

**What Gets Better:**
- **AI Response Time**: Instant AI responses instead of 2-5 second delays
- **Smooth Gameplay**: No waiting time disrupts the flow of the game
- **Adaptive Difficulty**: AI adjusts to user skill level based on gaze patterns
- **Enhanced Training**: AI systems learn from human decision-making patterns

### **Prediction Success Scenarios**

```
Scenario 1: Perfect Prediction
User Gaze: e2 → e4 (2 seconds)
Prediction: "e2-e4" (85% confidence)
Precomputation: All 12 AIs calculate responses
User Action: Clicks e2, then e4
Result: Instant AI response ("e7-e5")

Scenario 2: Prediction Miss
User Gaze: e2 → e4 (2 seconds)
Prediction: "e2-e4" (85% confidence)
Precomputation: All 12 AIs calculate responses
User Action: Clicks d2, then d4 (different move)
Result: Normal AI computation (2-3 seconds)

Scenario 3: No Prediction
User Gaze: Scattered, no clear pattern
Prediction: None (confidence < 70%)
Precomputation: None
User Action: Clicks any valid move
Result: Normal AI computation (2-3 seconds)
```

### **Performance Metrics**

**Target Prediction Accuracy:**
- **Beginner Players**: 60% (simpler, more predictable moves)
- **Intermediate Players**: 70% (moderate complexity)
- **Expert Players**: 80% (consistent patterns)

**Response Time Improvements:**
- **Prediction Hit**: <100ms AI response (95% faster)
- **Prediction Miss**: 2-5s AI response (normal speed)
- **Overall Improvement**: 50-80% faster gameplay (depending on accuracy)

**System Reliability:**
- **Eye-tracking Failure**: Game continues normally with mouse-only interaction
- **Prediction Errors**: No impact on game validity or user experience
- **Resource Constraints**: Automatic fallback to essential AIs only

## New Requirements Implementation Summary

### **REQUIREMENT 1: Dynamic Chess Board Detection**
- **Implementation**: Enhanced `ChessBoardMapper` with continuous board position tracking
- **Frequency**: Updates every 500ms to handle browser window movement
- **Accuracy**: Template matching with 80% confidence threshold
- **Benefit**: Maintains precise square mapping regardless of browser position

### **REQUIREMENT 2: Blue Square Highlighting (3 seconds)**
- **Implementation**: WebSocket-based real-time highlighting system
- **Color**: Blue highlighting via CSS class injection
- **Duration**: Exactly 3 seconds before automatic revert
- **Trigger**: Immediate highlighting when gaze focuses on any square

### **REQUIREMENT 3: Sustained Gaze Re-highlighting**
- **Implementation**: Gaze continuity detection with re-highlighting logic
- **Behavior**: Re-highlights same square if user continues looking after 3 seconds
- **Cycle**: Repeatable 3-second highlight cycles for sustained attention
- **State Management**: Tracks current highlighted square and timing

### **REQUIREMENT 4: Piece Thinking Analysis**
- **White Pieces**: Predicts user's possible moves when looking at their pieces
- **Black Pieces**: Anticipates AI's likely moves when user examines AI pieces
- **Analysis**: `PieceIntentionAnalyzer` determines strategic thinking patterns
- **Output**: Real-time intention classification and move predictions

### **Integration Benefits**
- **Precision**: Exact square detection regardless of board position on screen
- **Visual Feedback**: Immediate blue highlighting confirms gaze tracking accuracy
- **Strategic Insight**: Understanding user's thought process for both offensive and defensive planning
- **Enhanced Prediction**: More accurate move prediction based on piece-specific gaze patterns

This architecture creates a revolutionary chess experience where eye-tracking enables instant AI responses through predictive multi-agent processing, while providing precise visual feedback and strategic intention analysis that preserves the familiar mouse-based interaction model users expect.

## Architectural Review Recommendations and Implementations

### 1. Enhance Privacy/Security
To address privacy and security concerns, implement a dedicated PrivacyService for handling encrypted gaze data and audit logging. Update configurations and add OWASP-compliant practices.

```java
@Service
public class PrivacyService {
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private SecretKey encryptionKey;

    @PostConstruct
    public void init() throws Exception {
        // Load or generate encryption key securely
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        encryptionKey = keyGen.generateKey();
    }

    public byte[] encryptGazeData(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
        return cipher.doFinal(data);
    }

    public byte[] decryptGazeData(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
        return cipher.doFinal(encryptedData);
    }

    public void logAccess(String userId, String action) {
        // Log to secure audit trail
        logger.info("User {} performed action: {}", userId, action);
    }
}
```

Integrate this into EyeTrackingService by encrypting data before storage:
```java
// In processFrame method
byte[] gazeData = convertToByteArray(gazePoint);
byte[] encrypted = privacyService.encryptGazeData(gazeData);
storeEncryptedData(encrypted);
```

Add to application.properties:
```
chess.eyetracking.encryption.enabled=true
chess.eyetracking.audit.logging=true
```

### 2. Add Calibration Module
Introduce a CalibrationService with user-guided calibration to improve accuracy. Use adaptive algorithms for real-time corrections.

```java
@Service
public class CalibrationService {
    private Point2D[] calibrationPoints = new Point2D[9]; // 3x3 grid
    private Map<Point2D, Point2D> calibrationMap = new HashMap<>();

    public void startCalibration() {
        // Display calibration UI points and collect gaze data
        for (int i = 0; i < calibrationPoints.length; i++) {
            displayCalibrationPoint(i);
            Point2D actualGaze = eyeTrackingService.getCurrentGaze();
            calibrationMap.put(calibrationPoints[i], actualGaze);
        }
        buildCalibrationModel();
    }

    private void buildCalibrationModel() {
        // Use linear regression or affine transformation to create correction model
        // Example: Implement Kalman filter for ongoing corrections
    }

    public Point2D correctGazePoint(Point2D rawPoint) {
        // Apply calibration correction
        return applyAffineTransformation(rawPoint);
    }
}
```

Integrate into EyeTrackingService:
```java
// In calculateGazePoint
Point2D rawGaze = ...;
Point2D corrected = calibrationService.correctGazePoint(rawGaze);
```

Add frontend React component for calibration UI using shadcn/ui.

### 3. Improve Scalability
Replace fixed thread pool with dynamic work-stealing pool and add prioritization for AI computations.

```java
// In MultiAgentPrecomputationService
private ExecutorService aiExecutor = Executors.newWorkStealingPool();

private void precomputeWithPriority(List<String> priorityAIs) {
    // Submit tasks with priority
    priorityAIs.forEach(ai -> aiExecutor.submit(() -> computeForAI(ai)));
}

// Usage
if (systemLoad > 0.8) {
    precomputeWithPriority(Arrays.asList("Negamax", "QLearning", "MCTS"));
} else {
    precomputeWithAllAIs();
}
```

Add browser fallback in frontend using WebRTC for webcam access.

### 4. Refine Training
Implement hybrid training with offline datasets and anonymization.

```java
// In MovePredictionAI
public void hybridTrain() {
    loadOfflineDataset(); // Load pre-collected anonymized data
    anonymizeTrainingData();
    retrainNetwork();
}

private void anonymizeTrainingData() {
    trainingData.forEach(example -> {
        // Convert to abstract features, remove PII
        example.gazePattern.anonymize();
    });
}
```

Schedule offline training periodically.

### 5. Bolster Error Handling/Testing
Extend GlobalExceptionHandler and add try-catch in critical paths.

```java
// In GlobalExceptionHandler
@ExceptionHandler(VisionException.class)
public ResponseEntity<ApiResponse<?>> handleVisionException(VisionException ex) {
    return errorResponseEntity(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
}

// In EyeTrackingService
try {
    camera.read(frame);
} catch (Exception e) {
    throw new VisionException("Failed to capture frame", e);
}
```

Add unit tests:
```java
@Test
public void testGazeCalculation() {
    // Mock frame and assert output
}
```

### 6. Frontend Enhancements
Add React components for calibration and consent.

```tsx
// ConsentModal.tsx
import { Dialog } from '@/components/ui/dialog';

export function ConsentModal() {
  return (
    <Dialog>
      <p>Consent to eye-tracking?</p>
      <Button onClick={handleConsent}>Agree</Button>
    </Dialog>
  );
}
```

Ensure dark mode and a11y with Tailwind and ARIA attributes.