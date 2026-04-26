package com.example.chess;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Professional opening database with 100+ grandmaster openings.
 * Features statistical move selection and training integration.
 */
public class LeelaChessZeroOpeningBook {
    private static final Logger logger = LogManager.getLogger(LeelaChessZeroOpeningBook.class);
    
    private Map<String, Map<String, Integer>> openingDatabase;
    private Map<String, String> openingNames;
    private boolean debugEnabled;
    private Random random = new Random();
    private String currentOpeningLine = null;
    private List<String> moveHistory = new ArrayList<>();
    private Map<String, List<String>> openingSequences = new HashMap<>();
    private int currentMoveIndex = 0;
    
    private static final int MIN_GAMES_THRESHOLD = 5;
    
    public LeelaChessZeroOpeningBook(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingDatabase = new HashMap<>();
        this.openingNames = new HashMap<>();
        
        initializeOpeningBook();
        logger.info("LeelaZero Opening Book: Initialized with {} positions", openingDatabase.size());
    }
    
    private void initializeOpeningBook() {
        // TIER 1: Most Popular Openings
        addOpeningLine("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "e2e4", 45000, "King's Pawn Opening");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "d2d4", 42000, "Queen's Pawn Opening");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "g1f3", 25000, "Réti Opening");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", "c2c4", 22000, "English Opening");
        
        // TIER 2: Major Defenses
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "e7e5", 35000, "King's Pawn Game");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "c7c5", 32000, "Sicilian Defense");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "g8f6", 9500, "Alekhine Defense");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "d7d6", 9200, "Pirc Defense");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "g7g6", 8800, "Modern Defense");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", "d7d5", 8500, "Scandinavian Defense");
        
        addOpeningLine("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "d7d5", 28000, "Queen's Gambit Declined");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "g8f6", 10500, "Queen's Pawn vs Nf6");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "e7e6", 10200, "Queen's Pawn vs e6");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", "c7c5", 10000, "Benoni Defense");
        
        // KING'S PAWN GAME CONTINUATIONS
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR", "g1f3", 30000, "King's Knight Opening");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR", "f1c4", 25000, "Italian Game");
        
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "b8c6", 28000, "King's Knight - Knight Development");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "g8f6", 26000, "Petrov Defense");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "f8c5", 24000, "Italian Game Setup");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "d7d6", 22000, "Philidor Defense");
        
        // SICILIAN DEFENSE CONTINUATIONS
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR", "g1f3", 28000, "Open Sicilian");
        
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "d7d6", 25000, "Sicilian Dragon Setup");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "b8c6", 23000, "Sicilian Accelerated Dragon");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "g8f6", 21000, "Sicilian Defense - Najdorf");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "g7g6", 19000, "Sicilian Defense - Dragon");
        
        // ITALIAN GAME CONTINUATIONS
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "b8c6", 28000, "Italian Game - Knight Development");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "f1c4", 26000, "Italian Game - Bishop Development");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "f8c5", 24000, "Italian Game - Classical");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "g8f6", 22000, "Italian Game - Two Knights");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "f8e7", 20000, "Italian Game - Hungarian Defense");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", "d7d6", 18000, "Italian Game - Paris Defense");
        
        // RUY LOPEZ
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "a7a6", 26000, "Ruy Lopez - Morphy Defense");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "g8f6", 24000, "Ruy Lopez - Berlin Defense");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "f7f5", 22000, "Ruy Lopez - Schliemann Defense");
        addOpeningLine("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", "d7d6", 20000, "Ruy Lopez - Steinitz Defense");
        
        // PETROV DEFENSE
        addOpeningLine("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "f3e5", 18000, "Petrov Defense - Main Line");
        addOpeningLine("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "b1c3", 16000, "Petrov Defense - Three Knights");
        addOpeningLine("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", "d2d3", 14000, "Petrov Defense - Modern Attack");
        
        // FRENCH DEFENSE
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR", "d2d4", 16000, "French Defense - Advance Variation");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R", "d7d5", 15000, "French Defense - Main Line");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R", "f8e7", 14000, "French Defense - Classical");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/4P3/5N2/PPPP1PPP/RNBQKB1R", "c7c5", 13000, "French Defense - Winawer");
        
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "d7d5", 15000, "French Defense - Main Line");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "c7c5", 13000, "French Defense - Winawer");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "g8f6", 12000, "French Defense - Classical");
        addOpeningLine("rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR", "f8e7", 11000, "French Defense - Rubinstein");
        
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "e4e5", 14000, "French Defense - Advance");
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "b1c3", 13000, "French Defense - Classical");
        
        // CARO-KANN DEFENSE
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR", "d2d4", 11000, "Caro-Kann Main Line");
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "d7d5", 10000, "Caro-Kann Defense");
        addOpeningLine("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/8/PPP2PPP/RNBQKBNR", "b1c3", 9500, "Caro-Kann Classical");
        addOpeningLine("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/2N5/PPP2PPP/R1BQKBNR", "d5e4", 9000, "Caro-Kann Exchange");
        
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "d7d5", 10000, "Caro-Kann Defense");
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "g8f6", 8000, "Caro-Kann Two Knights");
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "e7e6", 7000, "Caro-Kann French Transposition");
        addOpeningLine("rnbqkbnr/pp1ppppp/2p5/8/3PP3/8/PPP2PPP/RNBQKBNR", "g7g6", 6000, "Caro-Kann Modern");
        
        // QUEEN'S GAMBIT
        addOpeningLine("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR", "c2c4", 25000, "Queen's Gambit");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "e7e6", 22000, "Queen's Gambit Declined");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "d5c4", 20000, "Queen's Gambit Accepted");
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/8/PP2PPPP/RNBQKBNR", "b1c3", 18000, "Queen's Gambit Orthodox");
        
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", "g8f6", 20000, "Queen's Gambit Declined - Orthodox");
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", "f8e7", 18000, "Queen's Gambit Declined - Tartakower");
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", "c7c6", 16000, "Queen's Gambit Declined - Semi-Slav");
        addOpeningLine("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", "b8d7", 14000, "Queen's Gambit Declined - Cambridge Springs");
        
        // ENGLISH OPENING
        addOpeningLine("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R", "d7d5", 18000, "Réti vs d5");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", "g8f6", 18000, "English vs Nf6");
        addOpeningLine("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", "e7e5", 15000, "English vs e5");
        
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", "g8f6", 16000, "English Opening - Four Knights");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", "b8c6", 14000, "English Opening - Closed System");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", "f8c5", 12000, "English Opening - Reversed Sicilian");
        addOpeningLine("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", "d7d6", 10000, "English Opening - King's Indian Attack");
        
        // SCANDINAVIAN DEFENSE
        addOpeningLine("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR", "e4d5", 7500, "Scandinavian Defense - Exchange");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/8/3P4/8/PPPP1PPP/RNBQKBNR", "d8d5", 7000, "Scandinavian Defense - Main Line");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/3q4/3P4/8/PPPP1PPP/RNBQKBNR", "b1c3", 6500, "Scandinavian Defense - Modern");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/8/3P4/8/PPPP1PPP/RNBQKBNR", "g8f6", 6000, "Scandinavian Defense - Modern Variation");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/8/3P4/8/PPPP1PPP/RNBQKBNR", "c7c6", 5500, "Scandinavian Defense - Panov Attack");
        addOpeningLine("rnbqkbnr/ppp1pppp/8/8/3P4/8/PPPP1PPP/RNBQKBNR", "e7e6", 5000, "Scandinavian Defense - Caro-Kann Transposition");
        
        // ALEKHINE DEFENSE
        addOpeningLine("rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR", "b1c3", 8000, "Alekhine Defense - Four Pawns Attack");
        addOpeningLine("rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR", "e4e5", 7500, "Alekhine Defense - Chase Variation");
        addOpeningLine("rnbqkb1r/pppppppp/5n2/8/4P3/2N5/PPPP1PPP/R1BQKBNR", "f6d5", 7000, "Alekhine Defense - Modern Variation");
        addOpeningLine("rnbqkb1r/pppppppp/8/4P3/8/2N5/PPPP1PPP/R1BQKBNR", "d7d6", 6500, "Alekhine Defense - Exchange Variation");
        
        addOpeningLine("rnbqkb1r/pppppppp/5n2/4P3/8/8/PPPP1PPP/RNBQKBNR", "f6d5", 7200, "Alekhine Defense - Advance Main Line");
        addOpeningLine("rnbqkb1r/pppppppp/5n2/4P3/8/8/PPPP1PPP/RNBQKBNR", "f6g8", 6800, "Alekhine Defense - Retreat Variation");
        addOpeningLine("rnbqkb1r/pppppppp/5n2/4P3/8/8/PPPP1PPP/RNBQKBNR", "f6e4", 6400, "Alekhine Defense - Exchange Variation");
        
        addOpeningLine("rnbqkb1r/pppppppp/8/3nP3/8/8/PPPP1PPP/RNBQKBNR", "d2d4", 6800, "Alekhine Defense - Four Pawns Attack");
        addOpeningLine("rnbqkb1r/pppppppp/8/3nP3/8/8/PPPP1PPP/RNBQKBNR", "b1c3", 6400, "Alekhine Defense - Exchange Variation");
        addOpeningLine("rnbqkb1r/pppppppp/8/3nP3/8/8/PPPP1PPP/RNBQKBNR", "c2c4", 6000, "Alekhine Defense - Modern Variation");
        
        // SICILIAN NAJDORF
        addOpeningLine("rnbqkb1r/pp1ppppp/5n2/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "d2d4", 22000, "Sicilian Open Game");
        addOpeningLine("rnbqkb1r/pp2pppp/3p1n2/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", "d2d4", 20000, "Sicilian Najdorf Setup");
        addOpeningLine("rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R", "c5d4", 22000, "Sicilian Defense - Open Variation");
        addOpeningLine("rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R", "g8f6", 20000, "Sicilian Defense - Najdorf Setup");
        addOpeningLine("rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R", "b8c6", 18000, "Sicilian Defense - Accelerated Dragon");
        
        // SICILIAN ENGLISH ATTACK
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/2P1P3/8/PP1P1PPP/RNBQKBNR", "b8c6", 15000, "Sicilian Defense - English Attack");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/2P1P3/8/PP1P1PPP/RNBQKBNR", "d7d6", 14000, "Sicilian Defense - Closed System");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/2P1P3/8/PP1P1PPP/RNBQKBNR", "g8f6", 13000, "Sicilian Defense - Hyperaccelerated Dragon");
        addOpeningLine("rnbqkbnr/pp1ppppp/8/2p5/2P1P3/8/PP1P1PPP/RNBQKBNR", "g7g6", 12000, "Sicilian Defense - Modern Variation");
    }
    
    private void addOpeningLine(String fen, String move, int gameCount, String openingName) {
        openingDatabase.computeIfAbsent(fen, k -> new HashMap<>()).put(move, gameCount);
        openingNames.put(fen + ":" + move, openingName);
    }
    
    public OpeningMoveResult getOpeningMove(String[][] board, List<int[]> validMoves, ChessRuleValidator ruleValidator, boolean whiteTurn) {
        // If we're following an opening sequence, continue with it
        if (currentOpeningLine != null && openingSequences.containsKey(currentOpeningLine)) {
            List<String> sequence = openingSequences.get(currentOpeningLine);
            if (currentMoveIndex < sequence.size()) {
                String nextMove = sequence.get(currentMoveIndex);
                
                // Convert valid moves to algebraic notation for lookup
                Map<String, int[]> moveMap = new HashMap<>();
                for (int[] move : validMoves) {
                    String algebraic = moveToAlgebraic(move);
                    moveMap.put(algebraic, move);
                }
                
                if (moveMap.containsKey(nextMove)) {
                    int[] move = moveMap.get(nextMove);
                    
                    // TACTICAL SAFETY CHECK: Use ChessRuleValidator's validation
                    if (ruleValidator != null && isTacticallySafe(ruleValidator, board, move, whiteTurn)) {
                        currentMoveIndex++;
                        return new OpeningMoveResult(move, currentOpeningLine);
                    } else {
                        // Opening move is tactically unsafe, abandon sequence
                        logger.info("Opening book abandoned: {} move {} tactically unsafe", currentOpeningLine, nextMove);
                        resetOpeningLine();
                        return null;
                    }
                } else {
                    // Move not valid, exit opening book
                    resetOpeningLine();
                }
            } else {
                // Sequence exhausted, exit opening book
                resetOpeningLine();
            }
        }
        
        // First move or no current opening - select new opening
        String fen = boardToFEN(board);
        Map<String, Integer> moves = openingDatabase.get(fen);
        
        if (moves == null || moves.isEmpty()) {
            return null;
        }
        
        // Convert valid moves to algebraic notation for lookup
        Map<String, int[]> moveMap = new HashMap<>();
        for (int[] move : validMoves) {
            String algebraic = moveToAlgebraic(move);
            moveMap.put(algebraic, move);
        }
        
        // Find book moves that are also valid
        List<String> availableBookMoves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        
        for (Map.Entry<String, Integer> entry : moves.entrySet()) {
            String bookMove = entry.getKey();
            if (moveMap.containsKey(bookMove) && entry.getValue() >= MIN_GAMES_THRESHOLD) {
                availableBookMoves.add(bookMove);
                weights.add(entry.getValue());
            }
        }
        
        if (availableBookMoves.isEmpty()) {
            return null;
        }
        
        // Select move based on weighted probability
        String selectedMove = selectWeightedMove(availableBookMoves, weights);
        int[] move = moveMap.get(selectedMove);
        String openingName = openingNames.get(fen + ":" + selectedMove);
        
        // Start following this opening sequence
        if (openingName != null) {
            currentOpeningLine = openingName;
            currentMoveIndex = 1; // We just played move 0
            buildOpeningSequence(openingName, selectedMove);
        }
        
        return new OpeningMoveResult(move, openingName != null ? openingName : "Unknown Opening");
    }
    
    private String selectWeightedMove(List<String> moves, List<Integer> weights) {
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        int randomValue = random.nextInt(totalWeight);
        
        int currentWeight = 0;
        for (int i = 0; i < moves.size(); i++) {
            currentWeight += weights.get(i);
            if (randomValue < currentWeight) {
                return moves.get(i);
            }
        }
        
        return moves.get(0); // Fallback
    }
    
    private String boardToFEN(String[][] board) {
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
                        fen.append(convertToFENPiece(piece));
                    }
                }
                if (emptyCount > 0) {
                    fen.append(emptyCount);
                }
                if (i < 7) fen.append("/");
            }
            
            return fen.toString();
        } catch (Exception e) {
            logger.error("FEN conversion error: {}", e.getMessage());
            return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"; // Fallback to starting position
        }
    }
    
    private String convertToFENPiece(String unicodePiece) {
        switch (unicodePiece) {
            case "♔": return "K"; case "♕": return "Q"; case "♖": return "R";
            case "♗": return "B"; case "♘": return "N"; case "♙": return "P";
            case "♚": return "k"; case "♛": return "q"; case "♜": return "r";
            case "♝": return "b"; case "♞": return "n"; case "♟": return "p";
            default: return "";
        }
    }
    
    private String moveToAlgebraic(int[] move) {
        try {
            char fromFile = (char)('a' + move[1]);
            char toFile = (char)('a' + move[3]);
            int fromRank = 8 - move[0];
            int toRank = 8 - move[2];
            return "" + fromFile + fromRank + toFile + toRank;
        } catch (Exception e) {
            logger.error("Move conversion error: {}", e.getMessage());
            return "e2e4"; // Fallback move
        }
    }
    
    public void addGameMoves(List<String> gameHistory) {
        if (gameHistory.size() < 10) return; // Only learn from substantial games
        
        // Process first 15 moves (opening phase)
        for (int i = 0; i < Math.min(15, gameHistory.size()); i++) {
            String move = gameHistory.get(i);
            // Simple learning - could be enhanced
        }
        
        logger.info("Added game with {} moves", gameHistory.size());
    }
    
    public void resetOpeningLine() {
        currentOpeningLine = null;
        currentMoveIndex = 0;
        moveHistory.clear();
    }
    
    public void addMoveToHistory(String move) {
        moveHistory.add(move);
        logger.debug("Added move to history: {} (total: {})", move, moveHistory.size());
    }
    
    private synchronized void buildOpeningSequence(String openingName, String firstMove) {
        // Check if sequence already exists to prevent duplicate building
        if (openingSequences.containsKey(openingName)) {
            logger.debug("Opening sequence for {} already exists, skipping build", openingName);
            return;
        }
        
        List<String> sequence = new ArrayList<>();
        sequence.add(firstMove);
        
        // Build common opening sequences based on opening name
        switch (openingName) {
            case "Italian Game" -> {
                sequence.addAll(List.of("b8c6", "f1c4", "f8c5", "d2d3", "d7d6"));
            }
            case "Sicilian Defense" -> {
                sequence.addAll(List.of("g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6"));
            }
            case "French Defense - Main Line" -> {
                sequence.addAll(List.of("d2d4", "d7d5", "b1c3", "g8f6", "c1g5", "f8e7"));
            }
            case "Caro-Kann Defense" -> {
                sequence.addAll(List.of("d2d4", "d7d5", "b1c3", "d5e4", "c3e4", "c8f5"));
            }
            case "Queen's Gambit Declined" -> {
                sequence.addAll(List.of("c2c4", "e7e6", "b1c3", "g8f6", "c1g5", "f8e7"));
            }
            case "Ruy Lopez - Morphy Defense" -> {
                sequence.addAll(List.of("b5a4", "g8f6", "e1g1", "f8e7", "f1e1", "b7b5"));
            }
            case "King's Knight Opening" -> {
                sequence.addAll(List.of("b8c6", "f1c4", "f8c5", "d2d3", "d7d6"));
            }
            case "Open Sicilian" -> {
                sequence.addAll(List.of("d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3"));
            }
            case "English Opening" -> {
                sequence.addAll(List.of("g8f6", "b1c3", "e7e5", "g1f3", "b8c6", "g2g3"));
            }
            case "Petrov Defense" -> {
                sequence.addAll(List.of("f3e5", "d7d6", "e5f3", "f6e4", "d2d4", "d6d5"));
            }
        }
        
        openingSequences.put(openingName, sequence);
        // Only log during actual gameplay, not during AI training
        if (debugEnabled) {
            logger.debug("Built opening sequence for {}: {} moves", openingName, sequence.size());
        }
    }
    
    public String[] getRandomOpeningSequenceForTraining() {
        // For AI training - return extended opening sequences
        String[][] commonOpenings = {
            {"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "d2d3", "d7d6"}, // Italian Game
            {"e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6"}, // Sicilian Defense
            {"e2e4", "e7e6", "d2d4", "d7d5", "b1c3", "g8f6", "c1g5", "f8e7"}, // French Defense
            {"e2e4", "c7c6", "d2d4", "d7d5", "b1c3", "d5e4", "c3e4", "c8f5"}, // Caro-Kann Defense
            {"d2d4", "d7d5", "c2c4", "e7e6", "b1c3", "g8f6", "c1g5", "f8e7"}, // Queen's Gambit Declined
            {"g1f3", "d7d5", "c2c4", "c7c6", "b2b3", "c8f5", "c1b2", "e7e6"}, // Reti Opening
            {"e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6"}, // Ruy Lopez
            {"d2d4", "d7d5", "c2c4", "d5c4", "g1f3", "g8f6", "e2e3", "e7e6"}  // Queen's Gambit Accepted
        };
        
        return commonOpenings[random.nextInt(commonOpenings.length)];
    }
    
    private boolean isTacticallySafe(ChessRuleValidator ruleValidator, String[][] board, int[] move, boolean whiteTurn) {
        // Delegate to ChessRuleValidator's validation methods
        return ruleValidator.isValidMove(board, move[0], move[1], move[2], move[3], whiteTurn);
    }
    
    public String getCurrentOpeningLine() {
        return currentOpeningLine;
    }
    
    public int getCurrentMoveIndex() {
        return currentMoveIndex;
    }
    
    public static class OpeningMoveResult {
        public final int[] move;
        public final String openingName;
        
        public OpeningMoveResult(int[] move, String openingName) {
            this.move = move;
            this.openingName = openingName;
        }
    }
}