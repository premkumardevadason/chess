package com.example.chess.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "mcp.encryption.enabled=true",
    "chess.ai.qlearning.enabled=false",
    "chess.ai.deeplearning.enabled=false", 
    "chess.ai.deeplearningcnn.enabled=false",
    "chess.ai.dqn.enabled=false",
    "chess.ai.mcts.enabled=false",
    "chess.ai.alphazero.enabled=false",
    "chess.ai.negamax.enabled=true",
    "chess.ai.openai.enabled=false",
    "chess.ai.leelazerochess.enabled=false",
    "chess.ai.genetic.enabled=false",
    "chess.ai.alphafold3.enabled=false",
    "chess.ai.a3c.enabled=false"
})
public class MCPDoubleRatchetServiceTest {
    
    private static final Logger logger = LogManager.getLogger(MCPDoubleRatchetServiceTest.class);
    
    private MCPDoubleRatchetService doubleRatchetService;
    
    @BeforeEach
    void setUp() {
        doubleRatchetService = new MCPDoubleRatchetService();
    }
    
    @Test
    void testEncryptDecryptRoundTrip() {
        logger.info("Testing Double Ratchet encrypt/decrypt round trip");
        
        String agentId = "test-agent-1";
        String originalMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"make_chess_move\",\"arguments\":{\"sessionId\":\"test-session\",\"move\":\"e4\"}}}";
        
        // Establish session
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt message
        EncryptedMCPMessage encrypted = doubleRatchetService.encryptMessage(agentId, originalMessage);
        assertNotNull(encrypted);
        assertTrue(encrypted.isEncrypted());
        assertNotNull(encrypted.getCiphertext());
        assertNotNull(encrypted.getIv());
        assertNotNull(encrypted.getHeader());
        
        // Decrypt message
        String decrypted = doubleRatchetService.decryptMessage(agentId, encrypted);
        assertEquals(originalMessage, decrypted);
        
        logger.info("✓ Encrypt/decrypt round trip successful");
    }
    
    @Test
    void testForwardSecrecy() {
        logger.info("Testing Double Ratchet forward secrecy");
        
        String agentId = "test-agent-2";
        String message1 = "{\"jsonrpc\":\"2.0\",\"method\":\"create_chess_game\"}";
        String message2 = "{\"jsonrpc\":\"2.0\",\"method\":\"make_chess_move\"}";
        
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt two messages
        EncryptedMCPMessage encrypted1 = doubleRatchetService.encryptMessage(agentId, message1);
        EncryptedMCPMessage encrypted2 = doubleRatchetService.encryptMessage(agentId, message2);
        
        // Verify different ciphertexts (forward secrecy)
        assertNotEquals(encrypted1.getCiphertext(), encrypted2.getCiphertext());
        assertNotEquals(encrypted1.getIv(), encrypted2.getIv());
        
        // Both should decrypt correctly
        assertEquals(message1, doubleRatchetService.decryptMessage(agentId, encrypted1));
        assertEquals(message2, doubleRatchetService.decryptMessage(agentId, encrypted2));
        
        logger.info("✓ Forward secrecy validated");
    }
    
    @Test
    void testMultipleAgentIsolation() {
        logger.info("Testing multiple agent session isolation");
        
        String agent1 = "test-agent-3";
        String agent2 = "test-agent-4";
        String message = "{\"jsonrpc\":\"2.0\",\"method\":\"get_board_state\"}";
        
        // Establish separate sessions
        doubleRatchetService.establishSession(agent1);
        doubleRatchetService.establishSession(agent2);
        
        // Encrypt same message for both agents
        EncryptedMCPMessage encrypted1 = doubleRatchetService.encryptMessage(agent1, message);
        EncryptedMCPMessage encrypted2 = doubleRatchetService.encryptMessage(agent2, message);
        
        // Should produce different ciphertexts
        assertNotEquals(encrypted1.getCiphertext(), encrypted2.getCiphertext());
        
        // Each agent can only decrypt their own message
        assertEquals(message, doubleRatchetService.decryptMessage(agent1, encrypted1));
        assertEquals(message, doubleRatchetService.decryptMessage(agent2, encrypted2));
        
        // Cross-decryption should fail
        assertThrows(RuntimeException.class, () -> {
            doubleRatchetService.decryptMessage(agent1, encrypted2);
        });
        
        logger.info("✓ Agent isolation validated");
    }
    
    @Test
    void testSessionCleanup() {
        logger.info("Testing session cleanup");
        
        String agentId = "test-agent-5";
        String message = "{\"jsonrpc\":\"2.0\",\"method\":\"analyze_position\"}";
        
        // Establish session and encrypt message
        doubleRatchetService.establishSession(agentId);
        EncryptedMCPMessage encrypted = doubleRatchetService.encryptMessage(agentId, message);
        
        // Verify encryption works
        assertEquals(message, doubleRatchetService.decryptMessage(agentId, encrypted));
        
        // Remove session
        doubleRatchetService.removeSession(agentId);
        
        // Should create new session automatically
        EncryptedMCPMessage newEncrypted = doubleRatchetService.encryptMessage(agentId, message);
        assertEquals(message, doubleRatchetService.decryptMessage(agentId, newEncrypted));
        
        logger.info("✓ Session cleanup validated");
    }
    
    @Test
    void testBackwardCompatibility() {
        logger.info("Testing backward compatibility with unencrypted messages");
        
        String agentId = "test-agent-6";
        String plaintextMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"get_legal_moves\"}";
        
        // Create unencrypted message
        EncryptedMCPMessage unencrypted = new EncryptedMCPMessage(plaintextMessage, false);
        
        // Should return plaintext as-is
        String result = doubleRatchetService.decryptMessage(agentId, unencrypted);
        assertEquals(plaintextMessage, result);
        
        logger.info("✓ Backward compatibility validated");
    }
    
    @Test
    void testRatchetHeaderProgression() {
        logger.info("Testing ratchet header counter progression");
        
        String agentId = "test-agent-7";
        String message = "{\"jsonrpc\":\"2.0\",\"method\":\"get_move_hint\"}";
        
        doubleRatchetService.establishSession(agentId);
        
        // Encrypt multiple messages and verify counter progression
        EncryptedMCPMessage msg1 = doubleRatchetService.encryptMessage(agentId, message);
        EncryptedMCPMessage msg2 = doubleRatchetService.encryptMessage(agentId, message);
        EncryptedMCPMessage msg3 = doubleRatchetService.encryptMessage(agentId, message);
        
        // Verify counter progression
        assertTrue(msg2.getHeader().getMessageCounter() > msg1.getHeader().getMessageCounter());
        assertTrue(msg3.getHeader().getMessageCounter() > msg2.getHeader().getMessageCounter());
        
        logger.info("✓ Ratchet header progression validated");
    }
}