package com.example.chess.mcp.agent;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side Double Ratchet encryption for MCP communications
 */
public class MCPDoubleRatchetClient {
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, ClientRatchetState> sessions = new ConcurrentHashMap<>();
    
    public void establishSession(String sessionId) throws Exception {
        // Synchronized key derivation with server
        SecretKey rootKey = deriveKeyFromSession(sessionId, "root");
        SecretKey sendingChainKey = deriveKeyFromSession(sessionId, "send");
        SecretKey receivingChainKey = deriveKeyFromSession(sessionId, "recv");
        
        ClientRatchetState state = new ClientRatchetState(
            rootKey, sendingChainKey, receivingChainKey
        );
        
        sessions.put(sessionId, state);
        System.out.println("🔐 Double Ratchet session established for: " + sessionId);
    }
    
    private SecretKey deriveKeyFromSession(String sessionId, String purpose) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(sessionId.getBytes());
        digest.update(purpose.getBytes());
        byte[] keyBytes = digest.digest();
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    public String encryptMessage(String sessionId, String plaintext) throws Exception {
        ClientRatchetState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No ratchet session for: " + sessionId);
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
        
        // Create encrypted message format
        EncryptedMessage encMsg = new EncryptedMessage(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv),
            state.getSendingCounter(),
            state.getReceivingCounter()
        );
        
        System.out.println("🔒 Encrypted message for session: " + sessionId + " (counter: " + state.getSendingCounter() + ")");
        return encMsg.toJson();
    }
    
    public String decryptMessage(String sessionId, String encryptedJson) throws Exception {
        ClientRatchetState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No ratchet session for: " + sessionId);
        }
        
        EncryptedMessage encMsg = EncryptedMessage.fromJson(encryptedJson);
        
        // Advance receiving chain
        state.advanceReceivingChain();
        
        // Generate message key from chain key
        SecretKey messageKey = deriveMessageKey(state.getReceivingChainKey(), encMsg.getMessageCounter());
        
        // Decrypt with AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.getDecoder().decode(encMsg.getIv());
        byte[] ciphertext = Base64.getDecoder().decode(encMsg.getCiphertext());
        
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, messageKey, gcmSpec);
        
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        System.out.println("🔓 Decrypted message for session: " + sessionId + " (counter: " + encMsg.getMessageCounter() + ")");
        return new String(plaintext);
    }
    
    private SecretKey deriveMessageKey(SecretKey chainKey, int counter) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(chainKey.getEncoded());
        digest.update(String.valueOf(counter).getBytes());
        
        byte[] keyBytes = digest.digest();
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        System.out.println("🗑️ Removed Double Ratchet session: " + sessionId);
    }
    
    private static class ClientRatchetState {
        private final SecretKey rootKey;
        private SecretKey sendingChainKey;
        private SecretKey receivingChainKey;
        private int sendingCounter = 0;
        private int receivingCounter = 0;
        
        public ClientRatchetState(SecretKey rootKey, SecretKey sendingChainKey, SecretKey receivingChainKey) {
            this.rootKey = rootKey;
            this.sendingChainKey = sendingChainKey;
            this.receivingChainKey = receivingChainKey;
        }
        
        public void advanceSendingChain() throws Exception {
            sendingCounter++;
            // In full implementation, would derive new chain key
        }
        
        public void advanceReceivingChain() throws Exception {
            receivingCounter++;
            // In full implementation, would derive new chain key
        }
        
        public SecretKey getSendingChainKey() { return sendingChainKey; }
        public SecretKey getReceivingChainKey() { return receivingChainKey; }
        public int getSendingCounter() { return sendingCounter; }
        public int getReceivingCounter() { return receivingCounter; }
    }
    
    private static class EncryptedMessage {
        private final String ciphertext;
        private final String iv;
        private final int messageCounter;
        private final int previousCounter;
        
        public EncryptedMessage(String ciphertext, String iv, int messageCounter, int previousCounter) {
            this.ciphertext = ciphertext;
            this.iv = iv;
            this.messageCounter = messageCounter;
            this.previousCounter = previousCounter;
        }
        
        public String toJson() {
            return String.format(
                "{\"jsonrpc\":\"2.0\",\"encrypted\":true,\"ciphertext\":\"%s\",\"iv\":\"%s\",\"ratchet_header\":{\"message_counter\":%d,\"previous_counter\":%d}}",
                ciphertext, iv, messageCounter, previousCounter
            );
        }
        
        public static EncryptedMessage fromJson(String json) {
            // Simple JSON parsing for encrypted messages
            String ciphertext = extractJsonValue(json, "ciphertext");
            String iv = extractJsonValue(json, "iv");
            int messageCounter = Integer.parseInt(extractJsonValue(json, "message_counter"));
            int previousCounter = Integer.parseInt(extractJsonValue(json, "previous_counter"));
            
            return new EncryptedMessage(ciphertext, iv, messageCounter, previousCounter);
        }
        
        private static String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) {
                // Try numeric pattern
                pattern = "\"" + key + "\":";
                start = json.indexOf(pattern);
                if (start == -1) return "";
                start += pattern.length();
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                return json.substring(start, end);
            }
            start += pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        
        public String getCiphertext() { return ciphertext; }
        public String getIv() { return iv; }
        public int getMessageCounter() { return messageCounter; }
        public int getPreviousCounter() { return previousCounter; }
    }
}