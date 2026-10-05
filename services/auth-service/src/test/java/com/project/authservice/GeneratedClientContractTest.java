package com.project.authservice;

import com.project.authservice.controller.AdminController;
import com.project.authservice.controller.AuthController;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.security.CustomOAuth2SuccessHandler;
import com.project.authservice.security.JwtAuthFilter;
import com.project.authservice.security.OAuth2ClientConfig;
import com.project.authservice.security.SecurityConfig;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.authservice.service.UserProfileService;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.InvalidScopeException;
import com.project.authservice.generated.testclient.api.AdministrationApi;
import com.project.authservice.generated.testclient.api.AuthenticationApi;
import com.project.authservice.generated.testclient.api.UserProfileApi;
import com.project.authservice.generated.testclient.invoker.ApiClient;
import com.project.authservice.generated.testclient.model.Token200Response;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.net.URI;
import java.util.List;
import java.util.Set;
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
        when(roleRepository.findAll()).thenReturn(List.of());

        AdministrationApi api = new AdministrationApi(client().apiClient);
        api.getApiClient().setBearerToken("admin-token");
        var response = api.listRolesWithHttpInfo();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void socialLoginRouteIsUnavailableWhenNoProviderIsConfigured() {
        HttpClientErrorException.NotFound missingRoute = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> client().api.startSocialSignInWithHttpInfo("google"),
                HttpClientErrorException.NotFound.class);
        assertThat(missingRoute.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(404));
    }

    private Client client() {
        AtomicReference<URI> lastUri = new AtomicReference<>();
        RestTemplate restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newHttpClient()));
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
    @Import({AuthController.class, AdminController.class, SecurityConfig.class, JwtAuthFilter.class,
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
