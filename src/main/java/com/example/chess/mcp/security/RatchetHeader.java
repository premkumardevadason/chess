package com.example.chess.mcp.security;

/**
 * Double Ratchet header containing DH key and counters
 */
public class RatchetHeader {
    
    private final String dhPublicKey;
    private final int previousCounter;
    private final int messageCounter;
    
    public RatchetHeader(String dhPublicKey, int previousCounter, int messageCounter) {
        this.dhPublicKey = dhPublicKey;
        this.previousCounter = previousCounter;
        this.messageCounter = messageCounter;
    }
    
    public String getDhPublicKey() { return dhPublicKey; }
    public int getPreviousCounter() { return previousCounter; }
    public int getMessageCounter() { return messageCounter; }
}