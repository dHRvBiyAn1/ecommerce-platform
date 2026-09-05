package com.project.authservice.service;

import com.project.authservice.config.ServiceClientProperties;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.InvalidScopeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientCredentialsServiceTest {

    @Mock
    private JwtService jwtService;

    private ClientCredentialsService service;

    @BeforeEach
    void setUp() {
        ServiceClientProperties properties = new ServiceClientProperties(
                Duration.ofMinutes(5),
                Map.of("order-service", new ServiceClientProperties.Client(
                        "correct-secret",
                         Set.of("inventory.write", "coupons.read"))));
        service = new ClientCredentialsService(properties, jwtService);
    }

    @Test
    void validClientReceivesTokenLimitedToRequestedScope() {
        when(jwtService.generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofMinutes(5)))
                .thenReturn("signed-service-token");

        ServiceTokenResponse response = service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write"));

        assertThat(response.accessToken()).isEqualTo("signed-service-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(response.scope()).isEqualTo("inventory.write");
        verify(jwtService).generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofMinutes(5));
    }

    @Test
    void badSecretIsRejectedWithoutIssuingToken() {
        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "wrong-secret", "inventory.write")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid client credentials");

        verifyNoInteractions(jwtService);
    }

    @Test
    void scopeOutsideClientAllowlistIsRejected() {
        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.delete")))
                .isExactlyInstanceOf(InvalidScopeException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("code", "INVALID_SCOPE")
                .hasMessage("Requested scope is not allowed");

        verifyNoInteractions(jwtService);
    }

    @Test
    void subSecondTokenTtlIsRejectedWithoutIssuingToken() {
        service = new ClientCredentialsService(propertiesWithTtl(Duration.ofMillis(500)), jwtService);

        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Service token TTL must be between 1 second and 15 minutes");

        verifyNoInteractions(jwtService);
    }

    @Test
    void oneSecondTokenTtlIsAccepted() {
        service = new ClientCredentialsService(propertiesWithTtl(Duration.ofSeconds(1)), jwtService);
        when(jwtService.generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofSeconds(1)))
                .thenReturn("signed-service-token");

        ServiceTokenResponse response = service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write"));

        assertThat(response.expiresIn()).isEqualTo(1);
        verify(jwtService).generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofSeconds(1));
    }

    @Test
    void fractionalTokenTtlUsesWholeSecondsInExpiresIn() {
        Duration ttl = Duration.ofMillis(1_500);
        service = new ClientCredentialsService(propertiesWithTtl(ttl), jwtService);
        when(jwtService.generateServiceToken("order-service", Set.of("inventory.write"), ttl))
                .thenReturn("signed-service-token");

        ServiceTokenResponse response = service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write"));

        assertThat(response.expiresIn()).isEqualTo(1);
        verify(jwtService).generateServiceToken("order-service", Set.of("inventory.write"), ttl);
    }

    @Test
    void fifteenMinuteTokenTtlIsAccepted() {
        service = new ClientCredentialsService(propertiesWithTtl(Duration.ofMinutes(15)), jwtService);
        when(jwtService.generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofMinutes(15)))
                .thenReturn("signed-service-token");

        ServiceTokenResponse response = service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write"));

        assertThat(response.expiresIn()).isEqualTo(900);
        verify(jwtService).generateServiceToken(
                "order-service", Set.of("inventory.write"), Duration.ofMinutes(15));
    }

    @Test
    void tokenTtlAboveFifteenMinutesIsRejectedWithoutIssuingToken() {
        service = new ClientCredentialsService(propertiesWithTtl(Duration.ofMinutes(15).plusSeconds(1)), jwtService);

        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.write")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Service token TTL must be between 1 second and 15 minutes");

        verifyNoInteractions(jwtService);
    }

    private static ServiceClientProperties propertiesWithTtl(Duration ttl) {
        return new ServiceClientProperties(
                ttl,
                Map.of("order-service", new ServiceClientProperties.Client(
                        "correct-secret",
                         Set.of("inventory.write", "coupons.read"))));
    }
}
