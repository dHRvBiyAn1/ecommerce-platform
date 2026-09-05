package com.project.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    @Test
    void mapsUserPermissionsAndServiceScopesWithoutChangingTheirNames() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "sub", "order-service",
                        "permissions", List.of("orders:read"),
                        "scope", "inventory.write coupons.write"));

        var authorities = JwtAuthenticationConverter.extractAuthorities(jwt);

        assertThat(authorities).extracting(Object::toString)
                .containsExactlyInAnyOrder(
                        "orders:read", "SCOPE_inventory.write", "SCOPE_coupons.write");
    }
}
