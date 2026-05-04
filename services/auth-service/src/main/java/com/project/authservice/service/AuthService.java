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
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

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

        return userMapper.toDto(user);
    }

    @Transactional
    public TokenResponse authenticate(String grantType, String email, String password, String refreshTokenCookie) {
        if ("password".equals(grantType)) {
            // Deprecated Resource Owner Password Credentials Grant logic
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AuthException("Invalid credentials"));

            UserCredential credential = userCredentialRepository
                    .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                    .orElseThrow(() -> new AuthException("Invalid credentials"));

            if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
                throw new AuthException("Invalid credentials");
            }

            return createTokenPair(user, UUID.randomUUID());
        } else if ("refresh_token".equals(grantType)) {
            if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
                throw new TokenRefreshException("Refresh token is missing");
            }

            RefreshToken rToken = refreshTokenRepository.findByToken(refreshTokenCookie)
                    .orElseThrow(() -> new TokenRefreshException("Refresh token is invalid"));

            if (rToken.isRevoked()) {
                // Potential replay attack! Revoke entire family
                refreshTokenRepository.revokeFamily(rToken.getFamilyId());
                throw new TokenRefreshException(
                        "Refresh token was revoked. Potential security issue. Please login again.");
            }

            if (rToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                refreshTokenRepository.delete(rToken);
                throw new TokenRefreshException("Refresh token expired");
            }

            // Revoke current token
            rToken.setRevoked(true);
            refreshTokenRepository.save(rToken);

            User user = rToken.getUser();
            return createTokenPair(user, rToken.getFamilyId());
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

        // We return the access token and the refresh token value (which the controller
        // will put in a cookie)
        // Here we extend TokenResponse to carry the refresh token temporarily
        TokenResponse response = new TokenResponse(accessToken);
        // We can pass the raw refresh token string so the controller sets the cookie
        // Using a custom property or subclass for internal transport
        return new TokenResponseWithRefresh(accessToken, refreshToken.getToken());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional
    public User processOAuth2User(String email, String displayName, String providerId, AuthProvider provider) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            // Check if credential exists
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
