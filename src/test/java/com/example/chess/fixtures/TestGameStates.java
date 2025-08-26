package com.example.chess.fixtures;

/**
 * Test fixtures for various game states and scenarios
 */
public class TestGameStates {
    
    /**
     * Game state after Scholar's Mate
     */
    public static String getScholarsMateGameState() {
        return """
        {
            "board": [
                ["♜","♞","♝","♛","♚","","♞","♜"],
                ["♟","♟","♟","♟","","♛","♟","♟"],
                ["","","","","","","",""],
                ["","","","","♟","","",""],
                ["","","♗","","♙","","",""],
                ["","","","","","","",""],
                ["♙","♙","♙","♙","","♙","♙","♙"],
                ["♖","♘","♗","♕","♔","","♘","♖"]
            ],
            "whiteTurn": false,
            "gameOver": true,
            "kingInCheck": [0,4],
            "checkmate": true,
            "moveCount": 7
        }
        """;
    }
    
    /**
     * Game state for castling test
     */
    public static String getCastlingGameState() {
        return """
        {
            "board": [
                ["♜","","","","♚","","","♜"],
                ["♟","♟","♟","♟","♟","♟","♟","♟"],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["♙","♙","♙","♙","♙","♙","♙","♙"],
                ["♖","","","","♔","","","♖"]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "canCastleKingside": true,
            "canCastleQueenside": true,
            "moveCount": 8
        }
        """;
    }
    
    /**
     * Game state with en passant opportunity
     */
    public static String getEnPassantGameState() {
        return """
        {
            "board": [
                ["♜","♞","♝","♛","♚","♝","♞","♜"],
                ["♟","♟","♟","","","♟","♟","♟"],
                ["","","","","","","",""],
                ["","","","♟","♙","♟","",""],
                ["","","","","","","",""],
            ["","","","","","","",""],
                ["♙","♙","♙","♙","","♙","♙","♙"],
                ["♖","♘","♗","♕","♔","♗","♘","♖"]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "lastMove": [1,5,3,5],
            "enPassantTarget": [2,5],
            "moveCount": 6
        }
        """;
    }
    
    /**
     * Game state requiring pawn promotion
     */
    public static String getPromotionGameState() {
        return """
        {
            "board": [
                ["♜","♞","♝","♛","♚","♝","♞","♜"],
                ["","♟","♟","♟","♟","♟","♟","♟"],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["♙","","♙","♙","♙","♙","♙","♙"],
                ["♖","♘","♗","♕","♔","♗","♘","♖"]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "awaitingPromotion": true,
            "promotionSquare": [0,0],
            "moveCount": 25
        }
        """;
    }
    
    /**
     * Stalemate game state
     */
    public static String getStalemateGameState() {
        return """
        {
            "board": [
                ["♚","","","","","","",""],
                ["","","♕","","","","",""],
                ["","♔","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""]
            ],
            "whiteTurn": false,
            "gameOver": true,
            "stalemate": true,
            "moveCount": 45
        }
        """;
    }
    
    /**
     * Complex middlegame position
     */
    public static String getMiddlegameState() {
        return """
        {
            "board": [
                ["♜","","♝","♛","♚","","","♜"],
                ["♟","♟","","♟","","♟","♟","♟"],
                ["","","♞","","","♞","",""],
                ["","","♟","","♟","","",""],
                ["","","♗","♙","","","",""],
                ["","","♘","","","♘","",""],
                ["♙","♙","♙","","♙","♙","♙","♙"],
                ["♖","","","♕","♔","♗","","♖"]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "moveCount": 18
        }
        """;
    }
    
    /**
     * Endgame position - King and Pawn vs King
     */
    public static String getKPvsKEndgame() {
        return """
        {
            "board": [
                ["","","","","♚","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","♙","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","","","",""],
                ["","","","","♔","","",""]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "moveCount": 67
        }
        """;
    }
    
    /**
     * Position with tactical threats
     */
    public static String getTacticalPosition() {
        return """
        {
            "board": [
                ["♜","","","♛","♚","","","♜"],
                ["♟","♟","","♟","","♟","♟","♟"],
                ["","","♞","","","","",""],
                ["","","","","♟","","",""],
                ["","","","","♙","","",""],
                ["","","♘","","","","",""],
                ["♙","♙","♙","♙","","♙","♙","♙"],
                ["♖","","♗","♕","♔","♗","","♖"]
            ],
            "whiteTurn": true,
            "gameOver": false,
            "threatenedPieces": [[0,3],[2,2]],
            "moveCount": 12
        }
        """;
    }
}