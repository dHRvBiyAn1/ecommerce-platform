package com.project.authservice.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loads RSA keypair(s) used to sign and verify JWTs.
 *
 * <p>Production loads from configurable {@link Resource} URIs (typically
 * {@code file:/run/secrets/auth-private.pem}). Development falls back to an
 * in-memory generated keypair so a fresh checkout boots without setup, but emits
 * a loud warning so it's never used by accident in prod.
 *
 * <p>Supports a "previous" key URI for rotation: while the current key signs
 * new tokens, the previous key continues to verify outstanding tokens until
 * they expire. JWKS exposes both.
 *
 * <p>Configuration:
 * <pre>
 * rsa:
 *   private-key-location: ${AUTH_RSA_PRIVATE_KEY_LOCATION:}
 *   public-key-location: ${AUTH_RSA_PUBLIC_KEY_LOCATION:}
 *   kid: ${AUTH_RSA_KID:auth-key-dev}
 *   previous-public-key-location: ${AUTH_RSA_PREVIOUS_PUBLIC_KEY_LOCATION:}
 *   previous-kid: ${AUTH_RSA_PREVIOUS_KID:}
 * </pre>
 */
@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class KeyManager {

    @Value("${rsa.private-key-location:}")
    private String privateKeyLocation;

    @Value("${rsa.public-key-location:}")
    private String publicKeyLocation;

    @Value("${rsa.kid:auth-key-dev}")
    private String kid;

    @Value("${rsa.previous-public-key-location:}")
    private String previousPublicKeyLocation;

    @Value("${rsa.previous-kid:}")
    private String previousKid;

    private final ResourceLoader resourceLoader;

    private JwtKey currentKey;
    private JwtKey previousKey;

    @PostConstruct
    public void init() {
        if (hasText(privateKeyLocation) && hasText(publicKeyLocation)) {
            try {
                currentKey = new JwtKey(kid,
                        loadPublicKey(publicKeyLocation),
                        loadPrivateKey(privateKeyLocation));
                log.info("Loaded RSA signing keypair from filesystem (kid={})", kid);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to load RSA keys from " + privateKeyLocation + " / " + publicKeyLocation, e);
            }
        } else {
            log.warn("===== AUTH-SERVICE: NO RSA KEYS CONFIGURED. =====");
            log.warn("Generating an EPHEMERAL keypair for development. All JWTs will be invalidated on restart.");
            log.warn("Run ./scripts/gen-keys.sh and set AUTH_RSA_PRIVATE_KEY_LOCATION + AUTH_RSA_PUBLIC_KEY_LOCATION.");
            currentKey = generateEphemeralKey("ephemeral-" + UUID.randomUUID().toString().substring(0, 8));
        }

        if (hasText(previousPublicKeyLocation) && hasText(previousKid)) {
            try {
                previousKey = new JwtKey(previousKid, loadPublicKey(previousPublicKeyLocation), null);
                log.info("Loaded previous RSA verification key (kid={})", previousKid);
            } catch (Exception e) {
                log.error("Failed to load previous public key from {}; rotation verification disabled",
                        previousPublicKeyLocation, e);
            }
        }
    }

    /** Map of {@code kid -> JwtKey} for JWKS construction. Order matters: current first. */
    public Map<String, JwtKey> activeKeys() {
        Map<String, JwtKey> out = new LinkedHashMap<>();
        out.put(currentKey.getKid(), currentKey);
        if (previousKey != null) {
            out.put(previousKey.getKid(), previousKey);
        }
        return out;
    }

    public RSAPublicKey publicKeyFor(String kid) {
        if (currentKey != null && currentKey.getKid().equals(kid)) return currentKey.getPublicKey();
        if (previousKey != null && previousKey.getKid().equals(kid)) return previousKey.getPublicKey();
        return null;
    }

    private RSAPublicKey loadPublicKey(String location) throws IOException, NoSuchAlgorithmException,
            java.security.spec.InvalidKeySpecException {
        Resource res = resourceLoader.getResource(location);
        try (InputStream in = res.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            byte[] der = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        }
    }

    private RSAPrivateKey loadPrivateKey(String location) throws IOException, NoSuchAlgorithmException,
            java.security.spec.InvalidKeySpecException {
        Resource res = resourceLoader.getResource(location);
        try (InputStream in = res.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }

    private static byte[] decodePem(String pem, String type) {
        String header = "-----BEGIN " + type + "-----";
        String footer = "-----END " + type + "-----";
        int begin = pem.indexOf(header);
        int end = pem.indexOf(footer);
        if (begin < 0 || end < 0) {
            throw new IllegalArgumentException("Not a valid PEM with type " + type);
        }
        String base64 = pem.substring(begin + header.length(), end).replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    private static JwtKey generateEphemeralKey(String kid) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            return new JwtKey(kid, (RSAPublicKey) kp.getPublic(), (RSAPrivateKey) kp.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
