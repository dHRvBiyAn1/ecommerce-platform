package com.project.authservice;

import com.project.authservice.controller.AdminController;
import com.project.authservice.controller.AuthController;
import com.project.authservice.controller.UserController;
import com.project.authservice.controller.JwkSetController;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.security.CustomOAuth2SuccessHandler;
import com.project.authservice.security.JwtAuthFilter;
import com.project.authservice.security.OAuth2ClientConfig;
import com.project.authservice.security.SecurityConfig;
import com.project.authservice.security.KeyManager;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.authservice.service.UserProfileService;
import com.project.authservice.service.SellerApplicationService;
import com.project.authservice.generated.mapper.AuthApiMapperImpl;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.InvalidScopeException;
import com.project.authservice.generated.testclient.api.AdministrationApi;
import com.project.authservice.generated.testclient.api.AuthenticationApi;
import com.project.authservice.generated.testclient.api.UserProfileApi;
import com.project.authservice.generated.testclient.api.DiscoveryApi;
import com.project.authservice.generated.testclient.invoker.ApiClient;
import com.project.authservice.generated.testclient.model.Token200Response;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = GeneratedClientContractTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:file:/dev/null",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "test.oauth.enabled=false",
                "security.cookies.secure=false",
                "jwt.refresh-token-expiration=60000"
        })
class GeneratedClientContractTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @LocalServerPort
    private int serverPort;

    @MockBean
    private AuthService authService;

    @MockBean
    private ClientCredentialsService clientCredentialsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private CustomOAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private PermissionRepository permissionRepository;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private SellerApplicationService sellerApplicationService;

    @MockBean
    private KeyManager keyManager;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void passwordAndRefreshGrantsUseFormBodiesAndRotateRefreshCookie() {
        when(authService.authenticate(eq("password"), eq("buyer@example.com"), eq("valid-pass"),
                isNull(), any(), any()))
                .thenReturn(new AuthService.TokenResponseWithRefresh("user-access", "refresh-one"));
        when(authService.authenticate(eq("refresh_token"), isNull(), isNull(), eq("refresh-one"),
                any(), any()))
                .thenReturn(new AuthService.TokenResponseWithRefresh("rotated-access", "refresh-two"));

        var client = client();
        var passwordResponse = client.api.tokenWithHttpInfo(
                null, null, null, null, null, null, null,
                "password", "buyer@example.com", "valid-pass", null, null, null);

        assertThat(passwordResponse.getBody().getData().getAccessToken()).isEqualTo("user-access");
        String firstCookie = passwordResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(firstCookie).contains("refresh_token=refresh-one", "HttpOnly", "SameSite=Strict");
        assertThat(client.lastUri.get().getRawQuery()).isNull();

        var refreshResponse = client.api.tokenWithHttpInfo(
                null, null, null, null, null, null, firstCookie.substring(0, firstCookie.indexOf(';')),
                "refresh_token", null, null, null, null, null);

        assertThat(refreshResponse.getBody().getData().getAccessToken()).isEqualTo("rotated-access");
        assertThat(refreshResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=refresh-two", "HttpOnly", "SameSite=Strict");
        verify(authService).authenticate(eq("refresh_token"), isNull(), isNull(), eq("refresh-one"),
                any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void clientCredentialsGrantKeepsSecretsOutOfUrlAndReturnsRawOAuthShape() {
        when(clientCredentialsService.issue(new ClientCredentialsRequest(
                "order-service", "service-secret", "inventory.write")))
                .thenReturn(new ServiceTokenResponse("service-access", "Bearer", 300L, "inventory.write"));

        var client = client();
        var responseEntity = client.api.tokenWithHttpInfo(
                null, null, null, null, null, null, null,
                "client_credentials", null, null, "order-service", "service-secret", "inventory.write");
        Token200Response response = responseEntity.getBody();

        assertThat(response.getAccessToken()).isEqualTo("service-access");
        assertThat(response.getTokenType().getValue()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(300L);
        assertThat(response.getScope()).isEqualTo("inventory.write");
        assertThat(responseEntity.getHeaders().containsKey(HttpHeaders.SET_COOKIE)).isFalse();
        assertThat(client.lastUri.get().getRawQuery()).isNull();
        verify(clientCredentialsService).issue(new ClientCredentialsRequest(
                "order-service", "service-secret", "inventory.write"));
    }

    @Test
    void invalidScopeAndClientCredentialsRetainOAuthErrorStatuses() {
        when(clientCredentialsService.issue(any(ClientCredentialsRequest.class)))
                .thenThrow(new InvalidScopeException("Requested scope is not allowed"));
        var client = client();

        HttpClientErrorException invalidScope = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client.api.token(null, null, null, null, null, null, null,
                        "client_credentials", null, null, "order-service", "service-secret", "admin"),
                HttpClientErrorException.class);
        assertThat(invalidScope.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(invalidScope.getResponseBodyAsString()).contains("invalid_scope");

        when(clientCredentialsService.issue(any(ClientCredentialsRequest.class)))
                .thenThrow(new AuthException("bad client details"));
        HttpClientErrorException invalidClient = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client.api.token(null, null, null, null, null, null, null,
                        "client_credentials", null, null, "order-service", "wrong-secret", "inventory.write"),
                HttpClientErrorException.class);
        assertThat(invalidClient.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        assertThat(invalidClient.getResponseBodyAsString()).contains("invalid_client")
                .doesNotContain("bad client details");

        when(authService.authenticate(eq("password"), eq("buyer@example.com"), eq("wrong-pass"),
                isNull(), any(), any()))
                .thenThrow(new AuthException("private credential detail"));
        HttpClientErrorException invalidPassword = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client.api.token(null, null, null, null, null, null, null,
                        "password", "buyer@example.com", "wrong-pass", null, null, null),
                HttpClientErrorException.class);
        assertThat(invalidPassword.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        assertThat(invalidPassword.getResponseBodyAsString()).contains("Request could not be processed")
                .doesNotContain("private credential detail");
    }

    @Test
    void logoutClearsRefreshCookieAndReturnsExplicitNullData() {
        when(tokenBlacklistService.isBlacklisted("valid-access-token")).thenReturn(false);
        when(jwtService.validateToken("valid-access-token")).thenReturn(true);
        when(jwtService.isUserToken("valid-access-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("valid-access-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("valid-access-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("valid-access-token")).thenReturn(Set.of());
        AuthenticationApi api = client().api;

        var response = api.logoutWithHttpInfo("Bearer valid-access-token", "refresh_token=refresh-one");

        assertThat(response.getBody().getStatus()).isEqualTo(200);
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=", "Max-Age=0");
        verify(authService).logout("valid-access-token", "refresh-one");
    }

    @Test
    void revokedBearerTokenReturnsTheFilterLevel401Response() {
        when(tokenBlacklistService.isBlacklisted("revoked-token")).thenReturn(true);
        UserProfileApi api = new UserProfileApi(client().apiClient);
        api.getApiClient().setBearerToken("revoked-token");

        HttpClientErrorException.Unauthorized revoked = org.assertj.core.api.Assertions.catchThrowableOfType(
                api::getProfile, HttpClientErrorException.Unauthorized.class);

        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        assertThat(revoked.getResponseBodyAsString()).isEqualTo("Token revoked");
    }

    @Test
    void blacklistBackendFailureRejectsTheRequestWithBodyless503() {
        when(tokenBlacklistService.isBlacklisted("outage-token"))
                .thenThrow(new IllegalStateException("redis connection details"));
        UserProfileApi api = new UserProfileApi(client().apiClient);
        api.getApiClient().setBearerToken("outage-token");

        org.springframework.web.client.HttpServerErrorException.ServiceUnavailable unavailable =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        api::getProfile,
                        org.springframework.web.client.HttpServerErrorException.ServiceUnavailable.class);

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(503));
        assertThat(unavailable.getResponseBodyAsString()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(userProfileService);
    }

    @Test
    void adminRouteRejectsMissingAuthenticationAndAuthenticatedUserWithoutPermission() {
        var api = new AdministrationApi(client().apiClient);
        HttpClientErrorException missingToken = org.assertj.core.api.Assertions.catchThrowableOfType(
                api::listRoles, HttpClientErrorException.class);
        assertThat(missingToken.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(403));
        assertThat(missingToken.getResponseBodyAsString()).isEmpty();

        when(tokenBlacklistService.isBlacklisted("ordinary-user-token")).thenReturn(false);
        when(jwtService.validateToken("ordinary-user-token")).thenReturn(true);
        when(jwtService.isUserToken("ordinary-user-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("ordinary-user-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("ordinary-user-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("ordinary-user-token")).thenReturn(Set.of());
        api.getApiClient().setBearerToken("ordinary-user-token");

        HttpClientErrorException forbidden = org.assertj.core.api.Assertions.catchThrowableOfType(
                api::listRoles, HttpClientErrorException.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(403));
        assertThat(forbidden.getResponseBodyAsString())
                .contains("\"code\":\"ACCESS_DENIED\"", "\"message\":\"Access denied\"");
    }

    @Test
    void administratorWithRequiredPermissionCanReadRoles() {
        when(tokenBlacklistService.isBlacklisted("admin-token")).thenReturn(false);
        when(jwtService.validateToken("admin-token")).thenReturn(true);
        when(jwtService.isUserToken("admin-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("admin-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("admin-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("admin-token")).thenReturn(Set.of("admin:roles:read"));
        UUID permissionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var permission = new com.project.authservice.entity.Permission();
        permission.setId(permissionId);
        permission.setName("admin:roles:read");
        var role = new com.project.authservice.entity.Role();
        role.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        role.setName("ROLE_ADMIN");
        role.setPermissions(Set.of(permission));
        when(roleRepository.findAll()).thenReturn(List.of(role));

        AdministrationApi api = new AdministrationApi(client().apiClient);
        api.getApiClient().setBearerToken("admin-token");
        var response = api.listRolesWithHttpInfo();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(response.getBody().getData()).singleElement().satisfies(apiRole -> {
            assertThat(apiRole.getId()).isEqualTo(role.getId());
            assertThat(apiRole.getName()).isEqualTo("ROLE_ADMIN");
            assertThat(apiRole.getPermissions()).singleElement().satisfies(apiPermission -> {
                assertThat(apiPermission.getId()).isEqualTo(permissionId);
                assertThat(apiPermission.getName()).isEqualTo("admin:roles:read");
            });
        });
    }

    @Test
    void adminRoleAssignmentRetainsNotEmptyAndNotBlankCollectionValidation() {
        when(tokenBlacklistService.isBlacklisted("admin-token")).thenReturn(false);
        when(jwtService.validateToken("admin-token")).thenReturn(true);
        when(jwtService.isUserToken("admin-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("admin-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("admin-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("admin-token")).thenReturn(Set.of("admin:users:write"));

        AdministrationApi adminApi = new AdministrationApi(client().apiClient);
        adminApi.getApiClient().setBearerToken("admin-token");
        var userId = java.util.UUID.fromString(USER_ID);

        var nullList = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> adminApi.setRoles(userId, new com.project.authservice.generated.testclient.model.AssignRolesRequest()),
                org.springframework.web.client.HttpClientErrorException.class);
        assertThat(nullList.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(nullList.getResponseBodyAsString()).contains("must not be empty");

        var emptyList = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> adminApi.setRoles(userId,
                        new com.project.authservice.generated.testclient.model.AssignRolesRequest().roles(List.of())),
                org.springframework.web.client.HttpClientErrorException.class);
        assertThat(emptyList.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(emptyList.getResponseBodyAsString()).contains("must not be empty");

        var blankElement = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> adminApi.setRoles(userId,
                        new com.project.authservice.generated.testclient.model.AssignRolesRequest().roles(List.of(" "))),
                org.springframework.web.client.HttpClientErrorException.class);
        assertThat(blankElement.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(blankElement.getResponseBodyAsString()).contains("must not be blank");

        var nullElement = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> adminApi.setRoles(userId,
                        new com.project.authservice.generated.testclient.model.AssignRolesRequest()
                                .roles(java.util.Arrays.asList((String) null))),
                org.springframework.web.client.HttpClientErrorException.class);
        assertThat(nullElement.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(nullElement.getResponseBodyAsString()).contains("must not be blank");
    }

    @Test
    void socialLoginRouteIsUnavailableWhenNoProviderIsConfigured() {
        HttpClientErrorException.NotFound missingRoute = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client().api.startSocialSignInWithHttpInfo("google"),
                HttpClientErrorException.NotFound.class);
        assertThat(missingRoute.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(404));
    }

    @Test
    void generatedProviderAndJwksClientsDecodeThePublicAuthenticationRoutes()
            throws java.security.NoSuchAlgorithmException {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var publicKey = (java.security.interfaces.RSAPublicKey) generator.generateKeyPair().getPublic();
        String keyId = "contract-test-key";
        when(keyManager.activeKeys()).thenReturn(Map.of(keyId,
                new com.project.authservice.security.JwtKey(keyId, publicKey, null)));
        var authApi = client().api;
        var discovery = authApi.providers();
        assertThat(discovery.getPassword()).isTrue();
        assertThat(discovery.getOauth2()).isFalse();
        assertThat(discovery.getProviders()).isEmpty();

        ApiClient apiClient = client().apiClient;
        DiscoveryApi jwksApi = new DiscoveryApi(apiClient);
        var jwks = jwksApi.jwks();
        assertThat(jwks.getKeys()).hasSize(1);
        var jwk = jwks.getKeys().getFirst();
        assertThat(jwk.getKty()).isEqualTo("RSA");
        assertThat(jwk.getUse()).isEqualTo("sig");
        assertThat(jwk.getAlg()).isEqualTo("RS256");
        assertThat(jwk.getKid()).isEqualTo(keyId);
        assertThat(new java.math.BigInteger(1,
                java.util.Base64.getUrlDecoder().decode(jwk.getN()))).isEqualTo(publicKey.getModulus());
        assertThat(new java.math.BigInteger(1,
                java.util.Base64.getUrlDecoder().decode(jwk.getE()))).isEqualTo(publicKey.getPublicExponent());

        var repeatedJwk = jwksApi.jwks().getKeys().getFirst();
        assertThat(repeatedJwk.getN()).isEqualTo(jwk.getN());
        assertThat(repeatedJwk.getE()).isEqualTo(jwk.getE());
    }

    @Test
    void generatedSellerStatusEnumRetainsTrimmedAndOptionalBinding() {
        stubAdminSellerApplicationReadPermission();
        when(sellerApplicationService.list(any(), any()))
                .thenAnswer(invocation -> new org.springframework.data.domain.PageImpl<>(
                        List.of(new com.project.authservice.dto.response.seller.SellerApplicationResponse(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                UUID.fromString(USER_ID), "seller@example.com", "Seller One",
                                com.project.authservice.entity.SellerApplicationStatus.APPROVED,
                                "Seller Business", "123456789012345", "+1 555 0101",
                                new com.project.authservice.dto.AddressDto("Seller One", "+1 555 0101",
                                        "2 Market Street", "Springfield", "IL", "62702", "US"),
                                "4321", "Business notes", null,
                                java.time.LocalDateTime.parse("2026-10-01T10:00:00"), null, null)),
                        invocation.getArgument(1), 1));
        ApiClient apiClient = client().apiClient;
        apiClient.setBearerToken("admin-token");
        AdministrationApi api = new AdministrationApi(apiClient);

        var sellerPage = api.callList(com.project.authservice.generated.testclient.model.SellerApplicationStatus.APPROVED,
                0, 20, null);
        assertThat(sellerPage.getStatus()).isEqualTo(200);
        assertThat(sellerPage.getData().getContent()).singleElement().satisfies(application -> {
            assertThat(application.getId()).isEqualTo(UUID.fromString("44444444-4444-4444-4444-444444444444"));
            assertThat(application.getUserId()).isEqualTo(UUID.fromString(USER_ID));
            assertThat(application.getUserEmail()).isEqualTo("seller@example.com");
            assertThat(application.getUserDisplayName()).isEqualTo("Seller One");
            assertThat(application.getStatus().getValue()).isEqualTo("APPROVED");
            assertThat(application.getBusinessName()).isEqualTo("Seller Business");
            assertThat(application.getGstin()).isEqualTo("123456789012345");
            assertThat(application.getContactPhone()).isEqualTo("+1 555 0101");
            assertThat(application.getPickupAddress().getStreet()).isEqualTo("2 Market Street");
            assertThat(application.getPickupAddress().getCountry()).isEqualTo("US");
            assertThat(application.getBankAccountLast4()).isEqualTo("4321");
            assertThat(application.getNotes()).isEqualTo("Business notes");
            assertThat(application.getRejectionReason()).isNull();
            assertThat(application.getSubmittedAt()).isEqualTo(
                    java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
            assertThat(application.getReviewedAt()).isNull();
            assertThat(application.getReviewedBy()).isNull();
        });
        assertThat(sellerPage.getData().getTotalElements()).isEqualTo(1);
        assertThat(sellerPage.getData().getTotalPages()).isEqualTo(1);
        assertThat(sellerPage.getData().getPageable().getPageSize()).isEqualTo(20);
        assertThat(sellerPage.getData().getPageable().getOffset()).isZero();
        assertThat(sellerPage.getData().getNumberOfElements()).isEqualTo(1);
        assertAdminJsonResponse(rawAdminGet("/api/admin/seller-applications?status=%20APPROVED%20"));
        assertAdminJsonResponse(rawAdminGet("/api/admin/seller-applications?status="));
        assertAdminJsonResponse(rawAdminGet("/api/admin/seller-applications"));

        when(tokenBlacklistService.isBlacklisted("revoked-token")).thenReturn(true);
        HttpClientErrorException.Unauthorized revoked = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> rawAdminGet("/api/admin/seller-applications", "revoked-token"),
                HttpClientErrorException.Unauthorized.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(401));
        assertThat(revoked.getResponseHeaders().getContentType()).isNull();
        assertThat(revoked.getResponseBodyAsString()).isEqualTo("Token revoked");

        var statuses = org.mockito.ArgumentCaptor.forClass(
                com.project.authservice.entity.SellerApplicationStatus.class);
        var pageables = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(sellerApplicationService, org.mockito.Mockito.times(4))
                .list(statuses.capture(), pageables.capture());
        assertThat(statuses.getAllValues()).containsExactly(
                com.project.authservice.entity.SellerApplicationStatus.APPROVED,
                com.project.authservice.entity.SellerApplicationStatus.APPROVED, null, null);
        assertThat(pageables.getAllValues()).allSatisfy(pageable -> {
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().getOrderFor("submittedAt").getDirection())
                    .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        });
    }

    @Test
    void invalidSellerApplicationStatusRemainsBadRequestTypeMismatch() {
        stubAdminSellerApplicationReadPermission();

        HttpClientErrorException invalid = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> rawAdminGet("/api/admin/seller-applications?status=BOGUS"),
                HttpClientErrorException.class);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(invalid.getResponseBodyAsString())
                .contains("\"code\":\"TYPE_MISMATCH\"", "Parameter 'status' has invalid value: BOGUS");
        org.mockito.Mockito.verifyNoInteractions(sellerApplicationService);
    }

    @Test
    void generatedAdminPageableBindingPreservesSortDirectionAndDefaults() {
        when(jwtService.validateToken("admin-token")).thenReturn(true);
        when(jwtService.isUserToken("admin-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("admin-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("admin-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("admin-token")).thenReturn(Set.of("admin:users:read"));
        when(userRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            return Page.empty(pageable);
        });

        ApiClient apiClient = client().apiClient;
        apiClient.setBearerToken("admin-token");
        AdministrationApi adminApi = new AdministrationApi(apiClient);

        var requested = adminApi.listUsers(2, 7, List.of("createdAt,desc"));
        assertThat(requested.getData().getPageable().getPageNumber()).isEqualTo(2);
        assertThat(requested.getData().getPageable().getPageSize()).isEqualTo(7);
        assertThat(requested.getData().getPageable().getSort().getSorted()).isTrue();
        verify(userRepository).findAll(org.mockito.ArgumentMatchers.<Pageable>argThat(pageable ->
                pageable.getPageNumber() == 2 && pageable.getPageSize() == 7
                        && pageable.getSort().getOrderFor("createdAt").getDirection()
                                == org.springframework.data.domain.Sort.Direction.DESC));

        var defaults = adminApi.listUsers(null, null, null);
        assertThat(defaults.getData().getPageable().getPageNumber()).isZero();
        assertThat(defaults.getData().getPageable().getPageSize()).isEqualTo(20);
        verify(userRepository).findAll(org.mockito.ArgumentMatchers.<Pageable>argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 20 && pageable.getSort().isUnsorted()));
    }

    @Test
    void generatedInterfacesRegisterEachMvcOperationOnce() {
        Map<String, Set<RequestMethod>> expected = Map.ofEntries(
                Map.entry("/api/auth/change-password", Set.of(RequestMethod.POST)),
                Map.entry("/api/auth/logout", Set.of(RequestMethod.POST)),
                Map.entry("/api/auth/providers", Set.of(RequestMethod.GET)),
                Map.entry("/api/auth/register", Set.of(RequestMethod.POST)),
                Map.entry("/api/auth/token", Set.of(RequestMethod.POST)),
                Map.entry("/.well-known/jwks.json", Set.of(RequestMethod.GET)),
                Map.entry("/api/user/seller-application", Set.of(RequestMethod.GET, RequestMethod.POST)),
                Map.entry("/api/user/profile", Set.of(RequestMethod.GET, RequestMethod.PUT)),
                Map.entry("/api/admin/seller-applications", Set.of(RequestMethod.GET)),
                Map.entry("/api/admin/seller-applications/{id}", Set.of(RequestMethod.GET)),
                Map.entry("/api/admin/seller-applications/{id}/approve", Set.of(RequestMethod.PUT)),
                Map.entry("/api/admin/seller-applications/{id}/reject", Set.of(RequestMethod.PUT)),
                Map.entry("/api/admin/roles", Set.of(RequestMethod.GET, RequestMethod.POST)),
                Map.entry("/api/admin/roles/{roleId}", Set.of(RequestMethod.DELETE)),
                Map.entry("/api/admin/roles/{roleId}/permissions", Set.of(RequestMethod.PUT)),
                Map.entry("/api/admin/users", Set.of(RequestMethod.GET)),
                Map.entry("/api/admin/users/{userId}", Set.of(RequestMethod.GET)),
                Map.entry("/api/admin/users/{userId}/active", Set.of(RequestMethod.PUT)),
                Map.entry("/api/admin/users/{userId}/roles", Set.of(RequestMethod.PUT)));
        var registered = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> AuthController.class.isAssignableFrom(entry.getValue().getBeanType())
                        || UserController.class.isAssignableFrom(entry.getValue().getBeanType())
                        || AdminController.class.isAssignableFrom(entry.getValue().getBeanType())
                        || JwkSetController.class.isAssignableFrom(entry.getValue().getBeanType()))
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(method -> entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                                .map(path -> Map.entry(path, method))))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        java.util.stream.Collectors.groupingBy(Map.Entry::getValue,
                                java.util.stream.Collectors.counting())));
        assertThat(registered).hasSize(expected.size());
        expected.forEach((path, methods) -> {
            assertThat(registered).containsKey(path);
            assertThat(registered.get(path).keySet()).containsExactlyInAnyOrderElementsOf(methods);
            methods.forEach(method -> assertThat(registered.get(path).get(method)).isEqualTo(1L));
        });
    }

    private void stubAdminSellerApplicationReadPermission() {
        when(jwtService.validateToken("admin-token")).thenReturn(true);
        when(jwtService.isUserToken("admin-token")).thenReturn(true);
        when(jwtService.getUserIdFromToken("admin-token")).thenReturn(USER_ID);
        when(jwtService.getRolesFromToken("admin-token")).thenReturn(Set.of());
        when(jwtService.getPermissionsFromToken("admin-token")).thenReturn(Set.of("admin:users:read"));
    }

    private static void assertAdminJsonResponse(org.springframework.http.ResponseEntity<String> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).contains("\"status\":200");
    }

    private org.springframework.http.ResponseEntity<String> rawAdminGet(String path) {
        return rawAdminGet(path, "admin-token");
    }

    private org.springframework.http.ResponseEntity<String> rawAdminGet(String path, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        RestTemplate restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newHttpClient()));
        return restTemplate.exchange(java.net.URI.create("http://localhost:" + serverPort + path),
                org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers),
                String.class);
    }

    private Client client() {
        AtomicReference<URI> lastUri = new AtomicReference<>();
        RestTemplate restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newHttpClient()));
        var jacksonConverter = restTemplate.getMessageConverters().stream()
                .filter(org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class::isInstance)
                .map(org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class::cast)
                .findFirst().orElseThrow();
        jacksonConverter.setObjectMapper(jacksonConverter.getObjectMapper().copy()
                .registerModule(new org.openapitools.jackson.nullable.JsonNullableModule()));
        ClientHttpRequestInterceptor uriRecorder = (request, body, execution) -> {
            lastUri.set(request.getURI());
            return execution.execute(request, body);
        };
        restTemplate.setInterceptors(List.of(uriRecorder));
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath("http://localhost:" + serverPort);
        return new Client(new AuthenticationApi(apiClient), apiClient, lastUri);
    }

    private record Client(AuthenticationApi api, ApiClient apiClient, AtomicReference<URI> lastUri) {}

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            KafkaAutoConfiguration.class
    })
    @Import({AuthController.class, AdminController.class, UserController.class, JwkSetController.class,
            AuthApiMapperImpl.class, SecurityConfig.class, JwtAuthFilter.class,
            AuthenticatedUserValidator.class, GlobalExceptionHandler.class, TestSecurityConfig.class})
    static class TestApplication {}

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    static class TestSecurityConfig {
        @Bean
        OAuth2ClientConfig.OAuth2EnabledFlag oauth2EnabledFlag(
                @Value("${test.oauth.enabled:false}") boolean enabled) {
            return new OAuth2ClientConfig.OAuth2EnabledFlag(enabled);
        }
    }
}

@SpringBootTest(
        classes = GeneratedOAuthRedirectClientTest.OAuthTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:file:/dev/null",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
class GeneratedOAuthRedirectClientTest {

    @LocalServerPort
    private int serverPort;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private CustomOAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    void configuredProviderRedirectsToItsAuthorizationEndpointWithoutNetworkAccess() {
        AtomicReference<Integer> responseStatus = new AtomicReference<>();
        AtomicReference<URI> responseLocation = new AtomicReference<>();
        RestTemplate restTemplate = new RestTemplate(
                new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newHttpClient()));
        restTemplate.setInterceptors(List.of((request, body, execution) -> {
            var response = execution.execute(request, body);
            responseStatus.set(response.getStatusCode().value());
            responseLocation.set(response.getHeaders().getLocation());
            return response;
        }));
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath("http://localhost:" + serverPort);
        AuthenticationApi api = new AuthenticationApi(apiClient);

        org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> api.startSocialSignInWithHttpInfo("google"),
                org.springframework.web.client.RestClientException.class);

        assertThat(responseStatus.get()).isEqualTo(302);
        assertThat(responseLocation.get().getHost()).isEqualTo("accounts.google.com");
        assertThat(responseLocation.get().getRawQuery()).contains("client_id=test-client");
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            KafkaAutoConfiguration.class
    })
    @Import({SecurityConfig.class, JwtAuthFilter.class, OAuthTestConfiguration.class})
    static class OAuthTestApplication {}

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    static class OAuthTestConfiguration {
        @Bean
        OAuth2ClientConfig.OAuth2EnabledFlag oauth2EnabledFlag() {
            return new OAuth2ClientConfig.OAuth2EnabledFlag(true);
        }

        @Bean
        org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
                clientRegistrationRepository() {
            return new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(
                    org.springframework.security.config.oauth2.client.CommonOAuth2Provider.GOOGLE
                            .getBuilder("google")
                            .clientId("test-client")
                            .clientSecret("test-secret")
                            .redirectUri("http://localhost:8080/login/oauth2/code/google")
                            .build());
        }
    }
}
