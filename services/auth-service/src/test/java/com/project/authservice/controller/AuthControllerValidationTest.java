package com.project.authservice.controller;

import com.project.authservice.dto.request.ChangePasswordRequest;
import com.project.authservice.exception.AuthException;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthControllerValidationTest {

    private final AuthService authService = mock(AuthService.class);

    @Test
    void malformedPrincipalFailsBeforeChangePasswordService() {
        AuthController controller = new AuthController(authService, mock(ClientCredentialsService.class),
                new AuthenticatedUserValidator());

        assertThatThrownBy(() -> controller.changePassword(new ChangePasswordRequest("old", "newpass"),
                new UsernamePasswordAuthenticationToken("not-a-uuid", null)))
                .isInstanceOf(AuthException.class);

        verifyNoInteractions(authService);
    }
}
