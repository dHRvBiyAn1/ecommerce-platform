package com.project.authservice.service;

import com.project.authservice.security.KeyManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void serviceTokenContainsIdentityScopeTypeAndUniqueIdClaims() {
        KeyManager keyManager = new KeyManager(new DefaultResourceLoader());
        keyManager.init();
        JwtService jwtService = new JwtService(keyManager);
        ReflectionTestUtils.setField(jwtService, "issuer", "auth-service");

        String token = jwtService.generateServiceToken(
                "order-service",
                Set.of("inventory.reserve", "inventory.commit"),
                Duration.ofMinutes(5));

        Claims claims = Jwts.parser()
                .verifyWith(keyManager.getCurrentKey().getPublicKey())
                .requireIssuer("auth-service")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("order-service");
        assertThat(claims.get("token_type", String.class)).isEqualTo("service");
        assertThat(claims.get("scope", String.class)).isEqualTo("inventory.commit inventory.reserve");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(300_000L);
    }
}
