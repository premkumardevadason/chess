package com.example.chess.mcp.security;

/**
 * DTO for exposing a PreKey bundle over HTTP.
 */
public record PreKeyBundleDto(
    int registrationId,
    int deviceId,
    int preKeyId,
    String preKeyPublic,
    int signedPreKeyId,
    String signedPreKeyPublic,
    String signedPreKeySignature,
    String identityKey
) {}


