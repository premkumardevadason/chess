package com.example.chess.mcp.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * JSON-RPC 2.0 request structure
 */
public class JsonRpcRequest {
    
    @JsonProperty("jsonrpc")
    private final String jsonrpc = "2.0";
    private final long id;
    private final String method;
    private final Map<String, Object> params;
    
    public JsonRpcRequest(long id, String method, Map<String, Object> params) {
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
    
    public Map<String, Object> getParams() {
        return params;
    }
}