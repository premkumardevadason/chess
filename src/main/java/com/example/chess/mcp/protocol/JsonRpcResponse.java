package com.example.chess.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    private Object id;
    private Object result;
    private JsonRpcError error;
    
    public JsonRpcResponse() {}
    
    public JsonRpcResponse(Object id, Object result) {
        this.id = id;
        this.result = result;
    }
    
    public JsonRpcResponse(Object id, JsonRpcError error) {
        this.id = id;
        this.error = error;
    }
    
    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse(id, result);
    }
    
    public static JsonRpcResponse error(Object id, int code, String message) {
        return new JsonRpcResponse(id, new JsonRpcError(code, message));
    }
    
    public static JsonRpcResponse methodNotFound(Object id) {
        return error(id, -32601, "Method not found");
    }
    
    public static JsonRpcResponse invalidParams(Object id) {
        return error(id, -32602, "Invalid params");
    }
    
    public static JsonRpcResponse internalError(Object id, String message) {
        return error(id, -32603, "Internal error: " + message);
    }
    
    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    
    public Object getId() { return id; }
    public void setId(Object id) { this.id = id; }
    
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    
    public JsonRpcError getError() { return error; }
    public void setError(JsonRpcError error) { this.error = error; }
    
    public static class JsonRpcError {
        private int code;
        private String message;
        private Object data;
        
        public JsonRpcError() {}
        
        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
}