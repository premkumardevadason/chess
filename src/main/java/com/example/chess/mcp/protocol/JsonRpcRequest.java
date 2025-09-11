package com.example.chess.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcRequest {
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    private Object id;
    private String method;
    private Map<String, Object> params;
    
    // Encryption fields
    private Boolean encrypted;
    private String ciphertext;
    private String iv;
    private Map<String, Object> ratchet_header;
    
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
    
    // Encryption getters/setters
    public Boolean getEncrypted() { return encrypted; }
    public void setEncrypted(Boolean encrypted) { this.encrypted = encrypted; }
    
    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }
    
    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
    
    public Map<String, Object> getRatchet_header() { return ratchet_header; }
    public void setRatchet_header(Map<String, Object> ratchet_header) { this.ratchet_header = ratchet_header; }
    
    public boolean isEncrypted() {
        return encrypted != null && encrypted;
    }
}