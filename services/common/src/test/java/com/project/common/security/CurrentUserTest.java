package com.project.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    void userWithMachineScopeRetainsUserIdWithoutBecomingService() {
        Jwt jwt = Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .subject("55bd8159-cb90-4f9d-aaf0-f02e524d8522")
                .claim("scope", "orders.read")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(CurrentUser.isService()).isFalse();
        assertThat(CurrentUser.requireId()).isEqualTo(java.util.UUID.fromString("55bd8159-cb90-4f9d-aaf0-f02e524d8522"));
    }

    @Test
    void serviceSubjectThatLooksLikeUuidNeverBecomesAUserId() {
        Jwt jwt = Jwt.withTokenValue("service-token")
                .header("alg", "RS256")
                .subject("55bd8159-cb90-4f9d-aaf0-f02e524d8522")
                .claim("token_type", "service")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(CurrentUser.id()).isEmpty();
        assertThatThrownBy(CurrentUser::requireId)
                .isInstanceOf(com.project.common.exception.ForbiddenOperationException.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceTokenIsRecognizedWithoutParsingClientIdAsUserUuid() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("order-service")
                .claim("token_type", "service")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(CurrentUser.isService()).isTrue();
        assertThat(CurrentUser.id()).isEmpty();
    }
}
