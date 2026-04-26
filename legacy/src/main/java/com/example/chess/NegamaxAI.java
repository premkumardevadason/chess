package com.example.chess;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Classical chess engine using Negamax with alpha-beta pruning.
 * Features iterative deepening, transposition tables, and time-bounded search.
 */
public class NegamaxAI {
    
    // Records for better data structures
    public record TranspositionEntry(int score, int depth, int flag, int[] bestMove) {}
    public record SearchResult(int[] move, int score, int depth, long timeMs) {}
    public record PositionEvaluation(int material, int positional, int kingSafety, int mobility) {}
    private static final Logger logger = LogManager.getLogger(NegamaxAI.class);
    
    private boolean debugEnabled;
    private Map<String, TranspositionEntry> transpositionTable = new HashMap<>();
    private LeelaChessZeroOpeningBook openingBook;
    private long searchStartTime;
    private static final int MAX_SEARCH_TIME_MS = 5000; // 5 seconds
    private static final int MAX_DEPTH = 6;
    
    // Move ordering optimizations
    private int[][][] killerMoves = new int[MAX_DEPTH + 1][2][4]; // 2 killer moves per depth, each move has 4 coordinates
    private Map<String, Integer> historyTable = new HashMap<>(); // History heuristic
    
    // Transposition table flags
    private static final int EXACT = 0;
    private static final int LOWER_BOUND = 1;
    private static final int UPPER_BOUND = 2;
    
    // Piece values (centipawns)
    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;
    
    // Endgame tablebase support (placeholder for Syzygy integration)
    private boolean endgameTablebaseEnabled = false;
    
    public NegamaxAI(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        logger.info("Negamax AI: Initialized with depth {} and {}s time limit + Lc0 opening book + optimizations", MAX_DEPTH, MAX_SEARCH_TIME_MS/1000);
        logger.info("Negamax optimizations: Move ordering (MVV-LVA, killers, history), null move pruning, late move reductions");
    }
    
    /**
     * Select best move using iterative deepening negamax with alpha-beta pruning
     */
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Check opening book first
        if (openingBook != null) {
            VirtualChessBoard virtualBoard = new VirtualChessBoard(board, true);
            LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), true);
            if (openingResult != null) {
                logger.info("*** Negamax: Using Lc0 opening move - {} ***", openingResult.openingName);
                return openingResult.move;
            }
        }
        
        searchStartTime = System.currentTimeMillis();
        transpositionTable.clear(); // Clear for each move
        
        int[] bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        
        logger.debug("*** Negamax: Starting iterative deepening search (max depth " + MAX_DEPTH + ") ***");
        
        // Iterative deepening: search depth 1, 2, 3, 4, 5, 6
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (isTimeUp()) break;
            
            int[] currentBestMove = null;
            int currentBestScore = Integer.MIN_VALUE;
            
            for (int[] move : validMoves) {
                if (isTimeUp()) break;
                
                String[][] newBoard = makeMove(board, move);
                int score = -negamax(newBoard, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
                
                if (score > currentBestScore) {
                    currentBestScore = score;
                    currentBestMove = move;
                }
            }
            
            if (currentBestMove != null) {
                bestMove = currentBestMove;
                bestScore = currentBestScore;
                
                long elapsed = System.currentTimeMillis() - searchStartTime;
                logger.debug("Negamax depth " + depth + ": Best score " + bestScore + " (" + elapsed + "ms)");
            }
        }
        
        long totalTime = System.currentTimeMillis() - searchStartTime;
        logger.debug("*** Negamax: Completed in " + totalTime + "ms, TT entries: " + transpositionTable.size() + " ***");
        
        return bestMove != null ? bestMove : validMoves.get(0);
    }
    
    /**
     * Negamax algorithm with alpha-beta pruning, null move pruning, and late move reductions
     */
    private int negamax(String[][] board, int depth, int alpha, int beta, boolean isWhiteTurn) {
        return negamax(board, depth, alpha, beta, isWhiteTurn, true);
    }
    
    private int negamax(String[][] board, int depth, int alpha, int beta, boolean isWhiteTurn, boolean allowNullMove) {
        if (isTimeUp()) return 0;
        
        // Endgame tablebase lookup (if enabled and few pieces)
        if (endgameTablebaseEnabled && countPieces(board) <= 7) {
            Integer tbResult = queryEndgameTablebase(board, isWhiteTurn);
            if (tbResult != null) {
                return tbResult;
            }
        }
        
        // Terminal node
        if (depth == 0) {
            return evaluatePosition(board, isWhiteTurn);
        }
        
        // Transposition table lookup
        String boardKey = encodeBoardState(board, isWhiteTurn);
        TranspositionEntry entry = transpositionTable.get(boardKey);
        if (entry != null && entry.depth() >= depth) {
            if (entry.flag() == EXACT) return entry.score();
            if (entry.flag() == LOWER_BOUND && entry.score() >= beta) return entry.score();
            if (entry.flag() == UPPER_BOUND && entry.score() <= alpha) return entry.score();
        }
        
        boolean inCheck = isKingInCheck(board, isWhiteTurn);
        
        // Null move pruning
        if (allowNullMove && !inCheck && depth >= 3 && hasNonPawnMaterial(board, isWhiteTurn)) {
            int nullScore = -negamax(board, depth - 3, -beta, -beta + 1, !isWhiteTurn, false);
            if (nullScore >= beta) {
                return beta; // Null move cutoff
            }
        }
        
        List<int[]> moves = getAllValidMoves(board, isWhiteTurn);
        if (moves.isEmpty()) {
            // Checkmate or stalemate
            if (inCheck) {
                return -KING_VALUE + (MAX_DEPTH - depth); // Prefer later checkmates
            }
            return 0; // Stalemate
        }
        
        // Move ordering
        orderMoves(moves, board, depth, entry != null ? entry.bestMove() : null);
        
        int maxScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        int flag = UPPER_BOUND;
        
        for (int i = 0; i < moves.size(); i++) {
            int[] move = moves.get(i);
            String[][] newBoard = makeMove(board, move);
            
            int score;
            if (i == 0) {
                // Full search for first move
                score = -negamax(newBoard, depth - 1, -beta, -alpha, !isWhiteTurn, true);
            } else {
                // Late move reductions
                int reduction = 0;
                if (depth >= 3 && i >= 4 && !inCheck && !isCapture(board, move) && !isPromotion(move)) {
                    reduction = 1;
                }
                
                // Search with reduced depth
                score = -negamax(newBoard, depth - 1 - reduction, -alpha - 1, -alpha, !isWhiteTurn, true);
                
                // Re-search if necessary
                if (score > alpha && reduction > 0) {
                    score = -negamax(newBoard, depth - 1, -alpha - 1, -alpha, !isWhiteTurn, true);
                }
                if (score > alpha && score < beta) {
                    score = -negamax(newBoard, depth - 1, -beta, -alpha, !isWhiteTurn, true);
                }
            }
            
            if (score > maxScore) {
                maxScore = score;
                bestMove = move;
            }
            
            if (score > alpha) {
                alpha = score;
                flag = EXACT;
            }
            
            if (alpha >= beta) {
                // Update killer moves and history
                if (!isCapture(board, move)) {
                    updateKillerMoves(move, depth);
                    updateHistoryTable(move, depth);
                }
                flag = LOWER_BOUND;
                break; // Alpha-beta cutoff
            }
        }
        
        // Store in transposition table
        transpositionTable.put(boardKey, new TranspositionEntry(maxScore, depth, flag, bestMove));
        
        return maxScore;
    }
    
    /**
     * Advanced position evaluation
     */
    private int evaluatePosition(String[][] board, boolean forWhite) {
        int score = 0;
        
        // Material evaluation
        score += evaluateMaterial(board);
        
        // Positional evaluation
        score += evaluatePieceSquareTables(board);
        score += evaluateKingSafety(board);
        score += evaluatePawnStructure(board);
        score += evaluateMobility(board);
        
        return forWhite ? score : -score;
    }
    
    private int evaluateMaterial(String[][] board) {
        int score = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int value = getPieceValue(piece);
                    score += "♔♕♖♗♘♙".contains(piece) ? value : -value;
                }
            }
        }
        return score;
    }
    
    private int evaluatePieceSquareTables(String[][] board) {
        // Simplified piece-square tables (center control, development)
        int score = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty()) {
                    int positionalValue = getPositionalValue(piece, i, j);
                    score += "♔♕♖♗♘♙".contains(piece) ? positionalValue : -positionalValue;
                }
            }
        }
        return score;
    }
    
    private int evaluateKingSafety(String[][] board) {
        int score = 0;
        // Penalty for exposed kings
        int[] whiteKing = findKing(board, true);
        int[] blackKing = findKing(board, false);
        
        if (whiteKing != null) {
            score += isKingInCheck(board, true) ? -50 : 0;
            score += countAttackersAroundKing(board, whiteKing, false) * -10;
        }
        
        if (blackKing != null) {
            score -= isKingInCheck(board, false) ? -50 : 0;
            score -= countAttackersAroundKing(board, blackKing, true) * -10;
        }
        
        return score;
    }
    
    private int evaluatePawnStructure(String[][] board) {
        int score = 0;
        // Bonus for advanced pawns, penalty for isolated/doubled pawns
        for (int j = 0; j < 8; j++) {
            int whitePawns = 0, blackPawns = 0;
            for (int i = 0; i < 8; i++) {
                if ("♙".equals(board[i][j])) {
                    whitePawns++;
                    score += (6 - i) * 2; // Bonus for advanced pawns
                }
                if ("♟".equals(board[i][j])) {
                    blackPawns++;
                    score -= (i - 1) * 2; // Bonus for advanced pawns
                }
            }
            // Penalty for doubled pawns
            if (whitePawns > 1) score -= (whitePawns - 1) * 10;
            if (blackPawns > 1) score += (blackPawns - 1) * 10;
        }
        return score;
    }
    
    private int evaluateMobility(String[][] board) {
        int whiteMobility = getAllValidMoves(board, true).size();
        int blackMobility = getAllValidMoves(board, false).size();
        return (whiteMobility - blackMobility) * 2;
    }
    
    private int getPieceValue(String piece) {
        return switch (piece) {
            case "♙", "♟" -> PAWN_VALUE;
            case "♘", "♞" -> KNIGHT_VALUE;
            case "♗", "♝" -> BISHOP_VALUE;
            case "♖", "♜" -> ROOK_VALUE;
            case "♕", "♛" -> QUEEN_VALUE;
            case "♔", "♚" -> KING_VALUE;
            default -> 0;
        };
    }
    
    private int getPositionalValue(String piece, int row, int col) {
        // Center squares are more valuable
        int centerBonus = 0;
        if (row >= 3 && row <= 4 && col >= 3 && col <= 4) centerBonus = 10;
        else if (row >= 2 && row <= 5 && col >= 2 && col <= 5) centerBonus = 5;
        
        // Development bonus for pieces off back rank
        int developmentBonus = 0;
        if ("♘♞♗♝".contains(piece)) {
            if (("♘♗".contains(piece) && row != 7) || ("♞♝".contains(piece) && row != 0)) {
                developmentBonus = 15;
            }
        }
        
        return centerBonus + developmentBonus;
    }
    
    private int[] findKing(String[][] board, boolean isWhite) {
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
    
    private int countAttackersAroundKing(String[][] board, int[] kingPos, boolean byWhite) {
        int attackers = 0;
        String enemyPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = kingPos[0] + dr, c = kingPos[1] + dc;
                if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                    if (isSquareAttackedBy(board, r, c, enemyPieces)) {
                        attackers++;
                    }
                }
            }
        }
        return attackers;
    }
    
    private boolean isSquareAttackedBy(String[][] board, int row, int col, String pieces) {
        // Simplified attack detection
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && pieces.contains(piece)) {
                    if (canAttack(board, i, j, row, col, piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean canAttack(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
        // Simplified attack logic (reuse existing logic from ChessGame)
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        return switch (piece) {
            case "♙" -> fromRow - toRow == 1 && colDiff == 1;
            case "♟" -> toRow - fromRow == 1 && colDiff == 1;
            case "♖", "♜" -> (rowDiff == 0 || colDiff == 0) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♗", "♝" -> rowDiff == colDiff && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♕", "♛" -> (rowDiff == 0 || colDiff == 0 || rowDiff == colDiff) && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case "♘", "♞" -> (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
            case "♔", "♚" -> rowDiff <= 1 && colDiff <= 1;
            default -> false;
        };
    }
    
    private boolean isPathClear(String[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowDir = Integer.compare(toRow, fromRow);
        int colDir = Integer.compare(toCol, fromCol);
        
        int currentRow = fromRow + rowDir;
        int currentCol = fromCol + colDir;
        
        while (currentRow != toRow || currentCol != toCol) {
            if (!board[currentRow][currentCol].isEmpty()) return false;
            currentRow += rowDir;
            currentCol += colDir;
        }
        return true;
    }
    
    private boolean isKingInCheck(String[][] board, boolean isWhite) {
        int[] kingPos = findKing(board, isWhite);
        if (kingPos == null) return false;
        
        String enemyPieces = isWhite ? "♚♛♜♝♞♟" : "♔♕♖♗♘♙";
        return isSquareAttackedBy(board, kingPos[0], kingPos[1], enemyPieces);
    }
    
    private List<int[]> getAllValidMoves(String[][] board, boolean forWhite) {
        // AI vs User: Use ChessGame's ChessRuleValidator
        // AI vs AI Training: Use VirtualChessBoard's ChessRuleValidator
        VirtualChessBoard virtualBoard = new VirtualChessBoard(board, forWhite);
        return virtualBoard.getAllValidMoves(forWhite);
    }
    
    private void addPieceMoves(String[][] board, int row, int col, String piece, List<int[]> moves, boolean forWhite) {
        // Generate pseudo-legal moves for each piece type
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (r == row && c == col) continue;
                
                if (canAttack(board, row, col, r, c, piece)) {
                    String target = board[r][c];
                    if (target.isEmpty() || isOpponentPiece(target, forWhite)) {
                        moves.add(new int[]{row, col, r, c});
                    }
                }
            }
        }
    }
    
    private boolean isOpponentPiece(String piece, boolean forWhite) {
        if (piece.isEmpty()) return false;
        boolean pieceIsWhite = "♔♕♖♗♘♙".contains(piece);
        return pieceIsWhite != forWhite;
    }
    
    private boolean isLegalMove(String[][] board, int[] move, boolean forWhite) {
        // Check if move leaves own king in check
        String[][] newBoard = makeMove(board, move);
        return !isKingInCheck(newBoard, forWhite);
    }
    
    private String[][] makeMove(String[][] board, int[] move) {
        String[][] newBoard = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, newBoard[i], 0, 8);
        }
        
        String piece = newBoard[move[0]][move[1]];
        newBoard[move[2]][move[3]] = piece;
        newBoard[move[0]][move[1]] = "";
        
        return newBoard;
    }
    
    private String encodeBoardState(String[][] board, boolean isWhiteTurn) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                sb.append(board[i][j].isEmpty() ? "." : board[i][j]);
            }
        }
        sb.append(isWhiteTurn ? "W" : "B");
        return sb.toString();
    }
    
    private boolean isTimeUp() {
        return (System.currentTimeMillis() - searchStartTime) > MAX_SEARCH_TIME_MS;
    }
    
    /**
     * Order moves for better alpha-beta pruning
     */
    private void orderMoves(List<int[]> moves, String[][] board, int depth, int[] hashMove) {
        moves.sort((move1, move2) -> {
            int score1 = getMoveOrderingScore(move1, board, depth, hashMove);
            int score2 = getMoveOrderingScore(move2, board, depth, hashMove);
            return Integer.compare(score2, score1); // Descending order
        });
    }
    
    private int getMoveOrderingScore(int[] move, String[][] board, int depth, int[] hashMove) {
        // Hash move (from transposition table)
        if (hashMove != null && Arrays.equals(move, hashMove)) {
            return 10000;
        }
        
        // MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
        if (isCapture(board, move)) {
            String victim = board[move[2]][move[3]];
            String attacker = board[move[0]][move[1]];
            return getPieceValue(victim) - getPieceValue(attacker) / 10 + 5000;
        }
        
        // Killer moves
        for (int i = 0; i < 2; i++) {
            if (killerMoves[depth][i] != null && Arrays.equals(move, killerMoves[depth][i])) {
                return 4000 - i * 100;
            }
        }
        
        // History heuristic
        String moveKey = encodeMoveForHistory(move);
        return historyTable.getOrDefault(moveKey, 0);
    }
    
    private void updateKillerMoves(int[] move, int depth) {
        if (depth < killerMoves.length) {
            // Shift killer moves
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move.clone();
        }
    }
    
    private void updateHistoryTable(int[] move, int depth) {
        String moveKey = encodeMoveForHistory(move);
        int bonus = depth * depth;
        historyTable.put(moveKey, historyTable.getOrDefault(moveKey, 0) + bonus);
    }
    
    private String encodeMoveForHistory(int[] move) {
        return move[0] + "," + move[1] + "," + move[2] + "," + move[3];
    }
    
    private boolean isCapture(String[][] board, int[] move) {
        return !board[move[2]][move[3]].isEmpty();
    }
    
    private boolean isPromotion(int[] move) {
        return (move[0] == 1 && move[2] == 0) || (move[0] == 6 && move[2] == 7);
    }
    
    private boolean hasNonPawnMaterial(String[][] board, boolean isWhite) {
        String pieces = isWhite ? "♕♖♗♘" : "♛♜♝♞";
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (pieces.contains(board[i][j])) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Clear transposition table (called during shutdown/reset)
     */
    public void clearCache() {
        transpositionTable.clear();
        historyTable.clear();
        killerMoves = new int[MAX_DEPTH + 1][2][4];
        logger.debug("Negamax: Transposition table, history, and killer moves cleared");
    }
    
    /**
     * Count total pieces on board for endgame tablebase threshold
     */
    private int countPieces(String[][] board) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (!board[i][j].isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Query endgame tablebase (placeholder for Syzygy integration)
     * Returns: null if not found, positive for win, negative for loss, 0 for draw
     */
    private Integer queryEndgameTablebase(String[][] board, boolean isWhiteTurn) {
        // TODO: Integrate Syzygy tablebase
        // This would require:
        // 1. Syzygy tablebase files (3-7 piece endings)
        // 2. Native library integration (JNI)
        // 3. Position encoding to tablebase format
        
        // For now, return null (tablebase not available)
        return null;
    }
    
    /**
     * Enable/disable endgame tablebase support
     */
    public void setEndgameTablebaseEnabled(boolean enabled) {
        this.endgameTablebaseEnabled = enabled;
        logger.info("Negamax: Endgame tablebase support {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Get current cache size for debugging
     */
    public int getCacheSize() {
        return transpositionTable.size();
    }
    
    public void stopThinking() {
        // Negamax doesn't have async thinking threads like MCTS/AlphaZero
        // This is a no-op for compatibility
    }
    
    /**
     * Get optimization statistics
     */
    public String getOptimizationStats() {
        return String.format("TT: %d entries, History: %d moves, Killers: %d depths", 
            transpositionTable.size(), historyTable.size(), killerMoves.length);
    }
    

}