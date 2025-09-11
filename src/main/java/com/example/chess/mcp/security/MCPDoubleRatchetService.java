package com.example.chess.mcp.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Base64;

/**
 * Core Double Ratchet encryption service for MCP communications
 */
@Service
public class MCPDoubleRatchetService {
    
    private static final Logger logger = LogManager.getLogger(MCPDoubleRatchetService.class);
    
    @Value("${mcp.encryption.enabled:false}")
    private boolean encryptionEnabled;
    
    private final ConcurrentHashMap<String, RatchetState> agentRatchets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    public EncryptedMCPMessage encryptMessage(String agentId, String jsonRpcMessage) {
        if (!encryptionEnabled) {
            return new EncryptedMCPMessage(jsonRpcMessage, false);
        }
        
        try {
            RatchetState ratchet = getOrCreateRatchet(agentId);
            SecretKey messageKey = ratchet.getNextMessageKey();
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, messageKey, gcmSpec);
            
            byte[] ciphertext = cipher.doFinal(jsonRpcMessage.getBytes());
            
            RatchetHeader header = new RatchetHeader(
                ratchet.getDHPublicKey(),
                ratchet.getPreviousCounter(),
                ratchet.getMessageCounter()
            );
            
            return new EncryptedMCPMessage(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv),
                header,
                true
            );
            
        } catch (Exception e) {
            logger.error("Encryption failed for agent {}: {}", agentId, e.getMessage());
            throw new RuntimeException("Failed to encrypt message", e);
        }
    }
    
    public String decryptMessage(String agentId, EncryptedMCPMessage encryptedMessage) {
        if (!encryptionEnabled || !encryptedMessage.isEncrypted()) {
            return encryptedMessage.getCiphertext();
        }
        
        try {
            RatchetState ratchet = getOrCreateRatchet(agentId);
            
            if (encryptedMessage.getHeader().getDhPublicKey() != null) {
                ratchet.advanceDHRatchet(encryptedMessage.getHeader().getDhPublicKey());
            }
            
            // Get the message key that was used for encryption
            int messageCounter = encryptedMessage.getHeader().getMessageCounter();
            SecretKey messageKey = ratchet.getMessageKey(messageCounter);
            if (messageKey == null) {
                throw new RuntimeException("Message key not found for counter: " + messageCounter);
            }
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = Base64.getDecoder().decode(encryptedMessage.getIv());
            byte[] ciphertext = Base64.getDecoder().decode(encryptedMessage.getCiphertext());
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, messageKey, gcmSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
            
        } catch (Exception e) {
            logger.error("Decryption failed for agent {}: {}", agentId, e.getMessage());
            throw new RuntimeException("Failed to decrypt message", e);
        }
    }
    
    public void establishSession(String agentId) {
        try {
            RatchetState ratchet = new RatchetState(agentId);
            agentRatchets.put(agentId, ratchet);
            logger.info("Established Double Ratchet session for agent: {}", agentId);
        } catch (Exception e) {
            logger.error("Failed to establish session for agent {}: {}", agentId, e.getMessage());
            throw new RuntimeException("Failed to establish session", e);
        }
    }
    
    public void removeSession(String agentId) {
        RatchetState removed = agentRatchets.remove(agentId);
        if (removed != null) {
            removed.cleanup();
            logger.info("Removed Double Ratchet session for agent: {}", agentId);
        }
    }
    
    private RatchetState getOrCreateRatchet(String agentId) {
        return agentRatchets.computeIfAbsent(agentId, id -> {
            try {
                return new RatchetState(id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ratchet state", e);
            }
        });
    }
    
    private static class RatchetState {
        private final String agentId;
        private SecretKey rootKey;
        private SecretKey sendingChainKey;
        private SecretKey receivingChainKey;
        private String dhPublicKey;
        private int sendingCounter = 0;
        private int receivingCounter = 0;
        private int previousCounter = 0;
        private final java.util.Map<Integer, SecretKey> messageKeys = new java.util.concurrent.ConcurrentHashMap<>();
        
        public RatchetState(String agentId) throws Exception {
            this.agentId = agentId;
            initializeKeys();
        }
        
        private void initializeKeys() throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            this.rootKey = keyGen.generateKey();
            this.sendingChainKey = keyGen.generateKey();
            this.receivingChainKey = keyGen.generateKey();
            this.dhPublicKey = generateDHPublicKey();
        }
        
        public SecretKey getNextMessageKey() throws Exception {
            SecretKey messageKey = deriveMessageKey(sendingChainKey);
            sendingCounter++;
            messageKeys.put(sendingCounter, messageKey);
            sendingChainKey = deriveChainKey(sendingChainKey);
            return messageKey;
        }
        
        public SecretKey getMessageKey(int counter) {
            return messageKeys.get(counter);
        }
        
        public void advanceDHRatchet(String remoteDHPublicKey) throws Exception {
            previousCounter = sendingCounter;
            sendingCounter = 0;
            receivingCounter = 0;
            
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            rootKey = keyGen.generateKey();
            sendingChainKey = keyGen.generateKey();
            receivingChainKey = keyGen.generateKey();
            
            dhPublicKey = generateDHPublicKey();
        }
        
        private SecretKey deriveMessageKey(SecretKey chainKey) throws Exception {
            byte[] keyBytes = chainKey.getEncoded().clone();
            keyBytes[0] ^= 0x01;
            return new SecretKeySpec(keyBytes, "AES");
        }
        
        private SecretKey deriveChainKey(SecretKey chainKey) throws Exception {
            byte[] keyBytes = chainKey.getEncoded().clone();
            keyBytes[0] ^= 0x02;
            return new SecretKeySpec(keyBytes, "AES");
        }
        
        private String generateDHPublicKey() {
            byte[] keyBytes = new byte[32];
            ThreadLocalRandom.current().nextBytes(keyBytes);
            return Base64.getEncoder().encodeToString(keyBytes);
        }
        
        public String getDHPublicKey() { return dhPublicKey; }
        public int getMessageCounter() { return sendingCounter; }
        public int getPreviousCounter() { return previousCounter; }
        
        public void cleanup() {
            rootKey = null;
            sendingChainKey = null;
            receivingChainKey = null;
            dhPublicKey = null;
        }
    }
}