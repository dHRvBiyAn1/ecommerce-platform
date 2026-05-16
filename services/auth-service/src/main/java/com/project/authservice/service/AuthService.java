package com.project.authservice.service;

import com.project.authservice.dto.RegistrationRequest;
import com.project.authservice.dto.TokenResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.*;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.TokenRefreshException;
import com.project.authservice.exception.UserAlreadyExistsException;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.RefreshTokenRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.service.UserEventPublisher.EventType;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserEventPublisher userEventPublisher;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenDurationMs;

    @Transactional
    public UserProfileDto register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.getRoles().add(userRole);

        user = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.setUser(user);
        credential.setAuthProvider(AuthProvider.LOCAL);
        credential.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userCredentialRepository.save(credential);

        try {
            userEventPublisher.publishEvent(EventType.CREATED, user.getId(), user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to publish registration event", e);
        }

        return userMapper.toDto(user);
    }

    @Transactional
    public TokenResponse authenticate(String grantType, String email, String password, String refreshTokenCookie) {
        if ("password".equals(grantType)) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("Invalid credentials"));

            UserCredential credential = userCredentialRepository
                    .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                    .orElseThrow(() -> new AuthException("Invalid credentials"));

            if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
                throw new AuthException("Invalid credentials");
            }

            TokenResponse result = createTokenPair(user, UUID.randomUUID());

            try {
                userEventPublisher.publishEvent(EventType.LOGGED_IN, user.getId(), user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish login event", e);
            }

            return result;
        } else if ("refresh_token".equals(grantType)) {
            if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
                throw new TokenRefreshException("Refresh token is missing");
            }

            RefreshToken rToken = refreshTokenRepository.findByToken(refreshTokenCookie)
                    .orElseThrow(() -> new TokenRefreshException("Refresh token is invalid"));

            if (rToken.isRevoked()) {
                refreshTokenRepository.revokeFamily(rToken.getFamilyId());
                throw new TokenRefreshException(
                        "Refresh token was revoked. Potential security issue. Please login again.");
            }

            if (rToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                refreshTokenRepository.delete(rToken);
                throw new TokenRefreshException("Refresh token expired");
            }

            rToken.setRevoked(true);
            refreshTokenRepository.save(rToken);

            User user = rToken.getUser();
            TokenResponse result = createTokenPair(user, rToken.getFamilyId());

            try {
                userEventPublisher.publishEvent(EventType.LOGGED_IN, user.getId(), user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to publish login event", e);
            }

            return result;
        } else {
            throw new AuthException("Unsupported grant type");
        }
    }

    private TokenResponse createTokenPair(User user, UUID familyId) {
        String accessToken = jwtService.generateToken(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(LocalDateTime.now().plus(refreshTokenDurationMs, ChronoUnit.MILLIS));
        refreshToken.setFamilyId(familyId);
        refreshTokenRepository.save(refreshToken);

        TokenResponse response = new TokenResponse(accessToken);
        return new TokenResponseWithRefresh(accessToken, refreshToken.getToken());
    }

    @Transactional
    public void logout(String accessToken, String refreshTokenValue) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                tokenBlacklistService.blacklistToken(accessToken);
            } catch (Exception e) {
                log.warn("Failed to blacklist access token", e);
            }

            try {
                String email = jwtService.getEmailFromToken(accessToken);
                userEventPublisher.publishEvent(EventType.LOGGED_OUT, null, email);
            } catch (Exception e) {
                log.warn("Failed to publish logout event", e);
            }
        }

        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found"));

        UserCredential credential = userCredentialRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new AuthException("Password login not configured for this account"));

        if (!passwordEncoder.matches(oldPassword, credential.getPasswordHash())) {
            throw new AuthException("Invalid old password");
        }

        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialRepository.save(credential);
    }

    @Transactional
    public User processOAuth2User(String email, String displayName, String providerId, AuthProvider provider) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            Optional<UserCredential> credOpt = userCredentialRepository.findByUserIdAndAuthProvider(user.getId(),
                    provider);
            if (credOpt.isEmpty()) {
                UserCredential credential = new UserCredential();
                credential.setUser(user);
                credential.setAuthProvider(provider);
                credential.setProviderUserId(providerId);
                userCredentialRepository.save(credential);
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Default role not found"));
            user.getRoles().add(userRole);
            user = userRepository.save(user);

            UserCredential credential = new UserCredential();
            credential.setUser(user);
            credential.setAuthProvider(provider);
            credential.setProviderUserId(providerId);
            userCredentialRepository.save(credential);
        }
        return user;
    }

    public TokenResponseWithRefresh generateTokenPairForOAuth2(User user) {
        return (TokenResponseWithRefresh) createTokenPair(user, UUID.randomUUID());
    }

    @Getter
    public static class TokenResponseWithRefresh extends TokenResponse {
        private final String refreshToken;

        public TokenResponseWithRefresh(String accessToken, String refreshToken) {
            super(accessToken);
            this.refreshToken = refreshToken;
        }

    }
}
