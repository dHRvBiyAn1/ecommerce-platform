package com.project.common.feign;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ServiceTokenProviderTest {

    @Test
    void cachesTokenUntilItsRefreshWindow() {
        ServiceTokenClient client = mock(ServiceTokenClient.class);
        ServiceAuthProperties properties = new ServiceAuthProperties();
        properties.setTokenUri("http://auth/api/auth/token");
        properties.setClientId("order-service");
        properties.setClientSecret("secret");
        properties.setScope("inventory.write coupons.write");
        when(client.requestToken(properties)).thenReturn(new ServiceTokenResponse(
                "access-token", "Bearer", 300, "inventory.write coupons.write"));
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        ServiceTokenProvider provider = new ServiceTokenProvider(client, properties, clock);

        assertThat(provider.getAccessToken()).isEqualTo("access-token");
        assertThat(provider.getAccessToken()).isEqualTo("access-token");

        verify(client).requestToken(properties);
        verifyNoMoreInteractions(client);
    }
}
