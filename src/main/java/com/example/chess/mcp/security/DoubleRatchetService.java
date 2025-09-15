package com.example.chess.mcp.security;

/**
 * Abstraction for Double Ratchet services.
 */
public interface DoubleRatchetService {

    EncryptedMCPMessage encryptMessage(String agentId, String jsonRpcMessage);

    String decryptMessage(String agentId, EncryptedMCPMessage encryptedMessage);

    void establishSession(String agentId);

    void establishSession(String agentId, boolean isServer);

    void removeSession(String agentId);

    boolean hasSession(String agentId);
}


