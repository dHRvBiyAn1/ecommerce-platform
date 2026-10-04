package com.project.order.validation;

import com.project.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAccessValidatorTest {

    private final OrderAccessValidator validator = new OrderAccessValidator();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paymentServiceWithOrdersReadScopeMayReadAnyOrder() {
        authenticate("payment-service", "service", "SCOPE_orders.read");

        assertThatCode(() -> validator.validateRead(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void serviceWithoutOrdersReadScopeIsRejected() {
        authenticate("unknown-service", "service");

        assertThatThrownBy(() -> validator.validateRead(UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void userScopeDoesNotBypassOwnership() {
        authenticate(UUID.randomUUID().toString(), "user", "SCOPE_orders.read");
        assertThatThrownBy(() -> validator.validateRead(UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void serviceAdminWithoutScopeDoesNotBypassOwnership() {
        UUID owner = UUID.randomUUID();
        authenticate(owner.toString(), "service", "ROLE_ADMIN", "orders:read");
        assertThatThrownBy(() -> validator.validateRead(owner))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private static void authenticate(String subject, String tokenType, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("token_type", tokenType)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
