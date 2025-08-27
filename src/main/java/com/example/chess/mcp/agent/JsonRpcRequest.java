package com.example.chess.mcp.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 request structure
 */
public class JsonRpcRequest {
    
    private final String jsonrpc = "2.0";
    private final long id;
    private final String method;
    private final JsonNode params;
    
    public JsonRpcRequest(long id, String method, JsonNode params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
    
    public String getJsonrpc() {
        return jsonrpc;
    }
    
    public long getId() {
        return id;
    }
    
    public String getMethod() {
        return method;
    }
    
    public JsonNode getParams() {
        return params;
    }
}