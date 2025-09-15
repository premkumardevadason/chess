package com.example.chess.mcp.security;

import org.springframework.stereotype.Service;
import org.springframework.stereotype.Component;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.rfc7748.X25519;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double Ratchet service with DH ratchet (X25519) and HKDF-based chain keys.
 * Backward-compatible message envelope (ciphertext + iv + header).
 */
@Service
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "mcp.encryption.impl", havingValue = "hkdf", matchIfMissing = true)
public class MCPDoubleRatchetService implements DoubleRatchetService {

	private final SecureRandom secureRandom = new SecureRandom();
	private final ConcurrentHashMap<String, RatchetState> sessions = new ConcurrentHashMap<>();
	private final ThreadLocal<Cipher> cipherPool = ThreadLocal.withInitial(() -> {
		try {
			return Cipher.getInstance("AES/GCM/NoPadding");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	});

	// Toggle via reflection in tests if needed
	private volatile boolean encryptionEnabled = true;

	/**
	 * Encrypts MCP JSON-RPC message using Double Ratchet
	 */
	public EncryptedMCPMessage encryptMessage(String agentId, String jsonRpcMessage) {
		try {
			if (!encryptionEnabled) {
				return new EncryptedMCPMessage(jsonRpcMessage, false);
			}
			RatchetState state = sessions.get(agentId);
			if (state == null) {
				throw new IllegalStateException("No session for agent: " + agentId);
			}

			// Prepare header
			String dhPublicB64 = Base64.getEncoder().encodeToString(state.dhPublicKey);
			int previousCounter = state.sendingCounter;
			int nextCounter = state.sendingCounter + 1;
			RatchetHeader header = new RatchetHeader(dhPublicB64, previousCounter, nextCounter);

			// Derive message key from current chain key (before advancing)
			SecretKey messageKey = deriveMessageKey(state.sendingChainKey, nextCounter);

			// Encrypt with AES-GCM
			Cipher cipher = cipherPool.get();
			byte[] iv = new byte[12];
			secureRandom.nextBytes(iv);

			GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
			cipher.init(Cipher.ENCRYPT_MODE, messageKey, gcmSpec);

			byte[] ciphertext = cipher.doFinal(jsonRpcMessage.getBytes(StandardCharsets.UTF_8));

			// Advance sending chain after using message key
			state.advanceSendingChain();

			return new EncryptedMCPMessage(
				Base64.getEncoder().encodeToString(ciphertext),
				Base64.getEncoder().encodeToString(iv),
				header,
				true
			);
		} catch (Exception e) {
			throw new RuntimeException("Encryption failed for agent " + agentId, e);
		}
	}

	/**
	 * Decrypts MCP JSON-RPC message using Double Ratchet
	 */
	public String decryptMessage(String agentId, EncryptedMCPMessage encryptedMessage) {
		try {
			RatchetState state = sessions.get(agentId);
			if (state == null) {
				throw new IllegalStateException("No session for agent: " + agentId);
			}

			if (!encryptedMessage.isEncrypted() || encryptedMessage.getIv() == null) {
				// Backward-compatible plaintext
				return encryptedMessage.getCiphertext();
			}

			byte[] ciphertext = Base64.getDecoder().decode(encryptedMessage.getCiphertext());
			byte[] iv = Base64.getDecoder().decode(encryptedMessage.getIv());
			RatchetHeader header = encryptedMessage.getHeader();
			if (header == null) {
				throw new IllegalArgumentException("Missing ratchet header");
			}

			String remoteDhB64 = header.getDhPublicKey();
			int messageCounter = header.getMessageCounter();

			String replayKey = remoteDhB64 + ":" + messageCounter;
			if (!state.seenMessageIds.add(replayKey)) {
				throw new RuntimeException("Replay detected for message " + messageCounter);
			}

			byte[] remoteDhPub = Base64.getDecoder().decode(remoteDhB64);
			// DH ratchet if remote DH changed
			if (state.remoteDhPublicKey == null || !constantTimeEquals(state.remoteDhPublicKey, remoteDhPub)) {
				state.performDhRatchet(remoteDhPub);
			}

			SecretKey messageKey = state.getOrDeriveReceivingMessageKey(messageCounter);

			Cipher cipher = cipherPool.get();
			GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
			cipher.init(Cipher.DECRYPT_MODE, messageKey, gcmSpec);
			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Decryption failed for agent " + agentId, e);
		}
	}

	/**
	 * Establishes new Double Ratchet session with agent
	 */
	public void establishSession(String agentId) {
		establishSession(agentId, false); // Default to client mode
	}

	/**
	 * Establishes new Double Ratchet session with role specification
	 */
	public void establishSession(String agentId, boolean isServer) {
		try {
			// Random root key per session (no predictable derivation)
			byte[] rootKey = new byte[32];
			secureRandom.nextBytes(rootKey);

			// Initial X25519 key pair
			byte[] dhPrivate = new byte[X25519.SCALAR_SIZE];
			byte[] dhPublic = new byte[X25519.POINT_SIZE];
			X25519.generatePrivateKey(secureRandom, dhPrivate);
			X25519.generatePublicKey(dhPrivate, 0, dhPublic, 0);

			// Initialize chain keys based on role
			byte[] sendingChainKey = deriveChainKey(rootKey, isServer ? "server-to-client" : "client-to-server");
			byte[] receivingChainKey = deriveChainKey(rootKey, isServer ? "client-to-server" : "server-to-client");

			RatchetState state = new RatchetState(rootKey, sendingChainKey, receivingChainKey, dhPrivate, dhPublic);
			sessions.put(agentId, state);

			String role = isServer ? "SERVER" : "CLIENT";
			System.out.println("🔐 Double Ratchet session established for: " + agentId + " (" + role + ")");
		} catch (Exception e) {
			throw new RuntimeException("Failed to establish session for " + agentId, e);
		}
	}

	public void removeSession(String agentId) {
		RatchetState state = sessions.remove(agentId);
		if (state != null) state.clearSensitive();
		System.out.println("🗑️ Removed Double Ratchet session: " + agentId);
	}

	public boolean hasSession(String agentId) {
		return sessions.containsKey(agentId);
	}

	private byte[] deriveChainKey(byte[] rootKey, String purpose) {
		HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
		hkdf.init(new HKDFParameters(rootKey, null, purpose.getBytes(StandardCharsets.UTF_8)));
		byte[] chainKeyBytes = new byte[32];
		hkdf.generateBytes(chainKeyBytes, 0, 32);
		return chainKeyBytes;
	}

	private static SecretKey deriveMessageKey(byte[] chainKey, int counter) {
		HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
		hkdf.init(new HKDFParameters(chainKey, null, ("message-" + counter).getBytes(StandardCharsets.UTF_8)));
		byte[] messageKeyBytes = new byte[32];
		hkdf.generateBytes(messageKeyBytes, 0, 32);
		return new SecretKeySpec(messageKeyBytes, "AES");
	}

	private static byte[] hkdfExtractAndExpand(byte[] salt, byte[] ikm, String info, int outLen) {
		HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
		hkdf.init(new HKDFParameters(ikm, salt, info.getBytes(StandardCharsets.UTF_8)));
		byte[] out = new byte[outLen];
		hkdf.generateBytes(out, 0, outLen);
		return out;
	}

	private boolean constantTimeEquals(byte[] a, byte[] b) {
		if (a == null || b == null || a.length != b.length) return false;
		int r = 0;
		for (int i = 0; i < a.length; i++) r |= (a[i] ^ b[i]);
		return r == 0;
	}

	private static class RatchetState {
		private byte[] rootKey;
		private byte[] sendingChainKey;
		private byte[] receivingChainKey;
		private int sendingCounter = 0;
		private int receivingCounter = 0;
		private byte[] dhPrivateKey;
		private byte[] dhPublicKey;
		private byte[] remoteDhPublicKey;
		private final Map<String, SecretKey> skippedMessageKeys = new ConcurrentHashMap<>();
		private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();

		RatchetState(byte[] rootKey, byte[] sendingChainKey, byte[] receivingChainKey, byte[] dhPrivateKey, byte[] dhPublicKey) {
			this.rootKey = rootKey;
			this.sendingChainKey = sendingChainKey;
			this.receivingChainKey = receivingChainKey;
			this.dhPrivateKey = dhPrivateKey;
			this.dhPublicKey = dhPublicKey;
		}

		void advanceSendingChain() {
			HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
			hkdf.init(new HKDFParameters(sendingChainKey, null, "chain-advance".getBytes(StandardCharsets.UTF_8)));
			byte[] newChainKeyBytes = new byte[32];
			hkdf.generateBytes(newChainKeyBytes, 0, 32);
			sendingChainKey = newChainKeyBytes;
			sendingCounter++;
		}

		void advanceReceivingChain() {
			HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
			hkdf.init(new HKDFParameters(receivingChainKey, null, "chain-advance".getBytes(StandardCharsets.UTF_8)));
			byte[] newChainKeyBytes = new byte[32];
			hkdf.generateBytes(newChainKeyBytes, 0, 32);
			receivingChainKey = newChainKeyBytes;
			receivingCounter++;
		}

		void performDhRatchet(byte[] newRemoteDhPublic) {
			// 1) RK', CKr = KDF(RK, DH(ourPriv, theirPub))
			byte[] shared1 = new byte[32];
			X25519.scalarMult(dhPrivateKey, 0, newRemoteDhPublic, 0, shared1, 0);
			byte[] out1 = hkdfExtractAndExpand(rootKey, shared1, "rk-ckr", 64);
			byte[] rkPrime = new byte[32];
			byte[] ckr = new byte[32];
			System.arraycopy(out1, 0, rkPrime, 0, 32);
			System.arraycopy(out1, 32, ckr, 0, 32);

			// 2) Generate new local DH
			byte[] newPriv = new byte[X25519.SCALAR_SIZE];
			byte[] newPub = new byte[X25519.POINT_SIZE];
			SecureRandom rnd = new SecureRandom();
			X25519.generatePrivateKey(rnd, newPriv);
			X25519.generatePublicKey(newPriv, 0, newPub, 0);

			// 3) RK'', CKs = KDF(RK', DH(newPriv, theirPub))
			byte[] shared2 = new byte[32];
			X25519.scalarMult(newPriv, 0, newRemoteDhPublic, 0, shared2, 0);
			byte[] out2 = hkdfExtractAndExpand(rkPrime, shared2, "rk-cks", 64);
			byte[] rkDblPrime = new byte[32];
			byte[] cks = new byte[32];
			System.arraycopy(out2, 0, rkDblPrime, 0, 32);
			System.arraycopy(out2, 32, cks, 0, 32);

			// Update state
			this.rootKey = rkDblPrime;
			this.receivingChainKey = ckr;
			this.sendingChainKey = cks;
			this.remoteDhPublicKey = newRemoteDhPublic;
			this.dhPrivateKey = newPriv;
			this.dhPublicKey = newPub;
			this.receivingCounter = 0;
			this.sendingCounter = 0;
		}

		SecretKey getOrDeriveReceivingMessageKey(int targetCounter) {
			String base = Base64.getEncoder().encodeToString(remoteDhPublicKey == null ? new byte[0] : remoteDhPublicKey);
			String keyId = base + ":" + targetCounter;
			SecretKey existing = skippedMessageKeys.remove(keyId);
			if (existing != null) {
				return existing;
			}

			// Derive and cache skipped keys up to targetCounter - 1
			while (receivingCounter + 1 < targetCounter) {
				int next = receivingCounter + 1;
				SecretKey mk = deriveMessageKey(receivingChainKey, next);
				advanceReceivingChain();
				skippedMessageKeys.put(base + ":" + next, mk);
			}

			// Derive key for targetCounter
			int next = receivingCounter + 1;
			if (next != targetCounter) {
				// If target is not immediate next, ensure logic is consistent
				// Fall back to deriving directly without advancing state unexpectedly
				SecretKey mk = deriveMessageKey(receivingChainKey, targetCounter);
				// Do not advance chain beyond target
				return mk;
			}
			SecretKey mk = deriveMessageKey(receivingChainKey, next);
			advanceReceivingChain();
			return mk;
		}

		void clearSensitive() {
			if (rootKey != null) java.util.Arrays.fill(rootKey, (byte)0);
			if (sendingChainKey != null) java.util.Arrays.fill(sendingChainKey, (byte)0);
			if (receivingChainKey != null) java.util.Arrays.fill(receivingChainKey, (byte)0);
			if (dhPrivateKey != null) java.util.Arrays.fill(dhPrivateKey, (byte)0);
			if (dhPublicKey != null) java.util.Arrays.fill(dhPublicKey, (byte)0);
			if (remoteDhPublicKey != null) java.util.Arrays.fill(remoteDhPublicKey, (byte)0);
			skippedMessageKeys.clear();
			seenMessageIds.clear();
		}
	}
}