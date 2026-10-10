package com.project.authservice.controller;

import com.project.authservice.security.JwtKey;
import com.project.authservice.security.KeyManager;
import com.project.authservice.generated.api.DiscoveryApi;
import com.project.authservice.generated.model.JwkSet;
import com.project.authservice.generated.model.JsonWebKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the public keys auth-service uses to sign JWTs in the standard JWKS format.
 * Resource servers ({@code spring-boot-starter-oauth2-resource-server}) fetch and cache
 * this. When we rotate keys, this endpoint serves both the new and previous public keys
 * until tokens signed with the old one expire.
 */
@RestController
@RequiredArgsConstructor
public class JwkSetController implements DiscoveryApi {

    private final KeyManager keyManager;

    @Override
    public ResponseEntity<JwkSet> jwks() {
        List<JsonWebKey> keys = new ArrayList<>();
        for (JwtKey key : keyManager.activeKeys().values()) {
            Map<String, Object> jwk = toJwk(key);
            keys.add(new JsonWebKey().kty((String) jwk.get("kty")).use((String) jwk.get("use"))
                    .alg((String) jwk.get("alg")).kid((String) jwk.get("kid"))
                    .n((String) jwk.get("n")).e((String) jwk.get("e")));
        }
        return ResponseEntity.ok(new JwkSet().keys(keys));
    }

    private Map<String, Object> toJwk(JwtKey key) {
        RSAPublicKey pub = key.getPublicKey();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", key.getKid());
        jwk.put("n", base64UrlNoPad(pub.getModulus().toByteArray()));
        jwk.put("e", base64UrlNoPad(pub.getPublicExponent().toByteArray()));
        return jwk;
    }

    private static String base64UrlNoPad(byte[] bytes) {
        // BigInteger.toByteArray() may return a leading 0x00 sign byte for unsigned values;
        // strip it so the JWK matches what verifiers expect.
        int offset = (bytes.length > 1 && bytes[0] == 0) ? 1 : 0;
        byte[] trimmed = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, trimmed, 0, trimmed.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }
}
