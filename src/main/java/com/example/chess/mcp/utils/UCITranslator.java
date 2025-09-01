package com.example.chess.mcp.utils;

import java.util.List;
import java.util.ArrayList;

/**
 * Utility class for translating between UCI notation and chess engine coordinates
 */
public class UCITranslator {
    
    /**
     * Parse UCI move notation to coordinates (e.g. "e2e4" -> [6,4,4,4], "c7c8Q" -> [1,2,0,2])
     * Supports both 4-character moves and 5-character pawn promotion moves
     */
    public static int[] parseUCIMove(String uciMove) {
        if (uciMove == null || (uciMove.length() != 4 && uciMove.length() != 5)) return null;
        
        try {
            char fromFile = uciMove.charAt(0);
            char fromRank = uciMove.charAt(1);
            char toFile = uciMove.charAt(2);
            char toRank = uciMove.charAt(3);
            
            int fromCol = fromFile - 'a';
            int fromRow = 8 - (fromRank - '0');
            int toCol = toFile - 'a';
            int toRow = 8 - (toRank - '0');
            
            if (fromCol >= 0 && fromCol < 8 && fromRow >= 0 && fromRow < 8 &&
                toCol >= 0 && toCol < 8 && toRow >= 0 && toRow < 8) {
                return new int[]{fromRow, fromCol, toRow, toCol};
            }
        } catch (Exception e) {
            return null;
        }
        
        return null;
    }
    
    /**
     * Format move coordinates to UCI notation (e.g. [6,4,4,4] -> "e2e4")
     */
    public static String formatMoveToUCI(int[] move) {
        if (move == null || move.length != 4) return null;
        
        char fromFile = (char)('a' + move[1]);
        char toFile = (char)('a' + move[3]);
        int fromRank = 8 - move[0];
        int toRank = 8 - move[2];
        return "" + fromFile + fromRank + toFile + toRank;
    }
    
    /**
     * Convert list of coordinate moves to UCI notation
     */
    public static List<String> convertMovesToUCI(List<int[]> moves) {
        List<String> uciMoves = new ArrayList<>();
        for (int[] move : moves) {
            String uci = formatMoveToUCI(move);
            if (uci != null) {
                uciMoves.add(uci);
            }
        }
        return uciMoves;
    }
}