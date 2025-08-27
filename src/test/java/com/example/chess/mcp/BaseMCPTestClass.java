package com.example.chess.mcp;

import com.example.chess.BaseTestClass;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseMCPTestClass extends BaseTestClass {
    
    @BeforeEach
    public void mcpSetUp() {
        super.baseSetUp();
        // MCP-specific setup can go here
    }
}



