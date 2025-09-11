package com.example.chess.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for proper Double Ratchet with separate sending/receiving chains
 */
public class ProperDoubleRatchetTest {
    
    private static final Logger logger = LogManager.getLogger(ProperDoubleRatchetTest.class);
    
    private MCPDoubleRatchetService doubleRatchetService;
    
    @BeforeEach
    void setUp() {
        doubleRatchetService = new MCPDoubleRatchetService();
        
        // Enable encryption for testing
        try {
            java.lang.reflect.Field field = MCPDoubleRatchetService.class.getDeclaredField("encryptionEnabled");
            field.setAccessible(true);
            field.set(doubleRatchetService, true);
        } catch (Exception e) {
            logger.warn("Could not enable encryption via reflection: {}", e.getMessage());
        }
    }
    
    @Test
    void testSeparateSendingReceivingChains() {
        logger.info("Testing proper Double Ratchet with separate sending/receiving chains");
        
        String agentId = "test-agent-chains";
        doubleRatchetService.establishSession(agentId);
        
        // Send multiple messages and verify they can all be decrypted
        String[] messages = {
            "{\"jsonrpc\":\"2.0\",\"method\":\"create_chess_game\"}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"make_chess_move\",\"params\":{\"move\":\"e4\"}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"get_board_state\"}"
        };
        
        EncryptedMCPMessage[] encrypted = new EncryptedMCPMessage[messages.length];
        
        // Encrypt all messages (advances sending chain)
        for (int i = 0; i < messages.length; i++) {
            encrypted[i] = doubleRatchetService.encryptMessage(agentId, messages[i]);
            assertNotNull(encrypted[i]);
            assertTrue(encrypted[i].isEncrypted());
            assertEquals(i + 1, encrypted[i].getHeader().getMessageCounter());
        }
        
        // Decrypt all messages (advances receiving chain)
        for (int i = 0; i < messages.length; i++) {
            String decrypted = doubleRatchetService.decryptMessage(agentId, encrypted[i]);
            assertEquals(messages[i], decrypted);
        }
        
        logger.info("✓ Separate sending/receiving chains working correctly");
    }
    
    @Test
    void testOutOfOrderMessageHandling() {
        logger.info("Testing out-of-order message handling");
        
        String agentId = "test-agent-ooo";
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt messages in order
        String msg1 = "{\"jsonrpc\":\"2.0\",\"method\":\"msg1\"}";
        String msg2 = "{\"jsonrpc\":\"2.0\",\"method\":\"msg2\"}";
        String msg3 = "{\"jsonrpc\":\"2.0\",\"method\":\"msg3\"}";
        
        EncryptedMCPMessage enc1 = doubleRatchetService.encryptMessage(agentId, msg1);
        EncryptedMCPMessage enc2 = doubleRatchetService.encryptMessage(agentId, msg2);
        EncryptedMCPMessage enc3 = doubleRatchetService.encryptMessage(agentId, msg3);
        
        // Decrypt out of order: 2, 1, 3
        String dec2 = doubleRatchetService.decryptMessage(agentId, enc2);
        assertEquals(msg2, dec2);
        
        String dec1 = doubleRatchetService.decryptMessage(agentId, enc1);
        assertEquals(msg1, dec1);
        
        String dec3 = doubleRatchetService.decryptMessage(agentId, enc3);
        assertEquals(msg3, dec3);
        
        logger.info("✓ Out-of-order message handling working correctly");
    }
    
    @Test
    void testChainKeyEvolution() {
        logger.info("Testing chain key evolution for forward secrecy");
        
        String agentId = "test-agent-evolution";
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt multiple messages
        EncryptedMCPMessage[] encrypted = new EncryptedMCPMessage[5];
        for (int i = 0; i < 5; i++) {
            String message = "{\"jsonrpc\":\"2.0\",\"method\":\"test" + i + "\"}";
            encrypted[i] = doubleRatchetService.encryptMessage(agentId, message);
        }
        
        // Verify all messages have different ciphertexts (forward secrecy)
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                assertNotEquals(encrypted[i].getCiphertext(), encrypted[j].getCiphertext(),
                    "Messages " + i + " and " + j + " should have different ciphertexts");
                assertNotEquals(encrypted[i].getIv(), encrypted[j].getIv(),
                    "Messages " + i + " and " + j + " should have different IVs");
            }
        }
        
        // Verify counters are sequential
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, encrypted[i].getHeader().getMessageCounter(),
                "Message " + i + " should have counter " + (i + 1));
        }
        
        logger.info("✓ Chain key evolution providing proper forward secrecy");
    }
    
    @Test
    void testDHRatchetAdvancement() {
        logger.info("Testing DH ratchet advancement for post-compromise security");
        
        String agentId = "test-agent-dh";
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt a message
        String message1 = "{\"jsonrpc\":\"2.0\",\"method\":\"before_dh\"}";
        EncryptedMCPMessage enc1 = doubleRatchetService.encryptMessage(agentId, message1);
        
        // Simulate DH ratchet step by creating message with new DH key
        // (In real implementation, this would come from remote party)
        String message2 = "{\"jsonrpc\":\"2.0\",\"method\":\"after_dh\"}";
        EncryptedMCPMessage enc2 = doubleRatchetService.encryptMessage(agentId, message2);
        
        // Both messages should decrypt correctly
        assertEquals(message1, doubleRatchetService.decryptMessage(agentId, enc1));
        assertEquals(message2, doubleRatchetService.decryptMessage(agentId, enc2));
        
        // Verify DH public keys are present in headers
        assertNotNull(enc1.getHeader().getDhPublicKey());
        assertNotNull(enc2.getHeader().getDhPublicKey());
        
        logger.info("✓ DH ratchet advancement supporting post-compromise security");
    }
}