package com.project.authservice.controller;

import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.dto.AddressDto;
import com.project.authservice.entity.User;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.generated.mapper.AuthApiMapperImpl;
import com.project.authservice.service.SellerApplicationService;
import com.project.authservice.service.UserProfileService;
import com.project.authservice.security.CustomOAuth2SuccessHandler;
import com.project.authservice.security.KeyManager;
import com.project.authservice.security.JwtAuthFilter;
import com.project.authservice.security.OAuth2ClientConfig;
import com.project.authservice.security.SecurityConfig;
import com.project.authservice.security.JwtKey;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.common.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.Date;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = UserController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class, AuthenticatedUserValidator.class,
        AuthApiMapperImpl.class,
        UserControllerSecurityHttpTest.JwtTestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class UserControllerSecurityHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenBlacklistService blacklist;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private SellerApplicationService sellerApplicationService;

    @MockBean
    private CustomOAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    void serviceJwtSubjectIsRejectedBeforeUserProfileParsesPrincipalAsUuid() throws Exception {
        String token = jwtService.generateServiceToken(
                "11111111-1111-1111-1111-111111111111", Set.of("inventory.read"), Duration.ofMinutes(5));
        when(blacklist.isBlacklisted(token)).thenReturn(false);

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedSignedJwtIsRejectedByFilterWithBareUnauthorizedResponse() throws Exception {
        String token = signedUserToken("not-a-uuid");
        when(blacklist.isBlacklisted(token)).thenReturn(false);

        org.assertj.core.api.Assertions.assertThat(jwtService.isUserToken(token)).isTrue();

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void blacklistedUserJwtIsRejected() throws Exception {
        User user = new User();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        String token = jwtService.generateToken(user);
        when(blacklist.isBlacklisted(token)).thenReturn(true);

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blacklistBackendFailureFailsClosedWithoutLeakingTheInternalError(CapturedOutput output) throws Exception {
        User user = new User();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setEmail("customer@example.com");
        String token = jwtService.generateToken(user);
        UserProfileDto profile = new UserProfileDto(user.getId(), user.getEmail(), null, null, null, false,
                null, Set.of(), Set.of(), null, null, false);
        when(blacklist.isBlacklisted(token)).thenThrow(new IllegalStateException("redis connection details"));
        when(userProfileService.getProfile(user.getId())).thenReturn(profile);

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"));

        verifyNoInteractions(userProfileService);
        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("Blacklist check failed; rejecting request")
                .doesNotContain("redis connection details");
    }

    @Test
    void ordinaryUserJwtSubjectCanAccessUserProfile() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        User user = new User();
        user.setId(userId);
        user.setEmail("customer@example.com");
        String token = jwtService.generateToken(user);
        UserProfileDto profile = new UserProfileDto(userId, "customer@example.com", "Customer One",
                "https://example.com/customer.png", "+1 555 0100", true,
                LocalDateTime.parse("2026-10-01T09:30:00"), Set.of("ROLE_CUSTOMER"), Set.of("orders:read"),
                new AddressDto("Customer One", "+1 555 0100", "1 Main Street", "Springfield", "IL", "62701", "US"),
                null, true);

        when(blacklist.isBlacklisted(token)).thenReturn(false);
        when(userProfileService.getProfile(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Customer One"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/customer.png"))
                .andExpect(jsonPath("$.data.phone").value("+1 555 0100"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T09:30:00"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.data.permissions[0]").value("orders:read"))
                .andExpect(jsonPath("$.data.shippingAddress.fullName").value("Customer One"))
                .andExpect(jsonPath("$.data.shippingAddress.street").value("1 Main Street"))
                .andExpect(jsonPath("$.data.shippingAddress.country").value("US"))
                .andExpect(jsonPath("$.data.billingAddress").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void malformedAuthenticatedPrincipalIsRejectedBeforeProfileServiceWithStructuredUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/user/profile").with(user("not-a-uuid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Request could not be processed"));

        verifyNoInteractions(userProfileService);
    }

    @TestConfiguration
    static class DisabledOAuth2Config {
        @Bean
        OAuth2ClientConfig.OAuth2EnabledFlag oauth2Flag() {
            return new OAuth2ClientConfig.OAuth2EnabledFlag(false);
        }
    }

    @Autowired
    private JwtService jwtService;

    private String signedUserToken(String subject) {
        KeyManager keyManager = (KeyManager) org.springframework.test.util.ReflectionTestUtils
                .getField(jwtService, "keyManager");
        JwtKey key = keyManager.getCurrentKey();
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header().keyId(key.getKid()).type("JWT").and()
                .subject(subject)
                .issuer("auth-service")
                .issuedAt(new Date(now))
                .expiration(new Date(now + Duration.ofMinutes(5).toMillis()))
                .claim("email", "customer@example.com")
                .signWith(key.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtService jwtService() {
            KeyManager keyManager = new KeyManager(new DefaultResourceLoader());
            keyManager.init();
            JwtService jwtService = new JwtService(keyManager);
            org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "issuer", "auth-service");
            return jwtService;
        }
    }
}
