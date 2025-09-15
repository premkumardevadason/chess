package com.example.chess.mcp.security;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal in-memory SignalProtocolStore for demo/testing.
 * Not suitable for production persistence.
 */
public class InMemorySignalProtocolStore implements SignalProtocolStore {

    private final Map<SignalProtocolAddress, SessionRecord> sessions = new HashMap<>();
    // Note: bundles not persisted in-memory; generated on demand
    private final Map<Integer, PreKeyRecord> preKeys = new HashMap<>();
    private final Map<Integer, SignedPreKeyRecord> signedPreKeys = new HashMap<>();
    private final Map<SignalProtocolAddress, IdentityKey> trusted = new HashMap<>();

    private final IdentityKeyPair identityKeyPair;
    private final int registrationId;

    public InMemorySignalProtocolStore() {
        this.identityKeyPair = KeyHelper.generateIdentityKeyPair();
        this.registrationId = KeyHelper.generateRegistrationId(false);

        // Generate initial prekeys
        List<PreKeyRecord> preKeyRecords = KeyHelper.generatePreKeys(1, 50);
        for (PreKeyRecord r : preKeyRecords) preKeys.put(r.getId(), r);

        // Generate signed prekey
        try {
            SignedPreKeyRecord signed = KeyHelper.generateSignedPreKey(identityKeyPair, 1);
            signedPreKeys.put(signed.getId(), signed);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Failed to generate signed prekey", e);
        }
    }

    public PreKeyBundle getPreKeyBundleFor(SignalProtocolAddress address) {
        IdentityKey identityKey = identityKeyPair.getPublicKey();
        SignedPreKeyRecord spr = signedPreKeys.values().iterator().next();
        Optional<PreKeyRecord> anyPre = preKeys.values().stream().findFirst();
        if (anyPre.isEmpty()) {
            // regenerate if exhausted
            List<PreKeyRecord> more = KeyHelper.generatePreKeys(1000, 50);
            for (PreKeyRecord r : more) preKeys.put(r.getId(), r);
            anyPre = preKeys.values().stream().findFirst();
        }
        PreKeyRecord pr = anyPre.get();
        return new PreKeyBundle(
            registrationId,
            1,
            pr.getId(),
            pr.getKeyPair().getPublicKey(),
            spr.getId(),
            spr.getKeyPair().getPublicKey(),
            spr.getSignature(),
            identityKey
        );
    }

    // SessionStore
    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return sessions.getOrDefault(address, new SessionRecord());
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return sessions.keySet().stream()
            .filter(a -> a.getName().equals(name))
            .map(SignalProtocolAddress::getDeviceId)
            .distinct()
            .toList();
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessions.put(address, record);
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return sessions.containsKey(address);
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        sessions.remove(address);
    }

    @Override
    public void deleteAllSessions(String name) {
        sessions.keySet().removeIf(a -> a.getName().equals(name));
    }

    // IdentityKeyStore
    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return registrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        IdentityKey previous = trusted.put(address, identityKey);
        return previous == null || !previous.equals(identityKey);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        IdentityKey known = trusted.get(address);
        return known == null || known.equals(identityKey);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        return trusted.get(address);
    }

    // PreKeyStore
    @Override
    public PreKeyRecord loadPreKey(int preKeyId) {
        return preKeys.get(preKeyId);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        preKeys.put(preKeyId, record);
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return preKeys.containsKey(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        preKeys.remove(preKeyId);
    }

    // SignedPreKeyStore
    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) {
        return signedPreKeys.get(signedPreKeyId);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return signedPreKeys.values().stream().toList();
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        signedPreKeys.put(signedPreKeyId, record);
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return signedPreKeys.containsKey(signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        signedPreKeys.remove(signedPreKeyId);
    }
}


