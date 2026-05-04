package com.project.authservice.controller;

import com.project.authservice.security.RsaKeyProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

@RestController
public class JwkSetController {

    private final RsaKeyProperties rsaKeys;

    public JwkSetController(RsaKeyProperties rsaKeys) {
        this.rsaKeys = rsaKeys;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        RSAPublicKey publicKey = rsaKeys.getPublicKey();

        // Standard JWK representation for an RSA public key
        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray()),
                "kid", "auth-service-key-1"
        );

        return Map.of("keys", new Object[]{jwk});
    }
}
