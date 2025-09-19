package com.example.chess;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized Chess Tactical Defense System
 */
public class ChessTacticalDefense {
    private static final Logger logger = LogManager.getLogger(ChessTacticalDefense.class);
    
    public static int[] findBestDefensiveMove(String[][] board, List<int[]> validMoves, String aiName) {
        if (validMoves.isEmpty()) return null;
        
        // Priority 1: Queen under attack
        int[] queenDefense = detectQueenThreats(board, validMoves, aiName);
        if (queenDefense != null) return queenDefense;
        
        // Priority 2: Valuable pieces under attack (Rook, Bishop, Knight)
        int[] valuablePieceDefense = detectValuablePieceThreats(board, validMoves, aiName);
        if (valuablePieceDefense != null) return valuablePieceDefense;
        
        // Priority 3: Immediate checkmate threats
        int[] checkmateDefense = detectCheckmateThreats(board, validMoves, aiName);
        if (checkmateDefense != null) return checkmateDefense;
        
        // Priority 4: Opening traps
        int[] trapDefense = detectOpeningTraps(board, validMoves, aiName);
        if (trapDefense != null) return trapDefense;
        
        // Priority 5: Tactical patterns
        int[] tacticalDefense = detectTacticalThreats(board, validMoves, aiName);
        if (tacticalDefense != null) return tacticalDefense;
        
        // Priority 6: Positional defense (king safety)
        int[] kingSafetyDefense = defendKingSafety(board, validMoves);
        if (kingSafetyDefense != null) {
            logger.info("*** {}: KING SAFETY - Improving pawn shield ***", aiName);
            return kingSafetyDefense;
        }
        
        // Priority 7: Central control (weak squares)
        int[] centralDefense = defendWeakSquares(board, validMoves);
        if (centralDefense != null) {
            logger.info("*** {}: CENTRAL CONTROL - Controlling key squares ***", aiName);
            return centralDefense;
        }
        
        return null;
    }
    
    private static int[] detectQueenThreats(String[][] board, List<int[]> validMoves, String aiName) {
        // Find Black Queen position
        int[] queenPos = null;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ("♛".equals(board[i][j])) {
                    queenPos = new int[]{i, j};
                    break;
                }
            }
            if (queenPos != null) break;
        }
        
        if (queenPos == null) return null;
        
        // Check if Queen is under attack
        boolean queenUnderAttack = isSquareUnderAttack(board, queenPos[0], queenPos[1], true);
        if (!queenUnderAttack) return null;
        
        logger.info("*** TACTICAL DEFENSE: Queen under attack at [{},{}] ***", queenPos[0], queenPos[1]);
        
        return defendValuablePiece(board, validMoves, queenPos, "♛", "Queen");
    }
    
    /**
     * Detect and defend threats against all valuable pieces (Rook, Bishop, Knight)
     */
    private static int[] detectValuablePieceThreats(String[][] board, List<int[]> validMoves, String aiName) {
        String[] valuablePieces = {"♜", "♝", "♞"}; // Rook, Bishop, Knight
        String[] pieceNames = {"Rook", "Bishop", "Knight"};
        
        for (int p = 0; p < valuablePieces.length; p++) {
            String pieceType = valuablePieces[p];
            String pieceName = pieceNames[p];
            
            // Find all pieces of this type
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    if (pieceType.equals(board[i][j])) {
                        // Check if this piece is under attack
                        if (isSquareUnderAttack(board, i, j, true)) {
                            logger.info("*** TACTICAL DEFENSE: {} under attack at [{},{}] ***", pieceName, i, j);
                            
                            int[] defense = defendValuablePiece(board, validMoves, new int[]{i, j}, pieceType, pieceName);
                            if (defense != null) {
                                return defense;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
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
    
    private static int[] detectOpeningTraps(String[][] board, List<int[]> validMoves, String aiName) {
        // Defend against Fried Liver Attack (Italian Game)
        int[] friedLiverDefense = defendFriedLiver(board, validMoves);
        if (friedLiverDefense != null) {
            logger.info("*** {}: FRIED LIVER ATTACK THREAT - Defending f7 ***", aiName);
            return friedLiverDefense;
        }
        
        // Defend against Fishing Pole trap (Ruy Lopez)
        int[] fishingPoleDefense = defendFishingPole(board, validMoves);
        if (fishingPoleDefense != null) {
            logger.info("*** {}: FISHING POLE TRAP THREAT - Keeping rook safe ***", aiName);
            return fishingPoleDefense;
        }
        
        // Execute Noah's Ark trap (trap White bishop)
        int[] noahsArkTrap = defendNoahsArk(board, validMoves);
        if (noahsArkTrap != null) {
            logger.info("*** {}: NOAH'S ARK TRAP - Trapping White bishop ***", aiName);
            return noahsArkTrap;
        }
        
        return null;
    }
    
    private static int[] detectTacticalThreats(String[][] board, List<int[]> validMoves, String aiName) {
        // Defend against pins on king
        int[] pinDefense = defendAgainstPins(board, validMoves);
        if (pinDefense != null) {
            logger.info("*** {}: PIN THREAT - Breaking pin ***", aiName);
            return pinDefense;
        }
        
        // Defend against forks (high-value threats only)
        int[] forkDefense = defendAgainstForks(board, validMoves);
        if (forkDefense != null) {
            logger.info("*** {}: FORK THREAT - Defending valuable pieces ***", aiName);
            return forkDefense;
        }
        
        // Defend against skewers
        int[] skewerDefense = defendAgainstSkewers(board, validMoves);
        if (skewerDefense != null) {
            logger.info("*** {}: SKEWER THREAT - Breaking alignment ***", aiName);
            return skewerDefense;
        }
        
        // Defend against discovered attacks
        int[] discoveredDefense = defendAgainstDiscoveredAttacks(board, validMoves);
        if (discoveredDefense != null) {
            logger.info("*** {}: DISCOVERED ATTACK THREAT - Controlling center ***", aiName);
            return discoveredDefense;
        }
        
        return null;
    }
    
//    private static int[] detectPositionalThreats(String[][] board, List<int[]> validMoves, String aiName) {
//        // Remove all positional defenses - let AI engines handle these
//        return null;
//    }
    
    private static int[] defendScholarsMate(String[][] board, List<int[]> validMoves) {
        // Check for classic Scholar's Mate setup: Queen on f3 + Bishop on c4
        boolean queenOnF3 = "♕".equals(board[5][5]);
        boolean bishopOnC4 = "♗".equals(board[4][2]);
        
        if (queenOnF3 && bishopOnC4) {
            // Priority 1: Knight to f6 - CLASSIC Scholar's Mate defense (with validation)
            for (int[] move : validMoves) {
                if ("♞".equals(board[move[0]][move[1]]) && move[2] == 2 && move[3] == 5) {
                    // Validate that this move actually stops the threat
                    if (!isScholarsMateStillThreat(board, move)) {
                        return move; // Nf6 - blocks checkmate threat
                    }
                }
            }
            
            // Priority 2: Any piece defending f7 (with validation)
            for (int[] move : validMoves) {
                if (move[2] == 1 && move[3] == 5) { // Any piece to f7
                    // Validate that this move actually stops the threat
                    if (!isScholarsMateStillThreat(board, move)) {
                        return move;
                    }
                }
            }
        }
        
        // Also check for general Queen + Bishop threats on f7
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
        
        if (queenThreatsF7 && bishopThreatsF7) {
            for (int[] move : validMoves) {
                if ("♞".equals(board[move[0]][move[1]]) && move[2] == 2 && move[3] == 5) {
                    // Validate that this move actually stops the threat
                    if (!isScholarsMateStillThreat(board, move)) {
                        return move; // Nf6
                    }
                }
            }
        }
        
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
        // Keep rook safe from trapping in Ruy Lopez
        for (int[] move : validMoves) {
            if ("♜".equals(board[move[0]][move[1]])) {
                // Don't move rook to trapped squares
                if (move[2] >= 2) {
                    return move;
                }
            }
        }
        return null;
    }
    
    private static int[] defendNoahsArk(String[][] board, List<int[]> validMoves) {
        // Trap white bishop with pawn moves a6/b5 when bishop is on long diagonal
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if ("♗".equals(board[r][c])) {
                    // Check if bishop can be trapped by a6 or b5
                    if ((r >= 3 && c >= 1 && r - c == 2) || (r >= 2 && c >= 2 && r - c == 0)) {
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
        // Break skewer alignment
        for (int[] move : validMoves) {
            if (breaksSkewer(board, move)) {
                return move;
            }
        }
        return null;
    }
    
    private static int[] defendAgainstDiscoveredAttacks(String[][] board, List<int[]> validMoves) {
        // Prevent discovered attacks by controlling center
        for (int[] move : validMoves) {
            if (preventsDiscoveredAttack(board, move)) {
                return move;
            }
        }
        return null;
    }
    
    private static int[] defendKingSafety(String[][] board, List<int[]> validMoves) {
        // Improve king safety with pawn shield
        for (int[] move : validMoves) {
            if (improvesKingSafety(board, move)) {
                return move;
            }
        }
        return null;
    }
    
    private static int[] defendWeakSquares(String[][] board, List<int[]> validMoves) {
        // Control central weak squares
        for (int[] move : validMoves) {
            if (controlsKeySquare(board, move)) {
                return move;
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
        return move[2] != move[0] || move[3] != move[1];
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
        return Math.abs(move[2] - move[0]) != Math.abs(move[3] - move[1]);
    }
    
    private static boolean preventsDiscoveredAttack(String[][] board, int[] move) {
        return move[2] >= 2 && move[2] <= 5;
    }
    
    private static boolean improvesKingSafety(String[][] board, int[] move) {
        return "♟".equals(board[move[0]][move[1]]) && move[0] == 1;
    }
    
    private static boolean controlsKeySquare(String[][] board, int[] move) {
        return (move[2] >= 3 && move[2] <= 4) && (move[3] >= 3 && move[3] <= 4);
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