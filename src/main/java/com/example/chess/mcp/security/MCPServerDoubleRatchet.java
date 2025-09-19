package com.example.chess.mcp.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side Double Ratchet decryption for MCP communications
 */
public class MCPServerDoubleRatchet {
    
    private static final Logger logger = LogManager.getLogger(MCPServerDoubleRatchet.class);
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, ServerRatchetState> sessions = new ConcurrentHashMap<>();
    
    public void establishSession(String agentId) throws Exception {
        // Simplified key exchange - use deterministic keys based on agentId
        // In production, would use proper DH key exchange
        byte[] agentBytes = agentId.getBytes();
        
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        
        // Generate synchronized keys (client uses same algorithm)
        SecretKey rootKey = deriveKeyFromAgent(agentId, "root");
        SecretKey receivingChainKey = deriveKeyFromAgent(agentId, "recv");
        SecretKey sendingChainKey = deriveKeyFromAgent(agentId, "send");
        
        ServerRatchetState state = new ServerRatchetState(
            rootKey, receivingChainKey, sendingChainKey
        );
        
        sessions.put(agentId, state);
        logger.debug("🔐 Server Double Ratchet session established for: " + agentId);
    }
    
    private SecretKey deriveKeyFromAgent(String agentId, String purpose) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(agentId.getBytes());
        digest.update(purpose.getBytes());
        byte[] keyBytes = digest.digest();
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    public String decryptMessage(String agentId, String ciphertext, String iv, int messageCounter) throws Exception {
        ServerRatchetState state = sessions.get(agentId);
        if (state == null) {
            throw new IllegalStateException("No server ratchet session for: " + agentId);
        }
        
        // Advance receiving chain
        state.advanceReceivingChain();
        
        // Generate message key from chain key
        SecretKey messageKey = deriveMessageKey(state.getReceivingChainKey(), messageCounter);
        
        // Decrypt with AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] ivBytes = Base64.getDecoder().decode(iv);
        byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
        
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, messageKey, gcmSpec);
        
        byte[] plaintext = cipher.doFinal(ciphertextBytes);
        
        logger.debug("🔓 Server decrypted message for: " + agentId + " (counter: " + messageCounter + ")");
        return new String(plaintext);
    }
    
    public String encryptMessage(String agentId, String plaintext) throws Exception {
        ServerRatchetState state = sessions.get(agentId);
        if (state == null) {
            throw new IllegalStateException("No server ratchet session for: " + agentId);
        }
        
        // Advance sending chain
        state.advanceSendingChain();
        
        // Generate message key from chain key
        SecretKey messageKey = deriveMessageKey(state.getSendingChainKey(), state.getSendingCounter());
        
        // Encrypt with AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, messageKey, gcmSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        
        // Create encrypted response format
        String encryptedResponse = String.format(
            "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\",\"iv\":\"%s\",\"ratchet_header\":{\"message_counter\":%d,\"previous_counter\":%d}}",
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv),
            state.getSendingCounter(),
            state.getReceivingCounter()
        );
        
        logger.debug("🔒 Server encrypted response for: " + agentId + " (counter: " + state.getSendingCounter() + ")");
        return encryptedResponse;
    }
    
    private SecretKey deriveMessageKey(SecretKey chainKey, int counter) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(chainKey.getEncoded());
        digest.update(String.valueOf(counter).getBytes());
        
        byte[] keyBytes = digest.digest();
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    public void removeSession(String agentId) {
        sessions.remove(agentId);
        logger.debug("🗑️ Removed server Double Ratchet session: " + agentId);
    }
    
    private static class ServerRatchetState {
        private final SecretKey rootKey;
        private SecretKey receivingChainKey;
        private SecretKey sendingChainKey;
        private int receivingCounter = 0;
        private int sendingCounter = 0;
        
        public ServerRatchetState(SecretKey rootKey, SecretKey receivingChainKey, SecretKey sendingChainKey) {
            this.rootKey = rootKey;
            this.receivingChainKey = receivingChainKey;
            this.sendingChainKey = sendingChainKey;
        }
        
        public void advanceReceivingChain() throws Exception {
            receivingCounter++;
            // In full implementation, would derive new chain key
        }
        
        public void advanceSendingChain() throws Exception {
            sendingCounter++;
            // In full implementation, would derive new chain key
        }
        
        public SecretKey getReceivingChainKey() { return receivingChainKey; }
        public SecretKey getSendingChainKey() { return sendingChainKey; }
        public int getReceivingCounter() { return receivingCounter; }
        public int getSendingCounter() { return sendingCounter; }
    }
}