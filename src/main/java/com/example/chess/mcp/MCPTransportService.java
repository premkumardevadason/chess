package com.example.chess.mcp;

import com.example.chess.mcp.protocol.JsonRpcRequest;
import com.example.chess.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.util.UUID;

@Service
public class MCPTransportService {
    
    private static final Logger logger = LogManager.getLogger(MCPTransportService.class);
    
    @Autowired
    private ChessMCPServer mcpServer;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public void startStdioTransport() {
        logger.info("Starting MCP Chess server via stdio transport");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);
        
        String agentId = generateAgentId();
        logger.info("Generated agent ID: {}", agentId);
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonRpcRequest request = objectMapper.readValue(line, JsonRpcRequest.class);
                    logger.debug("Received request: {}", request.getMethod());
                    
                    JsonRpcResponse response = mcpServer.handleJsonRpcRequest(request, agentId);
                    String responseJson = objectMapper.writeValueAsString(response);
                    
                    writer.println(responseJson);
                    writer.flush();
                    
                } catch (Exception e) {
                    logger.error("Error processing request: {}", e.getMessage());
                    JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                    writer.flush();
                }
            }
        } catch (IOException e) {
            logger.error("MCP stdio transport error: {}", e.getMessage());
        }
        
        logger.info("MCP stdio transport stopped");
    }
    
    private String generateAgentId() {
        return "mcp-agent-" + UUID.randomUUID().toString().substring(0, 8);
    }
}