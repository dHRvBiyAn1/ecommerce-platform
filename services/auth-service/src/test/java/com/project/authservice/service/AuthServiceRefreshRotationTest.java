package com.project.authservice.service;

import com.project.authservice.entity.RefreshToken;
import com.project.authservice.entity.User;
import com.project.authservice.exception.TokenRefreshException;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.RefreshTokenRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshRotationTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserMapper userMapper;
    @Mock private UserEventPublisher eventPublisher;
    @Mock private TokenBlacklistService blacklistService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, credentialRepository, roleRepository,
                refreshTokenRepository, passwordEncoder, jwtService, userMapper,
                eventPublisher, blacklistService);
        ReflectionTestUtils.setField(service, "refreshTokenDurationMs", 60_000L);
    }

    @Test
    void refreshRotationLoadsAndRevokesTokenUnderLock() {
        String rawToken = "raw-refresh-token";
        RefreshToken stored = activeToken();
        when(refreshTokenRepository.findForUpdateByTokenHash(TokenHasher.sha256(rawToken)))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateToken(stored.getUser())).thenReturn("access-token");

        AuthService.TokenResponseWithRefresh response = service.authenticate(
                "refresh_token", null, null, rawToken, "agent", "127.0.0.1");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).findForUpdateByTokenHash(TokenHasher.sha256(rawToken));
        verify(refreshTokenRepository, never()).findByTokenHash(TokenHasher.sha256(rawToken));
        verify(refreshTokenRepository).save(stored);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void reuseDetectionRevokesTokenFamily() {
        String rawToken = "reused-refresh-token";
        RefreshToken stored = activeToken();
        stored.setRevoked(true);
        when(refreshTokenRepository.findForUpdateByTokenHash(TokenHasher.sha256(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.authenticate(
                "refresh_token", null, null, rawToken, "agent", "127.0.0.1"))
                .isInstanceOf(TokenRefreshException.class);

        verify(refreshTokenRepository).revokeFamily(stored.getFamilyId());
        verify(jwtService, never()).generateToken(any());
    }

    private static RefreshToken activeToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(UUID.randomUUID());
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));
        return token;
    }
}
