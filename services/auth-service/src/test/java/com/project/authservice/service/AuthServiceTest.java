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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserMapper userMapper;
    @Mock private UserEventPublisher userEventPublisher;
    @Mock private TokenBlacklistService tokenBlacklistService;

    private AuthService authService;

    private Role customerRole;
    private Role sellerRole;
    private User user;
    private UserCredential credential;
    private RegistrationRequest request;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userCredentialRepository, roleRepository,
                refreshTokenRepository, passwordEncoder, jwtService, userMapper,
                userEventPublisher, tokenBlacklistService);

        customerRole = new Role();
        customerRole.setId(UUID.randomUUID());
        customerRole.setName("ROLE_CUSTOMER");

        sellerRole = new Role();
        sellerRole.setId(UUID.randomUUID());
        sellerRole.setName("ROLE_SELLER");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setActive(true);
        user.setRoles(Set.of(customerRole));

        credential = new UserCredential();
        credential.setUser(user);
        credential.setAuthProvider(AuthProvider.LOCAL);
        credential.setPasswordHash("encoded-password");

        request = new RegistrationRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setDisplayName("New User");
        request.setUserType(RegistrationRequest.UserType.CUSTOMER);
    }

    @Test
    void register_WithCustomerRole_Success() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userCredentialRepository.save(any(UserCredential.class))).thenReturn(credential);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserProfileDto());

        UserProfileDto result = authService.register(request);

        assertThat(result).isNotNull();
        verify(userRepository).save(argThat(u -> u.isActive() && u.getEmail().equals("new@example.com")));
        verify(userEventPublisher).publishEvent(UserEventPublisher.EventType.CREATED, user.getId(), user.getEmail());
    }

    @Test
    void register_WithSellerRole_Success() {
        request.setUserType(RegistrationRequest.UserType.SELLER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName("ROLE_SELLER")).thenReturn(Optional.of(sellerRole));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userCredentialRepository.save(any(UserCredential.class))).thenReturn(credential);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserProfileDto());

        authService.register(request);

        verify(roleRepository).findByName("ROLE_SELLER");
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_RoleNotFound_ThrowsException() {
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Default role not found: ROLE_CUSTOMER");
    }

    @Test
    void authenticate_WithValidPassword_Success() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userCredentialRepository.findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-token");

        TokenResponse result = authService.authenticate("password", user.getEmail(), "password123", null);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        verify(userEventPublisher).publishEvent(UserEventPublisher.EventType.LOGGED_IN, user.getId(), user.getEmail());
    }

    @Test
    void authenticate_WithInvalidPassword_ThrowsException() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userCredentialRepository.findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticate("password", user.getEmail(), "wrong-password", null))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void authenticate_WithUserNotFound_ThrowsException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate("password", "unknown@example.com", "password123", null))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void authenticate_WithValidRefreshToken_Success() {
        RefreshToken rToken = new RefreshToken();
        rToken.setToken("refresh-token");
        rToken.setUser(user);
        rToken.setFamilyId(UUID.randomUUID());
        rToken.setExpiryDate(LocalDateTime.now().plusDays(1));
        rToken.setRevoked(false);

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(rToken));
        when(jwtService.generateToken(user)).thenReturn("new-access-token");

        TokenResponse result = authService.authenticate("refresh_token", null, null, "refresh-token");

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
    }

    @Test
    void authenticate_WithRevokedRefreshToken_ThrowsException() {
        RefreshToken rToken = new RefreshToken();
        rToken.setToken("revoked-token");
        rToken.setUser(user);
        rToken.setFamilyId(UUID.randomUUID());
        rToken.setExpiryDate(LocalDateTime.now().plusDays(1));
        rToken.setRevoked(true);

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(rToken));

        assertThatThrownBy(() -> authService.authenticate("refresh_token", null, null, "revoked-token"))
                .isInstanceOf(TokenRefreshException.class);
        verify(refreshTokenRepository).revokeFamily(rToken.getFamilyId());
    }

    @Test
    void authenticate_WithExpiredRefreshToken_ThrowsException() {
        RefreshToken rToken = new RefreshToken();
        rToken.setToken("expired-token");
        rToken.setUser(user);
        rToken.setFamilyId(UUID.randomUUID());
        rToken.setExpiryDate(LocalDateTime.now().minusDays(1));
        rToken.setRevoked(false);

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(rToken));

        assertThatThrownBy(() -> authService.authenticate("refresh_token", null, null, "expired-token"))
                .isInstanceOf(TokenRefreshException.class);
        verify(refreshTokenRepository).delete(rToken);
    }

    @Test
    void authenticate_WithMissingRefreshToken_ThrowsException() {
        assertThatThrownBy(() -> authService.authenticate("refresh_token", null, null, null))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessage("Refresh token is missing");
    }

    @Test
    void authenticate_WithUnsupportedGrantType_ThrowsException() {
        assertThatThrownBy(() -> authService.authenticate("client_credentials", null, null, null))
                .isInstanceOf(AuthException.class)
                .hasMessage("Unsupported grant type");
    }

    @Test
    void logout_BlacklistsTokenAndRevokesRefreshToken() {
        when(jwtService.getEmailFromToken("access-token")).thenReturn(user.getEmail());

        authService.logout("access-token", "refresh-token");

        verify(tokenBlacklistService).blacklistToken("access-token");
        verify(userEventPublisher).publishEvent(UserEventPublisher.EventType.LOGGED_OUT, null, user.getEmail());
        verify(refreshTokenRepository).findByToken("refresh-token");
    }

    @Test
    void logout_WithNullTokens_DoesNothing() {
        authService.logout(null, null);
        verifyNoInteractions(tokenBlacklistService);
        verifyNoInteractions(userEventPublisher);
    }

    @Test
    void changePassword_WithValidOldPassword_Success() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userCredentialRepository.findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("old-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded");

        authService.changePassword(user.getEmail(), "old-password", "new-password");

        verify(userCredentialRepository).save(argThat(c -> c.getPasswordHash().equals("new-encoded")));
    }

    @Test
    void changePassword_WithInvalidOldPassword_ThrowsException() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userCredentialRepository.findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong-old", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(user.getEmail(), "wrong-old", "new-password"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid old password");
    }

    @Test
    void processOAuth2User_ExistingUser_ReturnsUser() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        User result = authService.processOAuth2User(user.getEmail(), "Test User", "google-id", AuthProvider.GOOGLE);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void processOAuth2User_NewUser_CreatesWithCustomerRole() {
        when(userRepository.findByEmail("new@oauth.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = authService.processOAuth2User("new@oauth.com", "OAuth User", "oauth-id", AuthProvider.GOOGLE);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("new@oauth.com");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getRoles()).contains(customerRole);
    }
}
