package com.project.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.authservice.exception.AuthException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthenticatedUserValidatorTest {

  private final AuthenticatedUserValidator validator = new AuthenticatedUserValidator();

  @Test
  void parsesUuidFromAuthenticationName() {
    UUID id = UUID.randomUUID();

    assertThat(validator.requireUserId(new UsernamePasswordAuthenticationToken(id, null)))
        .isEqualTo(id);
  }

  @Test
  void rejectsMalformedPrincipalAsAuthException() {
    assertThatThrownBy(
            () ->
                validator.requireUserId(
                    new UsernamePasswordAuthenticationToken("not-a-uuid", null)))
        .isInstanceOf(AuthException.class)
        .hasMessage("Invalid authenticated user");
  }
}
