package com.example.chess.mcp.tools;

import com.example.chess.mcp.session.MCPSessionManager;
import com.example.chess.mcp.session.ChessGameSession;
import com.example.chess.mcp.notifications.MCPNotificationService;
import com.example.chess.mcp.utils.UCITranslator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Component
public class ChessToolExecutor {
    
    private static final Logger logger = LogManager.getLogger(ChessToolExecutor.class);
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    @Autowired
    private MCPNotificationService notificationService;
    
    private static final String[] ALL_AI_SYSTEMS = {
        "AlphaZero", "LeelaChessZero", "AlphaFold3", "A3C", "MCTS", "Negamax",
        "OpenAI", "QLearning", "DeepLearning", "CNN", "DQN", "Genetic"
    };
    
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        logger.debug("Executing tool: {} with args: {}", toolName, arguments);
        
        switch (toolName) {
            case "create_chess_game":
                return createChessGame(arguments);
            case "make_chess_move":
                return makeChessMove(arguments);
            case "get_board_state":
                return getBoardState(arguments);
            case "analyze_position":
                return analyzePosition(arguments);
            case "get_legal_moves":
                return getLegalMoves(arguments);
            case "get_move_hint":
                return getMoveHint(arguments);
            case "create_tournament":
                return createTournament(arguments);
            case "get_tournament_status":
                return getTournamentStatus(arguments);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }
    
    private ToolResult createChessGame(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        String aiOpponent = (String) args.get("aiOpponent");
        String playerColor = (String) args.get("playerColor");
        Integer difficulty = (Integer) args.getOrDefault("difficulty", 5);
        
        String sessionId = sessionManager.createSession(agentId, aiOpponent, playerColor, difficulty);
        
        return ToolResult.success(
            String.format("Chess game created successfully! You are playing as %s against %s AI (difficulty %d).\n\nSession ID: %s\nStarting Position (FEN): rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\n\nYour turn! Make your opening move in UCI format (e.g. e2e4).",
                playerColor, aiOpponent, difficulty, sessionId),
            Map.of(
                "sessionId", sessionId,
                "fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "aiOpponent", aiOpponent,
                "playerColor", playerColor,
                "gameStatus", "active",
                "difficulty", difficulty
            )
        );
    }
    
    private ToolResult makeChessMove(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        String uciMove = (String) args.get("move");
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ToolResult.error("Session not found: " + sessionId, 
                Map.of("sessionId", sessionId));
        }
        
        ChessGameSession.MoveResult moveResult = session.makeMove(uciMove);
        
        if (!moveResult.isValid()) {
            return ToolResult.error("Invalid move: " + uciMove, 
                Map.of("legalMoves", moveResult.getLegalMoves()));
        }
        
        String responseText = "Move executed: " + uciMove;
        if (moveResult.getAiMove() != null) {
            responseText += "\nAI responds: " + moveResult.getAiMove();
        }
        responseText += "\n\nCurrent position (FEN): " + moveResult.getFEN();
        responseText += "\nGame Status: " + moveResult.getStatus();
        if ("active".equals(moveResult.getStatus())) {
            responseText += "\nYour turn! Use UCI notation (e.g. e2e4).";
        }
        
        String aiMoveUCI = moveResult.getAiMove();
        
        return ToolResult.success(responseText, Map.of(
            "fen", moveResult.getFEN(),
            "aiMove", aiMoveUCI,
            "gameStatus", moveResult.getStatus(),
            "thinkingTime", moveResult.getThinkingTime(),
            "lastMove", uciMove
        ));
    }
    
    private ToolResult getBoardState(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ToolResult.error("Session not found: " + sessionId, null);
        }
        
        ChessGameSession.GameState gameState = session.getGameState();
        
        return ToolResult.success(
            String.format("Current board state for session %s:\nPosition: %s\nStatus: %s\nMoves played: %d",
                sessionId, gameState.getFEN(), gameState.getStatus(), gameState.getMovesPlayed()),
            Map.of(
                "sessionId", gameState.getSessionId(),
                "gameState", gameState.getFEN(),
                "moveHistory", gameState.getMoveHistory(),
                "currentTurn", gameState.getCurrentTurn(),
                "gameStatus", gameState.getStatus(),
                "movesPlayed", gameState.getMovesPlayed()
            )
        );
    }
    
    private ToolResult analyzePosition(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        Integer depth = (Integer) args.getOrDefault("depth", 5);
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ToolResult.error("Session not found: " + sessionId, null);
        }
        
        // Get AI move in coordinate format and convert to UCI
        int[] aiBestMove = session.getGame().findBestMoveForTesting();
        String bestMoveUCI = null;
        if (aiBestMove != null && aiBestMove.length == 4) {
            bestMoveUCI = UCITranslator.formatMoveToUCI(aiBestMove);
        }
        
        double evaluation = session.getGame().evaluatePosition();
        
        return ToolResult.success(
            String.format("Position analysis for session %s: Best move %s (eval: %.2f)", 
                         sessionId, bestMoveUCI != null ? bestMoveUCI : "none", evaluation),
            Map.of(
                "evaluation", evaluation,
                "bestMove", bestMoveUCI != null ? bestMoveUCI : "none",
                "depth", depth,
                "analysis", "AI position analysis"
            )
        );
    }
    
    private ToolResult getLegalMoves(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ToolResult.error("Session not found: " + sessionId, null);
        }
        
        List<String> legalMoves = session.getLegalMoves();
        
        return ToolResult.success(
            String.format("Legal moves for session %s: %s", sessionId, String.join(", ", legalMoves)),
            Map.of("legalMoves", legalMoves)
        );
    }
    
    private ToolResult getMoveHint(Map<String, Object> args) {
        String sessionId = (String) args.get("sessionId");
        String hintLevel = (String) args.getOrDefault("hintLevel", "intermediate");
        
        ChessGameSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return ToolResult.error("Session not found: " + sessionId, null);
        }
        
        // Get AI move in coordinate format and convert to UCI
        int[] aiBestMove = session.getGame().findBestMoveForTesting();
        String suggestedMoveUCI = null;
        if (aiBestMove != null && aiBestMove.length == 4) {
            suggestedMoveUCI = UCITranslator.formatMoveToUCI(aiBestMove);
        }
        
        if (suggestedMoveUCI == null) {
            return ToolResult.error("No valid move found", null);
        }
        
        String explanation = "AI suggests this move based on position analysis";
        
        return ToolResult.success(
            String.format("Move hint for session %s: %s - %s", sessionId, suggestedMoveUCI, explanation),
            Map.of(
                "suggestedMove", suggestedMoveUCI,
                "explanation", explanation,
                "hintLevel", hintLevel
            )
        );
    }
    
    private ToolResult createTournament(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        String playerColor = (String) args.get("playerColor");
        Integer difficulty = (Integer) args.getOrDefault("difficulty", 5);
        
        logger.info("Creating tournament for agent {} against all 12 AI systems", agentId);
        
        List<String> sessionIds = new ArrayList<>();
        Map<String, String> tournamentResults = new HashMap<>();
        
        for (String aiSystem : ALL_AI_SYSTEMS) {
            try {
                String sessionId = sessionManager.createSession(agentId, aiSystem, playerColor, difficulty);
                sessionIds.add(sessionId);
                tournamentResults.put(aiSystem, sessionId);
                
                logger.debug("Created session {} for agent {} vs {}", sessionId, agentId, aiSystem);
                
            } catch (Exception e) {
                logger.warn("Failed to create session for agent {} vs {}: {}", agentId, aiSystem, e.getMessage());
                tournamentResults.put(aiSystem, "FAILED: " + e.getMessage());
            }
        }
        
        String tournamentId = "tournament-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Send tournament creation notification
        if (notificationService != null) {
            notificationService.notifyTournamentUpdate(agentId, tournamentId, Map.of(
                "event", "tournament_created",
                "totalGames", sessionIds.size(),
                "activeGames", sessionIds.size()
            ));
        }
        
        return ToolResult.success(
            String.format("Tournament created! Agent %s now playing against all 12 AI systems.\nTournament ID: %s\nActive Sessions: %d\nUse get_tournament_status tool to monitor progress.", 
                         agentId, tournamentId, sessionIds.size()),
            Map.of(
                "tournamentId", tournamentId,
                "agentId", agentId,
                "totalGames", sessionIds.size(),
                "sessions", tournamentResults,
                "playerColor", playerColor,
                "difficulty", difficulty
            )
        );
    }
    
    private ToolResult getTournamentStatus(Map<String, Object> args) {
        String agentId = (String) args.get("agentId");
        
        List<ChessGameSession> agentSessions = sessionManager.getAgentSessions(agentId);
        
        Map<String, Object> tournamentStatus = new HashMap<>();
        Map<String, String> gameResults = new HashMap<>();
        Map<String, Integer> moveCounts = new HashMap<>();
        
        int activeGames = 0;
        int completedGames = 0;
        int wins = 0, losses = 0, draws = 0;
        
        for (ChessGameSession session : agentSessions) {
            String aiOpponent = session.getAI().getClass().getSimpleName();
            String status = session.getGameStatus();
            
            gameResults.put(aiOpponent, status);
            moveCounts.put(aiOpponent, session.getMovesPlayed());
            
            if ("active".equals(status)) {
                activeGames++;
            } else {
                completedGames++;
                if (status.contains("win")) wins++;
                else if (status.contains("loss")) losses++;
                else if (status.contains("draw")) draws++;
            }
        }
        
        tournamentStatus.put("agentId", agentId);
        tournamentStatus.put("totalGames", agentSessions.size());
        tournamentStatus.put("activeGames", activeGames);
        tournamentStatus.put("completedGames", completedGames);
        tournamentStatus.put("wins", wins);
        tournamentStatus.put("losses", losses);
        tournamentStatus.put("draws", draws);
        tournamentStatus.put("gameResults", gameResults);
        tournamentStatus.put("moveCounts", moveCounts);
        
        double winRate = completedGames > 0 ? (double) wins / completedGames * 100 : 0;
        
        return ToolResult.success(
            String.format("Tournament Status for Agent %s:\nActive Games: %d, Completed: %d\nRecord: %d-%d-%d (%.1f%% win rate)\nPlaying against: %s", 
                         agentId, activeGames, completedGames, wins, losses, draws, winRate,
                         String.join(", ", gameResults.keySet())),
            tournamentStatus
        );
    }
    
    public static class ToolResult {
        private final boolean success;
        private final String message;
        private final Object data;
        
        private ToolResult(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public static ToolResult success(String message, Object data) {
            return new ToolResult(true, message, data);
        }
        
        public static ToolResult error(String message, Object data) {
            return new ToolResult(false, message, data);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
    }
}