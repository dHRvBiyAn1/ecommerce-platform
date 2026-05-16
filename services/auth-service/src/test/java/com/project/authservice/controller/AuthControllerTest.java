package com.project.authservice.controller;

import com.project.authservice.dto.RegistrationRequest;
import com.project.authservice.dto.TokenResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    void register_WithValidRequest_ReturnsCreated() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setDisplayName("Test User");
        request.setUserType(RegistrationRequest.UserType.CUSTOMER);

        when(authService.register(any(RegistrationRequest.class))).thenReturn(new UserProfileDto());

        ResponseEntity<?> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void token_WithPasswordGrant_ReturnsOk() {
        when(authService.authenticate("password", "test@example.com", "password123", null))
                .thenReturn(new TokenResponse("access-token"));

        ResponseEntity<?> response = authController.token("password", "test@example.com", "password123",
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void logout_ReturnsOk() {
        ResponseEntity<?> response = authController.logout(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
