package com.project.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.RefreshToken;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.entity.UserCredential;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.TokenRefreshException;
import com.project.authservice.generated.model.RegistrationRequest;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.RefreshTokenRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserCredentialRepository;
import com.project.authservice.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceCoverageTest {
  @Mock private UserRepository users;

  @Mock private UserCredentialRepository credentials;

  @Mock private RoleRepository roles;

  @Mock private RefreshTokenRepository refreshTokens;

  @Mock private PasswordEncoder passwords;

  @Mock private JwtService jwt;

  @Mock private UserMapper mapper;

  @Mock private UserEventPublisher events;

  @Mock private TokenBlacklistService blacklist;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            users, credentials, roles, refreshTokens, passwords, jwt, mapper, events, blacklist);
    ReflectionTestUtils.setField(service, "refreshTokenDurationMs", 60_000L);
  }

  @Test
  void registrationCreatesOnlyCustomerAndPublishesCreationEvent() {
    Role customer = new Role();
    customer.setName("ROLE_CUSTOMER");
    when(users.existsByEmail("new@example.com")).thenReturn(false);
    when(roles.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customer));
    when(users.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              user.setId(UUID.randomUUID());
              return user;
            });
    when(passwords.encode("StrongPassword123")).thenReturn("password-hash");

    service.register(new RegistrationRequest("new@example.com", "StrongPassword123", "New User"));

    verify(credentials)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                credential ->
                    credential.getAuthProvider() == AuthProvider.LOCAL
                        && "password-hash".equals(credential.getPasswordHash())));
    verify(events)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event -> event.getType() == com.project.common.event.UserEvent.Type.CREATED));
    assertThat(customer.getName()).isEqualTo("ROLE_CUSTOMER");
  }

  @Test
  void registrationRejectsDuplicateEmailBeforeLookingUpRoles() {
    when(users.existsByEmail("used@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.register(
                    new RegistrationRequest("used@example.com", "StrongPassword123", "User")))
        .isInstanceOf(com.project.authservice.exception.UserAlreadyExistsException.class);

    verify(roles, never()).findByName(any());
    verify(users, never()).save(any(User.class));
  }

  @Test
  void passwordGrantRejectsDisabledUsersAndInvalidCredentials() {
    User disabled = user(false);
    when(users.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabled));
    assertThatThrownBy(
            () -> service.authenticate("password", "disabled@example.com", "bad", null, null, null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Account disabled");

    User active = user(true);
    when(users.findByEmail("active@example.com")).thenReturn(Optional.of(active));
    when(credentials.findByUserIdAndAuthProvider(active.getId(), AuthProvider.LOCAL))
        .thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.authenticate("password", "active@example.com", "bad", null, null, null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Invalid credentials");
    verify(events, never()).publish(any());
  }

  @Test
  void passwordGrantIssuesTokenAndRefreshTokenForValidCredentials() {
    User active = user(true);
    UserCredential credential = new UserCredential();
    credential.setPasswordHash("hash");
    when(users.findByEmail(active.getEmail())).thenReturn(Optional.of(active));
    when(credentials.findByUserIdAndAuthProvider(active.getId(), AuthProvider.LOCAL))
        .thenReturn(Optional.of(credential));
    when(passwords.matches("StrongPassword123", "hash")).thenReturn(true);
    when(jwt.generateToken(active)).thenReturn("access-token");

    AuthService.TokenResponseWithRefresh response =
        service.authenticate(
            "password", active.getEmail(), "StrongPassword123", null, "agent", "127.0.0.1");

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).contains(".");
    verify(refreshTokens)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                token ->
                    token.getUser() == active
                        && "agent".equals(token.getUserAgent())
                        && "127.0.0.1".equals(token.getIpAddress())));
    verify(events)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event -> event.getType() == com.project.common.event.UserEvent.Type.LOGGED_IN));
  }

  @Test
  void refreshGrantRejectsMissingAndExpiredTokens() {
    assertThatThrownBy(() -> service.authenticate("refresh_token", null, null, " ", null, null))
        .isInstanceOf(TokenRefreshException.class)
        .hasMessage("Refresh token missing");
    String expiredRaw = "expired-token";
    RefreshToken expired = new RefreshToken();
    expired.setExpiryDate(LocalDateTime.now().minusSeconds(1));
    when(refreshTokens.findForUpdateByTokenHash(TokenHasher.sha256(expiredRaw)))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(
            () -> service.authenticate("refresh_token", null, null, expiredRaw, null, null))
        .isInstanceOf(TokenRefreshException.class)
        .hasMessage("Refresh token expired");

    verify(refreshTokens).delete(expired);
    verify(jwt, never()).generateToken(any());
  }

  @Test
  void logoutSkipsBlankTokensAndRevokesProvidedRefreshToken() {
    RefreshToken token = new RefreshToken();
    when(refreshTokens.findByTokenHash(TokenHasher.sha256("refresh")))
        .thenReturn(Optional.of(token));

    service.logout(" ", "refresh");
    service.logout("access", null);

    verify(blacklist).blacklistToken("access");
    assertThat(token.isRevoked()).isTrue();
    verify(refreshTokens).save(token);
  }

  @Test
  void logoutContinuesWhenAccessTokenBlacklistFails() {
    org.mockito.Mockito.doThrow(new IllegalStateException("cache down"))
        .when(blacklist)
        .blacklistToken("access");

    service.logout("access", " ");

    verify(blacklist).blacklistToken("access");
    verify(refreshTokens, never()).findByTokenHash(any());
  }

  @Test
  void changePasswordRequiresTheCurrentPasswordAndPublishesChange() {
    User user = user(true);
    UserCredential credential = new UserCredential();
    credential.setPasswordHash("old-hash");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(credentials.findByUserIdAndAuthProvider(user.getId(), AuthProvider.LOCAL))
        .thenReturn(Optional.of(credential));
    when(passwords.matches("old", "old-hash")).thenReturn(true);
    when(passwords.encode("new-password")).thenReturn("new-hash");

    service.changePasswordByUserId(user.getId(), "old", "new-password");

    assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
    verify(credentials).save(credential);
    verify(events)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.getType() == com.project.common.event.UserEvent.Type.PASSWORD_CHANGED));
  }

  @Test
  void oauthLinksAnExistingUserOrCreatesANewCustomer() {
    User existing = user(true);
    when(users.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
    when(credentials.findByUserIdAndAuthProvider(existing.getId(), AuthProvider.GOOGLE))
        .thenReturn(Optional.empty());

    assertThat(
            service.processOAuth2User(
                existing.getEmail(), "Ignored", "google-id", AuthProvider.GOOGLE))
        .isSameAs(existing);
    verify(credentials)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                credential ->
                    credential.getUser() == existing
                        && "google-id".equals(credential.getProviderUserId())));

    Role customer = new Role();
    customer.setName("ROLE_CUSTOMER");
    when(users.findByEmail("new-oauth@example.com")).thenReturn(Optional.empty());
    when(roles.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customer));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    User created =
        service.processOAuth2User(
            "new-oauth@example.com", "OAuth User", "google-id", AuthProvider.GOOGLE);

    assertThat(created.getRoles()).contains(customer);
    verify(credentials)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                credential ->
                    credential.getUser() == created
                        && credential.getAuthProvider() == AuthProvider.GOOGLE));
  }

  @Test
  void unsupportedGrantAndUnconfiguredOAuthRoleAreRejected() {
    assertThatThrownBy(
            () -> service.authenticate("client_credentials", null, null, null, null, null))
        .isInstanceOf(AuthException.class)
        .hasMessage("Unsupported grant type: client_credentials");
    when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(roles.findByName("ROLE_CUSTOMER")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.processOAuth2User("new@example.com", "New", "id", AuthProvider.GOOGLE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ROLE_CUSTOMER missing");
  }

  private User user(boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(active ? "active@example.com" : "disabled@example.com");
    user.setActive(active);
    return user;
  }
}
