# Negamax AI Documentation

## Overview
Negamax AI is a classical chess engine using the Negamax algorithm with alpha-beta pruning. It features iterative deepening, transposition tables, time-bounded search, and integration with the Leela Chess Zero opening book. The system provides traditional chess engine capabilities with modern optimizations.

## How It Works in Chess

### Core Algorithm
- **Negamax**: Simplified minimax algorithm for zero-sum games
- **Alpha-Beta Pruning**: Eliminates branches that won't affect the result
- **Iterative Deepening**: Searches progressively deeper until time limit
- **Transposition Table**: Caches previously evaluated positions

### Key Features
1. **Advanced Position Evaluation**: Material, positional, king safety, mobility
2. **Time Management**: 5-second time limit with iterative deepening
3. **Opening Book**: Professional Leela Chess Zero opening database
4. **Transposition Table**: Efficient position caching for speed

## Code Implementation

### Main Class Structure
```java
public class NegamaxAI {
    private boolean debugEnabled;
    private Map<String, TranspositionEntry> transpositionTable = new HashMap<>();
    private LeelaChessZeroOpeningBook openingBook;
    private long searchStartTime;
    private static final int MAX_SEARCH_TIME_MS = 5000; // 5 seconds
    private static final int MAX_DEPTH = 6;
    
    // Piece values (centipawns)
    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;
}
```

### Move Selection with Iterative Deepening
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    if (validMoves.size() == 1) return validMoves.get(0);
    
    // Check opening book first
    if (openingBook != null) {
        LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves);
        if (openingResult != null) {
            logger.info("*** Negamax: Using Lc0 opening move - {} ***", openingResult.openingName);
            return openingResult.move;
        }
    }
    
    searchStartTime = System.currentTimeMillis();
    transpositionTable.clear(); // Clear for each move
    
    int[] bestMove = null;
    int bestScore = Integer.MIN_VALUE;
    
    System.out.println("*** Negamax: Starting iterative deepening search (max depth " + MAX_DEPTH + ") ***");
    
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
            System.out.println("Negamax depth " + depth + ": Best score " + bestScore + " (" + elapsed + "ms)");
        }
    }
    
    long totalTime = System.currentTimeMillis() - searchStartTime;
    System.out.println("*** Negamax: Completed in " + totalTime + "ms, TT entries: " + transpositionTable.size() + " ***");
    
    return bestMove != null ? bestMove : validMoves.get(0);
}
```

### Negamax Algorithm with Alpha-Beta Pruning
```java
private int negamax(String[][] board, int depth, int alpha, int beta, boolean isWhiteTurn) {
    if (isTimeUp()) return 0;
    
    // Terminal node
    if (depth == 0) {
        return evaluatePosition(board, isWhiteTurn);
    }
    
    // Transposition table lookup
    String boardKey = encodeBoardState(board, isWhiteTurn);
    TranspositionEntry entry = transpositionTable.get(boardKey);
    if (entry != null && entry.depth() >= depth) {
        return entry.score();
    }
    
    List<int[]> moves = getAllValidMoves(board, isWhiteTurn);
    if (moves.isEmpty()) {
        // Checkmate or stalemate
        if (isKingInCheck(board, isWhiteTurn)) {
            return -KING_VALUE + (MAX_DEPTH - depth); // Prefer later checkmates
        }
        return 0; // Stalemate
    }
    
    int maxScore = Integer.MIN_VALUE;
    
    for (int[] move : moves) {
        String[][] newBoard = makeMove(board, move);
        int score = -negamax(newBoard, depth - 1, -beta, -alpha, !isWhiteTurn);
        
        maxScore = Math.max(maxScore, score);
        alpha = Math.max(alpha, score);
        
        if (alpha >= beta) {
            break; // Alpha-beta cutoff
        }
    }
    
    // Store in transposition table
    transpositionTable.put(boardKey, new TranspositionEntry(maxScore, depth));
    
    return maxScore;
}
```

## Chess Strategy

### Advanced Position Evaluation
```java
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
```

### King Safety Evaluation
```java
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
```

### Pawn Structure Analysis
```java
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
```

### Piece-Square Tables
```java
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
```

## Performance Characteristics

### Strengths
- **Deep Calculation**: Searches 6 plies deep with good evaluation
- **Tactical Accuracy**: Excellent at finding tactical combinations
- **Time Management**: Efficient use of allocated thinking time
- **Opening Knowledge**: Benefits from professional opening database

### Considerations
- **Search Depth**: Limited by time constraints
- **Position Types**: Better in tactical than positional games
- **Memory Usage**: Transposition table grows during search
- **Evaluation Function**: Relatively simple compared to modern engines

## Integration Features

### Transposition Table
```java
public record TranspositionEntry(int score, int depth) {}

// Store in transposition table
transpositionTable.put(boardKey, new TranspositionEntry(maxScore, depth));

// Lookup from transposition table
TranspositionEntry entry = transpositionTable.get(boardKey);
if (entry != null && entry.depth() >= depth) {
    return entry.score();
}
```

### Opening Book Integration
- **Professional Database**: Uses Leela Chess Zero opening book
- **Early Game**: Prioritizes opening book moves over search
- **Transition**: Smoothly transitions from book to search

### Time Management
```java
private boolean isTimeUp() {
    return (System.currentTimeMillis() - searchStartTime) > MAX_SEARCH_TIME_MS;
}
```

## Configuration

### Search Parameters
```java
private static final int MAX_SEARCH_TIME_MS = 5000; // 5 seconds
private static final int MAX_DEPTH = 6;            // Maximum search depth
```

### Evaluation Weights
- **Material Values**: Standard piece values in centipawns
- **Positional Bonuses**: Center control, development, king safety
- **Pawn Structure**: Advanced pawns, doubled pawns penalties

## Usage Examples

### Basic Setup
```java
NegamaxAI negamax = new NegamaxAI(true);
```

### Move Selection
```java
int[] move = negamax.selectMove(board, validMoves);
```

### Cache Management
```java
negamax.clearCache();
int cacheSize = negamax.getCacheSize();
```

## Technical Details

### Records for Data Structures
```java
public record TranspositionEntry(int score, int depth) {}
public record SearchResult(int[] move, int score, int depth, long timeMs) {}
public record PositionEvaluation(int material, int positional, int kingSafety, int mobility) {}
```

### Board State Encoding
```java
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
```

### Move Generation
- **Legal Moves**: Uses ChessGame's centralized move validation
- **Move Ordering**: Could be enhanced with killer moves, history heuristic
- **Quiescence Search**: Could be added for tactical positions

### Error Handling
- **Time Limits**: Graceful handling of time pressure
- **Invalid Positions**: Robust handling of edge cases
- **Memory Management**: Automatic transposition table cleanup

### Attack Detection
```java
private boolean canAttack(String[][] board, int fromRow, int fromCol, int toRow, int toCol, String piece) {
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
```

This Negamax implementation provides a solid classical chess engine foundation with modern optimizations like transposition tables and iterative deepening, enhanced by professional opening knowledge.