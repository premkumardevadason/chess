package com.example.chess;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;

/**
 * GPT-4 powered chess AI with natural language understanding.
 * Features strategic reasoning and FEN notation processing.
 */
public class OpenAiChessAI {
    
    // Records for better data structures
    public record GPTResponse(String text, int moveIndex, double confidence) {}
    public record PositionAnalysis(String fen, double evaluation, String reasoning) {}
    public record MoveDescription(int index, String from, String to, String notation) {}
    private static final String GPT_MODEL = "gpt-3.5-turbo-instruct";
    private static final Logger logger = LogManager.getLogger(OpenAiChessAI.class);
    private LanguageModel model;
    private boolean debugEnabled;
    private LeelaChessZeroOpeningBook openingBook;
    
    public OpenAiChessAI(String apiKey, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        
        // Initialize the OpenAI language model with:
        // - API key for authentication
        // - GPT-3.5-turbo-instruct model
        // - Temperature of 0.3 for more focused/deterministic outputs
        this.model = OpenAiLanguageModel.builder()
            .apiKey(apiKey)
            .modelName(GPT_MODEL)
            .temperature(0.3)
            .build();
            
        logger.info("*** OpenAI Chess AI: Initialized with {} and Lc0 opening book ***", GPT_MODEL);
    }
    
    public int[] selectMove(String[][] board, List<int[]> validMoves) {
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) return validMoves.get(0);
        
        // CRITICAL: Use centralized tactical defense system
        // Tactical defense now handled centrally in ChessGame.findBestMove()
        
        // Check opening book first
        if (openingBook != null) {
            VirtualChessBoard virtualBoard = new VirtualChessBoard(board, false);
            LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves, virtualBoard.getRuleValidator(), false);
            if (openingResult != null) {
                System.out.println("*** OpenAI: Using Lc0 opening move - " + openingResult.openingName + " ***");
                return openingResult.move;
            }
        }
        
        try {
            String fenNotation = boardToFEN(board);
            String movesDescription = describeValidMoves(validMoves);
            
            String prompt = String.format(
            	"You are a chess grandmaster playing as Black. Analyze this position and choose the best move.\n\n" +
                "Use official chess rules and incorporate strategic best practices. Given the current board position in FEN:\n" +
                "\"%s\"\n" +
                "...your task is to generate the best next move considering the following constraints and heuristics:\n\n" +
                "1. **Rule Compliance**\n" +
                "   - All moves must be legal as per FIDE rules.\n" +
                "   - Respect special rules:\n" +
                "     - Castling\n" +
                "     - En passant\n" +
                "     - Pawn promotion (prefer Queen unless situation demands underpromotion)\n\n" +
                "2. **Opening Principles (if early game)**\n" +
                "   - Control center squares (e4, d4, e5, d5)\n" +
                "   - Develop minor pieces early (Knights before Bishops)\n" +
                "   - Avoid early Queen deployment\n" +
                "   - Ensure King safety (prepare for castling)\n" +
                "   - Do not move the same piece twice unnecessarily\n\n" +
                "3. **Midgame Strategy (if middle game)**\n" +
                "   - Ensure piece coordination\n" +
                "   - Initiate tactics like forks, pins, skewers, discovered attacks\n" +
                "   - Look for vulnerabilities: isolated pawns, hanging pieces\n" +
                "   - Consider sacrifices only if they lead to a clear positional/strategic gain\n\n" +
                "4. **Endgame Principles (if few pieces remain)**\n" +
                "   - Activate King\n" +
                "   - Push passed pawns\n" +
                "   - Use opposition and triangulation\n" +
                "   - Promote pawns safely and forcefully\n" +
                "   - Trade into winning endgames only\n\n" +
                "5. **Check, Checkmate, and Defense**\n" +
                "   - If in check, escape legally\n" +
                "   - Avoid blunders and hanging pieces\n" +
                "   - Look for forced mates or sequences\n\n" +
                "6. **CRITICAL: Avoid Repetitive Moves**\n" +
                "   - DO NOT repeatedly move the same high-value piece (Queen, Rook, Bishop) back and forth\n" +
                "   - Avoid flip-flopping pieces between squares without clear purpose\n" +
                "   - Each move should contribute to positional improvement or tactical advantage\n" +
                "   - Prefer developing new pieces over moving already developed pieces unnecessarily\n\n" +
                "7. **AGGRESSIVE PLAY: Prioritize Attacks and Captures**\n" +
                "   - ALWAYS look for opportunities to capture opponent pieces (especially high-value pieces)\n" +
                "   - Prioritize moves that attack multiple enemy pieces simultaneously\n" +
                "   - Create threats that force opponent responses\n" +
                "   - Don't be overly defensive - seize tactical opportunities\n" +
                "   - Material advantage is crucial - capture when beneficial\n\n" +
                "Valid moves available:\n%s\n\n" +
                "Respond with ONLY the move number (0-%d) of your chosen move. No explanation needed.",
                fenNotation, movesDescription, validMoves.size() - 1
            );
            
            logger.debug("*** OpenAI: FEN sent to " + GPT_MODEL + ": " + fenNotation + " ***");
            
            String response = model.generate(prompt).content();
            logger.debug("*** OpenAI: " + GPT_MODEL + " response: '" + response + "' ***");
            
            int moveIndex = parseResponse(response, validMoves.size());
            
            logger.debug("*** OpenAI: " + GPT_MODEL + " selected move " + moveIndex + " ***");
            
            return validMoves.get(moveIndex);
            
        } catch (Exception e) {
            System.err.println("*** OpenAI: Error - " + e.getMessage() + " ***");
            return validMoves.get(0); // Fallback to first valid move
        }
    }
    
    private String boardToFEN(String[][] board) {
        StringBuilder fen = new StringBuilder();
        
        for (int i = 0; i < 8; i++) {
            int emptyCount = 0;
            for (int j = 0; j < 8; j++) {
                String piece = board[i][j];
                if (piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(convertPieceToFEN(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (i < 7) fen.append('/');
        }
        
        String castlingRights = getCastlingRights(board);
        fen.append(" b ").append(castlingRights).append(" - 0 1");
        
        return fen.toString();
    }
    
    private String convertPieceToFEN(String piece) {
        return switch (piece) {
            case "♔" -> "K";
            case "♕" -> "Q";
            case "♖" -> "R";
            case "♗" -> "B";
            case "♘" -> "N";
            case "♙" -> "P";
            case "♚" -> "k";
            case "♛" -> "q";
            case "♜" -> "r";
            case "♝" -> "b";
            case "♞" -> "n";
            case "♟" -> "p";
            default -> "";
        };
    }
    
    private String describeValidMoves(List<int[]> validMoves) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < validMoves.size(); i++) {
            int[] move = validMoves.get(i);
            String from = squareToAlgebraic(move[0], move[1]);
            String to = squareToAlgebraic(move[2], move[3]);
            sb.append(i).append(": ").append(from).append("-").append(to).append("\n");
        }
        
        return sb.toString();
    }
    
    private String squareToAlgebraic(int row, int col) {
        return String.valueOf((char)('a' + col)) + (8 - row);
    }
    
    private int parseResponse(String response, int maxMoves) {
        try {
            String cleaned = response.trim().replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) return 0;
            
            int moveIndex = Integer.parseInt(cleaned);
            return Math.max(0, Math.min(moveIndex, maxMoves - 1));
            
        } catch (Exception e) {
            return 0;
        }
    }
    
    private String getCastlingRights(String[][] board) {
        StringBuilder rights = new StringBuilder();
        
        // White castling
        if ("♔".equals(board[7][4]) && "♖".equals(board[7][7])) rights.append('K');
        if ("♔".equals(board[7][4]) && "♖".equals(board[7][0])) rights.append('Q');
        
        // Black castling
        if ("♚".equals(board[0][4]) && "♜".equals(board[0][7])) rights.append('k');
        if ("♚".equals(board[0][4]) && "♜".equals(board[0][0])) rights.append('q');
        
        return rights.length() > 0 ? rights.toString() : "-";
    }
    
    /**
     * Evaluate position quality for AI competition
     * Returns confidence score (0-100) for the selected move
     */
    public double evaluatePosition(String[][] board, int[] move) {
        try {
            String fenNotation = boardToFEN(board);
            String moveNotation = squareToAlgebraic(move[0], move[1]) + "-" + squareToAlgebraic(move[2], move[3]);
            
            String prompt = String.format(
                "As a chess grandmaster, evaluate this position after Black plays %s.\n\n" +
                "Position (FEN): %s\n\n" +
                "Rate the position quality from 0-100 where:\n" +
                "- 90-100: Winning advantage\n" +
                "- 70-89: Strong advantage\n" +
                "- 55-69: Slight advantage\n" +
                "- 45-54: Equal position\n" +
                "- 0-44: Disadvantage\n\n" +
                "Respond with ONLY a number (0-100).",
                moveNotation, fenNotation
            );
            
            String response = model.generate(prompt).content();
            String cleaned = response.trim().replaceAll("[^0-9]", "");
            
            if (!cleaned.isEmpty()) {
                int score = Integer.parseInt(cleaned);
                return Math.max(0, Math.min(score, 100));
            }
            
            return 50.0; // Default neutral score
            
        } catch (Exception e) {
            return 50.0; // Default neutral score on error
        }
    }
    

}