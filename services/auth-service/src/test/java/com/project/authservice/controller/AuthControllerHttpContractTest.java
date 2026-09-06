package com.project.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.request.RegistrationRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.InvalidScopeException;
import com.project.authservice.exception.TokenRefreshException;
import com.project.authservice.exception.UserAlreadyExistsException;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.common.constant.ErrorCode;
import com.project.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(value = AuthController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerHttpContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ClientCredentialsService clientCredentialsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void registerReturnsCommonSuccessEnvelopeWithTraceAndTimestamp() throws Exception {
        UserProfileDto profile = new UserProfileDto();
        profile.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        profile.setEmail("customer@example.com");
        profile.setDisplayName("Customer One");
        profile.setActive(true);
        profile.setCreatedAt(LocalDateTime.parse("2026-09-05T10:15:30"));
        profile.setHasPassword(true);

        when(authService.register(any(RegistrationRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "ValidPass123",
                                  "displayName": "Customer One"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerUserAlreadyExistsUsesCommonConflictContract() throws Exception {
        when(authService.register(any(RegistrationRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Email already in use"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "ValidPass123",
                                  "displayName": "Customer One"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already in use"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.value()))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenPasswordGrantAuthFailureUsesCommonUnauthorizedContract() throws Exception {
        when(authService.authenticate(
                "password",
                "customer@example.com",
                "WrongPass123",
                null,
                null,
                "127.0.0.1")).thenThrow(new AuthException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "password")
                        .param("email", "customer@example.com")
                        .param("password", "WrongPass123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.path").value("/api/auth/token"))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHENTICATED.value()))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenRefreshGrantFailureUsesCommonForbiddenContract() throws Exception {
        when(authService.authenticate(
                "refresh_token",
                null,
                null,
                null,
                null,
                "127.0.0.1")).thenThrow(new TokenRefreshException("Refresh token expired"));

        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "refresh_token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Refresh token expired"))
                .andExpect(jsonPath("$.path").value("/api/auth/token"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.value()))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void genericServerFailureIsSanitizedByCommonHandler() throws Exception {
        when(authService.register(any(RegistrationRequest.class)))
                .thenThrow(new RuntimeException("secret failure detail"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "ValidPass123",
                                  "displayName": "Customer One"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.value()))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("secret failure detail");
                });
    }

    @Test
    void clientCredentialsGrantReturnsRawOAuthJsonWithoutRefreshCookie() throws Exception {
        when(clientCredentialsService.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write"))).thenReturn(
                new ServiceTokenResponse("signed-service-token", "Bearer", 300L, "inventory.write"));

        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", "order-service")
                        .param("client_secret", "correct-secret")
                        .param("scope", "inventory.write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("signed-service-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.scope").value("inventory.write"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void logoutPreservesNullDataFieldInCommonEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenMissingGrantTypeReturnsOAuthInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/auth/token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("Missing grant_type"));
    }

    @Test
    void tokenUnsupportedGrantTypeReturnsOAuthUnsupportedGrantType() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "authorization_code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"))
                .andExpect(jsonPath("$.error_description").value("Unsupported grant_type"));
    }

    @Test
    void clientCredentialsInvalidClientReturnsOAuthInvalidClient() throws Exception {
        when(clientCredentialsService.issue(new ClientCredentialsRequest(
                "order-service", "wrong-secret", "inventory.write")))
                .thenThrow(new AuthException("Invalid client credentials"));

        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", "order-service")
                        .param("client_secret", "wrong-secret")
                        .param("scope", "inventory.write"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$.error_description").value("Invalid client credentials"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void clientCredentialsInvalidScopeReturnsOAuthInvalidScope() throws Exception {
        when(clientCredentialsService.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.delete")))
                .thenThrow(new InvalidScopeException("Scope wording intentionally changed"));

        mockMvc.perform(post("/api/auth/token")
                        .param("grant_type", "client_credentials")
                        .param("client_id", "order-service")
                        .param("client_secret", "correct-secret")
                        .param("scope", "inventory.delete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"))
                .andExpect(jsonPath("$.error_description").value("Scope wording intentionally changed"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void registerBindsJsonToNormalizedRecordRequest() throws Exception {
        UserProfileDto profile = new UserProfileDto();
        profile.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        profile.setEmail("customer@example.com");
        profile.setDisplayName("Customer One");

        when(authService.register(any(RegistrationRequest.class))).thenAnswer(invocation -> {
            Object request = invocation.getArgument(0);
            assertThat(request.getClass().getName())
                    .isEqualTo("com.project.authservice.dto.request.RegistrationRequest");
            assertThat(request.getClass().isRecord()).isTrue();
            assertThat(objectMapper.convertValue(request, Map.class))
                    .containsEntry("email", "customer@example.com")
                    .containsEntry("password", "ValidPass123")
                    .containsEntry("displayName", "Customer One");
            return profile;
        });

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "ValidPass123",
                                  "displayName": "Customer One"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("customer@example.com"));
    }
}
