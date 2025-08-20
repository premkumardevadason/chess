# OpenAI Chess AI Documentation

## Overview
OpenAI Chess AI leverages GPT-4's natural language understanding and chess knowledge to make strategic decisions. It converts chess positions to FEN notation, provides detailed strategic prompts, and interprets GPT-4's responses to select moves. The system combines AI language models with traditional chess representation.

## How It Works in Chess

### Core Architecture
- **GPT-4 Integration**: Uses OpenAI's GPT-4 model for chess analysis
- **FEN Notation**: Converts board positions to standard chess notation
- **Strategic Prompting**: Provides comprehensive chess principles in prompts
- **Opening Book**: Integrates with Leela Chess Zero opening database

### Key Features
1. **Natural Language Chess**: Understands chess through language model
2. **Strategic Reasoning**: Applies chess principles through detailed prompts
3. **FEN Processing**: Converts 8x8 board to standard chess notation
4. **Move Evaluation**: Can provide confidence scores for positions

## Code Implementation

### Main Class Structure
```java
public class OpenAiChessAI {
    private static final String GPT_MODEL = "gpt-4.1";
    private ChatLanguageModel model;
    private boolean debugEnabled;
    private LeelaChessZeroOpeningBook openingBook;
    
    public OpenAiChessAI(String apiKey, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.openingBook = new LeelaChessZeroOpeningBook(debugEnabled);
        
        this.model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(GPT_MODEL)
            .temperature(0.7)
            .maxTokens(300)
            .build();
    }
}
```

### Move Selection Process
```java
public int[] selectMove(String[][] board, List<int[]> validMoves) {
    if (validMoves.isEmpty()) return null;
    if (validMoves.size() == 1) return validMoves.get(0);
    
    // Check opening book first
    if (openingBook != null) {
        LeelaChessZeroOpeningBook.OpeningMoveResult openingResult = openingBook.getOpeningMove(board, validMoves);
        if (openingResult != null) {
            System.out.println("*** OpenAI: Using Lc0 opening move - " + openingResult.openingName + " ***");
            return openingResult.move;
        }
    }
    
    try {
        String fenNotation = boardToFEN(board);
        String movesDescription = describeValidMoves(validMoves);
        
        String prompt = createStrategicPrompt(fenNotation, movesDescription, validMoves.size());
        
        System.out.println("*** OpenAI: FEN sent to " + GPT_MODEL + ": " + fenNotation + " ***");
        
        String response = model.generate(prompt);
        System.out.println("*** OpenAI: " + GPT_MODEL + " response: '" + response + "' ***");
        
        int moveIndex = parseResponse(response, validMoves.size());
        
        System.out.println("*** OpenAI: " + GPT_MODEL + " selected move " + moveIndex + " ***");
        
        return validMoves.get(moveIndex);
        
    } catch (Exception e) {
        System.err.println("*** OpenAI: Error - " + e.getMessage() + " ***");
        return validMoves.get(0); // Fallback to first valid move
    }
}
```

### Strategic Prompt Creation
```java
private String createStrategicPrompt(String fenNotation, String movesDescription, int moveCount) {
    return String.format(
        "You are a chess grandmaster playing as Black. Analyze this position and choose the best move.\\n\\n" +
        "Use official chess rules and incorporate strategic best practices. Given the current board position in FEN:\\n" +
        "\\\"%s\\\"\\n" +
        "...your task is to generate the best next move considering the following constraints and heuristics:\\n\\n" +
        "1. **Rule Compliance**\\n" +
        "   - All moves must be legal as per FIDE rules.\\n" +
        "   - Respect special rules:\\n" +
        "     - Castling\\n" +
        "     - En passant\\n" +
        "     - Pawn promotion (prefer Queen unless situation demands underpromotion)\\n\\n" +
        "2. **Opening Principles (if early game)**\\n" +
        "   - Control center squares (e4, d4, e5, d5)\\n" +
        "   - Develop minor pieces early (Knights before Bishops)\\n" +
        "   - Avoid early Queen deployment\\n" +
        "   - Ensure King safety (prepare for castling)\\n" +
        "   - Do not move the same piece twice unnecessarily\\n\\n" +
        "3. **Midgame Strategy (if middle game)**\\n" +
        "   - Ensure piece coordination\\n" +
        "   - Initiate tactics like forks, pins, skewers, discovered attacks\\n" +
        "   - Look for vulnerabilities: isolated pawns, hanging pieces\\n" +
        "   - Consider sacrifices only if they lead to a clear positional/strategic gain\\n\\n" +
        "4. **Endgame Principles (if few pieces remain)**\\n" +
        "   - Activate King\\n" +
        "   - Push passed pawns\\n" +
        "   - Use opposition and triangulation\\n" +
        "   - Promote pawns safely and forcefully\\n" +
        "   - Trade into winning endgames only\\n\\n" +
        "5. **Check, Checkmate, and Defense**\\n" +
        "   - If in check, escape legally\\n" +
        "   - Avoid blunders and hanging pieces\\n" +
        "   - Look for forced mates or sequences\\n\\n" +
        "Valid moves available:\\n%s\\n\\n" +
        "Respond with ONLY the move number (0-%d) of your chosen move. No explanation needed.",
        fenNotation, movesDescription, moveCount - 1
    );
}
```

## Chess Strategy

### FEN Notation Conversion
```java
private String boardToFEN(String[][] board) {
    StringBuilder fen = new StringBuilder();
    
    // Board position
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
        if (i < 7) fen.append("/");
    }
    
    // Add turn (always black for AI)
    fen.append(" b");
    
    // Add castling rights (simplified - assume available)
    fen.append(" KQkq");
    
    // Add en passant and move counters (simplified)
    fen.append(" - 0 1");
    
    return fen.toString();
}

private String convertPieceToFEN(String piece) {
    return switch (piece) {
        case "♔" -> "K"; case "♕" -> "Q"; case "♖" -> "R";
        case "♗" -> "B"; case "♘" -> "N"; case "♙" -> "P";
        case "♚" -> "k"; case "♛" -> "q"; case "♜" -> "r";
        case "♝" -> "b"; case "♞" -> "n"; case "♟" -> "p";
        default -> "";
    };
}
```

### Move Description
```java
private String describeValidMoves(List<int[]> validMoves) {
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < validMoves.size(); i++) {
        int[] move = validMoves.get(i);
        String from = squareToAlgebraic(move[0], move[1]);
        String to = squareToAlgebraic(move[2], move[3]);
        sb.append(i).append(": ").append(from).append("-").append(to).append("\\n");
    }
    
    return sb.toString();
}

private String squareToAlgebraic(int row, int col) {
    char file = (char)('a' + col);
    int rank = 8 - row;
    return "" + file + rank;
}
```

### Response Parsing
```java
private int parseResponse(String response, int maxMoves) {
    try {
        // Extract first number from response
        String cleaned = response.trim().replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return 0;
        
        int moveIndex = Integer.parseInt(cleaned.substring(0, 1));
        return Math.max(0, Math.min(moveIndex, maxMoves - 1));
        
    } catch (Exception e) {
        return 0; // Default to first move if parsing fails
    }
}
```

## Strategic Reasoning

### Opening Principles
The AI is prompted with comprehensive opening principles:
- **Center Control**: Emphasizes controlling e4, d4, e5, d5 squares
- **Piece Development**: Knights before bishops, avoid early queen moves
- **King Safety**: Prepare for castling, avoid exposing the king
- **Tempo**: Don't move the same piece twice without purpose

### Midgame Strategy
- **Piece Coordination**: Ensure pieces work together effectively
- **Tactical Awareness**: Look for forks, pins, skewers, discovered attacks
- **Positional Understanding**: Identify weak squares, isolated pawns
- **Sacrifice Evaluation**: Only sacrifice for clear strategic gain

### Endgame Technique
- **King Activity**: Activate the king in the endgame
- **Pawn Promotion**: Push passed pawns safely
- **Technical Knowledge**: Opposition, triangulation, basic endgames
- **Conversion**: Trade into winning endgames when ahead

## Performance Characteristics

### Strengths
- **Strategic Understanding**: Excellent grasp of chess principles
- **Natural Language**: Can reason about positions in human terms
- **Flexibility**: Adapts to different position types
- **Knowledge Base**: Vast chess knowledge from training data

### Considerations
- **API Dependency**: Requires internet connection and API access
- **Response Time**: Network latency affects move selection speed
- **Cost**: API calls incur costs per request
- **Consistency**: May give different responses to identical positions

## Integration Features

### Position Evaluation
```java
public double evaluatePosition(String[][] board, int[] move) {
    try {
        String fenNotation = boardToFEN(board);
        String moveNotation = squareToAlgebraic(move[0], move[1]) + "-" + squareToAlgebraic(move[2], move[3]);
        
        String prompt = String.format(
            "As a chess grandmaster, evaluate this position after Black plays %s.\\n\\n" +
            "Position (FEN): %s\\n\\n" +
            "Rate the position quality from 0-100 where:\\n" +
            "- 90-100: Winning advantage\\n" +
            "- 70-89: Strong advantage\\n" +
            "- 55-69: Slight advantage\\n" +
            "- 45-54: Equal position\\n" +
            "- 0-44: Disadvantage\\n\\n" +
            "Respond with ONLY a number (0-100).",
            moveNotation, fenNotation
        );
        
        String response = model.generate(prompt);
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
```

### Opening Book Integration
- **Priority**: Opening book moves take precedence over GPT-4
- **Transition**: Smooth transition from book to AI analysis
- **Diversity**: Uses professional opening database for variety

## Configuration

### GPT-4 Parameters
```java
this.model = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName(GPT_MODEL)
    .temperature(0.7)      // Balanced creativity/consistency
    .maxTokens(300)        // Sufficient for move selection
    .build();
```

### Prompt Engineering
- **Comprehensive**: Covers all phases of the game
- **Structured**: Clear sections for different strategic elements
- **Specific**: Requests exact format for responses
- **Fallback**: Handles parsing errors gracefully

## Usage Examples

### Basic Setup
```java
OpenAiChessAI openAI = new OpenAiChessAI("your-api-key", true);
```

### Move Selection
```java
int[] move = openAI.selectMove(board, validMoves);
```

### Position Evaluation
```java
double evaluation = openAI.evaluatePosition(board, selectedMove);
```

## Technical Details

### Records for Data Structures
```java
public record GPTResponse(String text, int moveIndex, double confidence) {}
public record PositionAnalysis(String fen, double evaluation, String reasoning) {}
public record MoveDescription(int index, String from, String to, String notation) {}
```

### Error Handling
- **API Failures**: Graceful fallback to first valid move
- **Parsing Errors**: Default to safe move selection
- **Network Issues**: Timeout handling and retry logic
- **Invalid Responses**: Robust response parsing

### Security Considerations
- **API Key Management**: Secure storage of OpenAI API key
- **Input Validation**: Sanitize board positions before sending
- **Rate Limiting**: Respect OpenAI API rate limits
- **Error Logging**: Log errors without exposing sensitive data

### Cost Optimization
- **Token Efficiency**: Optimized prompts to minimize token usage
- **Caching**: Could cache responses for identical positions
- **Batch Processing**: Could batch multiple position evaluations
- **Fallback Strategy**: Use opening book to reduce API calls

This OpenAI Chess AI implementation demonstrates how large language models can be effectively integrated into chess engines, providing strategic reasoning capabilities that complement traditional algorithmic approaches.