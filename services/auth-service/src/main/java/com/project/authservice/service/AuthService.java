package com.project.authservice.service;

import com.project.authservice.dto.request.RegistrationRequest;
import com.project.authservice.dto.TokenResponse;
import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.RefreshToken;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.entity.UserCredential;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.TokenRefreshException;
import com.project.authservice.exception.UserAlreadyExistsException;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.RefreshTokenRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import com.project.common.constant.Roles;
import com.project.common.event.UserEvent;
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

    @Value("${jwt.refresh-token-expiration:2592000000}")
    private long refreshTokenDurationMs;

    /**
     * Public registration. Always assigns ROLE_CUSTOMER. Sellers must be promoted
     * by an admin via {@code POST /api/admin/users/{id}/assign-role/SELLER} or via
     * the dedicated seller-onboarding flow.
     */
    @Transactional
    public UserProfileDto register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already in use");
        }
        Role customerRole = roleRepository.findByName(Roles.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(Roles.CUSTOMER + " role missing"));

        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setActive(true);
        user.getRoles().add(customerRole);
        user = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.setUser(user);
        credential.setAuthProvider(AuthProvider.LOCAL);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        userCredentialRepository.save(credential);

        userEventPublisher.publish(UserEvent.userEventBuilder()
                .type(UserEvent.Type.CREATED)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build());

        return userMapper.toDto(user, true);
    }

    @Transactional
    public TokenResponseWithRefresh authenticate(String grantType, String email, String password,
                                                 String refreshTokenCookie, String userAgent, String ipAddress) {
        return switch (grantType == null ? "" : grantType) {
            case "password" -> passwordGrant(email, password, userAgent, ipAddress);
            case "refresh_token" -> refreshGrant(refreshTokenCookie, userAgent, ipAddress);
            default -> throw new AuthException("Unsupported grant type: " + grantType);
        };
    }

    private TokenResponseWithRefresh passwordGrant(String email, String password,
                                                   String userAgent, String ipAddress) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid credentials"));
        if (!user.isActive()) throw new AuthException("Account disabled");

        UserCredential cred = userCredentialRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (!passwordEncoder.matches(password, cred.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        TokenResponseWithRefresh tokens = createTokenPair(user, UUID.randomUUID(), userAgent, ipAddress);

        userEventPublisher.publish(UserEvent.userEventBuilder()
                .type(UserEvent.Type.LOGGED_IN)
                .userId(user.getId())
                .email(user.getEmail())
                .build());

        return tokens;
    }

    private TokenResponseWithRefresh refreshGrant(String refreshTokenCookie, String userAgent, String ipAddress) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new TokenRefreshException("Refresh token missing");
        }
        String hash = TokenHasher.sha256(refreshTokenCookie);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new TokenRefreshException("Refresh token invalid"));

        // Token-reuse detection: if a previously-rotated token is presented, revoke the entire family.
        if (stored.isRevoked()) {
            log.warn("Reuse of revoked refresh token detected for user={}; revoking family",
                    stored.getUser().getId());
            refreshTokenRepository.revokeFamily(stored.getFamilyId());
            throw new TokenRefreshException("Refresh token revoked. Please login again.");
        }

        if (stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new TokenRefreshException("Refresh token expired");
        }

        // Rotate: revoke old, issue new with same family id
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return createTokenPair(stored.getUser(), stored.getFamilyId(), userAgent, ipAddress);
    }

    private TokenResponseWithRefresh createTokenPair(User user, UUID familyId, String userAgent, String ipAddress) {
        String accessToken = jwtService.generateToken(user);

        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        String hash = TokenHasher.sha256(rawRefresh);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hash);
        rt.setExpiryDate(LocalDateTime.now().plus(refreshTokenDurationMs, ChronoUnit.MILLIS));
        rt.setFamilyId(familyId);
        rt.setUserAgent(userAgent);
        rt.setIpAddress(ipAddress);
        refreshTokenRepository.save(rt);

        return new TokenResponseWithRefresh(accessToken, rawRefresh);
    }

    @Transactional
    public void logout(String accessToken, String refreshTokenValue) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                tokenBlacklistService.blacklistToken(accessToken);
            } catch (Exception e) {
                log.warn("Failed to blacklist access token: {}", e.getMessage());
            }
        }
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            String hash = TokenHasher.sha256(refreshTokenValue);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }
    }

    @Transactional
    public void changePasswordByUserId(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));
        UserCredential cred = userCredentialRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL)
                .orElseThrow(() -> new AuthException("Password login not configured for this account"));
        if (!passwordEncoder.matches(oldPassword, cred.getPasswordHash())) {
            throw new AuthException("Invalid old password");
        }
        cred.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialRepository.save(cred);

        userEventPublisher.publish(UserEvent.userEventBuilder()
                .type(UserEvent.Type.PASSWORD_CHANGED)
                .userId(user.getId())
                .email(user.getEmail())
                .build());
    }

    @Transactional
    public User processOAuth2User(String email, String displayName, String providerId, AuthProvider provider) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (userCredentialRepository.findByUserIdAndAuthProvider(user.getId(), provider).isEmpty()) {
                UserCredential c = new UserCredential();
                c.setUser(user);
                c.setAuthProvider(provider);
                c.setProviderUserId(providerId);
                userCredentialRepository.save(c);
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setActive(true);
            Role customer = roleRepository.findByName(Roles.CUSTOMER)
                    .orElseThrow(() -> new IllegalStateException("ROLE_CUSTOMER missing"));
            user.getRoles().add(customer);
            user = userRepository.save(user);

            UserCredential c = new UserCredential();
            c.setUser(user);
            c.setAuthProvider(provider);
            c.setProviderUserId(providerId);
            userCredentialRepository.save(c);
        }
        return user;
    }

    public TokenResponseWithRefresh generateTokenPairForOAuth2(User user, String userAgent, String ipAddress) {
        return createTokenPair(user, UUID.randomUUID(), userAgent, ipAddress);
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
