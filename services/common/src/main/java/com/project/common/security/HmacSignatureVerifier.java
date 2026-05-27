package com.project.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.codec.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC helpers for webhook verification.
 *
 * <p>Used by payment-service to verify webhooks delivered with an
 * {@code X-Webhook-Signature: t=&lt;ts&gt;,v1=&lt;hex-hmac&gt;} header. Stripe-style format
 * for compatibility with real PSPs.
 */
public final class HmacSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacSignatureVerifier() {}

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return new String(Hex.encode(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    /**
     * Verify a {@code t=...,v1=...} signature header against the raw payload.
     * Tolerance is the maximum allowed clock skew in seconds.
     */
    public static boolean verify(String secret, String header, String payload, long toleranceSeconds) {
        if (secret == null || header == null || payload == null) return false;

        String tsValue = null;
        String sigValue = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "t" -> tsValue = kv[1];
                case "v1" -> sigValue = kv[1];
                default -> { /* ignore */ }
            }
        }
        if (tsValue == null || sigValue == null) return false;

        long ts;
        try {
            ts = Long.parseLong(tsValue);
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > toleranceSeconds) return false;

        String signedPayload = ts + "." + payload;
        String expected = sign(secret, signedPayload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sigValue.getBytes(StandardCharsets.UTF_8));
    }

    public static String header(String secret, String payload) {
        long ts = System.currentTimeMillis() / 1000;
        String signed = sign(secret, ts + "." + payload);
        return "t=" + ts + ",v1=" + signed;
    }

    public static String extractSignatureHeader(HttpServletRequest request) {
        String header = request.getHeader("X-Webhook-Signature");
        if (header == null) header = request.getHeader("Stripe-Signature");
        return header;
    }
}
