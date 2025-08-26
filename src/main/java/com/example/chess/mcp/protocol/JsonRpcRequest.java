package com.example.chess.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class JsonRpcRequest {
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    private Object id;
    private String method;
    private Map<String, Object> params;
    
    public JsonRpcRequest() {}
    
    public JsonRpcRequest(Object id, String method, Map<String, Object> params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
    
    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    
    public Object getId() { return id; }
    public void setId(Object id) { this.id = id; }
    
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}