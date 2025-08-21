package com.example.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ChessGame - Advanced chess engine with comprehensive AI integration
 * 
 * This class implements a complete chess game with the following features:
 * - Full chess rules including castling, en passant, pawn promotion
 * - Eight AI systems: Q-Learning, Deep Learning, Deep Q-Network, MCTS, AlphaZero, Negamax, OpenAI, Leela Chess Zero
 * - Comprehensive move validation with Queen safety and checkmate pattern recognition
 * - 34 chess opening sequences with 6-move depth
 * - Advanced threat detection and defensive play
 * - Undo/redo functionality and game state management
 * - Concurrent AI training and parallel move evaluation
 * - Flip-flop prevention and move repetition detection
 * 
 * Recent enhancements:
 * - Fixed Queen safety issues preventing suicidal moves
 * - Enhanced checkmate pattern recognition (Scholar's Mate, Back Rank Mate, Smothered Mate)
 * - Extended opening book from 4 to 6 moves for all openings
 * - Improved AI move validation with 9-step comprehensive checking
 * - Added piece pinning detection and legal move verification
 * 
 * @author Chess Development Team
 * @version 2.0
 */
@Component
public class ChessGame {
    private static final Logger logger = LogManager.getLogger(ChessGame.class);
    
    /** 8x8 chess board represented as Unicode chess pieces */
    private String[][] board = new String[8][8];
    /** Current turn indicator (true = white's turn, false = black's turn) */
    private boolean whiteTurn = true;
    /** Game over flag */
    private boolean gameOver = false;
    /** Random number generator for AI decisions */
    private Random random = new Random();
    /** Chess rule validator */
    private ChessRuleValidator ruleValidator = new ChessRuleValidator();

    /** Q-Learning AI system */
    private QLearningAI qLearningAI;
    /** Deep Learning neural network AI */
    private DeepLearningAI deepLearningAI;
    /** Deep Learning CNN AI system */
    private DeepLearningCNNAI deepLearningCNNAI;
    /** Deep Q-Network AI system */
    private DeepQNetworkAI dqnAI;
    /** Monte Carlo Tree Search AI system */
    private MonteCarloTreeSearchAI mctsAI;
    /** AlphaZero AI system */
    private AlphaZeroAI alphaZeroAI;
    /** Negamax + Alpha-Beta AI system */
    private NegamaxAI negamaxAI;
    /** OpenAI LLM AI system */
    private OpenAiChessAI openAiAI;
    /** LeelaChessZero AI system */
    private LeelaChessZeroAI leelaZeroAI;
    /** Genetic Algorithm AI system */
    private GeneticAlgorithmAI geneticAI;
    /** AlphaFold3 AI system */
    private AlphaFold3AI alphaFold3AI;

    private String previousBoardState = null;
    private int[] previousMove = null;
    
    // AI System Enable/Disable Configuration
    @Value("${chess.ai.qlearning.enabled:true}")
    private boolean qLearningEnabled;
    @Value("${chess.ai.deeplearning.enabled:true}")
    private boolean deepLearningEnabled;
    @Value("${chess.ai.deeplearningcnn.enabled:true}")
    private boolean deepLearningCNNEnabled;
    @Value("${chess.ai.dqn.enabled:true}")
    private boolean dqnEnabled;
    @Value("${chess.ai.mcts.enabled:true}")
    private boolean mctsEnabled;
    @Value("${chess.ai.alphazero.enabled:true}")
    private boolean alphaZeroEnabled;
    @Value("${chess.ai.negamax.enabled:true}")
    private boolean negamaxEnabled;
    @Value("${chess.ai.openai.enabled:true}")
    private boolean openAiEnabled;
    @Value("${chess.ai.openai}")
    private String openAiApiKey;
    @Value("${chess.ai.leelazerochess.enabled:true}")
    private boolean leelaZeroEnabled;
    @Value("${chess.ai.genetic.enabled:true}")
    private boolean geneticEnabled;
    @Value("${chess.ai.alphafold3.enabled:true}")
    private boolean alphaFold3Enabled;
    private boolean whiteKingMoved = false;
    private boolean blackKingMoved = false;
    private boolean whiteRookKingSideMoved = false;
    private boolean whiteRookQueenSideMoved = false;
    private boolean blackRookKingSideMoved = false;
    private boolean blackRookQueenSideMoved = false;
    private List<String> moveHistory = new ArrayList<>();
    private LeelaChessZeroOpeningBook leelaOpeningBook;
    private List<GameState> undoStack = new ArrayList<>();
    private List<GameState> redoStack = new ArrayList<>();
    private String lastRejectedMove = "";
    private List<String> recentMoves = new ArrayList<>();
    private Map<String, Integer> moveRepetitionCount = new HashMap<>();
    private boolean reEvaluationMode = false;
    private volatile boolean aiSystemsReady = false;
    private final Object aiInitLock = new Object();
    private int[] aiLastMove = null; // Track AI's last move for frontend blinking
    private volatile boolean stateChanged = false; // Track if any state changes occurred
    private ChessController controller; // Reference for WebSocket broadcasting
    private TrainingManager trainingManager; // Extracted training coordination
    private volatile long lastSaveTime = 0; // Track last save time to prevent redundant saves
    
    // AI selection for current game
    private String selectedAIForGame = null;
    private Random aiSelector = new Random();
    
    private static class GameState {
        String[][] board;
        boolean whiteTurn;
        boolean whiteKingMoved;
        boolean blackKingMoved;
        boolean whiteRookKingSideMoved;
        boolean whiteRookQueenSideMoved;
        boolean blackRookKingSideMoved;
        boolean blackRookQueenSideMoved;
        List<String> moveHistory;
        
        GameState(String[][] board, boolean whiteTurn, boolean whiteKingMoved, boolean blackKingMoved,
                 boolean whiteRookKingSideMoved, boolean whiteRookQueenSideMoved,
                 boolean blackRookKingSideMoved, boolean blackRookQueenSideMoved, List<String> moveHistory) {
            this.board = new String[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(board[i], 0, this.board[i], 0, 8);
            }
            this.whiteTurn = whiteTurn;
            this.whiteKingMoved = whiteKingMoved;
            this.blackKingMoved = blackKingMoved;
            this.whiteRookKingSideMoved = whiteRookKingSideMoved;
            this.whiteRookQueenSideMoved = whiteRookQueenSideMoved;
            this.blackRookKingSideMoved = blackRookKingSideMoved;
            this.blackRookQueenSideMoved = blackRookQueenSideMoved;
            this.moveHistory = new ArrayList<>(moveHistory);
        }
    }

    /**
     * Default constructor - initializes a new chess game with AI systems
     * Sets up the board, connects AI systems for knowledge sharing
     */
    public ChessGame() {
        // Initialize basic components first
        initializeBoard();
        // leelaOpeningBook will be initialized in @PostConstruct after Spring injection
        // AI systems will be initialized in @PostConstruct after Spring injection
    }
    
    /**
     * Spring @PostConstruct method - called after dependency injection
     */
    @PostConstruct
    private void initializeAfterInjection() {
        logger.info("@PostConstruct: Reading AI configuration");
        logger.info("Q-Learning enabled: {}", qLearningEnabled);
        logger.info("Deep Learning enabled: {}", deepLearningEnabled);
        logger.info("Deep Learning CNN enabled: {}", deepLearningCNNEnabled);
        logger.info("DQN enabled: {}", dqnEnabled);
        logger.info("MCTS enabled: {}", mctsEnabled);
        logger.info("AlphaZero enabled: {}", alphaZeroEnabled);
        logger.info("Negamax enabled: {}", negamaxEnabled);
        logger.info("OpenAI enabled: {}", openAiEnabled);
        logger.info("LeelaZero enabled: {}", leelaZeroEnabled);
        logger.info("Genetic enabled: {}", geneticEnabled);
        logger.info("AlphaFold3 enabled: {}", alphaFold3Enabled);
        
        // Initialize OpenCL detection early for all AI systems
        logger.info("Detecting AMD GPU and OpenCL support...");
        OpenCLDetector.detectAndConfigureOpenCL();
        if (OpenCLDetector.isOpenCLAvailable()) {
            logger.info("AMD GPU acceleration available: {}", OpenCLDetector.getGPUInfoString());
        } else {
            logger.info("AMD GPU not detected - AI systems will use CPU");
        }
        
        // Initialize LeelaZero opening book with Log4J debug
        leelaOpeningBook = new LeelaChessZeroOpeningBook(logger.isDebugEnabled());
        
        initializeAISystems();
        selectRandomAIForGame(); // Select AI for initial game
    }
    
    /**
     * Randomly select one AI to use for the current game
     */
    private void selectRandomAIForGame() {
        List<String> availableAIs = new ArrayList<>();
        
        if (qLearningEnabled && qLearningAI != null) availableAIs.add("QLearning");
        if (deepLearningEnabled && deepLearningAI != null) availableAIs.add("DeepLearning");
        if (deepLearningCNNEnabled && deepLearningCNNAI != null) availableAIs.add("DeepLearningCNN");
        if (dqnEnabled && dqnAI != null) availableAIs.add("DQN");
        if (mctsEnabled && mctsAI != null) availableAIs.add("MCTS");
        if (alphaZeroEnabled && alphaZeroAI != null) availableAIs.add("AlphaZero");
        if (negamaxEnabled && negamaxAI != null) availableAIs.add("Negamax");
        if (openAiEnabled && openAiAI != null) availableAIs.add("OpenAI");
        if (leelaZeroEnabled && leelaZeroAI != null) availableAIs.add("LeelaZero");
        if (geneticEnabled && geneticAI != null) availableAIs.add("Genetic");
        if (alphaFold3Enabled && alphaFold3AI != null) availableAIs.add("AlphaFold3");
        
        if (!availableAIs.isEmpty()) {
            selectedAIForGame = availableAIs.get(aiSelector.nextInt(availableAIs.size()));
            logger.info("Selected AI for this game: {}", selectedAIForGame);
        } else {
            selectedAIForGame = "None";
            logger.warn("No AI systems available for this game");
        }
    }
    
    /**
     * Lazy initialization of AI systems when first needed
     */
    private void ensureAISystemsInitialized() {
        synchronized (aiInitLock) {
            if (!aiSystemsReady) {
                initializeAISystems();
            }
        }
    }
    
    public ChessGame(QLearningAI qLearningAI, DeepLearningAI deepLearningAI, DeepQNetworkAI dqnAI) {
        // Use provided AI instances (constructor 2 - for testing)
        this.qLearningAI = qLearningAI;
        this.deepLearningAI = deepLearningAI;
        this.deepLearningCNNAI = null; // Not used in testing constructor
        this.dqnAI = dqnAI;
        
        initializeOptionalAISystems();
        initializeBoard();
        leelaOpeningBook = new LeelaChessZeroOpeningBook(false); // Use false for testing constructor
    }

    /**
     * Initialize AI systems based on configuration using virtual threads for concurrent loading
     * Note: @Value annotations may not be injected yet in constructor, so use defaults
     */
    private void initializeAISystems() {
        logger.info("*** STARTING CONCURRENT AI INITIALIZATION WITH VIRTUAL THREADS ***");
        
        // Create virtual threads for each AI system
        var qLearningTask = Thread.ofVirtual().start(() -> {
            if (qLearningEnabled) {
                qLearningAI = new QLearningAI(logger.isDebugEnabled());
                logger.info("Q-Learning AI: ENABLED");
            } else {
                qLearningAI = null;
                logger.info("Q-Learning AI: DISABLED");
            }
        });
        
        var deepLearningTask = Thread.ofVirtual().start(() -> {
            if (deepLearningEnabled) {
                deepLearningAI = new DeepLearningAI(logger.isDebugEnabled());
                logger.info("Deep Learning AI: ENABLED");
            } else {
                deepLearningAI = null;
                logger.info("Deep Learning AI: DISABLED");
            }
        });
        
        var deepLearningCNNTask = Thread.ofVirtual().start(() -> {
            if (deepLearningCNNEnabled) {
                deepLearningCNNAI = new DeepLearningCNNAI(logger.isDebugEnabled());
                logger.info("Deep Learning CNN AI: ENABLED");
            } else {
                deepLearningCNNAI = null;
                logger.info("Deep Learning CNN AI: DISABLED");
            }
        });
        
        var dqnTask = Thread.ofVirtual().start(() -> {
            if (dqnEnabled) {
                dqnAI = new DeepQNetworkAI(logger.isDebugEnabled());
                logger.info("DQN AI: ENABLED");
            } else {
                dqnAI = null;
                logger.info("DQN AI: DISABLED");
            }
        });
        
        var alphaZeroTask = Thread.ofVirtual().start(() -> {
            if (alphaZeroEnabled) {
                try {
                    alphaZeroAI = AlphaZeroFactory.createAlphaZeroAI(logger.isDebugEnabled());
                    logger.info("AlphaZero AI: ENABLED");
                } catch (Exception e) {
                    logger.error("AlphaZero AI: INITIALIZATION FAILED - {}", e.getMessage());
                    alphaZeroAI = null;
                }
            } else {
                alphaZeroAI = null;
                logger.info("AlphaZero AI: DISABLED");
            }
        });
        
        var negamaxTask = Thread.ofVirtual().start(() -> {
            if (negamaxEnabled) {
                negamaxAI = new NegamaxAI(logger.isDebugEnabled());
                logger.info("Negamax AI: ENABLED");
            } else {
                negamaxAI = null;
                logger.info("Negamax AI: DISABLED");
            }
        });
        
        var openAiTask = Thread.ofVirtual().start(() -> {
            if (openAiEnabled && openAiApiKey != null && !openAiApiKey.isEmpty()) {
                try {
                    openAiAI = new OpenAiChessAI(openAiApiKey, logger.isDebugEnabled());
                    logger.info("OpenAI AI: ENABLED");
                } catch (Exception e) {
                    logger.error("OpenAI AI: INITIALIZATION FAILED - {}", e.getMessage());
                    openAiAI = null;
                }
            } else {
                openAiAI = null;
                logger.info("OpenAI AI: DISABLED");
            }
        });
        
        var leelaZeroTask = Thread.ofVirtual().start(() -> {
            if (leelaZeroEnabled) {
                try {
                    leelaZeroAI = new LeelaChessZeroAI(logger.isDebugEnabled());
                    logger.info("LeelaChessZero AI: ENABLED");
                    logger.info("LeelaZero: Opening book initialized");
                } catch (Exception e) {
                    logger.error("LeelaChessZero AI: INITIALIZATION FAILED - {}", e.getMessage());
                    leelaZeroAI = null;
                }
            } else {
                leelaZeroAI = null;
                logger.info("LeelaChessZero AI: DISABLED");
            }
        });
        
        var geneticTask = Thread.ofVirtual().start(() -> {
            if (geneticEnabled) {
                try {
                    geneticAI = new GeneticAlgorithmAI(logger.isDebugEnabled());
                    logger.info("Genetic Algorithm AI: ENABLED");
                } catch (Exception e) {
                    logger.error("Genetic Algorithm AI: INITIALIZATION FAILED - {}", e.getMessage());
                    geneticAI = null;
                }
            } else {
                geneticAI = null;
                logger.info("Genetic Algorithm AI: DISABLED");
            }
        });
        
        var alphaFold3Task = Thread.ofVirtual().start(() -> {
            if (alphaFold3Enabled) {
                try {
                    alphaFold3AI = new AlphaFold3AI(logger.isDebugEnabled());
                    alphaFold3AI.setChessGameValidator(this); // Set ChessGame reference for rule validation
                    logger.info("AlphaFold3 AI: ENABLED");
                } catch (Exception e) {
                    logger.error("AlphaFold3 AI: INITIALIZATION FAILED - {}", e.getMessage());
                    alphaFold3AI = null;
                }
            } else {
                alphaFold3AI = null;
                logger.info("AlphaFold3 AI: DISABLED");
            }
        });
        
        // Wait for all virtual threads to complete in parallel (not sequential)
        try {
            // Create all tasks
            var allTasks = List.of(
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { qLearningTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { deepLearningTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { deepLearningCNNTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { dqnTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { alphaZeroTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { negamaxTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { openAiTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { leelaZeroTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { geneticTask.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }),
                java.util.concurrent.CompletableFuture.runAsync(() -> { try { alphaFold3Task.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } })
            );
            
            // Wait for ALL to complete in parallel (not sequential)
            java.util.concurrent.CompletableFuture.allOf(allTasks.toArray(new java.util.concurrent.CompletableFuture[0]))
                .get(60, java.util.concurrent.TimeUnit.SECONDS); // Single timeout for all
                
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("AI initialization timeout after 60 seconds: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("AI initialization error: {}", e.getMessage());
        }
        
        // Post-initialization setup that requires dependencies
        if (deepLearningEnabled && deepLearningAI != null && qLearningEnabled && qLearningAI != null) {
            deepLearningAI.setQLearningAI(qLearningAI);
        }
        
        if (deepLearningCNNEnabled && deepLearningCNNAI != null && qLearningEnabled && qLearningAI != null) {
            deepLearningCNNAI.setQLearningAI(qLearningAI);
        }
        
        // Set ChessGame references for state change notifications
        if (alphaFold3AI != null) {
            alphaFold3AI.setChessGameValidator(this);
        }
        
        // MCTS AI - needs to be initialized after others for references
        if (mctsEnabled) {
            mctsAI = new MonteCarloTreeSearchAI(logger.isDebugEnabled());
            if (qLearningAI != null && deepLearningAI != null && dqnAI != null) {
                mctsAI.setAIReferences(qLearningAI, deepLearningAI, dqnAI);
            }
            logger.info("MCTS AI: ENABLED");
        } else {
            mctsAI = null;
            logger.info("MCTS AI: DISABLED");
        }
        
        // Count enabled systems
        int enabledCount = 0;
        if (qLearningAI != null) enabledCount++;
        if (deepLearningAI != null) enabledCount++;
        if (deepLearningCNNAI != null) enabledCount++;
        if (dqnAI != null) enabledCount++;
        if (mctsAI != null) enabledCount++;
        if (alphaZeroAI != null) enabledCount++;
        if (negamaxAI != null) enabledCount++;
        if (openAiAI != null) enabledCount++;
        if (leelaZeroAI != null) enabledCount++;
        if (geneticAI != null) enabledCount++;
        if (alphaFold3AI != null) enabledCount++;
        
        // FALLBACK: If no AI systems are enabled, force enable LeelaChessZero as default
        if (enabledCount == 0) {
            logger.warn("*** NO AI SYSTEMS ENABLED - FORCING LEELACHESSZERO AS FALLBACK ***");
            try {
                leelaZeroAI = new LeelaChessZeroAI(logger.isDebugEnabled());
                enabledCount = 1;
                logger.info("LeelaChessZero AI: FORCE ENABLED (fallback when all AIs disabled)");
                logger.info("LeelaZero: Opening book initialized (fallback mode)");
            } catch (Exception e) {
                logger.error("LeelaChessZero AI: FALLBACK INITIALIZATION FAILED - {}", e.getMessage());
                logger.error("*** CRITICAL: NO AI SYSTEMS AVAILABLE - GAME MAY NOT FUNCTION ***");
            }
        }
        
        logger.info("CHESS GAME: {}/11 AI systems enabled", enabledCount);
        logger.info("*** PARALLEL AI INITIALIZATION COMPLETE ***");
        
        synchronized (aiInitLock) {
            aiSystemsReady = true;
            aiInitLock.notifyAll(); // Wake up any waiting threads
            logger.info("AI SYSTEMS READY - Race condition protection enabled");
        }
    }
    
    /**
     * Initialize optional AI systems for constructor 2 (testing - use defaults)
     */
    private void initializeOptionalAISystems() {
        int enabledCount = 1; // Q-Learning always enabled
        if (deepLearningAI != null) enabledCount++;
        if (deepLearningCNNAI != null) enabledCount++;
        if (dqnAI != null) enabledCount++;
        
        // MCTS AI (default enabled for constructor 2)
        mctsAI = new MonteCarloTreeSearchAI(false);
        if (qLearningAI != null && deepLearningAI != null && dqnAI != null) {
            mctsAI.setAIReferences(qLearningAI, deepLearningAI, dqnAI);
        }
        enabledCount++;
        
        // AlphaZero AI (default enabled for constructor 2)
        try {
            alphaZeroAI = AlphaZeroFactory.createAlphaZeroAI(false);
            enabledCount++;
            logger.info("AlphaZero AI: ENABLED (testing mode)");
        } catch (Exception e) {
            logger.error("AlphaZero AI: INITIALIZATION FAILED - {}", e.getMessage());
            alphaZeroAI = null;
        }
        
        // Negamax AI (default enabled for constructor 2)
        negamaxAI = new NegamaxAI(false);
        enabledCount++;
        
        logger.info("CHESS GAME: {}/9 AI systems enabled", enabledCount);
    }
    
    private void initializeBoard() {
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = "";
            }
        }
        
        board[7] = new String[]{"♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"};
        board[6] = new String[]{"♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"};
        
        board[0] = new String[]{"♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"};
        board[1] = new String[]{"♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"};
    }

    /**
     * Executes a chess move from one square to another
     * 
     * @param fromRow Source row (0-7)
     * @param fromCol Source column (0-7) 
     * @param toRow Destination row (0-7)
     * @param toCol Destination column (0-7)
     * @return true if move was executed successfully, false otherwise
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (gameOver || !isValidMove(fromRow, fromCol, toRow, toCol)) return false;
        
        saveGameState();
        
        String piece = board[fromRow][fromCol];
        String capturedPiece = board[toRow][toCol];
        
        // Check if King was captured - GAME OVER
        if (!capturedPiece.isEmpty()) {
            if ("♔".equals(capturedPiece)) {
                logger.info("WHITE KING CAPTURED - BLACK WINS!");
                gameOver = true;
            } else if ("♚".equals(capturedPiece)) {
                logger.info("BLACK KING CAPTURED - WHITE WINS!");
                gameOver = true;
            }
        }
        
        String moveNotation = formatMoveNotation(piece, fromRow, fromCol, toRow, toCol, capturedPiece);
        logger.info("MOVE {}: {}", (moveHistory.size() + 1), moveNotation);
        
        // Handle castling moves
        if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(toCol - fromCol) == 2) {
            if ("♔".equals(piece)) {
                if (toCol == 6) {
                    board[7][5] = board[7][7];
                    board[7][7] = "";
                } else if (toCol == 2) {
                    board[7][3] = board[7][0];
                    board[7][0] = "";
                }
            } else {
                if (toCol == 6) {
                    board[0][5] = board[0][7];
                    board[0][7] = "";
                } else if (toCol == 2) {
                    board[0][3] = board[0][0];
                    board[0][0] = "";
                }
            }
        }
        
        // Check if this move would cause pawn promotion
        if (isPawnPromotion(piece, toRow)) {
            // Automatically promote to Queen
            if ("♙".equals(piece)) {
                board[toRow][toCol] = "♕"; // White pawn promotes to Queen
            } else if ("♟".equals(piece)) {
                board[toRow][toCol] = "♛"; // Black pawn promotes to Queen
            }
            logger.info("PAWN PROMOTION: {} automatically promoted to Queen at [{},{}]", piece, toRow, toCol);
        } else {
            board[toRow][toCol] = piece;
        }
        board[fromRow][fromCol] = "";
        
        moveHistory.add(fromRow + "," + fromCol + "," + toRow + "," + toCol);
        updateCastlingRights(piece, fromRow, fromCol);
        stateChanged = true; // Mark state as changed when move is made
        
        // Track move in opening book for continuity
        if (leelaOpeningBook != null) {
            String algebraicMove = formatMoveToAlgebraic(new int[]{fromRow, fromCol, toRow, toCol});
            leelaOpeningBook.addMoveToHistory(algebraicMove);
            logger.debug("Added user move to opening book history: {}", algebraicMove);
        }
        
        // Clear AI move tracking when user makes a move
        aiLastMove = null;
        
        whiteTurn = !whiteTurn;
        
        // Don't start AI move if game is over
        if (!gameOver && !whiteTurn) {
            new Thread(this::makeComputerMove).start();
        }
        return true;
    }

    /**
     * Validates if a chess move is legal according to official chess rules
     * 
     * This is the core move validation method that ensures all moves comply with:
     * - Coordinate bounds (0-7 for both row and column)
     * - Piece ownership (correct player's turn)
     * - Piece-specific movement rules (pawn, rook, bishop, queen, king, knight)
     * - Path blocking validation (for sliding pieces: rook, bishop, queen)
     * - King safety (move doesn't expose own king to check or discovered check)
     * - Special moves (castling, en passant)
     * 
     * Critical enhancement: Added comprehensive King safety checking that simulates
     * each move to ensure the King remains safe after the move is made.
     * 
     * @param fromRow Source row (0-7)
     * @param fromCol Source column (0-7)
     * @param toRow Destination row (0-7) 
     * @param toCol Destination column (0-7)
     * @return true if move is legal, false otherwise
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (gameOver) {
            return false;
        }
        
        try {
            return ruleValidator.isValidMove(board, fromRow, fromCol, toRow, toCol, whiteTurn);
        } catch (Exception e) {
            logger.error("Error in isValidMove: {}", e.getMessage());
            return false;
        }
    }



    private synchronized void makeComputerMove() {
        logger.debug("=== makeComputerMove() ENTRY - gameOver={}, whiteTurn={} ===", gameOver, whiteTurn);
        if (gameOver) {
            logger.info("GAME IS OVER - AI CANNOT MOVE");
            return;
        }
        
        // CRITICAL FIX: Skip any duplicate opening book calls - let findBestMove() handle everything
        logger.debug("FORCING DIRECT CALL TO findBestMove() - No early returns allowed");
        
        // Wait for AI systems to be ready
        synchronized (aiInitLock) {
            if (!aiSystemsReady) {
                logger.info("WAITING FOR AI SYSTEMS TO INITIALIZE");
                try {
                    aiInitLock.wait(5000); // Wait up to 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!aiSystemsReady) {
                    logger.warn("AI SYSTEMS NOT READY - USING FALLBACK");
                }
            }
        }
        
        // CRITICAL FIX: Jump directly to findBestMove() - skip any duplicate opening book calls
        logger.debug("BYPASSING ANY DUPLICATE OPENING BOOK CALLS - Going directly to findBestMove()");
        try {
            saveGameState();
            Thread.sleep(1000 + random.nextInt(1000));
            
            logger.debug("ABOUT TO CALL findBestMove() - This should appear for BOTH moves");
            int[] bestMove = findBestMove();
            logger.info("findBestMove() returned: {}", bestMove != null ? java.util.Arrays.toString(bestMove) : "NULL");
            
            if (bestMove != null && bestMove.length == 4) {
                // Execute the move (rest of the method continues as normal)
                executeBestMove(bestMove);
            } else {
                logger.error("ERROR: No valid AI move found!");
                // Broadcast game state even when no move is found (checkmate/stalemate)
                broadcastGameState();
            }
        } catch (Exception e) {
            logger.error("ERROR in makeComputerMove: " + e.getMessage());
            e.printStackTrace();
        }
        
        printBoardState("AFTER AI MOVE");
        logger.info("=== AI MOVE END ===");
        return; // Exit early to prevent duplicate execution
    }
    
    private void executeBestMove(int[] bestMove) {
        try {
            // Final validation before making the move
            if (!isValidMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3])) {
                logger.error("ERROR: AI selected invalid move! [" + bestMove[0] + "," + bestMove[1] + "," + bestMove[2] + "," + bestMove[3] + "]");
                return;
            }
            
            // CRITICAL: Check if move leaves King in check BEFORE making the move
            if (!wouldMoveResolveCheck(bestMove)) {
                logger.info("ILLEGAL MOVE BLOCKED: Move would leave King in check!");
                return;
            }
            
            String piece = board[bestMove[0]][bestMove[1]];
            String capturedPiece = board[bestMove[2]][bestMove[3]];
            
            logger.info("AI SELECTED MOVE: " + piece + " from [" + bestMove[0] + "," + bestMove[1] + "] to [" + bestMove[2] + "," + bestMove[3] + "]");
            
            // Handle castling
            if (("♔".equals(piece) || "♚".equals(piece)) && Math.abs(bestMove[3] - bestMove[1]) == 2) {
                logger.info("CASTLING DETECTED");
                if ("♚".equals(piece)) {
                    if (bestMove[3] == 6) {
                        board[0][5] = board[0][7];
                        board[0][7] = "";
                    } else if (bestMove[3] == 2) {
                        board[0][3] = board[0][0];
                        board[0][0] = "";
                    }
                }
            }
            
            // Check for King capture - GAME OVER
            if (!capturedPiece.isEmpty()) {
                logger.info("CAPTURING: " + capturedPiece + " at [" + bestMove[2] + "," + bestMove[3] + "]");
                if ("♔".equals(capturedPiece)) {
                    logger.info("*** WHITE KING CAPTURED - BLACK WINS! ***");
                    gameOver = true;
                } else if ("♚".equals(capturedPiece)) {
                    logger.info("*** BLACK KING CAPTURED - WHITE WINS! ***");
                    gameOver = true;
                }
            }
            
            // Execute the move
            board[bestMove[2]][bestMove[3]] = piece;
            board[bestMove[0]][bestMove[1]] = "";
            
            // Handle pawn promotion
            if (handlePawnPromotion(piece, bestMove[2], bestMove[3])) {
                logger.info("AI PAWN PROMOTED TO QUEEN at [" + bestMove[2] + "," + bestMove[3] + "]");
            }
            
            // Update game state
            moveHistory.add(bestMove[0] + "," + bestMove[1] + "," + bestMove[2] + "," + bestMove[3]);
            updateCastlingRights(piece, bestMove[0], bestMove[1]);
            
            // Track AI move in opening book
            if (leelaOpeningBook != null) {
                String algebraicMove = formatMoveToAlgebraic(bestMove);
                leelaOpeningBook.addMoveToHistory(algebraicMove);
            }
            
            whiteTurn = true;
            
            // Store AI's last move for frontend blinking
            aiLastMove = new int[]{bestMove[0], bestMove[1], bestMove[2], bestMove[3]};
            
            // Broadcast AI move via WebSocket
            broadcastGameState();
            
        } catch (Exception e) {
            logger.error("ERROR in executeBestMove: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get opening move from Leela Chess Zero opening book
     * Used during user vs AI games
     */
    private int[] getOpeningMove() {
        int currentMove = ((moveHistory.size() + 1) / 2);
        logger.debug("=== OPENING BOOK CHECK: Move {} (moveHistory.size()={}) ===", currentMove, moveHistory.size());
        
        if (moveHistory.size() % 2 == 1 && moveHistory.size() <= 20) { // AI's turn (Black), still in opening phase
            logger.debug("Opening book: AI's turn, checking for book move...");
            try {
                List<int[]> validMoves = getAllValidMoves(false);
                logger.debug("Opening book: Found {} valid moves for AI", validMoves.size());
                
                if (leelaOpeningBook == null) {
                    logger.error("Opening book is NULL - cannot get opening move");
                    return null;
                }
                
                logger.debug("About to call leelaOpeningBook.getOpeningMove()...");
                LeelaChessZeroOpeningBook.OpeningMoveResult result = leelaOpeningBook.getOpeningMove(board, validMoves, ruleValidator, whiteTurn);
                logger.debug("leelaOpeningBook.getOpeningMove() returned: {}", result != null ? "Result object" : "NULL");
                
                if (result != null && result.move != null && isValidMove(result.move[0], result.move[1], result.move[2], result.move[3])) {
                    logger.info("LEELA OPENING: {} - Move from grandmaster database (move {})", result.openingName, currentMove);
                    return result.move;
                } else {
                    logger.debug("LeelaZero Opening Book: No book move found for current position (move {})", currentMove);
                    if (result != null) {
                        logger.debug("Result details: move={}, openingName={}", result.move != null ? java.util.Arrays.toString(result.move) : "NULL", result.openingName);
                    }
                }
            } catch (Exception e) {
                logger.error("*** LeelaZero Opening Book error: {} ***", e.getMessage());
                e.printStackTrace();
            }
        } else {
            logger.debug("LeelaZero Opening Book: Outside opening phase (move {})", currentMove);
        }
        
        return null;
    }
    
    private int[] findBestMove() {
        ensureAISystemsInitialized(); // Initialize AIs if not done yet
        saveGameState();
        
        // CHECK FOR OPENING BOOK MOVE
        int[] openingMove = getOpeningMove();
        if (openingMove != null) {
            logger.info("=== OPENING BOOK MOVE SELECTED ===");
            return openingMove;
        }
        
        // PRIORITY 1: MANDATORY - Handle check (legal chess requirement)
        logger.debug("=== PRIORITY 1: CHECK DETECTION ===");
        boolean kingInDanger = isKingInDanger(false);
        logger.debug("PRIORITY 1 CHECK: Is Black King in danger? {}", kingInDanger);
        if (kingInDanger) {
            logger.info("*** BLACK KING IN CHECK *** - Finding ALL legal responses!");
            
            // Find ALL legal responses to check (not just King moves)
            int[] checkResponse = findLegalCheckResponse();
            if (checkResponse != null) {
                String piece = board[checkResponse[0]][checkResponse[1]];
                logger.info("CHECK RESPONSE: " + piece + " [" + checkResponse[0] + "," + checkResponse[1] + "] to [" + checkResponse[2] + "," + checkResponse[3] + "]");
                return checkResponse;
            }
            
            // If no legal response found, it's checkmate
            logger.info("*** CHECKMATE - NO LEGAL MOVES TO ESCAPE CHECK ***");
            logger.info("*** WHITE WINS! ***");
            logger.info("*** GAME OVER ***");
            gameOver = true;
            broadcastGameState();
            return null;
        }
        
        // PRIORITY 2: CRITICAL - Defend Queen if under attack (but respect single AI selection)
        logger.debug("=== PRIORITY 2: QUEEN DEFENSE ===");
        int[] queenPos = findPiecePosition("♛");
        boolean queenUnderAttack = queenPos != null && isSquareUnderAttack(queenPos[0], queenPos[1], true);
        logger.debug("PRIORITY 2 CHECK: Queen position: {}, under attack: {}", 
            queenPos != null ? java.util.Arrays.toString(queenPos) : "NULL", queenUnderAttack);
        
        // Store critical defense move but don't return immediately - let single AI selection handle it
        int[] criticalDefenseMove = null;
        if (queenUnderAttack) {
            logger.info("*** CRITICAL: BLACK QUEEN UNDER ATTACK at [{},{}] ***", queenPos[0], queenPos[1]);
            
            // Check if Queen is pinned (can't move without exposing King)
            if (isPiecePinned(queenPos[0], queenPos[1])) {
                logger.info("*** QUEEN IS PINNED - Cannot move without exposing King! ***");
                
                // PRIORITY 1: Queen sacrifice - better to capture something valuable than lose Queen for nothing
                List<int[]> allValidMoves = getAllValidMoves(false);
                List<int[]> attackers = findAttackersOfSquare(queenPos[0], queenPos[1], true);
                
                // Check if Queen can capture something valuable even while pinned
                for (int[] move : allValidMoves) {
                    String piece = board[move[0]][move[1]];
                    if ("♛".equals(piece) && move[0] == queenPos[0] && move[1] == queenPos[1]) {
                        String captured = board[move[2]][move[3]];
                        if (!captured.isEmpty()) {
                            double capturedValue = getChessPieceValue(captured);
                            // Allow Queen sacrifice if capturing valuable piece (Rook, Bishop, Knight+)
                            if (capturedValue >= 300) {
                                logger.info("*** QUEEN SACRIFICE: Pinned Queen captures {} ({}) - better than losing Queen for nothing! ***", captured, capturedValue);
                                criticalDefenseMove = move;
                                break;
                            }
                        }
                    }
                }
                
                // PRIORITY 2: Try to capture attackers with other pieces
                if (criticalDefenseMove == null) {
                    for (int[] attacker : attackers) {
                        for (int[] move : allValidMoves) {
                            String piece = board[move[0]][move[1]];
                            if (!"♛".equals(piece) && move[2] == attacker[0] && move[3] == attacker[1]) {
                                if (isValidMove(move[0], move[1], move[2], move[3])) {
                                    String attackerPiece = board[attacker[0]][attacker[1]];
                                    System.out.println("PINNED QUEEN DEFENSE: " + piece + " captures " + attackerPiece + " threatening pinned Queen");
                                    criticalDefenseMove = move;
                                    break;
                                }
                            }
                        }
                        if (criticalDefenseMove != null) break;
                    }
                }
                
                // PRIORITY 3: If can't save Queen, make best alternative move
                if (criticalDefenseMove == null) {
                    logger.info("*** QUEEN LOST - Making best alternative move ***");
                }
            } else {
                // Queen not pinned - PRIORITIZE capturing attacking pieces first
                List<int[]> allValidMoves = getAllValidMoves(false);
                List<int[]> attackers = findAttackersOfSquare(queenPos[0], queenPos[1], true);
                
                // PRIORITY 1: Only capture attackers if it's a good trade or Queen has no safe escape
                for (int[] attacker : attackers) {
                    String attackerPiece = board[attacker[0]][attacker[1]];
                    double attackerValue = getChessPieceValue(attackerPiece);
                    
                    // Only capture if attacker is valuable (300+ points) or Queen has no escape
                    if (attackerValue >= 300) {
                        for (int[] move : allValidMoves) {
                            String piece = board[move[0]][move[1]];
                            if (move[2] == attacker[0] && move[3] == attacker[1]) {
                                if (isValidMove(move[0], move[1], move[2], move[3])) {
                                    System.out.println("CAPTURE VALUABLE ATTACKER: " + piece + " captures " + attackerPiece + " (" + attackerValue + ") threatening Queen");
                                    criticalDefenseMove = move;
                                    break;
                                }
                            }
                        }
                        if (criticalDefenseMove != null) break;
                    }
                }
                
                // PRIORITY 2: Queen escape move - PREFERRED over capturing low-value attackers
                if (criticalDefenseMove == null) {
                    for (int[] move : allValidMoves) {
                        String piece = board[move[0]][move[1]];
                        if ("♛".equals(piece) && move[0] == queenPos[0] && move[1] == queenPos[1]) {
                            if (isValidMove(move[0], move[1], move[2], move[3])) {
                                // CRITICAL: Check if Queen will be safe at destination
                                String captured = board[move[2]][move[3]];
                                board[move[2]][move[3]] = piece;
                                board[move[0]][move[1]] = "";
                                
                                boolean queenSafeAtDestination = !isSquareUnderAttack(move[2], move[3], true);
                                
                                // Restore board
                                board[move[0]][move[1]] = piece;
                                board[move[2]][move[3]] = captured;
                                
                                if (queenSafeAtDestination) {
                                    System.out.println("QUEEN ESCAPE: Moving Queen to safety [" + move[2] + "," + move[3] + "]");
                                    criticalDefenseMove = move;
                                    break;
                                } else {
                                    System.out.println("QUEEN ESCAPE REJECTED: [" + move[2] + "," + move[3] + "] still under attack");
                                }
                            }
                        }
                    }
                }
                
                // PRIORITY 3: Capture low-value attackers only if Queen has no safe escape
                if (criticalDefenseMove == null) {
                    boolean queenHasSafeEscape = false;
                    for (int[] move : allValidMoves) {
                        String piece = board[move[0]][move[1]];
                        if ("♛".equals(piece) && move[0] == queenPos[0] && move[1] == queenPos[1]) {
                            if (isValidMove(move[0], move[1], move[2], move[3])) {
                                String captured = board[move[2]][move[3]];
                                board[move[2]][move[3]] = piece;
                                board[move[0]][move[1]] = "";
                                
                                boolean safeAtDestination = !isSquareUnderAttack(move[2], move[3], true);
                                
                                board[move[0]][move[1]] = piece;
                                board[move[2]][move[3]] = captured;
                                
                                if (safeAtDestination) {
                                    queenHasSafeEscape = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Only capture low-value attackers if Queen has no escape
                    if (!queenHasSafeEscape) {
                        for (int[] attacker : attackers) {
                            for (int[] move : allValidMoves) {
                                String piece = board[move[0]][move[1]];
                                if (move[2] == attacker[0] && move[3] == attacker[1]) {
                                    if (isValidMove(move[0], move[1], move[2], move[3])) {
                                        String attackerPiece = board[attacker[0]][attacker[1]];
                                        System.out.println("DESPERATE CAPTURE: " + piece + " captures " + attackerPiece + " - Queen has no escape");
                                        criticalDefenseMove = move;
                                        break;
                                    }
                                }
                            }
                            if (criticalDefenseMove != null) break;
                        }
                    }
                }
            }
        }
        
        // PRIORITY 2.5: CRITICAL - Detect and respond to fork attacks
        logger.debug("=== PRIORITY 2.5: FORK DETECTION ===");
        List<int[]> allValidMoves = getAllValidMoves(false);
        int[] forkResponse = ChessTacticalDefense.defendAgainstForks(board, allValidMoves);
        if (forkResponse != null) {
            logger.info("*** FORK DETECTED - Responding with defensive move ***");
            return forkResponse;
        }
        
        // PRIORITY 2.6: CRITICAL - Check for pieces defending against checkmate (PINNED PIECES)
        logger.debug("=== PRIORITY 2.6: CRITICAL DEFENSE PIN CHECK ===");
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && "♚♛♜♝♞♟".contains(piece)) {
                    // Check if this piece is defending against checkmate
                    if (ChessTacticalDefense.wouldRemoveCriticalDefense(board, new int[]{i, j, i, j})) {
                        logger.info("*** CRITICAL DEFENSE PIECE DETECTED: {} at [{},{}] is defending against checkmate - CANNOT MOVE ***", piece, i, j);
                        // This piece is pinned due to checkmate defense - filter it out of AI moves
                    }
                }
            }
        }
        
        // PRIORITY 2.7: CRITICAL - Defend any valuable piece under immediate capture threat
        logger.debug("=== PRIORITY 2.7: VALUABLE PIECE DEFENSE ===");
        logger.debug("PRIORITY 2.7 CHECK: Checking for threatened valuable pieces...");
        String[] valuablePieces = {"♜", "♝", "♞"}; // Rook, Bishop, Knight
        boolean valuablePieceThreats = false;
        for (String pieceType : valuablePieces) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (pieceType.equals(board[i][j]) && isSquareUnderAttack(i, j, true)) {
                        logger.debug("*** CRITICAL: {} UNDER IMMEDIATE CAPTURE THREAT at [{},{}] ***", pieceType, i, j);
                        valuablePieceThreats = true;
                        
                        int[] defense = findPieceProtectionMove(new int[]{i, j}, pieceType);
                        if (defense != null) {
                            logger.debug("*** PIECE DEFENSE PRIORITY: Defending {} before other considerations ***", pieceType);
                            return defense;
                        } else {
                            logger.debug("*** WARNING: Cannot defend {} - piece will be lost ***", pieceType);
                        }
                    }
                }
            }
        }
        logger.debug("PRIORITY 2.7 RESULT: Valuable piece threats detected: {}", valuablePieceThreats);
        
        // PRIORITY 2.8: CENTRALIZED TACTICAL DEFENSE - Check once for all AIs
        logger.debug("=== PRIORITY 2.8: CENTRALIZED TACTICAL DEFENSE ===");
//        List<int[]> allValidMoves = getAllValidMoves(false);
        
        // CRITICAL: Filter out moves that would remove critical defenses
        List<int[]> safeValidMoves = new ArrayList<>();
        for (int[] move : allValidMoves) {
            if (!ChessTacticalDefense.wouldRemoveCriticalDefense(board, move)) {
                safeValidMoves.add(move);
            } else {
                String piece = board[move[0]][move[1]];
                logger.info("*** FILTERED CRITICAL DEFENSE MOVE: {} from [{},{}] to [{},{}] - would remove checkmate defense ***", 
                    piece, move[0], move[1], move[2], move[3]);
            }
        }
        
        // Use filtered moves if any remain, otherwise use all moves (emergency)
        List<int[]> movesToUse = safeValidMoves.isEmpty() ? allValidMoves : safeValidMoves;
        logger.info("*** CRITICAL DEFENSE FILTER: {} moves filtered, {} moves remaining ***", 
            allValidMoves.size() - safeValidMoves.size(), movesToUse.size());
        
        int[] tacticalDefense = ChessTacticalDefense.findBestDefensiveMove(board, movesToUse, "TACTICAL_DEFENSE");
        if (tacticalDefense != null && criticalDefenseMove == null) {
            logger.info("*** CENTRALIZED TACTICAL DEFENSE: Critical threat detected - storing for AI evaluation ***");
            criticalDefenseMove = tacticalDefense;
        }
        
        // PRIORITY 3: STRATEGIC - Let AI choose from safe moves
        logger.debug("=== PRIORITY 3: STRATEGIC MOVE SELECTION ===");
        logger.debug("PRIORITY 3: Reached strategic move selection phase");
        List<int[]> safeMoves = filterSafeMoves(allValidMoves);
        logger.debug("PRIORITY 3: Found {} total valid moves, {} safe moves", allValidMoves.size(), safeMoves.size());
        
        // Delegate to AI for strategic decision - use filtered moves that don't remove critical defenses
        List<int[]> movesToEvaluate = safeValidMoves.isEmpty() ? (safeMoves.isEmpty() ? allValidMoves : safeMoves) : safeValidMoves;
        logger.info("*** MOVE EVALUATION: Using {} moves (after critical defense filtering) ***", movesToEvaluate.size());
        
        // OPTIMIZED: Only evaluate the selected AI (unless critical defense needed)
        logger.debug("*** OPTIMIZED AI EXECUTION: Only evaluating selected AI '{}' ***", selectedAIForGame);
        long parallelStartTime = System.currentTimeMillis();
        
        // Initialize all tasks as null
        java.util.concurrent.CompletableFuture<int[]> qLearningTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> deepLearningTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> deepLearningCNNTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> dqnTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> mctsTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> alphaZeroTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> negamaxTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> openAiTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> leelaZeroTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> geneticTask = java.util.concurrent.CompletableFuture.completedFuture(null);
        java.util.concurrent.CompletableFuture<int[]> alphaFold3Task = java.util.concurrent.CompletableFuture.completedFuture(null);
        
        // Only evaluate the selected AI (unless critical defense override needed)
        if (criticalDefenseMove == null && selectedAIForGame != null && !"None".equals(selectedAIForGame)) {
            switch (selectedAIForGame) {
                case "QLearning":
                    if (isQLearningEnabled()) {
                        qLearningTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return qLearningAI.selectMove(board, movesToEvaluate, false);
                            } catch (Exception e) {
                                System.err.println("Q-Learning error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "DeepLearning":
                    if (isDeepLearningEnabled()) {
                        deepLearningTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return deepLearningAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("Deep Learning error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "DeepLearningCNN":
                    if (isDeepLearningCNNEnabled()) {
                        deepLearningCNNTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return deepLearningCNNAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("CNN Deep Learning error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "DQN":
                    if (isDQNEnabled()) {
                        dqnTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return dqnAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("DQN error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "MCTS":
                    if (isMCTSEnabled()) {
                        mctsTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return mctsAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("MCTS error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "AlphaZero":
                    if (isAlphaZeroEnabled()) {
                        alphaZeroTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return alphaZeroAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("AlphaZero error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "Negamax":
                    if (isNegamaxEnabled()) {
                        negamaxTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return negamaxAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("Negamax error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "OpenAI":
                    if (isOpenAiEnabled()) {
                        openAiTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return openAiAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("OpenAI error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "LeelaZero":
                    if (isLeelaZeroEnabled()) {
                        leelaZeroTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return leelaZeroAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("LeelaZero error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "Genetic":
                    if (isGeneticEnabled()) {
                        geneticTask = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return geneticAI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("Genetic error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
                case "AlphaFold3":
                    if (isAlphaFold3Enabled()) {
                        alphaFold3Task = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return alphaFold3AI.selectMove(board, movesToEvaluate);
                            } catch (Exception e) {
                                System.err.println("AlphaFold3 error: " + e.getMessage());
                                return null;
                            }
                        });
                    }
                    break;
            }
        } else {
            // Fallback: evaluate all AIs if no specific AI selected or critical defense needed
            logger.debug("*** FALLBACK: Evaluating all AIs (no specific selection or critical defense) ***");
            
            qLearningTask = isQLearningEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return qLearningAI.selectMove(board, movesToEvaluate, false);
                    } catch (Exception e) {
                        System.err.println("Q-Learning error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            deepLearningTask = isDeepLearningEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return deepLearningAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("Deep Learning error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            deepLearningCNNTask = isDeepLearningCNNEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return deepLearningCNNAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("CNN Deep Learning error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            dqnTask = isDQNEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return dqnAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("DQN error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            mctsTask = isMCTSEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return mctsAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("MCTS error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            alphaZeroTask = isAlphaZeroEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return alphaZeroAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("AlphaZero error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            negamaxTask = isNegamaxEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return negamaxAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("Negamax error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            openAiTask = isOpenAiEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return openAiAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("OpenAI error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            leelaZeroTask = isLeelaZeroEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return leelaZeroAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("LeelaZero error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            geneticTask = isGeneticEnabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return geneticAI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("Genetic error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            alphaFold3Task = isAlphaFold3Enabled() ? 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return alphaFold3AI.selectMove(board, movesToEvaluate);
                    } catch (Exception e) {
                        System.err.println("AlphaFold3 error: " + e.getMessage());
                        return null;
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        
        // Wait for all AIs to complete with timeout
        java.util.concurrent.CompletableFuture<Void> allTasks = java.util.concurrent.CompletableFuture.allOf(
            qLearningTask, deepLearningTask, deepLearningCNNTask, dqnTask, mctsTask, alphaZeroTask, negamaxTask, openAiTask, leelaZeroTask, geneticTask, alphaFold3Task);
        
        int[] qLearningMove = null;
        int[] deepLearningMove = null;
        int[] deepLearningCNNMove = null;
        int[] dqnMove = null;
        int[] mctsMove = null;
        int[] alphaZeroMove = null;
        int[] negamaxMove = null;
        int[] openAiMove = null;
        int[] leelaZeroMove = null;
        int[] geneticMove = null;
        int[] alphaFold3Move = null;
        
        // Wait for each AI individually with optimized timeouts
        try {
            qLearningMove = qLearningTask.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Q-Learning timeout/error: " + e.getMessage());
            qLearningTask.cancel(true);
        }
        
        try {
            deepLearningMove = deepLearningTask.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Deep Learning timeout/error: " + e.getMessage());
            deepLearningTask.cancel(true);
        }
        
        try {
            deepLearningCNNMove = deepLearningCNNTask.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("CNN Deep Learning timeout/error: " + e.getMessage());
            deepLearningCNNTask.cancel(true);
        }
        
        try {
            dqnMove = dqnTask.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("DQN timeout/error: " + e.getMessage());
            dqnTask.cancel(true);
        }
        
        try {
            mctsMove = mctsTask.get(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("MCTS timeout/error: " + e.getMessage());
            mctsTask.cancel(true);
            if (mctsAI != null) mctsAI.stopThinking();
        }
        
        try {
            alphaZeroMove = alphaZeroTask.get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("AlphaZero timeout/error: " + e.getMessage());
            alphaZeroTask.cancel(true);
            if (alphaZeroAI != null) alphaZeroAI.stopThinking();
        }
        
        try {
            negamaxMove = negamaxTask.get(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Negamax timeout/error: " + e.getMessage());
            negamaxTask.cancel(true);
        }
        
        try {
            openAiMove = openAiTask.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("OpenAI timeout/error: " + e.getMessage());
            openAiTask.cancel(true);
        }
        
        try {
            leelaZeroMove = leelaZeroTask.get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("LeelaZero timeout/error: " + e.getMessage());
            leelaZeroTask.cancel(true);
        }
        
        try {
            geneticMove = geneticTask.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Genetic timeout/error: " + e.getMessage());
            geneticTask.cancel(true);
        }
        
        try {
            alphaFold3Move = alphaFold3Task.get(4, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("AlphaFold3 timeout/error: " + e.getMessage());
            alphaFold3Task.cancel(true);
        }
        
        long parallelTime = System.currentTimeMillis() - parallelStartTime;
        logger.debug("*** PARALLEL AI EXECUTION: Completed in " + parallelTime + "ms (" + 
            String.format("%.1f", parallelTime/1000.0) + "s) ***");
        
        // OPTIMIZED LOGGING: Only log the selected AI's result (unless fallback mode)
        if (criticalDefenseMove == null && selectedAIForGame != null && !"None".equals(selectedAIForGame)) {
            logger.info("=== SELECTED AI RESULT ===");
            
            switch (selectedAIForGame) {
                case "QLearning":
                    if (qLearningMove != null) {
                        String piece = board[qLearningMove[0]][qLearningMove[1]];
                        logger.info("Q-Learning: " + piece + " [" + qLearningMove[0] + "," + qLearningMove[1] + "] → [" + qLearningMove[2] + "," + qLearningMove[3] + "]");
                    } else {
                        logger.info("Q-Learning: No move");
                    }
                    break;
                case "DeepLearning":
                    if (deepLearningMove != null) {
                        String piece = board[deepLearningMove[0]][deepLearningMove[1]];
                        logger.info("Deep Learning: " + piece + " [" + deepLearningMove[0] + "," + deepLearningMove[1] + "] → [" + deepLearningMove[2] + "," + deepLearningMove[3] + "]");
                    } else {
                        logger.info("Deep Learning: No move");
                    }
                    break;
                case "DeepLearningCNN":
                    if (deepLearningCNNMove != null) {
                        String piece = board[deepLearningCNNMove[0]][deepLearningCNNMove[1]];
                        logger.info("CNN Deep Learning: " + piece + " [" + deepLearningCNNMove[0] + "," + deepLearningCNNMove[1] + "] → [" + deepLearningCNNMove[2] + "," + deepLearningCNNMove[3] + "]");
                    } else {
                        logger.info("CNN Deep Learning: No move");
                    }
                    break;
                case "DQN":
                    if (dqnMove != null) {
                        String piece = board[dqnMove[0]][dqnMove[1]];
                        logger.info("DQN: " + piece + " [" + dqnMove[0] + "," + dqnMove[1] + "] → [" + dqnMove[2] + "," + dqnMove[3] + "]");
                    } else {
                        logger.info("DQN: No move");
                    }
                    break;
                case "MCTS":
                    if (mctsMove != null) {
                        String piece = board[mctsMove[0]][mctsMove[1]];
                        logger.info("MCTS: " + piece + " [" + mctsMove[0] + "," + mctsMove[1] + "] → [" + mctsMove[2] + "," + mctsMove[3] + "]");
                    } else {
                        logger.info("MCTS: No move");
                    }
                    break;
                case "AlphaZero":
                    if (alphaZeroMove != null) {
                        String piece = board[alphaZeroMove[0]][alphaZeroMove[1]];
                        logger.info("AlphaZero: " + piece + " [" + alphaZeroMove[0] + "," + alphaZeroMove[1] + "] → [" + alphaZeroMove[2] + "," + alphaZeroMove[3] + "]");
                    } else {
                        logger.info("AlphaZero: No move");
                    }
                    break;
                case "Negamax":
                    if (negamaxMove != null) {
                        String piece = board[negamaxMove[0]][negamaxMove[1]];
                        logger.info("Negamax: " + piece + " [" + negamaxMove[0] + "," + negamaxMove[1] + "] → [" + negamaxMove[2] + "," + negamaxMove[3] + "]");
                    } else {
                        logger.info("Negamax: No move");
                    }
                    break;
                case "OpenAI":
                    if (openAiMove != null) {
                        String piece = board[openAiMove[0]][openAiMove[1]];
                        logger.info("OpenAI: " + piece + " [" + openAiMove[0] + "," + openAiMove[1] + "] → [" + openAiMove[2] + "," + openAiMove[3] + "]");
                    } else {
                        logger.info("OpenAI: No move");
                    }
                    break;
                case "LeelaZero":
                    if (leelaZeroMove != null) {
                        String piece = board[leelaZeroMove[0]][leelaZeroMove[1]];
                        logger.info("LeelaZero: " + piece + " [" + leelaZeroMove[0] + "," + leelaZeroMove[1] + "] → [" + leelaZeroMove[2] + "," + leelaZeroMove[3] + "]");
                    } else {
                        logger.info("LeelaZero: No move");
                    }
                    break;
                case "Genetic":
                    if (geneticMove != null) {
                        String piece = board[geneticMove[0]][geneticMove[1]];
                        logger.info("Genetic: " + piece + " [" + geneticMove[0] + "," + geneticMove[1] + "] → [" + geneticMove[2] + "," + geneticMove[3] + "]");
                    } else {
                        logger.info("Genetic: No move");
                    }
                    break;
                case "AlphaFold3":
                    if (alphaFold3Move != null) {
                        String piece = board[alphaFold3Move[0]][alphaFold3Move[1]];
                        logger.info("AlphaFold3: " + piece + " [" + alphaFold3Move[0] + "," + alphaFold3Move[1] + "] → [" + alphaFold3Move[2] + "," + alphaFold3Move[3] + "]");
                    } else {
                        logger.info("AlphaFold3: No move");
                    }
                    break;
            }
        } else {
            // Fallback mode: log all AI results
            logger.info("=== ALL AI RESULTS (FALLBACK MODE) ===");
            
            if (isQLearningEnabled() && qLearningMove != null) {
                String piece = board[qLearningMove[0]][qLearningMove[1]];
                logger.info("Q-Learning: " + piece + " [" + qLearningMove[0] + "," + qLearningMove[1] + "] → [" + qLearningMove[2] + "," + qLearningMove[3] + "]");
            }
            
            if (isDeepLearningEnabled() && deepLearningMove != null) {
                String piece = board[deepLearningMove[0]][deepLearningMove[1]];
                logger.info("Deep Learning: " + piece + " [" + deepLearningMove[0] + "," + deepLearningMove[1] + "] → [" + deepLearningMove[2] + "," + deepLearningMove[3] + "]");
            }
            
            if (isDeepLearningCNNEnabled() && deepLearningCNNMove != null) {
                String piece = board[deepLearningCNNMove[0]][deepLearningCNNMove[1]];
                logger.info("CNN Deep Learning: " + piece + " [" + deepLearningCNNMove[0] + "," + deepLearningCNNMove[1] + "] → [" + deepLearningCNNMove[2] + "," + deepLearningCNNMove[3] + "]");
            }
            
            if (isDQNEnabled() && dqnMove != null) {
                String piece = board[dqnMove[0]][dqnMove[1]];
                logger.info("DQN: " + piece + " [" + dqnMove[0] + "," + dqnMove[1] + "] → [" + dqnMove[2] + "," + dqnMove[3] + "]");
            }
            
            if (isMCTSEnabled() && mctsMove != null) {
                String piece = board[mctsMove[0]][mctsMove[1]];
                logger.info("MCTS: " + piece + " [" + mctsMove[0] + "," + mctsMove[1] + "] → [" + mctsMove[2] + "," + mctsMove[3] + "]");
            }
            
            if (isAlphaZeroEnabled() && alphaZeroMove != null) {
                String piece = board[alphaZeroMove[0]][alphaZeroMove[1]];
                logger.info("AlphaZero: " + piece + " [" + alphaZeroMove[0] + "," + alphaZeroMove[1] + "] → [" + alphaZeroMove[2] + "," + alphaZeroMove[3] + "]");
            }
            
            if (isNegamaxEnabled() && negamaxMove != null) {
                String piece = board[negamaxMove[0]][negamaxMove[1]];
                logger.info("Negamax: " + piece + " [" + negamaxMove[0] + "," + negamaxMove[1] + "] → [" + negamaxMove[2] + "," + negamaxMove[3] + "]");
            }
            
            if (isOpenAiEnabled() && openAiMove != null) {
                String piece = board[openAiMove[0]][openAiMove[1]];
                logger.info("OpenAI: " + piece + " [" + openAiMove[0] + "," + openAiMove[1] + "] → [" + openAiMove[2] + "," + openAiMove[3] + "]");
            }
            
            if (isLeelaZeroEnabled() && leelaZeroMove != null) {
                String piece = board[leelaZeroMove[0]][leelaZeroMove[1]];
                logger.info("LeelaZero: " + piece + " [" + leelaZeroMove[0] + "," + leelaZeroMove[1] + "] → [" + leelaZeroMove[2] + "," + leelaZeroMove[3] + "]");
            }
            
            if (isGeneticEnabled() && geneticMove != null) {
                String piece = board[geneticMove[0]][geneticMove[1]];
                logger.info("Genetic: " + piece + " [" + geneticMove[0] + "," + geneticMove[1] + "] → [" + geneticMove[2] + "," + geneticMove[3] + "]");
            }
            
            if (isAlphaFold3Enabled() && alphaFold3Move != null) {
                String piece = board[alphaFold3Move[0]][alphaFold3Move[1]];
                logger.info("AlphaFold3: " + piece + " [" + alphaFold3Move[0] + "," + alphaFold3Move[1] + "] → [" + alphaFold3Move[2] + "," + alphaFold3Move[3] + "]");
            }
        }
        
        // COMPREHENSIVE MOVE VALIDATION - Based on 3 days of discoveries
        qLearningMove = isQLearningEnabled() ? validateAIMove(qLearningMove, "Q-Learning") : null;
        deepLearningMove = isDeepLearningEnabled() ? validateAIMove(deepLearningMove, "Deep Learning") : null;
        deepLearningCNNMove = isDeepLearningCNNEnabled() ? validateAIMove(deepLearningCNNMove, "CNN Deep Learning") : null;
        dqnMove = isDQNEnabled() ? validateAIMove(dqnMove, "Deep Q-Network") : null;
        mctsMove = isMCTSEnabled() ? validateAIMove(mctsMove, "MCTS") : null;
        alphaZeroMove = isAlphaZeroEnabled() ? validateAIMove(alphaZeroMove, "AlphaZero") : null;
        negamaxMove = isNegamaxEnabled() ? validateAIMove(negamaxMove, "Negamax") : null;
        openAiMove = isOpenAiEnabled() ? validateAIMove(openAiMove, "OpenAI") : null;
        leelaZeroMove = isLeelaZeroEnabled() ? validateAIMove(leelaZeroMove, "LeelaZero") : null;
        geneticMove = isGeneticEnabled() ? validateAIMove(geneticMove, "Genetic") : null;
        alphaFold3Move = isAlphaFold3Enabled() ? validateAIMove(alphaFold3Move, "AlphaFold3") : null;
        
        // FLIP-FLOP PREVENTION: Filter out repetitive moves (but preserve critical defense moves)
        List<int[]> filteredMoves = new ArrayList<>();
        for (int[] move : movesToEvaluate) {
            if (!isFlipFlopMove(move) || ChessTacticalDefense.wouldRemoveCriticalDefense(board, move)) {
                filteredMoves.add(move);
            } else {
                System.out.println("FLIP-FLOP FILTERED: " + formatMoveKey(move));
            }
        }
        if (filteredMoves.isEmpty()) {
            logger.info("*** ALL MOVES FILTERED - USING ORIGINAL SET ***");
            filteredMoves = movesToEvaluate;
        }
        
        // Use only the selected AI for this game (but override with critical defense if needed)
        int[] bestMove = null;
        String selectedAIName = "None";
        
        // CRITICAL: If we have a critical defense move, use it regardless of AI selection
        if (criticalDefenseMove != null) {
            bestMove = criticalDefenseMove;
            selectedAIName = "Critical Defense Override";
            logger.info("*** CRITICAL DEFENSE OVERRIDE: Using emergency move regardless of selected AI ***");
        } else if (selectedAIForGame != null && !"None".equals(selectedAIForGame)) {
            switch (selectedAIForGame) {
                case "QLearning":
                    if (qLearningMove != null) {
                        bestMove = qLearningMove;
                        selectedAIName = "Q-Learning";
                    }
                    break;
                case "DeepLearning":
                    if (deepLearningMove != null) {
                        bestMove = deepLearningMove;
                        selectedAIName = "Deep Learning";
                    }
                    break;
                case "DeepLearningCNN":
                    if (deepLearningCNNMove != null) {
                        bestMove = deepLearningCNNMove;
                        selectedAIName = "CNN Deep Learning";
                    }
                    break;
                case "DQN":
                    if (dqnMove != null) {
                        bestMove = dqnMove;
                        selectedAIName = "Deep Q-Network";
                    }
                    break;
                case "MCTS":
                    if (mctsMove != null) {
                        bestMove = mctsMove;
                        selectedAIName = "MCTS";
                    }
                    break;
                case "AlphaZero":
                    if (alphaZeroMove != null) {
                        bestMove = alphaZeroMove;
                        selectedAIName = "AlphaZero";
                    }
                    break;
                case "Negamax":
                    if (negamaxMove != null) {
                        bestMove = negamaxMove;
                        selectedAIName = "Negamax";
                    }
                    break;
                case "OpenAI":
                    if (openAiMove != null) {
                        bestMove = openAiMove;
                        selectedAIName = "OpenAI";
                    }
                    break;
                case "LeelaZero":
                    if (leelaZeroMove != null) {
                        bestMove = leelaZeroMove;
                        selectedAIName = "LeelaZero";
                    }
                    break;
                case "Genetic":
                    if (geneticMove != null) {
                        bestMove = geneticMove;
                        selectedAIName = "Genetic";
                    }
                    break;
                case "AlphaFold3":
                    if (alphaFold3Move != null) {
                        bestMove = alphaFold3Move;
                        selectedAIName = "AlphaFold3";
                    }
                    break;
            }
        }
        
        if (bestMove != null) {
            logger.info("Selected AI '{}' move: {}", selectedAIName, java.util.Arrays.toString(bestMove));
            // Track move for flip-flop prevention
            trackMove(bestMove);
            
            // Report result to MCTS with winning move information for learning
            if (mctsAI != null) {
                boolean mctsWon = "MCTS".equals(selectedAIName);
                mctsAI.reportMoveResult(mctsWon, bestMove, selectedAIName);
            }
            
            return bestMove;
        }
        
        // Check for checkmate/stalemate FIRST
        if (allValidMoves.isEmpty()) {
            if (isKingInDanger(false)) {
                logger.info("*** BLACK KING IS IN CHECKMATE - WHITE WINS! ***");
                logger.info("*** GAME OVER ***");
                gameOver = true;
                broadcastGameState();
            } else {
                logger.info("*** STALEMATE - DRAW! ***");
                logger.info("*** GAME OVER ***");
                gameOver = true;
                broadcastGameState();
            }
            return null;
        }
        
        // If we have moves but they're all being rejected, find any safe move
        if (qLearningMove == null && deepLearningMove == null && deepLearningCNNMove == null && dqnMove == null && mctsMove == null && alphaZeroMove == null && negamaxMove == null && leelaZeroMove == null && geneticMove == null && alphaFold3Move == null) {
            logger.info("*** ALL AI MOVES REJECTED - FINDING SAFE FALLBACK MOVE ***");
            
            // Find any move that doesn't sacrifice pieces unnecessarily
            for (int[] move : filteredMoves) {
                if (isValidMove(move[0], move[1], move[2], move[3])) {
                    String piece = board[move[0]][move[1]];
                    String captured = board[move[2]][move[3]];
                    
                    // Check if this move is actually safe
                    if (!isBlunderSacrifice(move)) {
                        logger.info("*** SAFE FALLBACK MOVE FOUND: " + piece + " [" + move[0] + "," + move[1] + "] to [" + move[2] + "," + move[3] + "] ***");
                        trackMove(move);
                        return move;
                    }
                }
            }
            
            // If still no safe move, check for checkmate
            if (isKingInDanger(false)) {
                logger.info("*** BLACK KING IS IN CHECKMATE - WHITE WINS! ***");
                logger.info("*** GAME OVER ***");
                gameOver = true;
                return null;
            }
        }
        
        return allValidMoves.get(0);
    }
    
    /**
     * Get the currently selected AI for this game
     */
    public String getSelectedAIForGame() {
        return selectedAIForGame;
    }
    
    /**
     * COMPREHENSIVE AI MOVE VALIDATION
     * 
     * This method implements a 9-step validation process based on extensive testing:
     * 1. Basic coordinate bounds checking
     * 2. Source square piece validation
     * 3. Player piece ownership verification (AI plays Black)
     * 4. Legal chess move validation (prevents King exposure to check)
     * 5. King capture detection for game end scenarios
     * 6. Strategic blunder prevention (allows tactical sacrifices)
     * 7. Queen safety validation (prevents moves into enemy attacks)
     * 8. High-value piece hanging warnings
     * 9. Game state and turn validation
     * 
     * This validation system was developed after discovering multiple AI issues
     * including Queen suicidal moves, illegal King exposures, and tactical blunders.
     */
    private int[] validateAIMove(int[] move, String aiName) {
        if (move == null) return null;
        
        // VALIDATION 1: Basic bounds check
        if (move.length != 4 || 
            move[0] < 0 || move[0] > 7 || move[1] < 0 || move[1] > 7 ||
            move[2] < 0 || move[2] > 7 || move[3] < 0 || move[3] > 7) {
            logger.info(aiName + " move REJECTED: Invalid coordinates");
            return null;
        }
        
        // VALIDATION 2: Source square must have a piece
        String piece = board[move[0]][move[1]];
        if (piece == null || piece.isEmpty()) {
            logger.info(aiName + " move REJECTED: No piece at source square");
            return null;
        }
        
        // VALIDATION 3: Must be Black piece (AI plays Black)
        if (!"♚♛♜♝♞♟".contains(piece)) {
            logger.info(aiName + " move REJECTED: Not a Black piece");
            return null;
        }
        
        // VALIDATION 4: MANDATORY - Legal chess move (doesn't expose King to check)
        if (!isValidMove(move[0], move[1], move[2], move[3])) {
            logger.info(aiName + " move REJECTED: ILLEGAL MOVE - Would expose King to check or invalid piece movement");
            return null;
        }
        
        // VALIDATION 5: CRITICAL - No King capture (should be handled by checkmate detection)
        String targetPiece = board[move[2]][move[3]];
        if ("♔".equals(targetPiece) || "♚".equals(targetPiece)) {
            logger.info(aiName + " move REJECTED: ILLEGAL KING CAPTURE - This indicates checkmate should have been detected");
            return null; // Prevent illegal king capture
        }
        
        // VALIDATION 6: STRATEGIC - Allow tactical sacrifices but prevent blunders
        if (isBlunderSacrifice(move)) {
            logger.info(aiName + " move REJECTED: Would sacrifice high-value piece for insufficient compensation");
            return null;
        }
        
        // VALIDATION 7: QUEEN SAFETY - Critical fix for Queen suicidal moves
        // Prevents Queen from moving into squares under enemy attack unless capturing high-value pieces
        if ("♛".equals(piece)) { // Black Queen
            String captured = board[move[2]][move[3]];
            board[move[2]][move[3]] = piece;
            board[move[0]][move[1]] = "";
            
            boolean queenUnderAttack = isSquareUnderAttack(move[2], move[3], true);
            
            board[move[0]][move[1]] = piece;
            board[move[2]][move[3]] = captured;
            
            if (queenUnderAttack) {
                double capturedValue = getChessPieceValue(captured);
                
                // SPECIAL CASE: Allow Queen sacrifice if Queen is already pinned/doomed
                if (isPiecePinned(move[0], move[1]) && capturedValue >= 300) {
                    logger.info(aiName + " move ALLOWED: Pinned Queen sacrifice captures " + captured + " (" + capturedValue + ")");
                    return move; // Allow sacrifice of doomed Queen
                }
                
                if (capturedValue < 700) { // Unless capturing Queen or very high value
                	logger.info(aiName + " move REJECTED: Queen would be under attack at [" + move[2] + "," + move[3] + "]");
                    return null;
                }
            }
        }
        
        // VALIDATION 8: SAFETY - Warn about hanging pieces (but don't reject tactical sacrifices)
        if ("♛♜♝♞".contains(piece)) { // High-value pieces
            String captured = board[move[2]][move[3]];
            board[move[2]][move[3]] = piece;
            board[move[0]][move[1]] = "";
            
            if (isSquareUnderAttack(move[2], move[3], true)) {
                double pieceValue = getChessPieceValue(piece);
                double capturedValue = getChessPieceValue(captured);
                if (capturedValue < pieceValue * 0.8) { // Unfavorable trade
                    // Check if this is a critical defensive move that should bypass safety warnings
                    if (!ChessTacticalDefense.isCriticalDefensiveMove(board, move, aiName)) {
                        logger.info("SAFETY WARNING: " + piece + " (" + aiName + ") would hang at [" + move[2] + "," + move[3] + "] - penalty: " + (pieceValue >= 300 ? 500.0 : pieceValue));
                    } else {
                        logger.info("CRITICAL DEFENSE: " + piece + " (" + aiName + ") bypasses safety warning for tactical necessity");
                    }
                }
            }
            
            // Restore board
            board[move[0]][move[1]] = piece;
            board[move[2]][move[3]] = captured;
        }
        
        // VALIDATION 9: GAME STATE - Don't move if game is over
        if (gameOver) {
            logger.info(aiName + " move REJECTED: Game is already over");
            return null;
        }
        
        // VALIDATION 10: TURN - Must be Black's turn (AI plays Black)
        if (whiteTurn) {
            logger.info(aiName + " move REJECTED: Not Black's turn");
            return null;
        }
        
        // All validations passed
        return move;
    }
    

    

    
    private int[] findCriticalThreatResponse() {
        // PRIORITY 0: Check for opportunity to capture White King (CHECKMATE!)
        int[] whiteKingPos = findPiecePosition("♔");
        if (whiteKingPos != null) {
            List<int[]> moves = getAllValidMoves(false);
            for (int[] move : moves) {
                if (move[2] == whiteKingPos[0] && move[3] == whiteKingPos[1]) {
                    if (isValidMove(move[0], move[1], move[2], move[3])) {
                        String piece = board[move[0]][move[1]];
                        System.out.println("CHECKMATE OPPORTUNITY: " + piece + " can capture White King!");
                        return move;
                    }
                }
            }
        }
        
        // PRIORITY 1: Check for opportunity to capture White Queen
        int[] whiteQueenPos = findPiecePosition("♕");
        if (whiteQueenPos != null) {
            List<int[]> moves = getAllValidMoves(false);
            for (int[] move : moves) {
                if (move[2] == whiteQueenPos[0] && move[3] == whiteQueenPos[1]) {
                    if (isValidMove(move[0], move[1], move[2], move[3])) {
                        String piece = board[move[0]][move[1]];
                        System.out.println("CRITICAL: " + piece + " can capture White Queen!");
                        return move;
                    }
                }
            }
        }
        
        // PRIORITY 2: Protect Black Queen
        int[] blackQueenPos = findPiecePosition("♛");
        if (blackQueenPos != null && isSquareUnderAttack(blackQueenPos[0], blackQueenPos[1], true)) {
            System.out.println("CRITICAL: Black Queen under attack!");
            int[] protection = findProtectionMove(blackQueenPos);
            if (protection != null) {
                return protection;
            }
        }
        
        // PRIORITY 3: Protect Black Knights and other valuable pieces
        String[] blackPieces = {"♞", "♜", "♝"}; // Knights, Rooks, Bishops
        for (String pieceType : blackPieces) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (pieceType.equals(board[i][j]) && isSquareUnderAttack(i, j, true)) {
                        System.out.println("CRITICAL: Black " + pieceType + " under attack at [" + i + "," + j + "]!");
                        int[] protection = findPieceProtectionMove(new int[]{i, j}, pieceType);
                        if (protection != null) {
                            return protection;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    

    
    private boolean isSafeMove(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        // Simulate the move
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        // Check if King would be in danger after this move
        boolean kingInDanger = isKingInDanger(false);
        
        // Also check if any high-value pieces would be undefended
        boolean queenInDanger = false;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♛".equals(board[i][j]) && isSquareUnderAttack(i, j, true)) {
                    queenInDanger = true;
                    break;
                }
            }
        }
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        return !kingInDanger && !queenInDanger;
    }
    
    private int[] findLeastDangerousMove(List<int[]> moves) {
        int[] leastDangerous = null;
        int leastDanger = Integer.MAX_VALUE;
        
        for (int[] move : moves) {
            String piece = board[move[0]][move[1]];
            String captured = board[move[2]][move[3]];
            
            board[move[2]][move[3]] = piece;
            board[move[0]][move[1]] = "";
            
            int danger = evaluateDanger();
            
            board[move[0]][move[1]] = piece;
            board[move[2]][move[3]] = captured;
            
            if (danger < leastDanger) {
                leastDanger = danger;
                leastDangerous = move;
            }
        }
        
        return leastDangerous;
    }
    
    private double calculateMoveReward(String capturedPiece, String movingPiece) {
        double reward = 0.0;
        
        // Reward for captures
        if (!capturedPiece.isEmpty()) {
            reward += getChessPieceValue(capturedPiece) / 100.0;
        }
        
        // Penalty for losing pieces
        int[] piecePos = findPiecePosition(movingPiece);
        if (piecePos != null && isSquareUnderAttack(piecePos[0], piecePos[1], true)) {
            reward -= getChessPieceValue(movingPiece) / 200.0;
        }
        
        // Bonus for getting out of check
        if (!isKingInDanger(false)) {
            reward += 0.5;
        }
        
        return reward;
    }
    

    
    private String encodeBoardState() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                sb.append(board[i][j].isEmpty() ? "." : board[i][j]);
            }
        }
        return sb.toString();
    }
    

    
    private int evaluateDanger() {
        int danger = 0;
        
        // Heavy penalty if king is in check
        if (isKingInDanger(false)) {
            danger += 10000;
        }
        
        // Count threatened pieces
        String myPieces = "♚♛♜♝♞♟";
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && myPieces.contains(piece)) {
                    if (isSquareUnderAttack(i, j, true)) {
                        danger += getChessPieceValue(piece) / 10;
                    }
                }
            }
        }
        
        return danger;
    }
    
    private int evaluateStrategicMove(int fromRow, int fromCol, int toRow, int toCol) {
        String piece = board[toRow][toCol];
        int score = 0;
        
        // PIECE PROTECTION - Bonus if move is defended
        if (isSquareDefended(toRow, toCol, false)) {
            score += 50;
        }
        
        // ATTACK ENEMY PIECES - Bonus for attacking valuable pieces
        score += evaluateAttackThreats(toRow, toCol) * 3;
        
        // CENTER CONTROL - Bonus for central squares
        if ((toRow >= 3 && toRow <= 4) && (toCol >= 3 && toCol <= 4)) {
            score += 30;
        }
        
        // PIECE DEVELOPMENT - Bonus for developing pieces
        if ("♞♝".contains(piece) && fromRow == 0) {
            score += 40;
        }
        
        return score;
    }
    
    private boolean isSquareDefended(int row, int col, boolean byWhite) {
        String friendlyPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && friendlyPieces.contains(piece)) {
                    boolean originalTurn = whiteTurn;
                    whiteTurn = byWhite;
                    if (isValidMove(i, j, row, col)) {
                        whiteTurn = originalTurn;
                        return true;
                    }
                    whiteTurn = originalTurn;
                }
            }
        }
        return false;
    }
    
    private int evaluateAttackThreats(int row, int col) {
        int threats = 0;
        String enemyPieces = "♔♕♖♗♘♙";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String enemy = board[i][j];
                if (!enemy.isEmpty() && enemyPieces.contains(enemy)) {
                    boolean originalTurn = whiteTurn;
                    whiteTurn = false;
                    if (isValidMove(row, col, i, j)) {
                        threats += getChessPieceValue(enemy) / 100;
                    }
                    whiteTurn = originalTurn;
                }
            }
        }
        
        return threats;
    }
    
    private int countThreatenedEnemyPieces(int row, int col) {
        int count = 0;
        String enemyPieces = "♔♕♖♗♘♙";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String enemy = board[i][j];
                if (!enemy.isEmpty() && enemyPieces.contains(enemy)) {
                    boolean originalTurn = whiteTurn;
                    whiteTurn = false;
                    if (isValidMove(row, col, i, j)) {
                        count++;
                    }
                    whiteTurn = originalTurn;
                }
            }
        }
        
        return count;
    }
    
    // NEGAMAX WITH ALPHA-BETA PRUNING
    private int[] negamaxRoot(int depth) {
        List<int[]> moves = getAllValidMoves(false);
        if (moves.isEmpty()) {
            logger.info("NO VALID MOVES FOUND FOR AI!");
            return null;
        }
        
        logger.info("FOUND " + moves.size() + " VALID MOVES FOR AI");
        
        // Use simple evaluation instead of complex negamax to avoid corruption
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (int[] move : moves) {
            int score = evaluateSimpleMove(move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        
        return bestMove != null ? bestMove : moves.get(0);
    }
    
    private int evaluateSimpleMove(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        int score = 0;
        
        // Make temporary move to evaluate
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        // DEFENSE PRIORITY: Massive bonus for getting out of check
        if (!isKingInDanger(false)) {
            score += 5000;
        }
        
        // DEFENSE PRIORITY: Bonus for protecting threatened Queen
        boolean queenSafe = true;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♛".equals(board[i][j]) && isSquareUnderAttack(i, j, true)) {
                    queenSafe = false;
                    break;
                }
            }
        }
        if (queenSafe) {
            score += 2000;
        }
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        // Capture bonus
        if (!captured.isEmpty()) {
            score += getChessPieceValue(captured) * 10;
        }
        
        // Center control bonus
        if (move[2] >= 3 && move[2] <= 4 && move[3] >= 3 && move[3] <= 4) {
            score += 30;
        }
        
        // Development bonus for knights and bishops
        if (("♞♝".contains(piece)) && move[0] == 0) {
            score += 50;
        }
        
        // Random factor to avoid repetition
        score += random.nextInt(10);
        
        return score;
    }
    
    private int negamax(int depth, int alpha, int beta, boolean isWhiteTurn) {
        if (depth == 0) {
            return evaluatePosition(isWhiteTurn);
        }
        
        List<int[]> moves = getAllValidMoves(isWhiteTurn);
        if (moves.isEmpty()) {
            if (isKingInDanger(isWhiteTurn)) {
                return -50000 + (4 - depth); // Checkmate penalty (closer = worse)
            }
            return 0; // Stalemate
        }
        
        int maxScore = Integer.MIN_VALUE;
        
        for (int[] move : moves) {
            makeMove(move);
            int score = -negamax(depth - 1, -beta, -alpha, !isWhiteTurn);
            undoMove(move);
            
            maxScore = Math.max(maxScore, score);
            alpha = Math.max(alpha, score);
            
            if (alpha >= beta) {
                break; // Alpha-beta pruning
            }
        }
        
        return maxScore;
    }
    
    private String tempCapturedPiece = "";
    
    private void makeMove(int[] move) {
        tempCapturedPiece = board[move[2]][move[3]];
        String piece = board[move[0]][move[1]];
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        whiteTurn = !whiteTurn;
    }
    
    private void undoMove(int[] move) {
        String piece = board[move[2]][move[3]];
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = tempCapturedPiece;
        whiteTurn = !whiteTurn;
    }
    
    private int evaluatePosition(boolean forWhite) {
        int score = 0;
        String myPieces = forWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        String enemyPieces = forWhite ? "♚♛♜♝♞♟" : "♔♕♖♗♘♙";
        
        // Material evaluation
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int value = (int)getChessPieceValue(piece);
                    if (myPieces.contains(piece)) {
                        score += value;
                    } else if (enemyPieces.contains(piece)) {
                        score -= value;
                    }
                }
            }
        }
        
        // King safety penalty
        if (isKingInDanger(forWhite)) {
            score -= 500;
        }
        if (isKingInDanger(!forWhite)) {
            score += 500;
        }
        
        return forWhite ? score : -score;
    }
    

    

    
    // Opening system now uses LeelaChessZero opening book exclusively
    
    // HELPER METHODS
    /**
     * Generates all valid moves for the specified player
     * 
     * @param forWhite true for white pieces, false for black pieces
     * @return List of valid moves as int arrays [fromRow, fromCol, toRow, toCol]
     */
    public List<int[]> getAllValidMoves(boolean forWhite) {
        boolean originalTurn = whiteTurn;
        try {
            whiteTurn = forWhite;
            return ruleValidator.getAllValidMoves(board, forWhite, whiteTurn);
        } finally {
            whiteTurn = originalTurn;
        }
    }
    
    private int evaluateAggressiveMove(int[] move) {
        String piece = board[move[0]][move[1]];
        String capturedPiece = board[move[2]][move[3]];
        
        int score = 0;
        
        // Make temporary move
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        // CHECKMATE - Highest priority
        if (isCheckmate(true)) {
            score += 50000;
        }
        // CHECK - High priority
        else if (isKingInDanger(true)) {
            score += 3000;
        }
        
        // CAPTURES - Material gain
        if (!capturedPiece.isEmpty()) {
            score += getChessPieceValue(capturedPiece) * 10;
        }
        
        // STRATEGIC BONUSES
        score += evaluateStrategicMove(move[0], move[1], move[2], move[3]);
        
        // KING SAFETY BONUS
        if (!isKingInDanger(false)) {
            score += 100;
        }
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = capturedPiece;
        
        return score > 0 ? score : random.nextInt(20);
    }
    
    private boolean isCheckmate(boolean forWhite) {
        return ruleValidator.isCheckmate(board, forWhite);
    }
    



    
    private void updateCastlingRights(String piece, int fromRow, int fromCol) {
        if ("♔".equals(piece)) whiteKingMoved = true;
        if ("♚".equals(piece)) blackKingMoved = true;
        if ("♖".equals(piece)) {
            if (fromRow == 7 && fromCol == 0) whiteRookQueenSideMoved = true;
            if (fromRow == 7 && fromCol == 7) whiteRookKingSideMoved = true;
        }
        if ("♜".equals(piece)) {
            if (fromRow == 0 && fromCol == 0) blackRookQueenSideMoved = true;
            if (fromRow == 0 && fromCol == 7) blackRookKingSideMoved = true;
        }
    }
    

    
    private boolean isKingInDanger(boolean forWhite) {
        return ruleValidator.isKingInDanger(board, forWhite);
    }
    

    

    

    
    // Helper methods for clean architecture

    
    private boolean isSafeCapture(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        if (captured.isEmpty()) return true;
        
        boolean captureIsSafe = !ruleValidator.isSquareDirectlyUnderAttack(board, move[2], move[3], true);
        boolean favorableTrade = getChessPieceValue(captured) > getChessPieceValue(piece);
        
        return captureIsSafe || favorableTrade;
    }
    
    private boolean isFavorableTrade(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        return !captured.isEmpty() && getChessPieceValue(captured) > getChessPieceValue(piece);
    }
    
    private int[] findPiecePosition(String targetPiece) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (targetPiece.equals(board[i][j])) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }
    
    private int[] findProtectionMove(int[] piecePos) {
        // First, identify WHO is attacking the Queen
        List<int[]> attackers = findAttackersOfSquare(piecePos[0], piecePos[1], true);
        
        if (attackers.isEmpty()) {
            System.out.println("ERROR: Queen protection called but no attackers found!");
            return null;
        }
        
        System.out.println("Queen at [" + piecePos[0] + "," + piecePos[1] + "] is attacked by " + attackers.size() + " piece(s)");
        
        List<int[]> moves = getAllValidMoves(false);
        
        // PRIORITY 1: Capture the attacking piece(s)
        for (int[] attacker : attackers) {
            for (int[] move : moves) {
                // Check if this move captures the attacker
                if (move[2] == attacker[0] && move[3] == attacker[1]) {
                    // CRITICAL: Validate move is legal and doesn't expose King
                    if (isValidMove(move[0], move[1], move[2], move[3])) {
                        String piece = board[move[0]][move[1]];
                        String captured = board[move[2]][move[3]];
                        
                        System.out.println("FOUND LEGAL CAPTURE: " + piece + " can capture attacker " + captured + " at [" + attacker[0] + "," + attacker[1] + "]");
                        return move;
                    } else {
                        String piece = board[move[0]][move[1]];
                        String captured = board[move[2]][move[3]];
                        System.out.println("REJECTED ILLEGAL CAPTURE: " + piece + " capturing " + captured + " would expose King to check");
                    }
                }
            }
        }
        
        // PRIORITY 2: Move the Queen to safety
        for (int[] move : moves) {
            String piece = board[move[0]][move[1]];
            
            // Only consider Queen moves
            if ("♛".equals(piece) && move[0] == piecePos[0] && move[1] == piecePos[1]) {
                // CRITICAL: Validate move is legal first
                if (isValidMove(move[0], move[1], move[2], move[3])) {
                    String captured = board[move[2]][move[3]];
                    
                    board[move[2]][move[3]] = piece;
                    board[move[0]][move[1]] = "";
                    
                    // Check if Queen will be safe at new position
                    boolean queenSafeAtNewPos = !isSquareUnderAttack(move[2], move[3], true);
                    
                    board[move[0]][move[1]] = piece;
                    board[move[2]][move[3]] = captured;
                    
                    if (queenSafeAtNewPos) {
                        System.out.println("QUEEN ESCAPE: Moving Queen to safe square [" + move[2] + "," + move[3] + "]");
                        return move;
                    }
                } else {
                    System.out.println("REJECTED ILLEGAL QUEEN MOVE: Would expose King to check");
                }
            }
        }
        
        // PRIORITY 3: Block the attack (only works for sliding pieces)
        for (int[] attacker : attackers) {
            String attackerPiece = board[attacker[0]][attacker[1]];
            if ("♖♜♗♝♕♛".contains(attackerPiece)) { // Sliding pieces only
                for (int[] move : moves) {
                    String piece = board[move[0]][move[1]];
                    if ("♛".equals(piece)) continue; // Don't move Queen (already tried)
                    
                    String captured = board[move[2]][move[3]];
                    board[move[2]][move[3]] = piece;
                    board[move[0]][move[1]] = "";
                    
                    boolean queenStillThreatened = isSquareUnderAttack(piecePos[0], piecePos[1], true);
                    
                    board[move[0]][move[1]] = piece;
                    board[move[2]][move[3]] = captured;
                    
                    if (!queenStillThreatened) {
                        // CRITICAL: Validate blocking move is legal
                        if (isValidMove(move[0], move[1], move[2], move[3])) {
                            System.out.println("QUEEN PROTECTION BY BLOCKING: " + piece + " blocks attack from " + attackerPiece);
                            return move;
                        } else {
                            System.out.println("REJECTED ILLEGAL BLOCK: Would expose King to check");
                        }
                    }
                }
            }
        }
        
        System.out.println("NO QUEEN PROTECTION FOUND - Queen may be lost!");
        return null;
    }
    
    public List<int[]> findAttackersOfSquare(int row, int col, boolean byWhite) {
        List<int[]> attackers = new ArrayList<>();
        String enemyPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && enemyPieces.contains(piece)) {
                    if (ruleValidator.canDirectlyAttack(board, i, j, row, col, piece)) {
                        attackers.add(new int[]{i, j});
                        logger.info("ATTACKER FOUND: {} at [{},{}] attacks [{},{}]", piece, i, j, row, col);
                    }
                }
            }
        }
        
        return attackers;
    }
    
    private double getChessPieceValue(String piece) {
        return ruleValidator.getChessPieceValue(piece);
    }
    
    private List<int[]> filterSafeMoves(List<int[]> moves) {
        List<int[]> safeMoves = new ArrayList<>();
        
        for (int[] move : moves) {
            String piece = board[move[0]][move[1]];
            String captured = board[move[2]][move[3]];
            
            // Filter out useless sacrifices of valuable pieces
            if (!captured.isEmpty() && getChessPieceValue(piece) >= 300) {
                if (isSafeCapture(move) || isFavorableTrade(move)) {
                    safeMoves.add(move);
                }
            } else {
                safeMoves.add(move);
            }
        }
        
        return safeMoves;
    }
    
    public boolean isSquareUnderAttack(int row, int col, boolean byWhite) {
        return ruleValidator.isSquareUnderAttack(board, row, col, byWhite);
    }
    
    // UNDO/REDO SYSTEM
    private void saveGameState() {
        undoStack.add(new GameState(board, whiteTurn, whiteKingMoved, blackKingMoved,
                                   whiteRookKingSideMoved, whiteRookQueenSideMoved,
                                   blackRookKingSideMoved, blackRookQueenSideMoved, moveHistory));
        redoStack.clear();
    }
    
    public boolean undoMove() {
        if (undoStack.isEmpty()) return false;
        
        redoStack.add(new GameState(board, whiteTurn, whiteKingMoved, blackKingMoved,
                                   whiteRookKingSideMoved, whiteRookQueenSideMoved,
                                   blackRookKingSideMoved, blackRookQueenSideMoved, moveHistory));
        
        GameState lastState = undoStack.remove(undoStack.size() - 1);
        
        for (int i = 0; i < 8; i++) {
            System.arraycopy(lastState.board[i], 0, board[i], 0, 8);
        }
        whiteTurn = lastState.whiteTurn;
        whiteKingMoved = lastState.whiteKingMoved;
        blackKingMoved = lastState.blackKingMoved;
        whiteRookKingSideMoved = lastState.whiteRookKingSideMoved;
        whiteRookQueenSideMoved = lastState.whiteRookQueenSideMoved;
        blackRookKingSideMoved = lastState.blackRookKingSideMoved;
        blackRookQueenSideMoved = lastState.blackRookQueenSideMoved;
        moveHistory = new ArrayList<>(lastState.moveHistory);
        
        return true;
    }
    
    public boolean redoMove() {
        if (redoStack.isEmpty()) return false;
        
        undoStack.add(new GameState(board, whiteTurn, whiteKingMoved, blackKingMoved,
                                   whiteRookKingSideMoved, whiteRookQueenSideMoved,
                                   blackRookKingSideMoved, blackRookQueenSideMoved, moveHistory));
        
        GameState redoState = redoStack.remove(redoStack.size() - 1);
        
        for (int i = 0; i < 8; i++) {
            System.arraycopy(redoState.board[i], 0, board[i], 0, 8);
        }
        whiteTurn = redoState.whiteTurn;
        whiteKingMoved = redoState.whiteKingMoved;
        blackKingMoved = redoState.blackKingMoved;
        whiteRookKingSideMoved = redoState.whiteRookKingSideMoved;
        whiteRookQueenSideMoved = redoState.whiteRookQueenSideMoved;
        blackRookKingSideMoved = redoState.blackRookKingSideMoved;
        blackRookQueenSideMoved = redoState.blackRookQueenSideMoved;
        moveHistory = new ArrayList<>(redoState.moveHistory);
        
        return true;
    }
    
    private void printBoardState(String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(label).append(":\n");
        for (int i = 0; i < 8; i++) {
            sb.append("Row ").append(i).append(": ");
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece.isEmpty()) {
                    sb.append("[ ] ");
                } else {
                    sb.append("[").append(piece).append("] ");
                }
            }
            sb.append("\n");
        }
        sb.append("Turn: ").append(whiteTurn ? "White" : "Black");
        logger.info(sb.toString());
    }
    
    private String formatMoveNotation(String piece, int fromRow, int fromCol, int toRow, int toCol, String capturedPiece) {
        char fromFile = (char)('a' + fromCol);
        char toFile = (char)('a' + toCol);
        int fromRank = 8 - fromRow;
        int toRank = 8 - toRow;
        
        String notation = piece + fromFile + fromRank;
        if (!capturedPiece.isEmpty()) {
            notation += "x" + capturedPiece;
        }
        notation += " to " + toFile + toRank;
        
        return notation;
    }
    
    // PUBLIC API
    /**
     * Gets the current board state
     * @return 8x8 array of chess pieces (Unicode strings)
     */
    public String[][] getBoard() { return board; }
    
    /**
     * Checks whose turn it is
     * @return true if it's white's turn, false if black's turn
     */
    public boolean isWhiteTurn() { return whiteTurn; }
    
    /**
     * Checks if the game has ended
     * @return true if game is over (checkmate, stalemate, or resignation)
     */
    public boolean isGameOver() { return gameOver; }
    public List<String> getMoveHistory() { return new ArrayList<>(moveHistory); }
    public String getLastMove() { return moveHistory.isEmpty() ? "No moves yet" : moveHistory.get(moveHistory.size() - 1); }
    public int getMoveCount() { return moveHistory.size(); }
    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public String getSelectedOpeningName() { return "LeelaZero Grandmaster Database"; }
    public int getOpeningProgress() { return Math.min(15, (moveHistory.size() + 1) / 2); }
    public int getOpeningLength() { return 15; }
    public QLearningAI getQLearningAI() { return qLearningAI; }
    public DeepLearningAI getDeepLearningAI() { return deepLearningAI; }
    public MonteCarloTreeSearchAI getMCTSAI() { return mctsAI; }
    public AlphaZeroAI getAlphaZeroAI() { return alphaZeroAI; }
    public NegamaxAI getNegamaxAI() { return negamaxAI; }
    
    // AI Status Methods
    public boolean isQLearningEnabled() { ensureAISystemsInitialized(); return qLearningEnabled && qLearningAI != null; }
    public boolean isDeepLearningEnabled() { ensureAISystemsInitialized(); return deepLearningEnabled && deepLearningAI != null; }
    public boolean isDeepLearningCNNEnabled() { ensureAISystemsInitialized(); return deepLearningCNNEnabled && deepLearningCNNAI != null; }
    public boolean isDQNEnabled() { ensureAISystemsInitialized(); return dqnEnabled && dqnAI != null; }
    public boolean isMCTSEnabled() { ensureAISystemsInitialized(); return mctsEnabled && mctsAI != null; }
    public boolean isAlphaZeroEnabled() { ensureAISystemsInitialized(); return alphaZeroEnabled && alphaZeroAI != null; }
    public boolean isNegamaxEnabled() { ensureAISystemsInitialized(); return negamaxEnabled && negamaxAI != null; }
    public boolean isOpenAiEnabled() { ensureAISystemsInitialized(); return openAiEnabled && openAiAI != null; }
    public boolean isLeelaZeroEnabled() { ensureAISystemsInitialized(); return leelaZeroEnabled && leelaZeroAI != null; }
    public boolean isGeneticEnabled() { ensureAISystemsInitialized(); return geneticEnabled && geneticAI != null; }
    public boolean isAlphaFold3Enabled() { ensureAISystemsInitialized(); return alphaFold3Enabled && alphaFold3AI != null; }
    public AlphaFold3AI getAlphaFold3AI() { return alphaFold3AI; }
    public DeepQNetworkAI getDQNAI() { return dqnAI; }
    public LeelaChessZeroAI getLeelaZeroAI() { return leelaZeroAI; }
    public GeneticAlgorithmAI getGeneticAI() { return geneticAI; }
    public LeelaChessZeroOpeningBook getLeelaOpeningBook() { return leelaOpeningBook; }
    public TrainingManager getTrainingManager() { return trainingManager; }
    
    // Methods for AI training - allow AIs to use ChessGame's move validation
    public void setBoard(String[][] newBoard) {
        for (int i = 0; i < 8; i++) {
            System.arraycopy(newBoard[i], 0, board[i], 0, 8);
        }
    }
    
    public void setWhiteTurn(boolean whiteTurn) {
        this.whiteTurn = whiteTurn;
    }
    

    

    
    // Deep Learning UI status methods
    public boolean isDeepLearningTraining() { return deepLearningAI != null && deepLearningAI.isTraining(); }
    public String getDeepLearningStatus() { return deepLearningAI != null ? deepLearningAI.getTrainingStatus() : "Disabled"; }
    public int getDeepLearningIterations() { return deepLearningAI != null ? deepLearningAI.getTrainingIterations() : 0; }
    
    // CNN Deep Learning UI status methods
    public boolean isDeepLearningCNNTraining() { return deepLearningCNNAI != null && deepLearningCNNAI.isTraining(); }
    public String getDeepLearningCNNStatus() { return deepLearningCNNAI != null ? deepLearningCNNAI.getTrainingStatus() : "Disabled"; }
    public int getDeepLearningCNNIterations() { return deepLearningCNNAI != null ? deepLearningCNNAI.getTrainingIterations() : 0; }
    public DeepLearningCNNAI getDeepLearningCNNAI() { return deepLearningCNNAI; }
    
    public void stopTraining() {
        if (trainingManager != null) {
            trainingManager.stopTraining(this);
        } else {
            logger.warn("TrainingManager is null - cannot stop training");
        }
        
        // Reset board to fresh game state after training
        if (isQLearningEnabled() && qLearningAI != null) {
            logger.info("*** Resetting board to fresh state after Q-Learning training ***");
            initializeBoard();
            whiteTurn = true;
            gameOver = false;
            moveHistory.clear();
            if (leelaOpeningBook != null) {
                leelaOpeningBook.resetOpeningLine();
            }
            broadcastGameState();
        }
        
        saveTrainingData();
    }
    
    /**
     * Resets the game to initial state while preserving AI knowledge
     * 
     * - Saves all AI training data to disk
     * - Resets board to starting position
     * - Clears move history and game state
     * - Preserves AI learning progress
     */
    public void resetGame() {
        // Only process game data and save if there were actual moves made
        if (moveHistory.size() > 0) {
            logger.info("*** PARALLEL AI GAME DATA PROCESSING: Starting all enabled AIs simultaneously ***");
            
            // Create parallel tasks for all AI game data processing
            var alphaZeroTask = isAlphaZeroEnabled() ? 
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boolean blackWon = gameOver && whiteTurn;
                        alphaZeroAI.addHumanGameData(board, moveHistory, blackWon);
                        logger.info("AlphaZero AI: Game data processed");
                    } catch (Exception e) {
                        logger.error("AlphaZero game data error: {}", e.getMessage());
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            var deepLearningTask = isDeepLearningEnabled() ? 
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boolean blackWon = gameOver && !whiteTurn;
                        deepLearningAI.addHumanGameData(board, moveHistory, blackWon);
                        logger.info("Deep Learning AI: Game data processed");
                    } catch (Exception e) {
                        logger.error("Deep Learning game data error: {}", e.getMessage());
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            var deepLearningCNNTask = isDeepLearningCNNEnabled() ? 
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boolean blackWon = gameOver && !whiteTurn;
                        deepLearningCNNAI.addHumanGameData(board, moveHistory, blackWon);
                        logger.info("CNN AI: Game data processed");
                    } catch (Exception e) {
                        logger.error("CNN game data error: {}", e.getMessage());
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            var dqnTask = isDQNEnabled() ? 
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boolean blackWon = gameOver && !whiteTurn;
                        dqnAI.addHumanGameData(board, moveHistory, blackWon);
                        logger.info("DQN AI: Game data processed");
                    } catch (Exception e) {
                        logger.error("DQN game data error: {}", e.getMessage());
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            var alphaFold3Task = isAlphaFold3Enabled() ? 
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boolean blackWon = gameOver && !whiteTurn;
                        alphaFold3AI.addHumanGameData(board, moveHistory, blackWon);
                        logger.info("AlphaFold3 AI: Game data processed");
                    } catch (Exception e) {
                        logger.error("AlphaFold3 game data error: {}", e.getMessage());
                    }
                }) : java.util.concurrent.CompletableFuture.completedFuture(null);
            
            // Wait for ALL AI game data processing to complete in parallel
            try {
                java.util.concurrent.CompletableFuture.allOf(
                    alphaZeroTask, deepLearningTask, deepLearningCNNTask, dqnTask, alphaFold3Task
                ).get(30, java.util.concurrent.TimeUnit.SECONDS);
                
                logger.info("*** PARALLEL AI GAME DATA PROCESSING: All AIs completed simultaneously ***");
                
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("AI game data processing timeout after 30 seconds: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("AI game data processing error: {}", e.getMessage());
            }
            
            // Add game to LeelaZero opening book learning
            if (leelaOpeningBook != null) {
                try {
                    leelaOpeningBook.addGameMoves(moveHistory);
                    logger.info("*** LeelaZero: Game added to opening book ***");
                } catch (Exception e) {
                    logger.error("*** LeelaZero: Opening book update error - " + e.getMessage() + " ***");
                }
            }
            
            // Save LeelaZero AI state only if there were moves
            if (isLeelaZeroEnabled()) {
                try {
                    // LeelaZero state saving handled automatically
                    logger.info("*** LeelaZero AI: State preserved during game reset ***");
                } catch (Exception e) {
                    logger.error("*** LeelaZero AI save error: " + e.getMessage() + " ***");
                }
            }
            
            // Save training data only when there were actual moves
            saveTrainingData();
        } else {
            logger.debug("*** No moves made - skipping game data processing and save ***");
        }
        
        // Clear AI trees and reset states
        if (mctsAI != null) {
            mctsAI.clearTree();
        }
        // Negamax is stateless - no reset needed
        
        // Reset board and game state but keep AI instances
        initializeBoard();
        whiteTurn = true;
        gameOver = false;
        aiLastMove = null; // Clear AI move tracking
        whiteKingMoved = false;
        blackKingMoved = false;
        whiteRookKingSideMoved = false;
        whiteRookQueenSideMoved = false;
        blackRookKingSideMoved = false;
        blackRookQueenSideMoved = false;
        moveHistory.clear();
        undoStack.clear();
        redoStack.clear();
        // Opening system now uses LeelaChessZero opening book exclusively
        if (leelaOpeningBook != null) {
            leelaOpeningBook.resetOpeningLine();
            logger.info("Opening book reset for new game");
        }
        lastRejectedMove = "";
        previousBoardState = null;
        previousMove = null;
        recentMoves.clear();
        moveRepetitionCount.clear();
        reEvaluationMode = false;
        
        synchronized (aiInitLock) {
            aiSystemsReady = true; // Keep ready state during reset
        }
        
        // Select a new random AI for this game
        selectRandomAIForGame();
        
        logger.info("NEW GAME STARTED - AI KNOWLEDGE SAVED & PRESERVED (9 AI SYSTEMS + LEELA OPENINGS)");
    }
    
    public void shutdown() {
        // Set the shutdown flag to prevent duplicate shutdown
        ChessApplication.shutdownInProgress = true;
        
        logger.info("*** CHESS APPLICATION SHUTDOWN INITIATED - SAVING ALL TRAINING DATA ***");
        
        try {
            // Stop training first with timeout
            stopTraining();
            
            // Give training threads time to stop gracefully
            Thread.sleep(1000);
            
            // AlphaZero and LeelaZero training will stop when threads are interrupted
            logger.info("*** AlphaZero and LeelaZero: Training will stop via thread interruption ***");
            
            // CRITICAL: Save all training data before shutdown - comprehensive save
            logger.info("*** SAVING ALL AI TRAINING DATA TO DISK ***");
            
            // Save Q-Learning data
            if (isQLearningEnabled()) {
                try {
                    qLearningAI.saveQTable();
                    logger.info("Q-Learning: Training data saved successfully");
                } catch (Exception e) {
                    logger.error("Q-Learning: Failed to save training data - {}", e.getMessage());
                }
            }
            
            // Save Deep Learning models
            if (isDeepLearningEnabled()) {
                try {
                    deepLearningAI.saveModelNow();
                    logger.info("Deep Learning: Model saved successfully");
                } catch (Exception e) {
                    logger.error("Deep Learning: Failed to save model - {}", e.getMessage());
                }
            }
            
            // Save CNN Deep Learning models
            if (isDeepLearningCNNEnabled()) {
                try {
                    deepLearningCNNAI.saveModelNow();
                    logger.info("CNN Deep Learning: Model saved successfully");
                } catch (Exception e) {
                    logger.error("CNN Deep Learning: Failed to save model - {}", e.getMessage());
                }
            }
            
            // Save DQN data
            if (isDQNEnabled()) {
                try {
                    dqnAI.saveModels();
                    dqnAI.saveExperiences();
                    logger.info("DQN: Models and experiences saved successfully");
                } catch (Exception e) {
                    logger.error("DQN: Failed to save training data - {}", e.getMessage());
                }
            }
            
            // Save AlphaZero neural network
            if (isAlphaZeroEnabled()) {
                try {
                    alphaZeroAI.saveNeuralNetwork();
                    logger.info("AlphaZero: Neural network saved successfully");
                } catch (Exception e) {
                    logger.error("AlphaZero: Failed to save neural network - {}", e.getMessage());
                }
            }
            
            // Save LeelaZero state
            if (isLeelaZeroEnabled()) {
                try {
                    leelaZeroAI.saveState();
                    logger.info("LeelaZero: State saved successfully");
                } catch (Exception e) {
                    logger.error("LeelaZero: Failed to save state - {}", e.getMessage());
                }
            }
            
            // Save Genetic Algorithm population
            if (isGeneticEnabled()) {
                try {
                    geneticAI.savePopulation();
                    logger.info("Genetic Algorithm: Population saved successfully");
                } catch (Exception e) {
                    logger.error("Genetic Algorithm: Failed to save population - {}", e.getMessage());
                }
            }
            
            // Save AlphaFold3 state
            if (isAlphaFold3Enabled()) {
                try {
                    alphaFold3AI.saveState();
                    logger.info("AlphaFold3: State saved successfully");
                } catch (Exception e) {
                    logger.error("AlphaFold3: Failed to save state - {}", e.getMessage());
                }
            }
            
            logger.info("*** ALL TRAINING DATA SAVED SUCCESSFULLY ***");
            
            // Give additional time for file I/O to complete
            Thread.sleep(500);
            
        } catch (Exception e) {
            logger.error("Error during training data save: {}", e.getMessage());
        }
        
        // Shutdown all AI systems gracefully (only enabled AIs)
        try {
            if (isQLearningEnabled()) {
                qLearningAI.shutdown();
            }
            if (isDeepLearningEnabled()) {
                deepLearningAI.shutdown();
            }
            if (isDeepLearningCNNEnabled()) {
                deepLearningCNNAI.shutdown();
            }
            if (isDQNEnabled()) {
                dqnAI.shutdown();
            }
            
            // Stop threaded AIs
            if (isMCTSEnabled()) {
                mctsAI.stopThinking();
            }
            if (isAlphaZeroEnabled()) {
                logger.info("*** AlphaZero: Shutting down ***");
                alphaZeroAI.shutdown();
            }
            if (isNegamaxEnabled()) {
                negamaxAI.clearCache();
            }
            if (isLeelaZeroEnabled()) {
                logger.info("*** LeelaZero: Shutting down ***");
                leelaZeroAI.shutdown(); // Proper shutdown with thread cleanup
            }
            
            if (isGeneticEnabled()) {
                logger.info("*** Genetic Algorithm: Shutting down ***");
                geneticAI.stopTraining(); // Ensure training is stopped and saved
            }
            
            if (isAlphaFold3Enabled()) {
                logger.info("*** AlphaFold3: Shutting down ***");
                alphaFold3AI.shutdown();
            }
            
        } catch (Exception e) {
            logger.error("Error during AI system shutdown: {}", e.getMessage());
        }
        
        logger.info("*** CHESS APPLICATION SHUTDOWN COMPLETE (11 AI SYSTEMS) ***");
    }
    
    public boolean deleteAllTrainingData() {
        logger.debug("*** DELETING ALL TRAINING DATA ***");
        
        boolean qLearningDeleted = isQLearningEnabled() ? qLearningAI.deleteQTable() : true;
        boolean deepLearningDeleted = isDeepLearningEnabled() ? deepLearningAI.deleteModel() : true;
        boolean deepLearningCNNDeleted = isDeepLearningCNNEnabled() ? deepLearningCNNAI.deleteModel() : true;
        boolean dqnDeleted = isDQNEnabled() ? dqnAI.deleteTrainingData() : true;
        
        // Delete AlphaZero cache file (only if enabled)
        boolean alphaZeroDeleted = true;
        if (isAlphaZeroEnabled()) {
            try {
                java.io.File alphaZeroCache = new java.io.File("alphazero_cache.dat");
                if (alphaZeroCache.exists()) {
                    alphaZeroDeleted = alphaZeroCache.delete();
                    logger.info("AlphaZero: Cache file deleted and neural network reset");
                } else {
                    logger.info("AlphaZero: No cache file found (fresh start)");
                }
            } catch (Exception e) {
                alphaZeroDeleted = false;
                System.err.println("AlphaZero: Failed to delete cache - " + e.getMessage());
            }
        }
        
        // OpenAI is stateless - no training data to delete
        boolean openAiDeleted = true;
        logger.info("OpenAI: No training data to delete (stateless API-based system)");
        
        // Delete LeelaZero training data (only if enabled)
        boolean leelaZeroDeleted = true;
        if (isLeelaZeroEnabled()) {
            try {
                java.io.File leelaOpeningBook = new java.io.File("leela_opening_book.dat");
                java.io.File leelaModel = new java.io.File("leela_neural_network.dat");
                java.io.File leelaCache = new java.io.File("leela_mcts_cache.dat");
                java.io.File leelaModelsDir = new java.io.File("leela_models");
                
                boolean book = !leelaOpeningBook.exists() || leelaOpeningBook.delete();
                boolean model = !leelaModel.exists() || leelaModel.delete();
                boolean cache = !leelaCache.exists() || leelaCache.delete();
                boolean modelsDir = deleteDirectory(leelaModelsDir);
                
                leelaZeroDeleted = book && model && cache && modelsDir;
                
                if (leelaZeroDeleted) {
                    logger.info("LeelaZero: All training files deleted (opening book, neural network, MCTS cache, models directory)");
                } else {
                    System.err.println("LeelaZero: Failed to delete some training files");
                }
            } catch (Exception e) {
                leelaZeroDeleted = false;
                System.err.println("LeelaZero: Failed to delete training data - " + e.getMessage());
            }
        }
        
        // Clear MCTS tree (stateless - no persistent data, only if enabled)
        if (isMCTSEnabled()) {
            mctsAI.clearTree();
        }
        
        // Negamax is stateless - no persistent data to delete
        logger.info("Negamax: No training data to delete (stateless algorithm)");
        
        // Delete Genetic Algorithm training data (only if enabled)
        boolean geneticDeleted = true;
        if (isGeneticEnabled()) {
            try {
                geneticAI.deleteTrainingData();
                logger.info("Genetic Algorithm: Training data deleted");
            } catch (Exception e) {
                geneticDeleted = false;
                System.err.println("Genetic Algorithm: Failed to delete training data - " + e.getMessage());
            }
        }
        
        // Delete AlphaFold3 training data
        boolean alphaFold3Deleted = true;
        if (isAlphaFold3Enabled()) {
            try {
                java.io.File alphaFold3State = new java.io.File("alphafold3_state.dat");
                if (alphaFold3State.exists()) {
                    alphaFold3Deleted = alphaFold3State.delete();
                    logger.info("AlphaFold3: Training data deleted");
                } else {
                    logger.info("AlphaFold3: No training data found");
                }
            } catch (Exception e) {
                alphaFold3Deleted = false;
                System.err.println("AlphaFold3: Failed to delete training data - " + e.getMessage());
            }
        }
        
        if (qLearningDeleted && deepLearningDeleted && deepLearningCNNDeleted && dqnDeleted && alphaZeroDeleted && leelaZeroDeleted && geneticDeleted && alphaFold3Deleted) {
            logger.debug("*** ALL TRAINING DATA DELETED SUCCESSFULLY (11 AI SYSTEMS) ***");
            return true;
        } else {
            logger.debug("*** FAILED TO DELETE SOME TRAINING DATA ***");
            logger.debug("Q-Learning: {}, Deep Learning: {}, CNN: {}, DQN: {}, AlphaZero: {}, LeelaZero: {}, Genetic: {}, AlphaFold3: {}, OpenAI: N/A (stateless), Negamax: N/A (stateless)", 
                qLearningDeleted, deepLearningDeleted, deepLearningCNNDeleted, dqnDeleted, alphaZeroDeleted, leelaZeroDeleted, geneticDeleted, alphaFold3Deleted);
            return false;
        }
    }
    
    /**
     * Starts training for all AI systems concurrently
     * 
     * Initiates:
     * - Q-Learning self-play training
     * - Deep Learning neural network training
     * - Deep Q-Network training
     * - AlphaZero self-play training
     * 
     * @param games Number of games for Q-Learning training
     */
    public void setController(ChessController controller) {
        this.controller = controller;
        if (isQLearningEnabled()) {
            qLearningAI.setController(controller);
        }
    }
    
    private void broadcastGameState() {
        if (controller != null) {
            controller.broadcastGameState(board, whiteTurn, gameOver, getKingInCheckPosition(), getThreatenedHighValuePieces(), aiLastMove);
            // Clear AI last move after broadcasting
            aiLastMove = null;
        }
    }
    
    public void trainAI() {
        ensureAISystemsInitialized();
        stateChanged = true;
        
        if (trainingManager == null) {
            trainingManager = new TrainingManager();
        }
        trainingManager.startTraining(this);
    }
    
    public int[] getKingInCheckPosition() {
        if (isKingInDanger(true)) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if ("♔".equals(board[i][j])) {
                        return new int[]{i, j};
                    }
                }
            }
        }
        
        if (isKingInDanger(false)) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if ("♚".equals(board[i][j])) {
                        return new int[]{i, j};
                    }
                }
            }
        }
        
        return null;
    }
    
    public int[][] getThreatenedHighValuePieces() {
        List<int[]> threatened = new ArrayList<>();
        
        // Check for threatened kings
        if (isKingInDanger(true)) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if ("♔".equals(board[i][j])) {
                        threatened.add(new int[]{i, j});
                    }
                }
            }
        }
        
        if (isKingInDanger(false)) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if ("♚".equals(board[i][j])) {
                        threatened.add(new int[]{i, j});
                    }
                }
            }
        }
        
        // Check for threatened queens
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if ("♕".equals(piece) && isSquareUnderAttack(i, j, false)) {
                    threatened.add(new int[]{i, j});
                }
                if ("♛".equals(piece) && isSquareUnderAttack(i, j, true)) {
                    threatened.add(new int[]{i, j});
                }
            }
        }
        
        return threatened.toArray(new int[threatened.size()][]);
    }
    
    public boolean hasStateChanged() {
        return stateChanged;
    }
    
    /**
     * Allow AI systems to notify ChessGame when their state changes during training
     * This ensures proper saving during shutdown even when no moves are made on the board
     */
    public void notifyStateChanged() {
        stateChanged = true;
    }
    
    public void saveTrainingData() {
        long currentTime = System.currentTimeMillis();
        if (!stateChanged && (currentTime - lastSaveTime) < 30000) {
            logger.debug("No state changes or recent save - skipping save");
            return;
        }
        lastSaveTime = currentTime;
        
        logger.info("*** SAVING ALL AI TRAINING DATA WITH TRUE PARALLEL EXECUTION ***");
        stateChanged = false; // Reset state change flag after saving
        
        // Create CompletableFuture tasks for TRUE parallel execution
        var qLearningTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isQLearningEnabled()) {
                try {
                    qLearningAI.saveQTable();
                    logger.debug("Q-Learning: Training data saved");
                } catch (Exception e) {
                    logger.error("Q-Learning: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var deepLearningTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isDeepLearningEnabled()) {
                try {
                    deepLearningAI.saveModelNow();
                    logger.debug("Deep Learning: Model saved");
                } catch (Exception e) {
                    logger.error("Deep Learning: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var deepLearningCNNTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isDeepLearningCNNEnabled()) {
                try {
                    deepLearningCNNAI.saveModelNow();
                    logger.debug("CNN Deep Learning: Model saved");
                } catch (Exception e) {
                    logger.error("CNN Deep Learning: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var dqnTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isDQNEnabled()) {
                try {
                    dqnAI.saveModels();
                    dqnAI.saveExperiences();
                    logger.debug("DQN: Models and experiences saved");
                } catch (Exception e) {
                    logger.error("DQN: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var alphaZeroTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isAlphaZeroEnabled()) {
                try {
                    alphaZeroAI.saveNeuralNetwork();
                    logger.debug("AlphaZero: Neural network saved");
                } catch (Exception e) {
                    logger.error("AlphaZero: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var leelaZeroTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isLeelaZeroEnabled()) {
                try {
                    // LeelaZero state saving handled automatically
                    logger.debug("LeelaZero: State preserved");
                } catch (Exception e) {
                    logger.error("LeelaZero: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var geneticTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isGeneticEnabled()) {
                try {
                    geneticAI.savePopulation();
                    logger.debug("Genetic Algorithm: Population saved");
                } catch (Exception e) {
                    logger.error("Genetic Algorithm: Save failed - {}", e.getMessage());
                }
            }
        });
        
        var alphaFold3Task = java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (isAlphaFold3Enabled()) {
                try {
                    alphaFold3AI.saveState();
                    logger.debug("AlphaFold3: State saved");
                } catch (Exception e) {
                    logger.error("AlphaFold3: Save failed - {}", e.getMessage());
                }
            }
        });
        
        // Wait for ALL tasks to complete in TRUE parallel execution
        try {
            java.util.concurrent.CompletableFuture.allOf(
                qLearningTask, deepLearningTask, deepLearningCNNTask, dqnTask, 
                alphaZeroTask, leelaZeroTask, geneticTask, alphaFold3Task
            ).get(60, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("AI save timeout after 60 seconds: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("AI save error: {}", e.getMessage());
        }
        
        logger.info("*** ALL AI TRAINING DATA SAVE COMPLETE ***");
    }
    
    private boolean isAnyTrainingActive() {
        return trainingManager != null && trainingManager.isTrainingActive();
    }
    
    /**
     * Determine if a move constitutes a blunder sacrifice
     * 
     * This method distinguishes between tactical sacrifices (which should be allowed)
     * and blunders (which should be prevented). The analysis considers:
     * - Piece values and compensation received
     * - Special Queen safety rules (prevents Queen suicidal moves)
     * - Checkmate opportunities (always allowed)
     * - Tactical compensation (check threats, positional gains)
     * 
     * FIXED: Made much more lenient to allow normal chess moves
     * 
     * @param move The move to evaluate [fromRow, fromCol, toRow, toCol]
     * @return true if the move is a blunder, false if it's acceptable
     */
    private boolean isBlunderSacrifice(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        double pieceValue = getChessPieceValue(piece);
        double capturedValue = getChessPieceValue(captured);
        
        // Only check Queen sacrifices - allow all other pieces to move freely
        if (!"♛".equals(piece)) return false;
        
        // SPECIAL CHECK: Queen safety - prevents suicidal Queen moves only
        if ("♛".equals(piece)) { // Black Queen
            // Allow Queen moves if capturing anything valuable
            if (capturedValue >= 300) {
                return false; // Allow Queen captures of Knight or higher
            }
            
            // Check if Queen will be under attack at destination
            board[move[2]][move[3]] = piece;
            board[move[0]][move[1]] = "";
            boolean queenUnderAttack = isSquareUnderAttack(move[2], move[3], true);
            
            // Check for checkmate after this move (always allow checkmate moves)
            boolean createsCheckmate = false;
            if (isKingInDanger(true)) {
                createsCheckmate = isPlayerInCheckmate(true);
            }
            
            board[move[0]][move[1]] = piece;
            board[move[2]][move[3]] = captured;
            
            // ALWAYS ALLOW CHECKMATE SACRIFICES
            if (createsCheckmate) {
                logger.debug("CHECKMATE SACRIFICE ALLOWED: Queen creates checkmate");
                return false;
            }
            
            // Only reject Queen moves that hang the Queen for nothing
            if (queenUnderAttack && capturedValue == 0) {
                logger.debug("QUEEN BLUNDER REJECTED: Queen hangs for nothing");
                return true;
            }
        }
        
        return false; // Allow all other moves
    }
    
    private boolean preventsImminentCheckmate(int[] move) {
        // Only check for Black (defending against White's checkmate threats)
        String piece = board[move[0]][move[1]];
        if ("♔♕♖♗♘♙".contains(piece)) return false; // Skip white pieces
        
        if (moveHistory.size() < 4) return false;
        
        int[] blackKing = findKing(false);
        if (blackKing == null) return false;
        
        // Check multiple checkmate patterns
        return preventsScholarsMate(move) || 
               preventsBackRankMate(move, blackKing) || 
               preventsSmotheredMate(move, blackKing);
    }
    
    private boolean preventsScholarsMate(int[] move) {
        int[] f7 = {1, 5}; // f7 square
        return preventsAttackOnSquare(move, f7);
    }
    
    private boolean preventsBackRankMate(int[] move, int[] blackKing) {
        // Back rank mate: King on 8th rank with no escape, Rook/Queen attacking
        if (blackKing[0] != 0) return false; // King not on back rank
        
        // Check if White has Rook/Queen on same rank
        for (int j = 0; j < 8; j++) {
            String piece = board[0][j];
            if ("♕♖".contains(piece)) { // White Queen or Rook
                // Check if this move creates escape square or blocks attack
                String captured = board[move[2]][move[3]];
                board[move[2]][move[3]] = board[move[0]][move[1]];
                board[move[0]][move[1]] = "";
                
                boolean stillTrapped = isKingTrappedOnBackRank(blackKing);
                
                board[move[0]][move[1]] = board[move[2]][move[3]];
                board[move[2]][move[3]] = captured;
                
                if (!stillTrapped) return true; // Move creates escape
            }
        }
        return false;
    }
    
    private boolean preventsSmotheredMate(int[] move, int[] blackKing) {
        // Smothered mate: Knight attacks King surrounded by own pieces
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♘".equals(board[i][j])) { // White Knight
                    if (canKnightAttackKing(i, j, blackKing) && isKingSurrounded(blackKing)) {
                        // Check if move creates escape or blocks Knight
                        return moveCreatesKingEscape(move, blackKing);
                    }
                }
            }
        }
        return false;
    }
    
    private boolean preventsAttackOnSquare(int[] move, int[] targetSquare) {
        int attackersBefore = countWhiteAttackersOnSquare(targetSquare);
        if (attackersBefore < 2) return false;
        
        // Simulate move
        String captured = board[move[2]][move[3]];
        board[move[2]][move[3]] = board[move[0]][move[1]];
        board[move[0]][move[1]] = "";
        
        int attackersAfter = countWhiteAttackersOnSquare(targetSquare);
        
        // Restore
        board[move[0]][move[1]] = board[move[2]][move[3]];
        board[move[2]][move[3]] = captured;
        
        return attackersAfter < attackersBefore;
    }
    
    private int countWhiteAttackersOnSquare(int[] square) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if ("♔♕♖♗♘♙".contains(piece)) {
                    if (canPieceAttackSquare(i, j, square[0], square[1], piece)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    private boolean isKingTrappedOnBackRank(int[] king) {
        // Check if King has escape squares on rank 2
        int[][] escapeSquares = {{1, king[1]-1}, {1, king[1]}, {1, king[1]+1}};
        for (int[] escape : escapeSquares) {
            if (escape[1] >= 0 && escape[1] < 8 && "".equals(board[escape[0]][escape[1]])) {
                return false; // Has escape
            }
        }
        return true;
    }
    
    private boolean canKnightAttackKing(int knightRow, int knightCol, int[] king) {
        int[] knightMoves = {-2,-1, -2,1, -1,-2, -1,2, 1,-2, 1,2, 2,-1, 2,1};
        for (int i = 0; i < knightMoves.length; i += 2) {
            int newRow = knightRow + knightMoves[i];
            int newCol = knightCol + knightMoves[i+1];
            if (newRow == king[0] && newCol == king[1]) return true;
        }
        return false;
    }
    
    private boolean isKingSurrounded(int[] king) {
        int[] directions = {-1,-1, -1,0, -1,1, 0,-1, 0,1, 1,-1, 1,0, 1,1};
        for (int i = 0; i < directions.length; i += 2) {
            int newRow = king[0] + directions[i];
            int newCol = king[1] + directions[i+1];
            if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                if ("".equals(board[newRow][newCol])) return false; // Has escape
            }
        }
        return true;
    }
    
    private boolean moveCreatesKingEscape(int[] move, int[] king) {
        String captured = board[move[2]][move[3]];
        board[move[2]][move[3]] = board[move[0]][move[1]];
        board[move[0]][move[1]] = "";
        
        boolean stillSurrounded = isKingSurrounded(king);
        
        board[move[0]][move[1]] = board[move[2]][move[3]];
        board[move[2]][move[3]] = captured;
        
        return !stillSurrounded;
    }
    
    private boolean canPieceAttackSquare(int fromRow, int fromCol, int toRow, int toCol, String piece) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        switch (piece) {
            case "♕": // White Queen
                return rowDiff == colDiff || fromRow == toRow || fromCol == toCol;
            case "♖": // White Rook  
                return fromRow == toRow || fromCol == toCol;
            case "♗": // White Bishop
                return rowDiff == colDiff;
            case "♘": // White Knight
                return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♙": // White Pawn
                return fromRow - toRow == 1 && colDiff == 1; // Diagonal attack
            default:
                return false;
        }
    }
    
    private int[] findKing(boolean isWhite) {
        String king = isWhite ? "♔" : "♚";
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (king.equals(board[i][j])) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }
    
    private int[] compareMoves(List<int[]> moves, List<String> aiNames) {
        int[] bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        logger.debug("=== AI MOVE COMPARISON ===");
        
        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);
            double score = evaluateMoveQuality(move);
            
            String piece = board[move[0]][move[1]];
            String aiName = (i < aiNames.size()) ? aiNames.get(i) : "AI-" + i;
            logger.debug("  {}: {} [{},{}] to [{},{}] - Score: {}", aiName, piece, move[0], move[1], move[2], move[3], String.format("%.1f", score));
            
            if (score > bestScore || (score == bestScore && random.nextBoolean())) {
                bestScore = score;
                bestMove = move;
            }
        }
        
        return bestMove;
    }
    
    private double evaluateMoveQuality(int[] move) {
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        double score = 0.0;
        
        // STRATEGIC PENALTIES FOR ALL PIECES
        // Prevent early King moves
        if ("♚".equals(piece) && moveHistory.size() < 10) {
            if (!isKingInDanger(false) && Math.abs(move[3] - move[1]) != 2) {
                score -= 1000.0;
            }
        }
        
        // Prevent useless piece shuffling for all major pieces
        String[] majorPieces = {"♛", "♜", "♝", "♞"};
        for (String majorPiece : majorPieces) {
            if (majorPiece.equals(piece)) {
                // Penalty for moving back to starting position without purpose
                if (isUselessShuffle(move, majorPiece)) {
                    score -= 300.0;
                }
                // Penalty for moving to less active squares
                if (isRetreatMove(move, majorPiece)) {
                    score -= 200.0;
                }
            }
        }
        
        // FLIP-FLOP PREVENTION: Heavy penalty for repetitive moves
        if (isFlipFlopMove(move)) {
            score -= 500.0;
            logger.debug("FLIP-FLOP PENALTY: -500 for repetitive move");
        }
        
        // Simulate the move
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        // Determine which player is moving
        boolean isWhiteMoving = "♔♕♖♗♘♙".contains(piece);
        
        // CHECKMATE DETECTION: Highest priority bonus
        if (isKingInDanger(!isWhiteMoving)) {
            if (isPlayerInCheckmate(!isWhiteMoving)) {
                score += 10000.0; // CHECKMATE BONUS - highest priority
                logger.debug("CHECKMATE BONUS: +10000 for checkmate move");
            } else {
                score += 500.0; // Check bonus
                logger.debug("CHECK BONUS: +500 for check move");
            }
        }
        
        // CHECKMATE THREAT ANTICIPATION: Detect if this move prevents imminent checkmate
        if (preventsImminentCheckmate(move)) {
            score += 2000.0; // High priority for preventing checkmate
            logger.debug("CHECKMATE PREVENTION BONUS: +2000 for blocking imminent checkmate");
        }
        
        // STRATEGIC BONUSES FOR ALL PIECES
        // Piece-specific strategic evaluation
        score += evaluatePieceStrategy(move, piece);
        
        // TACTICAL PRIORITY: Recapture after losing material
        if (!captured.isEmpty() && moveHistory.size() >= 2) {
            String lastMove = moveHistory.get(moveHistory.size() - 1);
            if (lastMove.contains("x")) {
                double captureValue = getChessPieceValue(captured);
                score += captureValue * 5.0;
                logger.debug("RECAPTURE BONUS: +{} for recapturing {}", captureValue * 5.0, captured);
            }
        }
        
        // Capture bonus - MAJOR PRIORITY with enhanced high-value piece preference
        if (!captured.isEmpty()) {
            double captureValue = getChessPieceValue(captured);
            score += captureValue * 3.0; // Triple reward for captures (increased from 2.0)
            
            // MASSIVE bonus for capturing high-value pieces
            if (captureValue >= 700) { // Knight value
                score += 1000.0; // Major bonus for Knight/Bishop/Rook/Queen captures
            } else if (captureValue >= 500) { // Bishop/Rook value
                score += 600.0; // Significant bonus for Bishop/Rook captures
            } else if (captureValue >= 300) { // Minor piece value
                score += 200.0; // Good bonus for minor piece captures
            }
            
            // Additional bonus for trading up (capturing higher value piece)
            double pieceValue = getChessPieceValue(piece);
            if (captureValue > pieceValue) {
                double tradingUpBonus = (captureValue - pieceValue) * 2.0;
                score += tradingUpBonus;
                logger.debug("TRADING UP BONUS: +{} for {} capturing {}", tradingUpBonus, piece, captured);
            }
        }
        
        // ATTACK BONUS: Reward moves that threaten enemy pieces
        score += evaluateAttackThreats(move[2], move[3]) * 5.0;
        
        // Check bonus - AGGRESSIVE PRIORITY
        if (isKingInDanger(true)) {
            score += 800.0; // Increased from 500
        }
        
        // CASTLING BONUS - Strategic advantage
        if ("♚".equals(piece) && Math.abs(move[3] - move[1]) == 2) {
            score += 500.0; // Major bonus for castling
            logger.debug("CASTLING BONUS: +500 for King castling move");
        }
        
        // DEFENSIVE BONUS: Reward moves that save threatened pieces
        String originalPiece = board[move[0]][move[1]];
        if (isSquareUnderAttack(move[0], move[1], true)) {
            double pieceValue = getChessPieceValue(originalPiece);
            score += pieceValue * 1.5; // Bonus for saving threatened piece
            
            // MASSIVE bonus for saving the Queen
            if ("♛".equals(originalPiece)) {
                score += 2000.0; // Critical Queen defense
                logger.debug("QUEEN DEFENSE BONUS: +2000 for saving threatened Queen");
            }
        }
        
        // FORK BONUS: Reward moves that attack multiple pieces
        int threatenedPieces = countThreatenedEnemyPieces(move[2], move[3]);
        if (threatenedPieces > 1) {
            score += threatenedPieces * 200.0; // Major bonus for forks
        }
        
        // Development bonus for pieces moving from starting positions
        if (("♜♞♝♛♚".contains(piece) && move[0] == 0) || ("♟".equals(piece) && move[0] == 1)) {
            score += 80.0;
            
            // EXTRA bonus for moves that enable castling
            if (!blackKingMoved && ("♞♝".contains(piece) || "♛".equals(piece))) {
                // Moving knight/bishop/queen from back rank enables castling
                if ((move[1] >= 1 && move[1] <= 3) || (move[1] >= 5 && move[1] <= 6)) {
                    score += 100.0; // Bonus for clearing castling path
                    logger.debug("CASTLING PREP BONUS: +100 for clearing castling path");
                }
            }
        }
        
        // Safety bonus (piece not under attack)
        if (!isSquareUnderAttack(move[2], move[3], true)) {
            score += 100.0;
        }
        
        // Center control bonus
        if (move[2] >= 3 && move[2] <= 4 && move[3] >= 3 && move[3] <= 4) {
            score += 50.0;
        }
        
        // AGGRESSIVE POSITIONING: Reward advancing pieces toward enemy
        if (move[2] > move[0]) { // Moving toward White's side
            score += (move[2] - move[0]) * 20.0;
        }
        
        // NEGATIVE FACTORS
        // Hanging piece penalty (but allow tactical sacrifices)
        if (isSquareUnderAttack(move[2], move[3], true)) {
            double pieceValue = getChessPieceValue(piece);
            double capturedValue = getChessPieceValue(captured);
            
            // Only penalize if it's not a favorable sacrifice
            if (capturedValue < pieceValue * 0.7) {
                // Increased penalty for high-value pieces to reduce sacrificial behavior
                double penalty = pieceValue >= 300 ? 500.0 : pieceValue * 0.6;
                score -= penalty;
            }
        }
        
        // King danger penalty
        if (isKingInDanger(false)) {
            score -= 300.0;
        }
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        return score;
    }
    
    private boolean isPawnPromotion(String piece, int toRow) {
        return ruleValidator.isPawnPromotion(piece, toRow);
    }
    
    private boolean handlePawnPromotion(String piece, int row, int col) {
        // White pawn promotion (reaches row 0)
        if ("♙".equals(piece) && row == 0) {
            board[row][col] = "♕"; // Promote to Queen
            return true;
        }
        // Black pawn promotion (reaches row 7)
        if ("♟".equals(piece) && row == 7) {
            board[row][col] = "♛"; // Promote to Queen
            return true;
        }
        return false;
    }
    

    
    public boolean promotePawn(int row, int col, String promotionPiece) {
        String piece = board[row][col];
        if ("♙".equals(piece) && row == 0) {
            board[row][col] = promotionPiece;
            return true;
        }
        if ("♟".equals(piece) && row == 7) {
            // Convert white piece to black equivalent
            switch (promotionPiece) {
                case "♕": board[row][col] = "♛"; break;
                case "♖": board[row][col] = "♜"; break;
                case "♗": board[row][col] = "♝"; break;
                case "♘": board[row][col] = "♞"; break;
            }
            return true;
        }
        return false;
    }
    
    private int[] findPieceProtectionMove(int[] piecePos, String pieceType) {
        List<int[]> attackers = findAttackersOfSquare(piecePos[0], piecePos[1], true);
        
        if (attackers.isEmpty()) {
            System.out.println("ERROR: Piece protection called but no attackers found!");
            return null;
        }
        
        System.out.println(pieceType + " at [" + piecePos[0] + "," + piecePos[1] + "] is attacked by " + attackers.size() + " piece(s)");
        
        List<int[]> moves = getAllValidMoves(false);
        
        // PRIORITY 1: Look for HIGH-VALUE captures first, but check if piece will be safe
        // Check if the threatened piece can capture something valuable AND survive
        for (int[] move : moves) {
            String piece = board[move[0]][move[1]];
            if (pieceType.equals(piece) && move[0] == piecePos[0] && move[1] == piecePos[1]) {
                String captured = board[move[2]][move[3]];
                if (!captured.isEmpty() && isValidMove(move[0], move[1], move[2], move[3])) {
                    double capturedValue = getChessPieceValue(captured);
                    double pieceValue = getChessPieceValue(pieceType);
                    double attackerValue = attackers.isEmpty() ? 0 : getChessPieceValue(board[attackers.get(0)[0]][attackers.get(0)[1]]);
                    
                    // Simulate the capture to check if piece will be safe
                    board[move[2]][move[3]] = piece;
                    board[move[0]][move[1]] = "";
                    boolean pieceWillBeSafe = !isSquareUnderAttack(move[2], move[3], true);
                    board[move[0]][move[1]] = piece;
                    board[move[2]][move[3]] = captured;
                    
                    // Only capture if it's a good trade OR piece will be safe
                    if (pieceWillBeSafe || capturedValue >= pieceValue * 0.8) {
                        if (capturedValue > attackerValue) {
                            System.out.println("BETTER CAPTURE DEFENSE: " + pieceType + " captures " + captured + " (" + capturedValue + ") instead of attacker (" + attackerValue + ") - Safe: " + pieceWillBeSafe);
                            return move;
                        }
                    } else {
                        System.out.println("CAPTURE REJECTED: " + pieceType + " capturing " + captured + " would hang piece for insufficient compensation");
                    }
                }
            }
        }
        
        // PRIORITY 2: Capture the attacking piece(s) if no better option
        for (int[] attacker : attackers) {
            for (int[] move : moves) {
                if (move[2] == attacker[0] && move[3] == attacker[1]) {
                    if (isValidMove(move[0], move[1], move[2], move[3])) {
                        String piece = board[move[0]][move[1]];
                        String captured = board[move[2]][move[3]];
                        System.out.println("CAPTURE DEFENSE: " + piece + " captures attacker " + captured + " threatening " + pieceType);
                        return move;
                    }
                }
            }
        }
        
        // PRIORITY 2: Move the threatened piece to safety (but check for pins first)
        for (int[] move : moves) {
            String piece = board[move[0]][move[1]];
            
            if (pieceType.equals(piece) && move[0] == piecePos[0] && move[1] == piecePos[1]) {
                // CRITICAL: Check if piece is pinned before moving (to King or Queen)
                if (isPiecePinned(piecePos[0], piecePos[1])) {
                    System.out.println("PIECE PINNED: " + pieceType + " at [" + piecePos[0] + "," + piecePos[1] + "] cannot move - would expose King!");
                    continue; // Skip this move - piece is pinned to King
                }
                
                // NEW: Check if piece is pinned to Queen
                if (isPiecePinnedToQueen(piecePos[0], piecePos[1])) {
                    System.out.println("PIECE PINNED TO QUEEN: " + pieceType + " at [" + piecePos[0] + "," + piecePos[1] + "] cannot move - would expose Queen!");
                    continue; // Skip this move - piece is pinned to Queen
                }
                
                // CRITICAL: Check if moving this piece would remove critical defense (e.g., Scholar's Mate defense)
                if (ChessTacticalDefense.wouldRemoveCriticalDefense(board, move)) {
                    System.out.println("CRITICAL DEFENSE PRESERVED: " + pieceType + " at [" + piecePos[0] + "," + piecePos[1] + "] cannot retreat - defending against checkmate threat!");
                    continue; // Skip this move - piece is defending against critical threat
                }
                
                if (isValidMove(move[0], move[1], move[2], move[3])) {
                    String captured = board[move[2]][move[3]];
                    
                    board[move[2]][move[3]] = piece;
                    board[move[0]][move[1]] = "";
                    
                    boolean pieceStillThreatened = isSquareUnderAttack(move[2], move[3], true);
                    boolean exposesQueen = false;
                    
                    // CRITICAL: Check if this move exposes the Queen to attack
                    int[] queenPos = findPiecePosition("♛");
                    if (queenPos != null) {
                        exposesQueen = isSquareUnderAttack(queenPos[0], queenPos[1], true);
                        if (exposesQueen) {
                            System.out.println("PIECE ESCAPE REJECTED: Moving " + pieceType + " would expose Queen to attack!");
                        }
                    }
                    
                    board[move[0]][move[1]] = piece;
                    board[move[2]][move[3]] = captured;
                    
                    if (!pieceStillThreatened && !exposesQueen) {
                        System.out.println("PIECE ESCAPE: Moving " + pieceType + " to safe square [" + move[2] + "," + move[3] + "]");
                        return move;
                    }
                }
            }
        }
        
        // PRIORITY 3: Block the attack (for sliding pieces)
        for (int[] attacker : attackers) {
            String attackerPiece = board[attacker[0]][attacker[1]];
            if ("♖♜♗♝♕♛".contains(attackerPiece)) { // Sliding pieces only
                List<int[]> blockingSquares = getBlockingSquares(attacker, piecePos);
                for (int[] blockSquare : blockingSquares) {
                    for (int[] move : moves) {
                        String piece = board[move[0]][move[1]];
                        if (!pieceType.equals(piece) && move[2] == blockSquare[0] && move[3] == blockSquare[1]) {
                            if (isValidMove(move[0], move[1], move[2], move[3])) {
                                System.out.println("BLOCK DEFENSE: " + piece + " blocks attack on " + pieceType);
                                return move;
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("NO PIECE PROTECTION FOUND - " + pieceType + " may be lost!");
        return null;
    }

    
    /**
     * COMPREHENSIVE CHECKMATE DETECTION
     * Follows official chess rules:
     * 1. King is in check
     * 2. No legal moves can get King out of check
     */
    private boolean isPlayerInCheckmate(boolean forWhite) {
        return ruleValidator.isCheckmate(board, forWhite);
    }
    
    /**
     * STRICT LEGAL CHESS MOVE VALIDATION
     * A move is legal if:
     * 1. Piece can move to that square (piece movement rules)
     * 2. Path is not blocked (for sliding pieces)
     * 3. Not capturing own piece
     * 4. After the move, own King is not in check
     */
    private boolean isLegalChessMove(int fromRow, int fromCol, int toRow, int toCol, boolean forWhite) {
        String piece = board[fromRow][fromCol];
        String target = board[toRow][toCol];
        
        // Basic validation
        if (piece.isEmpty()) return false;
        
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        if (isPieceWhite != forWhite) return false;
        
        // Can't capture own piece
        if (!target.isEmpty()) {
            boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
            if (isTargetWhite == isPieceWhite) return false;
        }
        
        // Check piece movement rules
        if (!ruleValidator.isValidPieceMove(piece, fromRow, fromCol, toRow, toCol)) return false;
        
        // Check path (except for knights)
        if (!"♘♞".contains(piece) && ruleValidator.isPathBlocked(board, fromRow, fromCol, toRow, toCol)) return false;
        
        // CRITICAL: Simulate move and check if King would be safe
        String originalTarget = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = "";
        
        boolean kingWouldBeSafe = !isKingInDanger(forWhite);
        
        // Restore board
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = originalTarget;
        
        return kingWouldBeSafe;
    }
    
    /**
     * FIND LEGAL CHECK RESPONSE
     * Returns moves that get the King out of check: King escape, capture attacker, or block
     */
    private int[] findLegalCheckResponse() {
        boolean originalTurn = whiteTurn;
        whiteTurn = false; // Black's turn
        
        try {
            // STEP 1: Find who is attacking the King
            int[] kingPos = findPiecePosition("♚");
            if (kingPos == null) return null;
            
            List<int[]> attackers = findAttackersOfSquare(kingPos[0], kingPos[1], true);
            logger.info("Black King at [{},{}] attacked by {} piece(s)", kingPos[0], kingPos[1], attackers.size());
            
            // STEP 2: Try to capture attacking pieces
            for (int[] attacker : attackers) {
                String attackerPiece = board[attacker[0]][attacker[1]];
                logger.debug("Checking if any Black piece can capture attacker {} at [{},{}]", attackerPiece, attacker[0], attacker[1]);
                
                for (int fromRow = 0; fromRow < 8; fromRow++) {
                    for (int fromCol = 0; fromCol < 8; fromCol++) {
                        String piece = board[fromRow][fromCol];
                        if (!piece.isEmpty() && "♚♛♜♝♞♟".contains(piece)) {
                            // Use the full isValidMove check to ensure move is actually legal
                            boolean savedTurn = whiteTurn;
                            whiteTurn = false; // Set to Black's turn for validation
                            
                            if (isValidMove(fromRow, fromCol, attacker[0], attacker[1])) {
                                System.out.println("CAPTURE SOLUTION: " + piece + " [" + fromRow + "," + fromCol + "] captures " + attackerPiece + " [" + attacker[0] + "," + attacker[1] + "]");
                                whiteTurn = savedTurn;
                                return new int[]{fromRow, fromCol, attacker[0], attacker[1]};
                            } else {
                                logger.debug("INVALID CAPTURE: {} [{},{}] cannot legally capture [{},{}]", piece, fromRow, fromCol, attacker[0], attacker[1]);
                            }
                            
                            whiteTurn = savedTurn;
                        }
                    }
                }
            }
            
            // STEP 3: Try King escape moves
            logger.debug("Testing King escape moves from [{},{}]", kingPos[0], kingPos[1]);
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int newRow = kingPos[0] + dr;
                    int newCol = kingPos[1] + dc;
                    
                    if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                        String targetSquare = board[newRow][newCol];
                        logger.debug("Testing King move to [{},{}] - contains: '{}'", newRow, newCol, targetSquare);
                        
                        boolean currentTurn = whiteTurn;
                        whiteTurn = false; // Set to Black's turn for validation
                        
                        if (isValidMove(kingPos[0], kingPos[1], newRow, newCol)) {
                            System.out.println("KING ESCAPE: King [" + kingPos[0] + "," + kingPos[1] + "] to [" + newRow + "," + newCol + "]");
                            whiteTurn = currentTurn;
                            return new int[]{kingPos[0], kingPos[1], newRow, newCol};
                        } else {
                            logger.debug("KING ESCAPE BLOCKED: [{},{}] - move not legal", newRow, newCol);
                        }
                        
                        whiteTurn = currentTurn;
                    }
                }
            }
            
            // STEP 4: Try blocking moves (for sliding piece attacks)
            for (int[] attacker : attackers) {
                String attackerPiece = board[attacker[0]][attacker[1]];
                if ("♕♛♖♜♗♝".contains(attackerPiece)) { // Sliding pieces
                    List<int[]> blockingSquares = getBlockingSquares(attacker, kingPos);
                    logger.debug("BLOCKING: Found {} potential blocking squares for {}", blockingSquares.size(), attackerPiece);
                    
                    for (int[] blockSquare : blockingSquares) {
                        logger.debug("BLOCKING: Testing block square [{},{}]", blockSquare[0], blockSquare[1]);
                        
                        for (int fromRow = 0; fromRow < 8; fromRow++) {
                            for (int fromCol = 0; fromCol < 8; fromCol++) {
                                String piece = board[fromRow][fromCol];
                                if (!piece.isEmpty() && "♚♛♜♝♞♟".contains(piece) && !"♚".equals(piece)) {
                                    // Use actuallyResolvesCheck for precise check resolution validation
                                    if (actuallyResolvesCheck(fromRow, fromCol, blockSquare[0], blockSquare[1])) {
                                        System.out.println("BLOCK SOLUTION: " + piece + " [" + fromRow + "," + fromCol + "] blocks at [" + blockSquare[0] + "," + blockSquare[1] + "]");
                                        return new int[]{fromRow, fromCol, blockSquare[0], blockSquare[1]};
                                    } else {
                                        logger.debug("BLOCK REJECTED: {} [{},{}] to [{},{}] - doesn't resolve check", piece, fromRow, fromCol, blockSquare[0], blockSquare[1]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return null; // No legal response found
            
        } finally {
            whiteTurn = originalTurn;
        }
    }
    
    /**
     * Get squares between attacker and king that can block the attack
     * 
     * This method calculates all squares along the line of attack from a sliding piece
     * (Queen, Rook, Bishop) to the King. These squares can potentially be occupied
     * by friendly pieces to block the attack and resolve check.
     * 
     * @param attacker Position of the attacking piece [row, col]
     * @param king Position of the King under attack [row, col]
     * @return List of squares that can block the attack
     */
    private List<int[]> getBlockingSquares(int[] attacker, int[] king) {
        List<int[]> blockingSquares = new ArrayList<>();
        
        // Calculate direction from attacker to king
        int rowDiff = king[0] - attacker[0];
        int colDiff = king[1] - attacker[1];
        
        // Normalize to get direction (-1, 0, or 1)
        int rowDir = Integer.signum(rowDiff);
        int colDir = Integer.signum(colDiff);
        
        // Start from square next to attacker, move toward king
        int currentRow = attacker[0] + rowDir;
        int currentCol = attacker[1] + colDir;
        
        // Add all squares between attacker and king (exclusive)
        while (currentRow != king[0] || currentCol != king[1]) {
            blockingSquares.add(new int[]{currentRow, currentCol});
            logger.debug("BLOCKING: Square [{},{}] can block attack", currentRow, currentCol);
            currentRow += rowDir;
            currentCol += colDir;
        }
        
        return blockingSquares;
    }
    
    /**
     * CRITICAL: Test if a move actually resolves check
     * 
     * This method is more precise than isValidMove for check resolution scenarios.
     * It specifically tests whether a proposed move will get the King out of check,
     * which is essential for legal play when the King is under attack.
     * 
     * The method simulates the move and checks if the King is still in danger,
     * ensuring that only moves that actually resolve the check are considered legal.
     * 
     * @param fromRow Source row of the move
     * @param fromCol Source column of the move  
     * @param toRow Destination row of the move
     * @param toCol Destination column of the move
     * @return true if the move resolves check, false otherwise
     */
    private boolean actuallyResolvesCheck(int fromRow, int fromCol, int toRow, int toCol) {
        String piece = board[fromRow][fromCol];
        if (piece.isEmpty() || !"♚♛♜♝♞♟".contains(piece)) {
            return false;
        }
        
        // CRITICAL: Use full move validation to ensure move is actually legal
        boolean originalTurn = whiteTurn;
        whiteTurn = false; // Set to Black's turn for validation
        
        boolean isLegalMove = isValidMove(fromRow, fromCol, toRow, toCol);
        
        whiteTurn = originalTurn; // Restore original turn
        
        if (!isLegalMove) {
            return false; // Move is not legal, cannot resolve check
        }
        
        // Simulate the move
        String target = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = "";
        
        // Check if Black King is still in danger after this move
        boolean kingStillInCheck = isKingInDanger(false);
        
        // Restore the board
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = target;
        
        // Return true only if the move gets the King out of check
        boolean resolves = !kingStillInCheck;
        if (resolves) {
            logger.info("CHECK RESOLVED: {} [{},{}] to [{},{}]", piece, fromRow, fromCol, toRow, toCol);
        }
        return resolves;
    }
    
    /**
     * CRITICAL: Verify that a move actually resolves check
     * This prevents illegal moves that leave the King in check
     */
    private boolean wouldMoveResolveCheck(int[] move) {
        // If not in check, any legal move is fine
        if (!isKingInDanger(false)) {
            return true;
        }
        
        // Simulate the move
        String piece = board[move[0]][move[1]];
        String captured = board[move[2]][move[3]];
        
        board[move[2]][move[3]] = piece;
        board[move[0]][move[1]] = "";
        
        // Check if King is still in danger after move
        boolean kingStillInCheck = isKingInDanger(false);
        
        // Restore board
        board[move[0]][move[1]] = piece;
        board[move[2]][move[3]] = captured;
        
        return !kingStillInCheck;
    }
    
    /**
     * Check if a piece is pinned (cannot move without exposing King to check)
     * 
     * A pinned piece is one that cannot legally move because doing so would expose
     * the King to check from an enemy piece. This is a critical concept in chess
     * that prevents many otherwise legal-looking moves.
     * 
     * The method temporarily removes the piece and checks if the King would be
     * in danger, indicating that the piece is pinned and cannot move.
     * 
     * This validation was added to fix issues where the AI was trying to move
     * pinned pieces, particularly the Queen when it was pinned to the King.
     * 
     * @param pieceRow Row of the piece to check
     * @param pieceCol Column of the piece to check
     * @return true if the piece is pinned, false otherwise
     */
    private boolean isPiecePinned(int pieceRow, int pieceCol) {
        return ruleValidator.isPiecePinned(board, pieceRow, pieceCol);
    }
    
    /**
     * Check if a piece is pinned to the Queen (cannot move without exposing Queen to attack)
     * 
     * Similar to King pinning, but checks if moving the piece would expose the Queen
     * to attack from enemy pieces. This prevents tactical blunders where moving a
     * defending piece allows the Queen to be captured.
     * 
     * @param pieceRow Row of the piece to check
     * @param pieceCol Column of the piece to check
     * @return true if the piece is pinned to Queen, false otherwise
     */
    private boolean isPiecePinnedToQueen(int pieceRow, int pieceCol) {
        String piece = board[pieceRow][pieceCol];
        if (piece.isEmpty()) return false;
        
        boolean isWhite = "♔♕♖♗♘♙".contains(piece);
        String queenPiece = isWhite ? "♕" : "♛";
        
        // Find Queen position
        int[] queenPos = findPiecePosition(queenPiece);
        if (queenPos == null) return false; // No Queen on board
        
        // Temporarily remove the piece
        board[pieceRow][pieceCol] = "";
        
        // Check if Queen would be under attack without this piece
        boolean queenInDanger = isSquareUnderAttack(queenPos[0], queenPos[1], !isWhite);
        
        // Restore the piece
        board[pieceRow][pieceCol] = piece;
        
        if (queenInDanger) {
            System.out.println("QUEEN PIN DETECTED: " + piece + " at [" + pieceRow + "," + pieceCol + "] is pinned - removing it exposes Queen!");
        }
        
        return queenInDanger;
    }
    

    
    /**
     * Check if a move would create a flip-flop pattern
     * 
     * Flip-flop detection prevents the AI from making repetitive moves that
     * don't contribute to game progress. This includes:
     * - Moving the same piece back and forth between two squares
     * - Repeating the same move multiple times
     * - Undoing the previous move without tactical purpose
     * 
     * This system was implemented to improve AI play quality and prevent
     * endless repetition in non-critical positions.
     * 
     * @param move The move to check [fromRow, fromCol, toRow, toCol]
     * @return true if the move creates a flip-flop pattern, false otherwise
     */
    private boolean isFlipFlopMove(int[] move) {
        String moveKey = formatMoveKey(move);
        
        // Check repetition count
        int count = moveRepetitionCount.getOrDefault(moveKey, 0);
        if (count >= 2) {
            return true;
        }
        
        // Check if this move undoes the last move
        if (recentMoves.size() >= 2) {
            String lastMove = recentMoves.get(recentMoves.size() - 1);
            String reverseMove = move[2] + "," + move[3] + "," + move[0] + "," + move[1];
            if (lastMove.equals(reverseMove)) {
                System.out.println("FLIP-FLOP: Move " + moveKey + " undoes " + lastMove);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Track move for flip-flop detection
     * 
     * Maintains a history of recent moves and their repetition counts to
     * identify and prevent flip-flop patterns. The system keeps track of:
     * - Last 6 moves for pattern recognition
     * - Repetition count for each unique move
     * - Automatic cleanup of old move data
     * 
     * @param move The move to track [fromRow, fromCol, toRow, toCol]
     */
    private void trackMove(int[] move) {
        String moveKey = formatMoveKey(move);
        
        // Add to recent moves (keep last 6 moves)
        recentMoves.add(moveKey);
        if (recentMoves.size() > 6) {
            recentMoves.remove(0);
        }
        
        // Update repetition count
        moveRepetitionCount.put(moveKey, moveRepetitionCount.getOrDefault(moveKey, 0) + 1);
        
        // Clean old entries (keep last 20 moves)
        if (moveRepetitionCount.size() > 20) {
            String oldestMove = recentMoves.get(0);
            moveRepetitionCount.remove(oldestMove);
        }
    }
    
    /**
     * Format move as string key for tracking
     */
    private String formatMoveKey(int[] move) {
        return move[0] + "," + move[1] + "," + move[2] + "," + move[3];
    }
    
    /**
     * Format move to algebraic notation for opening book
     */
    private String formatMoveToAlgebraic(int[] move) {
        try {
            char fromFile = (char)('a' + move[1]);
            char toFile = (char)('a' + move[3]);
            int fromRank = 8 - move[0];
            int toRank = 8 - move[2];
            return "" + fromFile + fromRank + toFile + toRank;
        } catch (Exception e) {
            logger.error("Error formatting move to algebraic: {}", e.getMessage());
            return "e2e4"; // Fallback
        }
    }
    
    /**
     * Encode current board state to FEN notation for debugging
     */
    private String encodeBoardToFEN() {
        try {
            StringBuilder fen = new StringBuilder();
            
            for (int i = 0; i < 8; i++) {
                int emptyCount = 0;
                for (int j = 0; j < 8; j++) {
                    String piece = board[i][j];
                    if (piece == null || piece.isEmpty()) {
                        emptyCount++;
                    } else {
                        if (emptyCount > 0) {
                            fen.append(emptyCount);
                            emptyCount = 0;
                        }
                        fen.append(convertUnicodeToFEN(piece));
                    }
                }
                if (emptyCount > 0) {
                    fen.append(emptyCount);
                }
                if (i < 7) fen.append("/");
            }
            
            return fen.toString();
        } catch (Exception e) {
            logger.error("Error encoding board to FEN: {}", e.getMessage());
            return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"; // Fallback to starting position
        }
    }
    
    /**
     * Convert Unicode chess piece to FEN notation
     */
    private String convertUnicodeToFEN(String unicodePiece) {
        switch (unicodePiece) {
            case "♔": return "K"; case "♕": return "Q"; case "♖": return "R";
            case "♗": return "B"; case "♘": return "N"; case "♙": return "P";
            case "♚": return "k"; case "♛": return "q"; case "♜": return "r";
            case "♝": return "b"; case "♞": return "n"; case "♟": return "p";
            default: return "";
        }
    }
    
    /**
     * Evaluate piece-specific strategic value
     */
    private double evaluatePieceStrategy(int[] move, String piece) {
        double score = 0.0;
        
        switch (piece) {
            case "♛": // Queen
                score += evaluateQueenStrategy(move);
                break;
            case "♜": // Rook
                score += evaluateRookStrategy(move);
                break;
            case "♝": // Bishop
                score += evaluateBishopStrategy(move);
                break;
            case "♞": // Knight
                score += evaluateKnightStrategy(move);
                break;
            case "♟": // Pawn
                score += evaluatePawnStrategy(move);
                break;
        }
        
        return score;
    }
    
    private double evaluateQueenStrategy(int[] move) {
        double score = 0.0;
        
        // Queen should be active but safe
        if (move[2] >= 3 && move[2] <= 5) score += 100.0; // Central activity
        if (isSquareDefended(move[2], move[3], false)) score += 150.0; // Safety
        if (countThreatenedEnemyPieces(move[2], move[3]) >= 2) score += 200.0; // Multi-threat
        
        return score;
    }
    
    private double evaluateRookStrategy(int[] move) {
        double score = 0.0;
        
        // Rooks prefer open files and ranks
        if (isOpenFile(move[3])) score += 150.0;
        if (move[2] == 1 || move[2] == 6) score += 100.0; // 7th/2nd rank
        if (move[0] == 0 && move[2] > 0) score += 80.0; // Development
        
        return score;
    }
    
    private double evaluateBishopStrategy(int[] move) {
        double score = 0.0;
        
        // Bishops prefer long diagonals
        if (isLongDiagonal(move[2], move[3])) score += 120.0;
        if (move[0] == 0 && move[2] > 0) score += 100.0; // Development
        if (countThreatenedEnemyPieces(move[2], move[3]) >= 1) score += 80.0;
        
        return score;
    }
    
    private double evaluateKnightStrategy(int[] move) {
        double score = 0.0;
        
        // Knights prefer central outposts
        if (move[2] >= 3 && move[2] <= 4 && move[3] >= 2 && move[3] <= 5) score += 150.0;
        if (move[0] == 0 && move[2] > 1) score += 120.0; // Development
        if (isOutpost(move[2], move[3])) score += 100.0;
        
        return score;
    }
    
    private double evaluatePawnStrategy(int[] move) {
        double score = 0.0;
        
        // Pawns should advance and support pieces
        if (move[2] > move[0]) score += 50.0; // Advance
        if (move[3] >= 3 && move[3] <= 4) score += 30.0; // Central
        if (move[2] >= 5) score += 80.0; // Advanced pawn
        
        return score;
    }
    
    /**
     * Check if move is useless shuffling
     */
    private boolean isUselessShuffle(int[] move, String piece) {
        // Check if piece returns to same rank/file without purpose
        if (move[0] == move[2] || move[1] == move[3]) {
            String captured = board[move[2]][move[3]];
            if (captured.isEmpty() && !isSquareDefended(move[2], move[3], false)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Enhanced check for Queen moves into Knight attack range
     */
//    private boolean isQueenMovingIntoKnightCheck(int[] move, String piece) {
//        if (!"♛".equals(piece)) return false;
//        
//        // Check if destination square is attacked by enemy Knight
//        int toRow = move[2], toCol = move[3];
//        
//        // Knight move patterns
//        int[][] knightMoves = {{-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}};
//        
//        for (int[] knightMove : knightMoves) {
//            int knightRow = toRow + knightMove[0];
//            int knightCol = toCol + knightMove[1];
//            
//            if (knightRow >= 0 && knightRow < 8 && knightCol >= 0 && knightCol < 8) {
//                String pieceAtSquare = board[knightRow][knightCol];
//                if ("♘".equals(pieceAtSquare)) { // White Knight
//                    System.out.println("QUEEN DANGER: Moving to [" + toRow + "," + toCol + "] puts Queen under attack by Knight at [" + knightRow + "," + knightCol + "]");
//                    return true;
//                }
//            }
//        }
//        
//        return false;
//    }
    
    /**
     * Check if move is retreating without purpose
     */
    private boolean isRetreatMove(int[] move, String piece) {
        // Moving backwards (toward own side) without tactical reason
        if (move[2] < move[0]) {
            String captured = board[move[2]][move[3]];
            if (captured.isEmpty() && !isKingInDanger(false)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if file is open (no pawns)
     */
    private boolean isOpenFile(int col) {
        for (int row = 0; row < 8; row++) {
            String piece = board[row][col];
            if ("♙".equals(piece) || "♟".equals(piece)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if square is on long diagonal
     */
    private boolean isLongDiagonal(int row, int col) {
        return (row + col == 7) || (row == col);
    }
    
    /**
     * Check if square is an outpost (defended by pawn, can't be attacked by enemy pawns)
     */
    private boolean isOutpost(int row, int col) {
        // Simplified: check if defended by own pawn
        if (row < 7 && col > 0 && "♟".equals(board[row + 1][col - 1])) return true;
        if (row < 7 && col < 7 && "♟".equals(board[row + 1][col + 1])) return true;
        return false;
    }
    
    /**
     * Check for critical threats that should override opening moves
     * 
     * This method identifies urgent tactical situations that require immediate
     * attention, overriding the normal opening book play. Critical threats include:
     * - Queen under attack (highest priority)
     * - King in check (mandatory response)
     * - Multiple important pieces under attack
     * - Strong opponent tactical opportunities
     * 
     * When critical threats are detected, the AI abandons opening theory
     * and focuses on tactical defense and counterplay.
     * 
     * @return true if critical threats exist, false otherwise
     */
    private boolean hasCriticalThreats() {
        // PRIORITY 1: Queen under attack
        int[] queenPos = findPiecePosition("♛");
        if (queenPos != null && isSquareUnderAttack(queenPos[0], queenPos[1], true)) {
            System.out.println("CRITICAL THREAT: Queen under attack at [" + queenPos[0] + "," + queenPos[1] + "]");
            return true;
        }
        
        // PRIORITY 2: King in check
        if (isKingInDanger(false)) {
            System.out.println("CRITICAL THREAT: King in check");
            return true;
        }
        
        // PRIORITY 3: Multiple pieces under attack
        int threatenedPieces = 0;
        String[] importantPieces = {"♜", "♝", "♞"}; // Rook, Bishop, Knight
        
        for (String pieceType : importantPieces) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (pieceType.equals(board[i][j]) && isSquareUnderAttack(i, j, true)) {
                        threatenedPieces++;
                        if (threatenedPieces >= 2) {
                            System.out.println("CRITICAL THREAT: Multiple important pieces under attack");
                            return true;
                        }
                    }
                }
            }
        }
        
        // PRIORITY 4: Opponent has strong tactical opportunity
        if (opponentHasTacticalThreat()) {
            System.out.println("CRITICAL THREAT: Opponent has strong tactical opportunity");
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if opponent has strong tactical threats
     */
    private boolean opponentHasTacticalThreat() {
        List<int[]> opponentMoves = getAllValidMoves(true); // White moves
        
        for (int[] move : opponentMoves) {
            String captured = board[move[2]][move[3]];
            
            // Check for high-value captures
            if (!captured.isEmpty() && getChessPieceValue(captured) >= 500) {
                return true;
            }
            
            // Check for checks
            String piece = board[move[0]][move[1]];
            board[move[2]][move[3]] = piece;
            board[move[0]][move[1]] = "";
            
            boolean givesCheck = isKingInDanger(false);
            
            // Restore board
            board[move[0]][move[1]] = piece;
            board[move[2]][move[3]] = captured;
            
            if (givesCheck) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Recursively delete directory and all its contents
     */
    private boolean deleteDirectory(java.io.File directory) {
        if (!directory.exists()) return true;
        
        if (directory.isDirectory()) {
            java.io.File[] files = directory.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (!deleteDirectory(file)) {
                        return false;
                    }
                }
            }
        }
        
        return directory.delete();
    }
}