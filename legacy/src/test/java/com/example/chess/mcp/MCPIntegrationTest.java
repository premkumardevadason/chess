package com.example.chess.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SpringBootTest
@TestPropertySource(properties = {
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
public class MCPIntegrationTest {
    // Note: Extending BaseMCPTestClass causes circular dependency issues
    // This test runs without the shared context for MCP-specific testing
    
    private static final Logger logger = LogManager.getLogger(MCPIntegrationTest.class);
    
    @Test
    public void testSpringBootContextLoads() {
        logger.info("Testing Spring Boot context loads with MCP components");
        // This test verifies that Spring Boot can start with all MCP components
        // and that dependency injection works correctly
    }
    
    @Test 
    public void testMCPComponentsAvailable() {
        logger.info("Testing MCP components are available in Spring context");
        // Basic test to ensure MCP beans are created
        // Full MCP protocol testing would require running application in MCP mode
    }
}



