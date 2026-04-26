package com.example.chess.mcp.validation;

import com.example.chess.mcp.session.MCPSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ResourceScopeValidatorTest {
    
    private static final Logger logger = LogManager.getLogger(ResourceScopeValidatorTest.class);
    
    private ResourceScopeValidator validator;
    
    @BeforeEach
    public void setUp() {
        validator = new ResourceScopeValidator();
        // Mock sessionManager is not needed for basic URI validation tests
    }
    
    @Test
    public void testGlobalResourceAccess() {
        logger.info("Testing global resource access validation");
        
        String[] globalResources = {
            "chess://ai-systems",
            "chess://opening-book",
            "chess://training-stats",
            "chess://tactical-patterns"
        };
        
        for (String resource : globalResources) {
            ResourceScopeValidator.AccessValidationResult result = 
                validator.validateResourceAccess("test-agent", resource);
            assertTrue(result.isAllowed(), "Resource " + resource + " should be accessible");
        }
    }
    
    @Test
    public void testAgentSessionsAccess() {
        logger.info("Testing agent sessions resource access");
        
        ResourceScopeValidator.AccessValidationResult result = 
            validator.validateResourceAccess("test-agent", "chess://game-sessions");
        assertTrue(result.isAllowed());
    }
    
    @Test
    public void testInvalidResourceUri() {
        logger.info("Testing invalid resource URI validation");
        
        String[] invalidUris = {
            "invalid://resource",
            "chess://../system",
            "chess://resource//path"
        };
        
        for (String uri : invalidUris) {
            ResourceScopeValidator.AccessValidationResult result = 
                validator.validateResourceAccess("test-agent", uri);
            assertFalse(result.isAllowed());
        }
    }
    
    @Test
    public void testForbiddenResourcePatterns() {
        logger.info("Testing forbidden resource pattern detection");
        
        String[] forbiddenUris = {
            "system://config",
            "file://etc/passwd",
            "chess://database-admin",
            "chess://system-root"
        };
        
        for (String uri : forbiddenUris) {
            ResourceScopeValidator.AccessValidationResult result = 
                validator.validateResourceAccess("test-agent", uri);
            assertFalse(result.isAllowed());
            assertTrue(result.isForbidden() || result.getReason().contains("Invalid resource URI format"));
        }
    }
}



