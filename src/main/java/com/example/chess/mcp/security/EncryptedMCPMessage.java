package com.example.chess.mcp.security;

/**
 * Encrypted MCP message wrapper for Signal Protocol Double Ratchet
 */
public class EncryptedMCPMessage {
    
    private final String ciphertext;
    private final String iv;
    private final RatchetHeader header;
    private final boolean encrypted;
    
    // Constructor for Signal Protocol messages (no IV needed)
    public EncryptedMCPMessage(String ciphertext, boolean encrypted) {
        this.ciphertext = ciphertext;
        this.iv = null;
        this.header = null;
        this.encrypted = encrypted;
    }
    
    // Constructor for custom Double Ratchet messages (with IV)
    public EncryptedMCPMessage(String ciphertext, String iv, RatchetHeader header, boolean encrypted) {
        this.ciphertext = ciphertext;
        this.iv = iv;
        this.header = header;
        this.encrypted = encrypted;
    }
    
    public String getCiphertext() { return ciphertext; }
    public String getIv() { return iv; }
    public RatchetHeader getHeader() { return header; }
    public boolean isEncrypted() { return encrypted; }
}