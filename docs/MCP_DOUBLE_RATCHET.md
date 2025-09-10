# MCP Double Ratchet Security Design

## Overview

This document outlines the integration of **Double Ratchet encryption** into the Chess MCP Server to provide forward secrecy and post-compromise security for all MCP communications. The design uses ephemeral keys with no message persistence, optimized for chess move communications.

## Security Requirements

### Core Security Goals
- **Forward Secrecy**: Past messages remain secure even if current keys are compromised
- **Post-Compromise Security**: Future messages are secure after key compromise recovery
- **Ephemeral Communications**: No key persistence after successful message delivery
- **MCP Protocol Compatibility**: Transparent encryption layer for JSON-RPC 2.0

### Chess-Specific Optimizations
- **Move-Based Ratcheting**: New keys generated per chess move
- **Session Isolation**: Independent key chains per MCP agent session
- **No Message History**: Keys discarded after successful move transmission
- **Tournament Mode**: Separate key chains for concurrent games

## Architecture

### Double Ratchet Library Selection

**Selected Library**: `libsignal-protocol-java` (Signal Foundation)
- **Repository**: https://github.com/signalapp/libsignal-protocol-java
- **License**: Apache 2.0 (commercial-friendly)
- **Features**: Complete Double Ratchet + X3DH implementation, battle-tested
- **Performance**: Optimized for real-time messaging

### Integration Points

```
MCP Transport Layer
├── WebSocket Transport
│   └── DoubleRatchetWebSocketHandler
├── Stdio Transport  
│   └── DoubleRatchetStdioHandler
└── Security Layer
    ├── MCPDoubleRatchetManager
    ├── SessionKeyStore (ephemeral)
    └── ChessRatchetProtocol
```

## Implementation Design

### 1. Core Security Components

#### MCPDoubleRatchetManager
```java
@Service
@Component
public class MCPDoubleRatchetManager {
    
    // Ephemeral key storage (in-memory only)
    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IdentityKey> agentIdentities = new ConcurrentHashMap<>();
    
    @Autowired
    private SignalProtocolStore protocolStore;
    
    @Autowired
    private MCPMoveHistoryManager moveHistoryManager;
    
    public EncryptedMCPMessage encryptMCPMessage(String agentId, String jsonMessage) throws EncryptionException;
    public String decryptMCPMessage(String agentId, EncryptedMCPMessage encrypted) throws DecryptionException;
    public void establishSession(String agentId, PreKeyBundle preKeyBundle) throws SessionException;
    public void cleanupSession(String agentId); // Called after successful move
}
```

#### ChessRatchetProtocol
```java
@Component
public class ChessRatchetProtocol {
    
    // Chess-optimized ratcheting
    public void ratchetOnMove(String sessionId, String move);
    public void ratchetOnGameEnd(String sessionId);
    
    // Tournament mode support
    public void initializeTournamentKeys(String agentId, List<String> gameIds);
    public void cleanupTournamentKeys(String agentId);
}

#### MCPMoveHistoryManager
```java
@Component
public class MCPMoveHistoryManager {
    
    /**
     * Extracts and preserves move history for AI training
     * while maintaining encryption for transmission
     */
    public void preserveMoveHistoryForTraining(String gameId, List<ChessMove> moves);
    
    /**
     * Encrypts move history separately for training data
     */
    public EncryptedTrainingData encryptTrainingData(TrainingDataBatch batch);
}
```

### 2. Transport Layer Integration

#### Secure WebSocket Handler
```java
@Component
public class DoubleRatchetWebSocketHandler extends TextWebSocketHandler {
    
    @Autowired
    private MCPDoubleRatchetManager ratchetManager;
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String agentId = extractAgentId(session);
        
        // Decrypt incoming message
        String decryptedJson = ratchetManager.decryptMCPMessage(agentId, 
            parseEncryptedMessage(message.getPayload()));
        
        // Process MCP request
        String responseJson = processMCPRequest(decryptedJson);
        
        // Encrypt and send response
        EncryptedMessage encrypted = ratchetManager.encryptMCPMessage(agentId, responseJson);
        session.sendMessage(new TextMessage(serializeEncrypted(encrypted)));
        
        // Cleanup keys if move completed successfully
        if (isMoveCompleted(responseJson)) {
            ratchetManager.ratchetOnMove(agentId);
        }
    }
}
```

### 3. Key Management Strategy

#### Ephemeral Key Lifecycle
```
Session Start → Initial Key Exchange → Move Encryption → Key Ratchet → Key Cleanup
     ↓               ↓                    ↓              ↓            ↓
  PreKey Bundle → Session Keys → Message Keys → New Chain → Memory Clear
```

#### No Persistence Policy
- **Memory Only**: All keys stored in volatile memory
- **Immediate Cleanup**: Keys deleted after successful message delivery
- **Session Timeout**: Automatic cleanup after 30 minutes of inactivity
- **Graceful Shutdown**: All keys cleared on application shutdown

### 4. MCP Protocol Extensions

#### Dedicated Key Exchange
```json
{
  "jsonrpc": "2.0",
  "method": "mcp/key_exchange",
  "params": {
    "agent_id": "unique-agent-identifier",
    "identity_key": "base64-encoded-identity-key",
    "prekey_bundle": {
      "identity_key": "base64-encoded-server-identity",
      "signed_prekey": "base64-encoded-signed-prekey",
      "prekey_signature": "base64-encoded-signature",
      "one_time_prekey": "base64-encoded-onetime-key"
    }
  }
}
```

#### Secure Initialization
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "clientInfo": {"name": "chess-client", "version": "1.0.0"},
    "security": {
      "doubleRatchet": true
    }
  }
}
```

#### Encrypted Message Format
```json
{
  "jsonrpc": "2.0",
  "id": "client-request-id",
  "encrypted": true,
  "ratchet_header": {
    "dh_public_key": "base64-encoded-dh-key",
    "previous_counter": 42,
    "message_counter": 43
  },
  "ciphertext": "base64-encoded-encrypted-mcp-message",
  "mac": "base64-encoded-hmac"
}
```

## Security Features

### Forward Secrecy Implementation
- **Root Key Ratcheting**: New root key generated per chess move
- **Chain Key Ratcheting**: New message keys for each MCP request/response
- **Key Deletion**: Immediate cleanup prevents key recovery

### Post-Compromise Security
- **Key Recovery**: New Diffie-Hellman exchange on next move
- **Chain Healing**: Compromised chains automatically replaced
- **Session Reset**: Emergency session reset capability

### Chess-Specific Security
- **Move Validation**: Encrypted moves still validated server-side
- **Tournament Isolation**: Independent key chains per concurrent game
- **AI System Protection**: AI responses encrypted before transmission

## Performance Considerations

### Optimization Strategies
- **Key Caching**: Temporary caching during active gameplay
- **Batch Operations**: Efficient key generation for tournament mode
- **Memory Management**: Aggressive cleanup to prevent memory leaks

### Performance Targets
- **Encryption Overhead**: < 5ms per message
- **Key Generation**: < 10ms per new session
- **Memory Usage**: < 1MB per active session
- **Concurrent Sessions**: Support for 100 encrypted sessions

## Configuration

### Production Configuration
```yaml
mcp:
  double-ratchet:
    enabled: true
    mode: production
    key-store: memory  # Ephemeral only
    key-rotation-interval: 24h
    audit-logging: true
    session-timeout: 1800000
    max-sessions: 100
    
    # Performance tuning
    key-cache-size: 50
    prekey-count: 100
    ratchet-on-move: true
    async-encryption: true
```

### Development Configuration
```yaml
mcp:
  double-ratchet:
    enabled: true
    mode: development
    key-store: memory
    debug-logging: true
    test-mode: false
    key-persistence: false  # Always ephemeral
```

## Implementation Phases

### Phase 1: Core Implementation
- [ ] Add libsignal-protocol-java dependency
- [ ] Implement MCPDoubleRatchetManager with SignalProtocolStore
- [ ] Create dedicated mcp/key_exchange method
- [ ] Implement MCPMoveHistoryManager for training data
- [ ] Unit tests for encryption/decryption and X3DH key exchange

### Phase 2: MCP Integration
- [ ] WebSocket transport integration with EncryptedMCPWebSocketHandler
- [ ] Stdio transport integration
- [ ] JSON-RPC 2.0 compliance with MCPEncryptionMiddleware
- [ ] Session management with MCPSessionRegistry
- [ ] Integration tests for end-to-end message flow

### Phase 3: Production Readiness
- [ ] Performance optimization with thread pools
- [ ] Security auditing and penetration testing
- [ ] Monitoring and logging implementation
- [ ] YAML configuration management
- [ ] Documentation and deployment guides

### Phase 4: Deployment
- [ ] Staging environment testing
- [ ] Gradual production rollout
- [ ] Performance monitoring and validation
- [ ] Security validation and compliance verification

## Testing Strategy

### Security Testing
- **Key Rotation Validation**: Verify keys change per move
- **Forward Secrecy Testing**: Confirm past message security
- **Compromise Recovery**: Test post-compromise security
- **Session Isolation**: Validate independent key chains

### Performance Testing
- **Encryption Overhead**: Measure latency impact
- **Memory Usage**: Monitor key storage efficiency
- **Concurrent Load**: Test 100 simultaneous encrypted sessions
- **Tournament Stress**: Validate 12-game concurrent encryption

### Integration Testing
- **MCP Compatibility**: Ensure protocol compliance
- **Chess Functionality**: Verify game logic integrity
- **AI System Integration**: Test encrypted AI communications
- **Error Scenarios**: Validate graceful failure handling

## Security Considerations

### Threat Model
- **Network Eavesdropping**: Encrypted communications prevent interception
- **Key Compromise**: Forward secrecy limits damage scope
- **Server Compromise**: Ephemeral keys minimize exposure
- **Replay Attacks**: Message counters prevent replay

### Risk Mitigation
- **No Key Persistence**: Eliminates long-term key exposure
- **Session Isolation**: Limits compromise scope to single agent
- **Automatic Cleanup**: Reduces attack surface over time
- **Monitoring**: Detects unusual encryption patterns

## Deployment Considerations

### Production Deployment
- **Key Generation**: Secure random number generation
- **Memory Protection**: Prevent key swapping to disk
- **Monitoring**: Track encryption performance and errors
- **Backup Strategy**: No key backup (by design)

### Operational Security
- **Log Sanitization**: Ensure no keys in application logs
- **Memory Dumps**: Prevent key exposure in crash dumps
- **Process Isolation**: Separate encryption from game logic
- **Update Strategy**: Secure library update procedures

## Conclusion

The Double Ratchet integration provides military-grade security for MCP chess communications while maintaining the ephemeral, move-based nature of chess gameplay. The design prioritizes forward secrecy and post-compromise security without compromising the real-time performance requirements of the chess application.

Key benefits:
- **Zero Persistence**: No long-term key storage reduces attack surface
- **Chess Optimized**: Ratcheting aligned with chess move patterns
- **MCP Compatible**: Transparent security layer for existing protocol
- **Scalable**: Supports 100 concurrent encrypted sessions
- **Battle-Tested**: Uses proven Signal Protocol implementation

This implementation establishes the Chess MCP Server as a reference implementation for secure, ephemeral communications in gaming and AI interaction protocols.