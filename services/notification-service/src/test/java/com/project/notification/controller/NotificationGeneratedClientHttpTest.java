package com.project.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.notification.generated.model.NotificationResponse;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.config.SecurityConfig;
import com.project.notification.model.Notification;
import com.project.notification.repository.NotificationDeliveryRepository;
import com.project.notification.repository.NotificationRepository;
import com.project.notification.service.EmailService;
import com.project.notification.service.NotificationService;
import com.project.notification.generated.testclient.api.NotificationsApi;
import com.project.notification.generated.testclient.invoker.ApiClient;
import com.project.notification.generated.testclient.invoker.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = NotificationGeneratedClientHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "spring.config.import=optional:file:/dev/null",
                "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
                "management.endpoints.enabled-by-default=false"})
class NotificationGeneratedClientHttpTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String OWNER_TOKEN = "notification-owner-token";
    private static final String OTHER_TOKEN = "notification-other-token";
    private static final String ADMIN_TOKEN = "notification-admin-token";

    @Autowired private ServletWebServerApplicationContext serverContext;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationRepository repository;
    @MockBean private NotificationDeliveryRepository deliveryRepository;
    @MockBean private EmailService emailService;
    @MockBean private JwtDecoder jwtDecoder;
    @SpyBean private NotificationService notificationService;

    private NotificationsApi ownerApi;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void configureClientsAndJwtDecoder() {
        when(jwtDecoder.decode(OWNER_TOKEN)).thenReturn(jwt(OWNER_TOKEN, OWNER, false));
        when(jwtDecoder.decode(OTHER_TOKEN)).thenReturn(jwt(OTHER_TOKEN, OTHER, false));
        when(jwtDecoder.decode(ADMIN_TOKEN)).thenReturn(jwt(ADMIN_TOKEN, ADMIN, true));
        ownerApi = client(OWNER, false);
    }

    @Test
    void generatedClientListsOwnedHistoryWithPageMetadataAndUnreadCount() throws Exception {
        LocalDateTime created = LocalDateTime.of(2026, 10, 5, 10, 30);
        Notification notification = notification("notice-1", OWNER, created);
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(OWNER), any()))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(1, 5, Sort.by("createdAt").descending()), 6));
        when(repository.countByUserIdAndStatus(OWNER, Notification.Status.SENT)).thenReturn(3L);

        var history = ownerApi.callListWithHttpInfo(1, 5, List.of("createdAt,desc"));
        var unread = ownerApi.unreadCountWithHttpInfo();

        assertThat(history.getStatusCode()).isEqualTo(200);
        assertThat(history.getData().getData().getContent()).hasSize(1);
        assertThat(history.getData().getData().getPage()).isEqualTo(1);
        assertThat(history.getData().getData().getTotalElements()).isEqualTo(6);
        assertThat(unread.getData().getData()).isEqualTo(3L);
        HttpResponse<String> rawHistory = rawListResponse("?page=1&size=5&sort=createdAt,desc");
        assertThat(rawHistory.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(rawHistory.body()).path("data").path("content").get(0))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(
                        new NotificationMapper().toResponse(notification))));
        verify(repository, times(2)).findByUserIdOrderByCreatedAtDesc(eq(OWNER),
                eq(PageRequest.of(1, 5, Sort.by(Sort.Order.desc("createdAt")))));
        verify(repository).countByUserIdAndStatus(OWNER, Notification.Status.SENT);
    }

    @Test
    void generatedClientPreservesLegacyNullStatusFromHttpResponse() throws Exception {
        NotificationResponse legacyRecord = new NotificationResponse(
                "legacy-null-status", OWNER, null, null, null, null, null, null,
                null, 0, null, null, null, null);
        doReturn(new PageImpl<>(List.of(legacyRecord), PageRequest.of(0, 20), 1))
                .when(notificationService).listForUser(eq(OWNER), any(Pageable.class));

        var history = ownerApi.callListWithHttpInfo(null, null, null);
        HttpResponse<String> rawHistory = rawListResponse("");

        assertThat(history.getStatusCode()).isEqualTo(200);
        assertThat(history.getData().getData().getContent()).hasSize(1);
        assertThat(history.getData().getData().getContent().get(0).getStatus()).isNull();
        assertThat(rawHistory.statusCode()).isEqualTo(200);
        var serializedRecord = objectMapper.readTree(objectMapper.writeValueAsBytes(legacyRecord));
        var rawRecord = objectMapper.readTree(rawHistory.body()).path("data").path("content").get(0);
        assertThat(rawRecord).isEqualTo(serializedRecord);
        assertThat(rawRecord.has("status")).isTrue();
        assertThat(rawRecord.get("status").isNull()).isTrue();
    }

    @Test
    void legacyStoredNotificationWithoutStatusRemainsReadableOverHttp() throws Exception {
        Notification legacy = Notification.builder().id("legacy-null-status").userId(OWNER)
                .recipient("owner@example.com").channel("EMAIL").category("ORDER")
                .subject("Order update").body("Order is ready").status(null).retryCount(0).build();
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(OWNER), any()))
                .thenReturn(new PageImpl<>(List.of(legacy)));

        var history = ownerApi.callListWithHttpInfo(null, null, null);
        HttpResponse<String> rawHistory = rawListResponse("");

        assertThat(history.getStatusCode()).isEqualTo(200);
        assertThat(history.getData().getData().getContent()).hasSize(1);
        assertThat(history.getData().getData().getContent().get(0).getStatus()).isNull();
        var rawRecord = objectMapper.readTree(rawHistory.body()).path("data").path("content").get(0);
        assertThat(rawHistory.statusCode()).isEqualTo(200);
        assertThat(rawRecord.path("id").asText()).isEqualTo("legacy-null-status");
        assertThat(rawRecord.has("status")).isTrue();
        assertThat(rawRecord.get("status").isNull()).isTrue();
    }

    @Test
    void generatedClientUsesSpringDefaultPageSizeAndDescendingSort() throws ApiException {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(OWNER), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var history = ownerApi.callListWithHttpInfo(null, null, List.of("createdAt,desc"));

        assertThat(history.getStatusCode()).isEqualTo(200);
        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(OWNER),
                eq(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Test
    void rawRequestsKeepRepeatedSortsAndSpringNegativePageableFallbacks() throws Exception {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(OWNER), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(rawList("?sort=createdAt,desc&sort=subject,asc")).isEqualTo(200);
        assertThat(rawList("?page=-1&size=5")).isEqualTo(200);
        assertThat(rawList("?page=2&size=-1")).isEqualTo(200);

        var pageables = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(3)).findByUserIdOrderByCreatedAtDesc(eq(OWNER), pageables.capture());
        assertThat(pageables.getAllValues()).containsExactly(
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("subject"))),
                PageRequest.of(0, 5),
                PageRequest.of(2, 20));
    }

    @Test
    void generatedClientEnforcesReadOwnershipAndAllowsAdministratorAccess() throws Exception {
        Notification notification = notification("notice-1", OWNER, LocalDateTime.now());
        when(repository.findById("notice-1")).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiException forbidden = catchThrowableOfType(() -> client(OTHER, false).markRead("notice-1"), ApiException.class);
        assertThat(forbidden.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        var marked = ownerApi.markReadWithHttpInfo("notice-1");
        assertThat(marked.getStatusCode()).isEqualTo(200);
        assertThat(marked.getData().getData().getStatus().toString()).isEqualTo("READ");

        var adminMarked = client(ADMIN, true).markRead("notice-1");
        assertThat(adminMarked.getData().getStatus().toString()).isEqualTo("READ");
    }

    @Test
    void anonymousRequestReturnsTheConfiguredSecurityErrorBody() {
        var anonymous = new NotificationsApi(new ApiClient().setHost("localhost")
                .setPort(serverContext.getWebServer().getPort()).setBasePath(""));
        ApiException rejected = catchThrowableOfType(anonymous::unreadCount, ApiException.class);
        assertThat(rejected.getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(rejected.getResponseBody()).contains("UNAUTHENTICATED", "Authentication required");
    }

    private int rawList(String query) throws Exception {
        return rawListResponse(query).statusCode();
    }

    private HttpResponse<String> rawListResponse(String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + serverContext.getWebServer().getPort() + "/api/v1/notifications" + query))
                .header("Authorization", "Bearer " + OWNER_TOKEN).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private NotificationsApi client(UUID user, boolean admin) {
        String token = admin ? ADMIN_TOKEN : user.equals(OWNER) ? OWNER_TOKEN : OTHER_TOKEN;
        ApiClient apiClient = new ApiClient().setHost("localhost").setPort(serverContext.getWebServer().getPort())
                .setBasePath("").setRequestInterceptor(request -> request.header("Authorization", "Bearer " + token));
        return new NotificationsApi(apiClient);
    }

    private static Jwt jwt(String token, UUID subject, boolean admin) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "none").subject(subject.toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(60)).claim("roles", admin ? List.of("ROLE_ADMIN") : List.of("ROLE_CUSTOMER"))
                .build();
    }

    private static Notification notification(String id, UUID user, LocalDateTime created) {
        return Notification.builder().id(id).userId(user).recipient("owner@example.com").channel("EMAIL")
                .category("ORDER").subject("Order update").body("Order is ready").status(Notification.Status.SENT)
                .retryCount(0).createdAt(created).build();
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"})
    @Import({NotificationController.class, SecurityConfig.class, NotificationMapper.class,
            NotificationService.class, NotificationAccessValidator.class, GlobalExceptionHandler.class})
    static class TestApplication { }
}
