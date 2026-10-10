package com.project.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthenticationConverterTest {

  @Test
  void mapsUserPermissionsAndServiceScopesWithoutChangingTheirNames() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            java.util.Map.of("alg", "none"),
            java.util.Map.of(
                "sub", "order-service",
                "permissions", List.of("orders:read"),
                "scope", "inventory.write coupons.write"));

    var authorities = JwtAuthenticationConverter.extractAuthorities(jwt);

    assertThat(authorities)
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("orders:read", "SCOPE_inventory.write", "SCOPE_coupons.write");
  }

  @Test
  void ignoresEmptyMissingAndUnsupportedScopeClaims() {
    for (Object scope : List.of("", "   ", 42)) {
      Jwt jwt = jwtWithClaims(Map.of("scope", scope));
      assertThat(JwtAuthenticationConverter.extractAuthorities(jwt)).isEmpty();
    }

    Jwt jwtWithoutScope = jwtWithClaims(Map.of());
    assertThat(JwtAuthenticationConverter.extractAuthorities(jwtWithoutScope)).isEmpty();
    Jwt jwtWithNullScope = jwtWithClaims(Collections.singletonMap("scope", null));
    assertThat(JwtAuthenticationConverter.extractAuthorities(jwtWithNullScope)).isEmpty();
  }

  @Test
  void preservesCollectionScopeConversion() {
    Jwt jwt = jwtWithClaims(Map.of("scope", List.of("read", 7)));

    assertThat(JwtAuthenticationConverter.extractAuthorities(jwt))
        .extracting(Object::toString)
        .containsExactly("SCOPE_read", "SCOPE_7");
  }

  private Jwt jwtWithClaims(Map<String, Object> claims) {
    Map<String, Object> jwtClaims = new HashMap<>();
    jwtClaims.put("sub", "test-subject");
    jwtClaims.putAll(claims);
    return new Jwt(
        "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), jwtClaims);
  }
}
