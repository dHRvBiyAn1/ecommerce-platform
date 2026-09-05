package com.project.common.feign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceTokenHardeningTest {
    @Test
    void acceptsCaseInsensitiveBearerAndAllRequestedScopesRegardlessOfOrder() {
        ServiceAuthProperties properties = properties();
        properties.setScope("inventory.write coupons.read coupons.write");
        ServiceTokenProvider provider = new ServiceTokenProvider(ignored -> new ServiceTokenResponse(
                "valid", "bearer", 1, "coupons.write inventory.write coupons.read"), properties);

        assertThat(provider.getAccessToken()).isEqualTo("valid");
    }

    @Test
    void rejectsResponseMissingOneOfSeveralRequestedScopes() {
        ServiceAuthProperties properties = properties();
        properties.setScope("inventory.write coupons.read coupons.write");
        ServiceTokenProvider provider = new ServiceTokenProvider(ignored -> new ServiceTokenResponse(
                "invalid", "Bearer", 300, "inventory.write coupons.read"), properties);

        assertThatThrownBy(provider::getAccessToken).isInstanceOf(IllegalStateException.class);
    }

    static Stream<ServiceTokenResponse> invalidResponses() {
        return Stream.of(null,
                new ServiceTokenResponse(null, "Bearer", 300, "coupons.read"),
                new ServiceTokenResponse("", "Bearer", 300, "coupons.read"),
                new ServiceTokenResponse("   ", "Bearer", 300, "coupons.read"),
                new ServiceTokenResponse("token", null, 300, "coupons.read"),
                new ServiceTokenResponse("token", "", 300, "coupons.read"),
                new ServiceTokenResponse("token", "Basic", 300, "coupons.read"),
                new ServiceTokenResponse("token", "Bearer", 0, "coupons.read"),
                new ServiceTokenResponse("token", "Bearer", -1, "coupons.read"),
                new ServiceTokenResponse("token", "Bearer", 300, null),
                new ServiceTokenResponse("token", "Bearer", 300, "   "),
                new ServiceTokenResponse("token", "Bearer", 300, "coupons.write"),
                new ServiceTokenResponse("token", "Bearer", 300, "coupons.read.extra"));
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsMalformedResponseWithoutPoisoningCache(ServiceTokenResponse invalid) {
        AtomicInteger calls = new AtomicInteger();
        ServiceTokenProvider provider = new ServiceTokenProvider(properties -> calls.incrementAndGet() == 1
                ? invalid : new ServiceTokenResponse("recovered", "Bearer", 300, "coupons.read"), properties());

        assertThatThrownBy(provider::getAccessToken).isInstanceOf(IllegalStateException.class);
        assertThat(provider.getAccessToken()).isEqualTo("recovered");
        assertThat(provider.getAccessToken()).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void refreshesBeforeExpiryAndRecoversAfterFailedRefreshWithoutServingStaleToken() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        AtomicInteger calls = new AtomicInteger();
        ServiceTokenProvider provider = new ServiceTokenProvider(properties -> {
            int call = calls.incrementAndGet();
            if (call == 2) throw new IllegalStateException("exchange unavailable");
            return new ServiceTokenResponse("token-" + call, "Bearer", 300, "coupons.read");
        }, properties(), clock);

        assertThat(provider.getAccessToken()).isEqualTo("token-1");
        when(clock.instant()).thenReturn(start.plusSeconds(269));
        assertThat(provider.getAccessToken()).isEqualTo("token-1");
        when(clock.instant()).thenReturn(start.plusSeconds(270));
        assertThatThrownBy(provider::getAccessToken).isInstanceOf(IllegalStateException.class);
        when(clock.instant()).thenReturn(start.plusSeconds(301));
        assertThat(provider.getAccessToken()).isEqualTo("token-3");
        assertThat(provider.getAccessToken()).isEqualTo("token-3");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void concurrentCallersShareOneExchange() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ServiceTokenProvider provider = new ServiceTokenProvider(properties -> {
            calls.incrementAndGet();
            return new ServiceTokenResponse("shared", "Bearer", 300, "coupons.read");
        }, properties(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    if (!start.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    return provider.getAccessToken();
                }));
            }
            start.countDown();
            for (Future<String> result : results) assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo("shared");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private ServiceAuthProperties properties() {
        ServiceAuthProperties properties = new ServiceAuthProperties();
        properties.setTokenUri("http://localhost/token");
        properties.setClientId("cart-service");
        properties.setClientSecret("test-secret");
        properties.setScope("coupons.read");
        return properties;
    }
}
