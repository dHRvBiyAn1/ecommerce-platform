package com.project.authservice.controller;

import com.project.authservice.generated.model.ChangePasswordRequest;
import com.project.authservice.mapper.AuthApiMapper;
import com.project.authservice.exception.AuthException;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthControllerValidationTest {

    private final AuthService authService = mock(AuthService.class);

    @Test
    void malformedPrincipalFailsBeforeChangePasswordService() {
        AuthController controller = new AuthController(authService, mock(ClientCredentialsService.class),
                new AuthenticatedUserValidator(), new AuthApiMapper());

        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken("not-a-uuid", null)));
        try {
            assertThatThrownBy(() -> controller.changePassword(new ChangePasswordRequest().oldPassword("old")
                    .newPassword("newpass"))).isInstanceOf(AuthException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verifyNoInteractions(authService);
    }
}
