package com.project.authservice.service;

import com.project.authservice.config.ServiceClientProperties;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
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
                        Set.of("inventory.reserve", "inventory.commit"))));
        service = new ClientCredentialsService(properties, jwtService);
    }

    @Test
    void validClientReceivesTokenLimitedToRequestedScope() {
        when(jwtService.generateServiceToken(
                "order-service", Set.of("inventory.reserve"), Duration.ofMinutes(5)))
                .thenReturn("signed-service-token");

        ServiceTokenResponse response = service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.reserve"));

        assertThat(response.accessToken()).isEqualTo("signed-service-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(300);
        assertThat(response.scope()).isEqualTo("inventory.reserve");
        verify(jwtService).generateServiceToken(
                "order-service", Set.of("inventory.reserve"), Duration.ofMinutes(5));
    }

    @Test
    void badSecretIsRejectedWithoutIssuingToken() {
        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "wrong-secret", "inventory.reserve")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid client credentials");

        verifyNoInteractions(jwtService);
    }

    @Test
    void scopeOutsideClientAllowlistIsRejected() {
        assertThatThrownBy(() -> service.issue(new ClientCredentialsRequest(
                "order-service", "correct-secret", "inventory.delete")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Requested scope is not allowed");

        verifyNoInteractions(jwtService);
    }
}
