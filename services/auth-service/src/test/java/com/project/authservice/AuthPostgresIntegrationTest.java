package com.project.authservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.authservice.entity.RefreshToken;
import com.project.authservice.entity.User;
import com.project.authservice.repository.RefreshTokenRepository;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import com.project.authservice.service.UserEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "jwt.refresh-token-expiration=60000",
        "auth.service-clients.token-ttl=PT5M",
        "auth.service-clients.clients.cart-service.secret=cart-secret",
        "auth.service-clients.clients.cart-service.allowed-scopes[0]=coupons.read"
})
@AutoConfigureMockMvc
class AuthPostgresIntegrationTest {

    private static final String DB_APPLICATION_NAME = "task25-refresh-concurrency";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private DataSource dataSource;

    @MockBean
    private UserEventPublisher userEventPublisher;
    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET application_name = '" + DB_APPLICATION_NAME + "'");
    }

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentRefreshRotationAllowsOneWinnerForSingleRefreshToken() throws Exception {
        String rawRefresh = "refresh-token." + UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.save(user("refresh@example.com")));
         refreshToken.setTokenHash(sha256(rawRefresh));
        refreshToken.setExpiryDate(LocalDateTime.now().plusMinutes(5));
        refreshToken.setFamilyId(UUID.randomUUID());
        refreshTokenRepository.save(refreshToken);

        var executor = Executors.newFixedThreadPool(2);
        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            lockRefreshToken(lockConnection, refreshToken.getId());

            List<Future<MvcResult>> results = List.of(
                    executor.submit(refreshAttempt(rawRefresh)),
                    executor.submit(refreshAttempt(rawRefresh)));
            awaitRefreshLockWaiters();
            lockConnection.commit();

            List<MvcResult> responses = results.stream().map(this::get).toList();
            List<Integer> statuses = responses.stream()
                    .map(result -> result.getResponse().getStatus())
                    .toList();

            assertThat(statuses).containsExactlyInAnyOrder(200, 403);
            MvcResult loser = responses.stream()
                    .filter(result -> result.getResponse().getStatus() == 403)
                    .findFirst().orElseThrow();
            JsonNode loserBody = objectMapper.readTree(loser.getResponse().getContentAsString());
            assertThat(loserBody.get("code").asText()).isEqualTo("FORBIDDEN");
            assertThat(loserBody.get("message").asText()).isEqualTo("Request could not be processed");
            assertPersistedRefreshLineage(refreshToken.getId(), refreshToken.getFamilyId());
        } finally {
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                        .as("refresh request workers must terminate")
                        .isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }

    @Test
    void configuredClientCredentialsTokenEndpointIssuesRequestedScopeToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "cart-service")
                        .param("client_secret", "cart-secret")
                        .param("scope", "coupons.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.scope").value("coupons.read"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.get("access_token").asText();
        assertThat(jwtService.validateToken(token)).isTrue();
        JsonNode payload = jwtPayload(token);
        assertThat(payload.get("sub").asText()).isEqualTo("cart-service");
        assertThat(payload.get("token_type").asText()).isEqualTo("service");
        assertThat(payload.get("scope").asText()).isEqualTo("coupons.read");
    }

    @Test
    void clientCredentialsTokenEndpointRejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "cart-service")
                        .param("client_secret", "wrong-secret")
                        .param("scope", "coupons.read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    private Callable<MvcResult> refreshAttempt(String rawRefresh) {
        return () -> {
            return mockMvc.perform(post("/api/auth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("grant_type", "refresh_token")
                            .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawRefresh)))
                    .andReturn();
        };
    }

    private void lockRefreshToken(Connection connection, UUID refreshTokenId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM refresh_tokens WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, refreshTokenId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
            }
        }
    }

    private void awaitRefreshLockWaiters() throws SQLException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        try (Connection observation = dataSource.getConnection();
             PreparedStatement statement = observation.prepareStatement("""
                     SELECT pid
                     FROM pg_stat_activity
                     WHERE application_name = ?
                       AND pid <> pg_backend_pid()
                       AND wait_event_type = 'Lock'
                       AND state = 'active'
                       AND query ILIKE '%refresh_tokens%'
                       AND query ILIKE '%for%no%key%update%'
                     ORDER BY pid
                     """)) {
            statement.setString(1, DB_APPLICATION_NAME);
            while (System.nanoTime() < deadline) {
                Set<Integer> waiterPids = new HashSet<>();
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) waiterPids.add(rows.getInt("pid"));
                }
                if (waiterPids.size() == 2) return;
                Thread.yield();
            }
        }
        throw new AssertionError("Both tagged refresh sessions did not wait on the refresh-token row lock");
    }

    private void assertPersistedRefreshLineage(UUID originalTokenId, UUID familyId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement original = connection.prepareStatement(
                     "SELECT revoked FROM refresh_tokens WHERE id = ?");
             PreparedStatement family = connection.prepareStatement(
                     "SELECT count(*) FROM refresh_tokens WHERE family_id = ?");
             PreparedStatement replacements = connection.prepareStatement(
                     "SELECT count(*) FROM refresh_tokens WHERE family_id = ? AND id <> ?");
             PreparedStatement revokedFamilyTokens = connection.prepareStatement(
                     "SELECT count(*) FROM refresh_tokens WHERE family_id = ? AND revoked = true")) {
            original.setObject(1, originalTokenId);
            try (var rows = original.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean("revoked")).isTrue();
            }

            family.setObject(1, familyId);
            replacements.setObject(1, familyId);
            replacements.setObject(2, originalTokenId);
            revokedFamilyTokens.setObject(1, familyId);
            assertThat(count(family)).isEqualTo(2);
            assertThat(count(replacements)).isEqualTo(1);
            assertThat(count(revokedFamilyTokens)).isEqualTo(2);
        }
    }

    private int count(PreparedStatement statement) throws SQLException {
        try (var rows = statement.executeQuery()) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private MvcResult get(Future<MvcResult> result) {
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setActive(true);
        return user;
    }

    private JsonNode jwtPayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
