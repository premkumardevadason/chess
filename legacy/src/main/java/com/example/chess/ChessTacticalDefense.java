package com.example.chess;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Threat Severity-Based Chess Defense System
 */
public class ChessTacticalDefense {
    private static final Logger logger = LogManager.getLogger(ChessTacticalDefense.class);
    
    // Threat severity levels
    private static final int CRITICAL = 1000;  // Immediate checkmate
    private static final int HIGH = 500;       // Major piece loss
    private static final int MEDIUM = 100;     // Minor piece loss
    private static final int LOW = 50;         // Positional threats
    
    public static int[] findBestDefensiveMove(String[][] board, List<int[]> validMoves, String aiName) {
        if (validMoves.isEmpty()) return null;
        
        ThreatAssessment bestThreat = null;
        int[] bestDefense = null;
        
        // Evaluate all threats and find highest severity
        List<ThreatAssessment> threats = assessAllThreats(board, validMoves);
        
        for (ThreatAssessment threat : threats) {
            if (bestThreat == null || threat.severity > bestThreat.severity) {
                bestThreat = threat;
                bestDefense = threat.defenseMove;
            }
        }
        
        if (bestThreat != null && bestThreat.severity >= MEDIUM) {
            logger.info("*** {}: {} THREAT (severity: {}) - {} ***", 
                aiName, bestThreat.type, bestThreat.severity, bestThreat.description);
            return bestDefense;
        }
        
        return null; // Let AI handle low-severity threats
    }
    
    private static class ThreatAssessment {
        final String type;
        final int severity;
        final String description;
        final int[] defenseMove;
        
        ThreatAssessment(String type, int severity, String description, int[] defenseMove) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.defenseMove = defenseMove;
        }
    }
    
    private static List<ThreatAssessment> assessAllThreats(String[][] board, List<int[]> validMoves) {
        List<ThreatAssessment> threats = new ArrayList<>();
        
        // CRITICAL: Immediate checkmate threats
        assessCheckmateThreats(board, validMoves, threats);
        
        // HIGH: Major piece threats (Queen, Rook)
        assessMajorPieceThreats(board, validMoves, threats);
        
        // MEDIUM: Minor piece threats (Bishop, Knight)
        assessMinorPieceThreats(board, validMoves, threats);
        
        return threats;
    }
    
    private static void assessCheckmateThreats(String[][] board, List<int[]> validMoves, List<ThreatAssessment> threats) {
        // Scholar's Mate detection
        int[] scholarDefense = defendScholarsMate(board, validMoves);
        if (scholarDefense != null) {
            threats.add(new ThreatAssessment("CHECKMATE", CRITICAL, "Scholar's Mate threat", scholarDefense));
        }
    }
    
    private static void assessMajorPieceThreats(String[][] board, List<int[]> validMoves, List<ThreatAssessment> threats) {
        // Queen threats
        assessPieceThreats(board, validMoves, threats, "♛", "Queen", HIGH);
        // Rook threats  
        assessPieceThreats(board, validMoves, threats, "♜", "Rook", HIGH);
    }
    
    private static void assessMinorPieceThreats(String[][] board, List<int[]> validMoves, List<ThreatAssessment> threats) {
        // Bishop threats
        assessPieceThreats(board, validMoves, threats, "♝", "Bishop", MEDIUM);
        // Knight threats
        assessPieceThreats(board, validMoves, threats, "♞", "Knight", MEDIUM);
    }
    
    private static void assessPieceThreats(String[][] board, List<int[]> validMoves, List<ThreatAssessment> threats, 
                                          String pieceType, String pieceName, int baseSeverity) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (pieceType.equals(board[i][j]) && isSquareUnderAttack(board, i, j, true)) {
                    int[] defense = defendValuablePiece(board, validMoves, new int[]{i, j}, pieceType, pieceName);
                    if (defense != null) {
                        threats.add(new ThreatAssessment("PIECE_ATTACK", baseSeverity, 
                            pieceName + " under attack", defense));
                    }
                }
            }
        }
    }
    

    
    /**
     * Universal piece defense logic: Escape → Block → Capture
     */
    private static int[] defendValuablePiece(String[][] board, List<int[]> validMoves, int[] piecePos, String pieceType, String pieceName) {
        // Priority 1: Try piece escape moves first
        for (int[] move : validMoves) {
            if (move[0] == piecePos[0] && move[1] == piecePos[1]) {
                // Simulate piece move to check safety
                String captured = board[move[2]][move[3]];
                board[move[2]][move[3]] = pieceType;
                board[move[0]][move[1]] = " ";
                
                boolean safe = !isSquareUnderAttack(board, move[2], move[3], true);
                
                // Restore board
                board[move[0]][move[1]] = pieceType;
                board[move[2]][move[3]] = captured;
                
                if (safe) {
                    logger.info("*** TACTICAL DEFENSE: {} escape to [{},{}] ***", pieceName, move[2], move[3]);
                    return move;
                }
            }
        }
        
        // Priority 2: Try blocking the attack
        List<int[]> attackers = findAttackersOfSquare(board, piecePos[0], piecePos[1], true);
        for (int[] attacker : attackers) {
            int[] blockMove = findBlockingMove(board, validMoves, attacker, piecePos);
            if (blockMove != null) {
                logger.info("*** TACTICAL DEFENSE: Block {} attack with piece to [{},{}] ***", pieceName, blockMove[2], blockMove[3]);
                return blockMove;
            }
        }
        
        // Priority 3: Try capturing attackers (only if advantageous)
        for (int[] attacker : attackers) {
            for (int[] move : validMoves) {
                if (move[2] == attacker[0] && move[3] == attacker[1]) {
                    // Check if capture is advantageous
                    String attackerPiece = board[attacker[0]][attacker[1]];
                    String capturingPiece = board[move[0]][move[1]];
                    double attackerValue = getChessPieceValue(attackerPiece);
                    double capturingValue = getChessPieceValue(capturingPiece);
                    double defendedValue = getChessPieceValue(pieceType);
                    
                    // Capture if we gain material or save a more valuable piece
                    if (attackerValue >= capturingValue || defendedValue > capturingValue) {
                        logger.info("*** TACTICAL DEFENSE: Capture {} attacker (save {}) ***", pieceName, pieceName);
                        return move;
                    }
                }
            }
        }
        
        return null;
    }
    
    public static boolean wouldRemoveCriticalDefense(String[][] board, int[] move) {
        if (move == null || move.length != 4) return false;
        
        int fromR = move[0], fromC = move[1];
        String piece = board[fromR][fromC];
        
        // Knight on f6 is pinned if it's defending against Scholar's Mate
        if ("♞".equals(piece) && fromR == 2 && fromC == 5) {
            // Check if Scholar's Mate threat currently exists
            boolean queenOnF3 = "♕".equals(board[5][5]);
            boolean bishopOnC4 = "♗".equals(board[4][2]);
            
            if (queenOnF3 && bishopOnC4) {
                // Knight is defending against Scholar's Mate - cannot move
                return true;
            }
        }
        
        // Any piece defending against checkmate is considered pinned
        if (isPieceDefendingAgainstCheckmate(board, fromR, fromC)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a piece is defending against an immediate checkmate threat
     * If moving this piece would result in checkmate, it's considered pinned
     */
    private static boolean isPieceDefendingAgainstCheckmate(String[][] board, int pieceRow, int pieceCol) {
        String piece = board[pieceRow][pieceCol];
        if (piece.isEmpty()) return false;
        
        // Temporarily remove the piece
        String[][] tempBoard = copyBoard(board);
        tempBoard[pieceRow][pieceCol] = " ";
        
        // Check if removing this piece creates checkmate for the defending side
        boolean isBlackPiece = "♚♛♜♝♞♟".contains(piece);
        
        if (isBlackPiece) {
            // Check if Black king would be in checkmate without this piece
            int[] blackKing = findKing(tempBoard, false);
            if (blackKing != null && isKingInCheckmate(tempBoard, blackKing, false)) {
                return true;
            }
        } else {
            // Check if White king would be in checkmate without this piece
            int[] whiteKing = findKing(tempBoard, true);
            if (whiteKing != null && isKingInCheckmate(tempBoard, whiteKing, true)) {
                return true;
            }
        }
        
        return false;
    }
    
    private static int[] findKing(String[][] board, boolean isWhite) {
        String king = isWhite ? "♔" : "♚";
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (king.equals(board[r][c])) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }
    
    private static boolean isKingInCheckmate(String[][] board, int[] kingPos, boolean isWhite) {
        // Check if king is under attack
        if (!isSquareUnderAttack(board, kingPos[0], kingPos[1], !isWhite)) {
            return false; // Not in check, so not checkmate
        }
        
        // Check if king has any legal escape moves
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int newR = kingPos[0] + dr;
                int newC = kingPos[1] + dc;
                
                if (newR >= 0 && newR < 8 && newC >= 0 && newC < 8) {
                    String targetSquare = board[newR][newC];
                    // Check if square is empty or contains enemy piece
                    boolean canMoveTo = " ".equals(targetSquare) || 
                        (isWhite && "♚♛♜♝♞♟".contains(targetSquare)) ||
                        (!isWhite && "♔♕♖♗♘♙".contains(targetSquare));
                    
                    if (canMoveTo && !isSquareUnderAttack(board, newR, newC, !isWhite)) {
                        return false; // King has escape square
                    }
                }
            }
        }
        
        return true; // King is in checkmate
    }
    
    private static boolean isSquareUnderAttack(String[][] board, int row, int col, boolean byWhite) {
        String attackingPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];
                if (attackingPieces.contains(piece)) {
                    if (canPieceAttackSquare(board, r, c, row, col, piece)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean canPieceAttackSquare(String[][] board, int fromR, int fromC, int toR, int toC, String piece) {
        switch (piece) {
            case "♕": case "♛": return canQueenAttack(fromR, fromC, toR, toC, board);
            case "♖": case "♜": return canRookAttack(fromR, fromC, toR, toC, board);
            case "♗": case "♝": return canBishopAttack(fromR, fromC, toR, toC, board);
            case "♘": case "♞": return canKnightAttack(fromR, fromC, toR, toC);
            case "♙": return canPawnAttack(fromR, fromC, toR, toC, true);
            case "♟": return canPawnAttack(fromR, fromC, toR, toC, false);
            case "♔": case "♚": return canKingAttack(fromR, fromC, toR, toC);
            default: return false;
        }
    }
    
    private static boolean canKnightAttack(int fromR, int fromC, int toR, int toC) {
        int dr = Math.abs(fromR - toR);
        int dc = Math.abs(fromC - toC);
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
    }
    
    private static boolean canPawnAttack(int fromR, int fromC, int toR, int toC, boolean isWhite) {
        int direction = isWhite ? 1 : -1;
        return toR == fromR + direction && Math.abs(toC - fromC) == 1;
    }
    
    private static boolean canKingAttack(int fromR, int fromC, int toR, int toC) {
        return Math.abs(fromR - toR) <= 1 && Math.abs(fromC - toC) <= 1;
    }
    
    private static boolean isScholarsMateStillThreat(String[][] board, int[] knightMove) {
        String[][] tempBoard = copyBoard(board);
        tempBoard[knightMove[2]][knightMove[3]] = tempBoard[knightMove[0]][knightMove[1]];
        tempBoard[knightMove[0]][knightMove[1]] = " ";
        
        boolean queenThreatsF7 = false;
        boolean bishopThreatsF7 = false;
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = tempBoard[r][c];
                if ("♕".equals(piece) && canQueenAttack(r, c, 1, 5, tempBoard)) {
                    queenThreatsF7 = true;
                }
                if ("♗".equals(piece) && canBishopAttack(r, c, 1, 5, tempBoard)) {
                    bishopThreatsF7 = true;
                }
            }
        }
        
        return queenThreatsF7 && bishopThreatsF7;
    }
    
    private static String[][] copyBoard(String[][] board) {
        String[][] copy = new String[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    private static int[] detectCheckmateThreats(String[][] board, List<int[]> validMoves, String aiName) {
        int[] scholarDefense = defendScholarsMate(board, validMoves);
        if (scholarDefense != null) {
            logger.info("*** {}: SCHOLAR'S MATE THREAT - Defending ***", aiName);
            return scholarDefense;
        }
        
        int[] foolDefense = defendFoolsMate(board, validMoves);
        if (foolDefense != null) {
            logger.info("*** {}: FOOL'S MATE THREAT - Defending ***", aiName);
            return foolDefense;
        }
        
        int[] legalDefense = defendLegalsMate(board, validMoves);
        if (legalDefense != null) {
            logger.info("*** {}: LÉGAL'S MATE THREAT - Defending ***", aiName);
            return legalDefense;
        }
        
        int[] backRankDefense = defendBackRankMate(board, validMoves);
        if (backRankDefense != null) {
            logger.info("*** {}: BACK RANK MATE THREAT - Defending ***", aiName);
            return backRankDefense;
        }
        
        int[] smotheredDefense = defendSmotheredMate(board, validMoves);
        if (smotheredDefense != null) {
            logger.info("*** {}: SMOTHERED MATE THREAT - Defending ***", aiName);
            return smotheredDefense;
        }
        
        return null;
    }
    

    

    
//    private static int[] detectPositionalThreats(String[][] board, List<int[]> validMoves, String aiName) {
//        // Remove all positional defenses - let AI engines handle these
//        return null;
//    }
    
    private static int[] defendScholarsMate(String[][] board, List<int[]> validMoves) {
        logger.debug("*** SCHOLAR'S MATE CHECK: Scanning board for Queen+Bishop f7 threats ***");
        
        // Check for ANY Queen + Bishop threats on f7 (Scholar's Mate pattern)
        boolean queenThreatsF7 = false;
        boolean bishopThreatsF7 = false;
        
        // Debug: Show what's on f7
        logger.debug("*** SCHOLAR'S MATE: f7 square [1,5] contains: '{}' ***", board[1][5]);
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];
                if ("♕".equals(piece)) {
                    logger.debug("*** SCHOLAR'S MATE: Found White Queen at [{},{}] ***", r, c);
                    if (canQueenAttack(r, c, 1, 5, board)) {
                        queenThreatsF7 = true;
                        logger.debug("*** SCHOLAR'S MATE: Queen at [{},{}] threatens f7 ***", r, c);
                    } else {
                        logger.debug("*** SCHOLAR'S MATE: Queen at [{},{}] does NOT threaten f7 ***", r, c);
                    }
                }
                if ("♗".equals(piece)) {
                    logger.debug("*** SCHOLAR'S MATE: Found White Bishop at [{},{}] ***", r, c);
                    if (canBishopAttack(r, c, 1, 5, board)) {
                        bishopThreatsF7 = true;
                        logger.debug("*** SCHOLAR'S MATE: Bishop at [{},{}] threatens f7 ***", r, c);
                    } else {
                        logger.debug("*** SCHOLAR'S MATE: Bishop at [{},{}] does NOT threaten f7 ***", r, c);
                    }
                }
            }
        }
        
        logger.debug("*** SCHOLAR'S MATE: Queen threatens f7: {}, Bishop threatens f7: {} ***", queenThreatsF7, bishopThreatsF7);
        
        // If both Queen and Bishop threaten f7, it's Scholar's Mate setup
        if (queenThreatsF7 && bishopThreatsF7) {
            logger.debug("*** SCHOLAR'S MATE DETECTED: Both Queen and Bishop threaten f7 ***");
            
            // Priority 1: Knight to f6 - CLASSIC Scholar's Mate defense
            for (int[] move : validMoves) {
                if ("♞".equals(board[move[0]][move[1]]) && move[2] == 2 && move[3] == 5) {
                    logger.debug("*** SCHOLAR'S MATE DEFENSE: Knight to f6 ***");
                    return move; // Nf6 - blocks both Queen and Bishop attacks on f7
                }
            }
            
            // Priority 2: Any piece to f7 to defend
            for (int[] move : validMoves) {
                if (move[2] == 1 && move[3] == 5) { // Any piece to f7
                    logger.debug("*** SCHOLAR'S MATE DEFENSE: Piece to f7 ***");
                    return move;
                }
            }
            
            // Priority 3: Block the Queen's attack path
            for (int[] move : validMoves) {
                if (move[2] == 2 && move[3] == 5) { // Any piece to f6
                    logger.debug("*** SCHOLAR'S MATE DEFENSE: Piece to f6 ***");
                    return move;
                }
            }
        }
        
        // Also check for early Queen threats (even without Bishop)
        if (queenThreatsF7) {
            logger.debug("*** EARLY QUEEN THREAT: Queen threatens f7 ***");
            // Defend f7 if Queen is threatening it
            for (int[] move : validMoves) {
                if ("♞".equals(board[move[0]][move[1]]) && move[2] == 2 && move[3] == 5) {
                    logger.debug("*** EARLY QUEEN DEFENSE: Knight to f6 ***");
                    return move; // Nf6 - blocks Queen attack
                }
            }
        }
        
        logger.debug("*** SCHOLAR'S MATE: No threats detected or no defense available ***");
        return null;
    }
    
    private static boolean canQueenAttack(int fromR, int fromC, int toR, int toC, String[][] board) {
        return canRookAttack(fromR, fromC, toR, toC, board) || canBishopAttack(fromR, fromC, toR, toC, board);
    }
    
    private static boolean canRookAttack(int fromR, int fromC, int toR, int toC, String[][] board) {
        if (fromR != toR && fromC != toC) return false;
        int dr = Integer.compare(toR, fromR);
        int dc = Integer.compare(toC, fromC);
        int r = fromR + dr, c = fromC + dc;
        while (r != toR || c != toC) {
            if (!" ".equals(board[r][c])) return false;
            r += dr; c += dc;
        }
        return true;
    }
    
    private static boolean canBishopAttack(int fromR, int fromC, int toR, int toC, String[][] board) {
        if (Math.abs(fromR - toR) != Math.abs(fromC - toC)) return false;
        int dr = Integer.compare(toR, fromR);
        int dc = Integer.compare(toC, fromC);
        int r = fromR + dr, c = fromC + dc;
        while (r != toR || c != toC) {
            if (!" ".equals(board[r][c])) return false;
            r += dr; c += dc;
        }
        return true;
    }
    
    private static int[] defendFoolsMate(String[][] board, List<int[]> validMoves) {
        // Detect if White has weakened kingside with f3/g4
        boolean f3Weak = " ".equals(board[5][5]) && "♙".equals(board[4][5]);
        boolean g4Weak = " ".equals(board[4][6]) && "♙".equals(board[3][6]);
        
        if (f3Weak || g4Weak) {
            // Look for Queen to h4 checkmate
            for (int[] move : validMoves) {
                if ("♛".equals(board[move[0]][move[1]]) && move[2] == 4 && move[3] == 7) {
                    return move; // Qh4# checkmate!
                }
            }
        }
        return null;
    }
    
    private static int[] defendLegalsMate(String[][] board, List<int[]> validMoves) {
        // Detect knight sacrifice on f7 setup
        for (int[] move : validMoves) {
            if (move[2] == 1 && move[3] == 5) { // f7 square
                String piece = board[move[0]][move[1]];
                if ("♞♝".contains(piece)) {
                    return move; // Defend f7
                }
            }
        }
        return null;
    }
    
    private static int[] defendBackRankMate(String[][] board, List<int[]> validMoves) {
        // Only defend if there's an IMMEDIATE back rank mate threat (mate in 1)
        int[] kingPos = findKing(board, false);
        if (kingPos != null && kingPos[0] == 0) {
            // Check if White can deliver checkmate on next move
            for (int col = 0; col < 8; col++) {
                String piece = board[0][col];
                if ("♕♖".contains(piece)) {
                    // Check if this piece can deliver immediate checkmate
                    if (canPieceAttackSquare(board, 0, col, kingPos[0], kingPos[1], piece)) {
                        // Only defend if it's actually checkmate (no escape squares)
                        if (!hasEscapeSquares(board, kingPos[0], kingPos[1])) {
                            for (int[] move : validMoves) {
                                if ("♟".equals(board[move[0]][move[1]]) && move[0] == 1) {
                                    return move; // Create escape square
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private static int[] defendSmotheredMate(String[][] board, List<int[]> validMoves) {
        // Prevent king from being trapped by own pieces
        int[] kingPos = findKing(board, false);
        if (kingPos != null) {
            for (int[] move : validMoves) {
                if ("♚".equals(board[move[0]][move[1]])) {
                    // Move king to square with escape routes
                    if (hasEscapeSquares(board, move[2], move[3])) {
                        return move;
                    }
                }
            }
        }
        return null;
    }
    
    private static int[] defendFriedLiver(String[][] board, List<int[]> validMoves) {
        // Detect Italian Game bishop on c4 threatening f7
        boolean bishopOnC4 = "♗".equals(board[4][2]);
        if (bishopOnC4) {
            for (int[] move : validMoves) {
                if (move[2] == 1 && move[3] == 5) { // Defend f7
                    return move;
                }
            }
        }
        return null;
    }
    
    private static int[] defendFishingPole(String[][] board, List<int[]> validMoves) {
        // Only defend against actual Ruy Lopez fishing pole trap
        if (!isRuyLopezPosition(board)) return null;
        
        for (int[] move : validMoves) {
            if ("♜".equals(board[move[0]][move[1]])) {
                // Check if rook is actually in danger of being trapped
                if (isRookInDanger(board, move[0], move[1]) && move[2] >= 2) {
                    return move;
                }
            }
        }
        return null;
    }
    
    private static boolean isRuyLopezPosition(String[][] board) {
        // Check for Ruy Lopez setup: White bishop on b5, Black knight on c6
        return "♗".equals(board[3][1]) && "♞".equals(board[2][2]);
    }
    
    private static boolean isRookInDanger(String[][] board, int rookR, int rookC) {
        // Check if rook has limited escape squares
        int escapeSquares = 0;
        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
        
        for (int[] dir : directions) {
            int r = rookR + dir[0];
            int c = rookC + dir[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && " ".equals(board[r][c])) {
                escapeSquares++;
            }
        }
        
        return escapeSquares <= 1; // Rook is in danger if very few escape squares
    }
    
    private static int[] defendNoahsArk(String[][] board, List<int[]> validMoves) {
        // Only execute Noah's Ark if bishop is actually trappable and it's mid/endgame
        int totalPieces = countTotalPieces(board);
        if (totalPieces > 20) return null; // Don't execute in opening
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if ("♗".equals(board[r][c])) {
                    // More precise bishop trap detection
                    if (isBishopActuallyTrappable(board, r, c)) {
                        for (int[] move : validMoves) {
                            if ("♟".equals(board[move[0]][move[1]])) {
                                if ((move[2] == 2 && move[3] == 0) || (move[2] == 3 && move[3] == 1)) {
                                    return move; // a6 or b5 to trap bishop
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private static boolean isBishopActuallyTrappable(String[][] board, int bishopR, int bishopC) {
        // Check if bishop has limited escape squares
        int escapeSquares = 0;
        int[][] directions = {{-1,-1},{-1,1},{1,-1},{1,1}};
        
        for (int[] dir : directions) {
            int r = bishopR + dir[0];
            int c = bishopC + dir[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                if (" ".equals(board[r][c])) {
                    escapeSquares++;
                    break;
                } else if ("♚♛♜♝♞♟".contains(board[r][c])) {
                    break; // Blocked by black piece
                }
                r += dir[0]; c += dir[1];
            }
        }
        
        return escapeSquares <= 2; // Bishop is trappable if few escape squares
    }
    
    private static int countTotalPieces(String[][] board) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (!" ".equals(board[r][c])) {
                    count++;
                }
            }
        }
        return count;
    }
    
    private static int[] defendAgainstPins(String[][] board, List<int[]> validMoves) {
        // Break pins by moving pinned piece or blocking
        for (int[] move : validMoves) {
            if (breaksPinOnKing(board, move)) {
                return move;
            }
        }
        return null;
    }
    
    public static int[] defendAgainstForks(String[][] board, List<int[]> validMoves) {
        // Check all White pieces for fork potential
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if ("♔♕♖♗♘♙".contains(piece)) { // White pieces
                    List<int[]> threatenedPieces = findPiecesThreatenedBy(board, i, j);
                    
                    // Fork detected if attacking 2+ valuable pieces
                    if (threatenedPieces.size() >= 2) {
                        double totalThreatValue = 0;
                        for (int[] threatened : threatenedPieces) {
                            String threatenedPiece = board[threatened[0]][threatened[1]];
                            totalThreatValue += getChessPieceValue(threatenedPiece);
                        }
                        
                        // Only respond to high-value forks
                        if (totalThreatValue >= 600) { // At least Rook + Pawn value
                            // Try to capture the forking piece
                            int[] captureMove = findCaptureMove(board, validMoves, i, j);
                            if (captureMove != null) {
                                return captureMove;
                            }
                            
                            // Try to move the most valuable threatened piece
                            int[] escapeMove = findBestEscapeMove(board, validMoves, threatenedPieces);
                            if (escapeMove != null) {
                                return escapeMove;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private static int[] defendAgainstSkewers(String[][] board, List<int[]> validMoves) {
        // Only defend against actual skewers with high-value pieces
        if (hasActualSkewer(board)) {
            for (int[] move : validMoves) {
                if (breaksSkewer(board, move)) {
                    return move;
                }
            }
        }
        return null;
    }
    
    private static boolean hasActualSkewer(String[][] board) {
        // Only detect IMMEDIATE skewer threats with high-value pieces at risk
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];
                if ("♔♕♖♗♘♙".contains(piece)) { // White pieces
                    if (hasImmediateSkewer(board, r, c)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean hasImmediateSkewer(String[][] board, int attackerR, int attackerC) {
        String attacker = board[attackerR][attackerC];
        if (!"♕♖♗".contains(attacker)) return false; // Only Queen, Rook, Bishop
        
        // Check all 8 directions for IMMEDIATE skewer threats
        int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        
        for (int[] dir : directions) {
            if (hasHighValueSkewer(board, attackerR, attackerC, dir[0], dir[1])) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean hasHighValueSkewer(String[][] board, int startR, int startC, int dr, int dc) {
        String attacker = board[startR][startC];
        boolean isWhiteAttacker = "♔♕♖♗♘♙".contains(attacker);
        
        String firstPiece = null;
        String secondPiece = null;
        int firstR = -1, firstC = -1;
        int r = startR + dr, c = startC + dc;
        
        // Find first piece in this direction
        while (r >= 0 && r < 8 && c >= 0 && c < 8) {
            if (!" ".equals(board[r][c])) {
                firstPiece = board[r][c];
                firstR = r; firstC = c;
                break;
            }
            r += dr; c += dc;
        }
        
        if (firstPiece == null) return false;
        
        // Find second piece in same direction
        r += dr; c += dc;
        while (r >= 0 && r < 8 && c >= 0 && c < 8) {
            if (!" ".equals(board[r][c])) {
                secondPiece = board[r][c];
                break;
            }
            r += dr; c += dc;
        }
        
        if (secondPiece == null) return false;
        
        // Only consider it a skewer if:
        // 1. Both pieces are Black (opposite of White attacker)
        // 2. Back piece is Queen or Rook (high value)
        // 3. Front piece is less valuable
        // 4. Attacker can actually attack along this line
        boolean firstIsBlack = "♚♛♜♝♞♟".contains(firstPiece);
        boolean secondIsBlack = "♚♛♜♝♞♟".contains(secondPiece);
        
        if (isWhiteAttacker && firstIsBlack && secondIsBlack) {
            double firstValue = getChessPieceValue(firstPiece);
            double secondValue = getChessPieceValue(secondPiece);
            
            // Only high-value skewers: Queen or Rook behind
            if (secondValue >= 500 && firstValue < secondValue) {
                // Verify attacker can actually attack along this line
                return canAttackerSkewer(attacker, dr, dc);
            }
        }
        
        return false;
    }
    
    private static int[] defendAgainstDiscoveredAttacks(String[][] board, List<int[]> validMoves) {
        // Only defend against ACTUAL discovered attacks with high-value pieces at risk
        if (hasActualDiscoveredAttack(board)) {
            for (int[] move : validMoves) {
                if (preventsDiscoveredAttack(board, move)) {
                    return move;
                }
            }
        }
        return null;
    }
    

    

    
    // Helper methods
//    private static int[] findKing(String[][] board, boolean isWhite) {
//        String king = isWhite ? "♔" : "♚";
//        for (int r = 0; r < 8; r++) {
//            for (int c = 0; c < 8; c++) {
//                if (king.equals(board[r][c])) {
//                    return new int[]{r, c};
//                }
//            }
//        }
//        return null;
//    }
    
    private static boolean hasEscapeSquares(String[][] board, int kingRow, int kingCol) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int newRow = kingRow + dr, newCol = kingCol + dc;
                if (newRow >= 0 && newRow < 8 && newCol >= 0 && newCol < 8) {
                    if (" ".equals(board[newRow][newCol])) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean breaksPinOnKing(String[][] board, int[] move) {
        // Only consider it a pin-breaking move if there's actually a pin
        String piece = board[move[0]][move[1]];
        if (piece.isEmpty()) return false;
        
        // Find the king
        boolean isBlackPiece = "♚♛♜♝♞♟".contains(piece);
        int[] kingPos = findKing(board, !isBlackPiece);
        if (kingPos == null) return false;
        
        // Check if this piece is actually pinned to the king
        return isPiecePinnedToKing(board, move[0], move[1], kingPos[0], kingPos[1]);
    }
    
    private static boolean isPiecePinnedToKing(String[][] board, int pieceR, int pieceC, int kingR, int kingC) {
        // Check if piece and king are on same line (rank, file, or diagonal)
        int dr = kingR - pieceR;
        int dc = kingC - pieceC;
        
        if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) {
            return false; // Not on same line
        }
        
        // Look for attacking piece behind the potentially pinned piece
        int stepR = Integer.compare(dr, 0);
        int stepC = Integer.compare(dc, 0);
        
        // Check squares between piece and king
        int r = pieceR + stepR;
        int c = pieceC + stepC;
        while (r != kingR || c != kingC) {
            if (!" ".equals(board[r][c])) {
                return false; // Path blocked
            }
            r += stepR;
            c += stepC;
        }
        
        // Look for attacking piece in opposite direction
        r = pieceR - stepR;
        c = pieceC - stepC;
        while (r >= 0 && r < 8 && c >= 0 && c < 8) {
            String attackerPiece = board[r][c];
            if (!" ".equals(attackerPiece)) {
                // Check if this piece can attack along this line
                boolean isWhiteAttacker = "♔♕♖♗♘♙".contains(attackerPiece);
                boolean isBlackKing = "♚".equals(board[kingR][kingC]);
                
                if (isWhiteAttacker == isBlackKing) { // Opposite colors
                    if (canPieceAttackAlongLine(attackerPiece, stepR, stepC)) {
                        return true; // Found pinning piece
                    }
                }
                break; // Path blocked by other piece
            }
            r -= stepR;
            c -= stepC;
        }
        
        return false;
    }
    
    private static boolean canPieceAttackAlongLine(String piece, int stepR, int stepC) {
        switch (piece) {
            case "♕": case "♛": return true; // Queen attacks all directions
            case "♖": case "♜": return stepR == 0 || stepC == 0; // Rook attacks ranks/files
            case "♗": case "♝": return Math.abs(stepR) == Math.abs(stepC); // Bishop attacks diagonals
            default: return false;
        }
    }
    
    /**
     * Find all Black pieces threatened by a specific White piece
     */
    private static List<int[]> findPiecesThreatenedBy(String[][] board, int attackerRow, int attackerCol) {
        List<int[]> threatened = new ArrayList<>();
        String attackerPiece = board[attackerRow][attackerCol];
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if ("♚♛♜♝♞♟".contains(piece)) { // Black pieces
                    if (canPieceAttackSquare(board, attackerRow, attackerCol, i, j, attackerPiece)) {
                        // Only count valuable pieces (not pawns)
                        if (getChessPieceValue(piece) >= 300) {
                            threatened.add(new int[]{i, j});
                        }
                    }
                }
            }
        }
        return threatened;
    }
    
    /**
     * Find a move to capture the specified piece
     */
    private static int[] findCaptureMove(String[][] board, List<int[]> validMoves, int targetRow, int targetCol) {
        for (int[] move : validMoves) {
            if (move[2] == targetRow && move[3] == targetCol) {
                return move;
            }
        }
        return null;
    }
    
    /**
     * Find the best escape move for threatened pieces
     */
    private static int[] findBestEscapeMove(String[][] board, List<int[]> validMoves, List<int[]> threatenedPieces) {
        // Sort by piece value (most valuable first)
        threatenedPieces.sort((a, b) -> {
            double valueA = getChessPieceValue(board[a[0]][a[1]]);
            double valueB = getChessPieceValue(board[b[0]][b[1]]);
            return Double.compare(valueB, valueA);
        });
        
        // Try to move the most valuable piece first
        for (int[] threatened : threatenedPieces) {
            for (int[] move : validMoves) {
                if (move[0] == threatened[0] && move[1] == threatened[1]) {
                    // Check if destination is safe
                    String piece = board[move[0]][move[1]];
                    String captured = board[move[2]][move[3]];
                    
                    // Simulate move
                    String[][] tempBoard = copyBoard(board);
                    tempBoard[move[2]][move[3]] = piece;
                    tempBoard[move[0]][move[1]] = " ";
                    
                    boolean safe = !isSquareUnderAttack(tempBoard, move[2], move[3], true);
                    
                    if (safe) {
                        return move;
                    }
                }
            }
        }
        return null;
    }
    
    private static double getChessPieceValue(String piece) {
        switch (piece) {
            case "♙": case "♟": return 100; // Pawn
            case "♘": case "♞": return 300; // Knight
            case "♗": case "♝": return 300; // Bishop
            case "♖": case "♜": return 500; // Rook
            case "♕": case "♛": return 900; // Queen
            case "♔": case "♚": return 10000; // King
            default: return 0;
        }
    }
    
    private static boolean breaksSkewer(String[][] board, int[] move) {
        // Only consider it skewer-breaking if it actually disrupts a real skewer
        String piece = board[move[0]][move[1]];
        if (piece.isEmpty()) return false;
        
        // Simulate the move and check if it breaks any existing skewers
        String[][] tempBoard = copyBoard(board);
        tempBoard[move[2]][move[3]] = piece;
        tempBoard[move[0]][move[1]] = " ";
        
        // Check if the move actually reduces skewer threats
        return !hasActualSkewer(tempBoard);
    }
    
    private static boolean preventsDiscoveredAttack(String[][] board, int[] move) {
        return move[2] >= 2 && move[2] <= 5;
    }
    
    /**
     * Check if there's an actual discovered attack threat with high-value pieces
     */
    private static boolean hasActualDiscoveredAttack(String[][] board) {
        // Only detect discovered attacks if Queen or Rook is at risk AND it's not a Scholar's Mate setup
        
        // First check if this is a Scholar's Mate pattern - if so, don't trigger discovered attack
        boolean queenThreatsF7 = false;
        boolean bishopThreatsF7 = false;
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];
                if ("♕".equals(piece) && canQueenAttack(r, c, 1, 5, board)) {
                    queenThreatsF7 = true;
                }
                if ("♗".equals(piece) && canBishopAttack(r, c, 1, 5, board)) {
                    bishopThreatsF7 = true;
                }
            }
        }
        
        // If Scholar's Mate pattern detected, don't trigger discovered attack defense
        if (queenThreatsF7 || bishopThreatsF7) {
            logger.debug("*** DISCOVERED ATTACK: Skipping due to Scholar's Mate pattern ***");
            return false;
        }
        
        // Only detect discovered attacks if Queen or Rook is at risk
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = board[r][c];
                if ("♛♜".contains(piece)) { // Black Queen or Rook
                    if (isSquareUnderAttack(board, r, c, true)) {
                        logger.debug("*** DISCOVERED ATTACK: High-value piece {} at [{},{}] under attack ***", piece, r, c);
                        return true; // High-value piece under attack
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean improvesKingSafety(String[][] board, int[] move) {
        return "♟".equals(board[move[0]][move[1]]) && move[0] == 1;
    }
    
    private static boolean controlsKeySquare(String[][] board, int[] move) {
        return (move[2] >= 3 && move[2] <= 4) && (move[3] >= 3 && move[3] <= 4);
    }
    
    /**
     * Check if an attacking piece can create a skewer along the given direction
     */
    private static boolean canAttackerSkewer(String attacker, int dr, int dc) {
        switch (attacker) {
            case "♕": return true; // Queen can skewer in any direction
            case "♖": return dr == 0 || dc == 0; // Rook only on ranks/files
            case "♗": return Math.abs(dr) == Math.abs(dc); // Bishop only on diagonals
            default: return false;
        }
    }
    
    public static boolean isCriticalDefensiveMove(String[][] board, int[] move, String aiName) {
        if (move == null) return false;
        
        // IMPORTANT: Don't block translated moves - they preserve strategic value
        // Only check if the actual piece on the board is defending against checkmate
        String piece = board[move[0]][move[1]];
        if (piece.isEmpty()) return false; // Translated move might reference empty square
        
        // Check if moving this piece would remove critical defense against checkmate
        return isPieceDefendingAgainstCheckmate(board, move[0], move[1]);
    }
    
    private static List<int[]> findAttackersOfSquare(String[][] board, int row, int col, boolean byWhite) {
        List<int[]> attackers = new ArrayList<>();
        String enemyPieces = byWhite ? "♔♕♖♗♘♙" : "♚♛♜♝♞♟";
        
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (!piece.isEmpty() && enemyPieces.contains(piece)) {
                    if (canPieceAttackSquare(board, i, j, row, col, piece)) {
                        attackers.add(new int[]{i, j});
                    }
                }
            }
        }
        return attackers;
    }
    
    /**
     * Find a move that blocks the attack from attacker to target
     */
    private static int[] findBlockingMove(String[][] board, List<int[]> validMoves, int[] attacker, int[] target) {
        String attackerPiece = board[attacker[0]][attacker[1]];
        
        // Only sliding pieces (Queen, Rook, Bishop) can be blocked
        if (!"♕♖♗♛♜♝".contains(attackerPiece)) {
            return null; // Knight, King, Pawn attacks cannot be blocked
        }
        
        // Find squares between attacker and target
        List<int[]> blockingSquares = getSquaresBetween(attacker[0], attacker[1], target[0], target[1]);
        
        // Try to move any piece to a blocking square
        for (int[] blockSquare : blockingSquares) {
            for (int[] move : validMoves) {
                if (move[2] == blockSquare[0] && move[3] == blockSquare[1]) {
                    // Don't block with the Queen itself (defeats the purpose)
                    if (!"♛".equals(board[move[0]][move[1]])) {
                        return move;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get all squares between two positions (for blocking calculations)
     */
    private static List<int[]> getSquaresBetween(int fromR, int fromC, int toR, int toC) {
        List<int[]> squares = new ArrayList<>();
        
        int dr = Integer.compare(toR, fromR);
        int dc = Integer.compare(toC, fromC);
        
        // Must be on same rank, file, or diagonal
        if (dr == 0 && dc == 0) return squares; // Same square
        if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) return squares; // Not diagonal
        
        int r = fromR + dr;
        int c = fromC + dc;
        
        while (r != toR || c != toC) {
            squares.add(new int[]{r, c});
            r += dr;
            c += dc;
        }
        
        return squares;
    }
}