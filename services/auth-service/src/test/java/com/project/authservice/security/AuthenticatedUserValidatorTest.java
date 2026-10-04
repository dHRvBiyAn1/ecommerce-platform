package com.project.authservice.security;

import com.project.authservice.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserValidatorTest {

    private final AuthenticatedUserValidator validator = new AuthenticatedUserValidator();

    @Test
    void parsesUuidFromAuthenticationName() {
        UUID id = UUID.randomUUID();

        assertThat(validator.requireUserId(new UsernamePasswordAuthenticationToken(id, null))).isEqualTo(id);
    }

    @Test
    void rejectsMalformedPrincipalAsAuthException() {
        assertThatThrownBy(() -> validator.requireUserId(
                new UsernamePasswordAuthenticationToken("not-a-uuid", null)))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid authenticated user");
    }
}
