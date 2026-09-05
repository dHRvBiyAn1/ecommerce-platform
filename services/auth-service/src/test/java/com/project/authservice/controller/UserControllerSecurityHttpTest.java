package com.project.authservice.controller;

import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.security.CustomOAuth2SuccessHandler;
import com.project.authservice.security.KeyManager;
import com.project.authservice.security.JwtAuthFilter;
import com.project.authservice.security.OAuth2ClientConfig;
import com.project.authservice.security.SecurityConfig;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.common.exception.GlobalExceptionHandler;
import org.springframework.core.io.DefaultResourceLoader;
import org.junit.jupiter.api.Test;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = UserController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class, UserControllerSecurityHttpTest.JwtTestConfig.class})
class UserControllerSecurityHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenBlacklistService blacklist;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserMapper userMapper;

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
    void ordinaryUserJwtSubjectCanAccessUserProfile() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        User user = new User();
        user.setId(userId);
        user.setEmail("customer@example.com");
        String token = jwtService.generateToken(user);
        UserProfileDto profile = new UserProfileDto();
        profile.setId(userId);
        profile.setEmail("customer@example.com");

        when(blacklist.isBlacklisted(token)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(profile);

        mockMvc.perform(get("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("customer@example.com"));
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
