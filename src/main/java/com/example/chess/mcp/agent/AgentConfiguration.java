package com.example.chess.mcp.agent;

/**
 * Configuration class for MCP Chess Agent
 */
public class AgentConfiguration {
    
    private String serverHost = "localhost";
    private int serverPort = 8082;
    private String transportType = "websocket";
    private int concurrentLimit = 2;
    private int timeoutSeconds = 30;
    private int retryAttempts = 3;
    private int gamesPerSession = 100;
    private int aiDifficulty = 8;
    private boolean tournamentMode = false;
    private String whiteAI = "AlphaZero";
    private String blackAI = "LeelaChessZero";
    
    // Getters and setters
    public String getServerHost() { return serverHost; }
    public void setServerHost(String serverHost) { this.serverHost = serverHost; }
    
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    
    public int getConcurrentLimit() { return concurrentLimit; }
    public void setConcurrentLimit(int concurrentLimit) { this.concurrentLimit = concurrentLimit; }
    
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    
    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
    
    public int getGamesPerSession() { return gamesPerSession; }
    public void setGamesPerSession(int gamesPerSession) { this.gamesPerSession = gamesPerSession; }
    
    public int getAiDifficulty() { return aiDifficulty; }
    public void setAiDifficulty(int aiDifficulty) { this.aiDifficulty = aiDifficulty; }
    
    public boolean isTournamentMode() { return tournamentMode; }
    public void setTournamentMode(boolean tournamentMode) { this.tournamentMode = tournamentMode; }
    
    public String getWhiteAI() { return whiteAI; }
    public void setWhiteAI(String whiteAI) { this.whiteAI = whiteAI; }
    
    public String getBlackAI() { return blackAI; }
    public void setBlackAI(String blackAI) { this.blackAI = blackAI; }
    
    public String getServerUrl() {
        if ("websocket".equals(transportType)) {
            return "ws://" + serverHost + ":" + serverPort + "/";
        }
        return serverHost + ":" + serverPort;
    }
}