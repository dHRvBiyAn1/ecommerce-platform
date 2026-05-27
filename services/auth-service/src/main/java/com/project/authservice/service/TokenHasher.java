package com.project.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes opaque tokens (refresh tokens) with SHA-256 before persistence. We don't
 * need a slow KDF here: the tokens are already 256 bits of entropy (UUID v4) so
 * brute-force of the hash isn't realistic, and SHA-256 lets us store the digest
 * in an indexable {@code char(64)} column.
 */
final class TokenHasher {

    private TokenHasher() {}

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
