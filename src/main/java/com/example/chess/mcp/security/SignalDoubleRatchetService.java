package com.example.chess.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double Ratchet service backed by libsignal-protocol-java.
 * Uses PreKeySignalMessage/SignalMessage and an in-memory protocol store per agent.
 */
@Service
@ConditionalOnProperty(name = "mcp.encryption.impl", havingValue = "signal")
public class SignalDoubleRatchetService implements DoubleRatchetService {

    private static class AgentContext {
        private final InMemorySignalProtocolStore store;
        private final SignalProtocolAddress remoteAddress;
        private final SessionCipher sessionCipher;

        AgentContext(InMemorySignalProtocolStore store, SignalProtocolAddress remoteAddress) {
            this.store = store;
            this.remoteAddress = remoteAddress;
            this.sessionCipher = new SessionCipher(store, remoteAddress);
        }
    }

    private final ConcurrentHashMap<String, AgentContext> sessions = new ConcurrentHashMap<>();

    public PreKeyBundleDto getPreKeyBundle(String agentId) {
        AgentContext ctx = sessions.computeIfAbsent(agentId, id ->
            new AgentContext(new InMemorySignalProtocolStore(), new SignalProtocolAddress(id, 1))
        );
        var store = ctx.store;
        var address = ctx.remoteAddress;
        var bundle = store.getPreKeyBundleFor(address);
        return new PreKeyBundleDto(
            bundle.getRegistrationId(),
            bundle.getDeviceId(),
            bundle.getPreKeyId(),
            java.util.Base64.getEncoder().encodeToString(bundle.getPreKey().serialize()),
            bundle.getSignedPreKeyId(),
            java.util.Base64.getEncoder().encodeToString(bundle.getSignedPreKey().serialize()),
            java.util.Base64.getEncoder().encodeToString(bundle.getSignedPreKeySignature()),
            java.util.Base64.getEncoder().encodeToString(store.getIdentityKeyPair().getPublicKey().serialize())
        );
    }

    public void initializeWithPreKeyBundle(String agentId, PreKeyBundleDto dto) {
        try {
            AgentContext ctx = sessions.computeIfAbsent(agentId, id ->
                new AgentContext(new InMemorySignalProtocolStore(), new SignalProtocolAddress(id, 1))
            );

            byte[] preKeyPubBytes = java.util.Base64.getDecoder().decode(dto.preKeyPublic());
            byte[] spkPubBytes = java.util.Base64.getDecoder().decode(dto.signedPreKeyPublic());
            byte[] spkSigBytes = java.util.Base64.getDecoder().decode(dto.signedPreKeySignature());
            byte[] identityKeyBytes = java.util.Base64.getDecoder().decode(dto.identityKey());

            ECPublicKey preKeyPub = Curve.decodePoint(preKeyPubBytes, 0);
            ECPublicKey spkPub = Curve.decodePoint(spkPubBytes, 0);
            IdentityKey identityKey = new IdentityKey(identityKeyBytes, 0);

            // Verify that the signed prekey is signed by the identity key
            boolean verified = Curve.verifySignature(identityKey.getPublicKey(), spkPub.serialize(), spkSigBytes);
            if (!verified) {
                throw new SecurityException("Invalid signed prekey signature: identity verification failed");
            }
            // Persist identity as trusted (TOFU or prior trust model)
            ctx.store.saveIdentity(ctx.remoteAddress, identityKey);

            PreKeyBundle bundle = new PreKeyBundle(
                dto.registrationId(),
                dto.deviceId(),
                dto.preKeyId(),
                preKeyPub,
                dto.signedPreKeyId(),
                spkPub,
                spkSigBytes,
                identityKey
            );

            SessionBuilder builder = new SessionBuilder(ctx.store, ctx.remoteAddress);
            builder.process(bundle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Signal session with PreKey bundle", e);
        }
    }

    @Override
    public EncryptedMCPMessage encryptMessage(String agentId, String jsonRpcMessage) {
        try {
            AgentContext ctx = sessions.get(agentId);
            if (ctx == null) {
                throw new IllegalStateException("No Signal session for agent: " + agentId);
            }

            byte[] plaintext = jsonRpcMessage.getBytes(StandardCharsets.UTF_8);
            CiphertextMessage message = ctx.sessionCipher.encrypt(plaintext);
            String ciphertextB64 = Base64.getEncoder().encodeToString(message.serialize());
            // Signal does not expose IV or custom ratchet header externally
            return new EncryptedMCPMessage(ciphertextB64, true);
        } catch (Exception e) {
            throw new RuntimeException("Signal encryption failed for agent " + agentId, e);
        }
    }

    @Override
    public String decryptMessage(String agentId, EncryptedMCPMessage encryptedMessage) {
        try {
            AgentContext ctx = sessions.computeIfAbsent(agentId, id ->
                new AgentContext(new InMemorySignalProtocolStore(), new SignalProtocolAddress(id, 1))
            );

            byte[] ciphertext = Base64.getDecoder().decode(encryptedMessage.getCiphertext());
            byte[] plaintext;
            try {
                // Try PreKeySignalMessage (session establishment)
                PreKeySignalMessage preKeyMsg = new PreKeySignalMessage(ciphertext);
                plaintext = ctx.sessionCipher.decrypt(preKeyMsg);
            } catch (InvalidMessageException ex) {
                // Fallback to regular SignalMessage
                SignalMessage signalMessage = new SignalMessage(ciphertext);
                plaintext = ctx.sessionCipher.decrypt(signalMessage);
            }
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (UntrustedIdentityException e) {
            throw new RuntimeException("Untrusted identity during Signal decryption for agent " + agentId, e);
        } catch (Exception e) {
            throw new RuntimeException("Signal decryption failed for agent " + agentId, e);
        }
    }

    @Override
    public void establishSession(String agentId) {
        // Server side: Prepare identity and prekeys; session will be established on first PreKey message
        establishSession(agentId, true);
    }

    @Override
    public void establishSession(String agentId, boolean isServer) {
        sessions.computeIfAbsent(agentId, id -> new AgentContext(new InMemorySignalProtocolStore(), new SignalProtocolAddress(id, 1)));
        // Note: For real clients, publish PreKeyBundle from the store of this agentId.
    }

    @Override
    public void removeSession(String agentId) {
        sessions.remove(agentId);
    }

    @Override
    public boolean hasSession(String agentId) {
        return sessions.containsKey(agentId);
    }
}


