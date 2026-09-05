package com.project.common.feign;

import java.time.Clock;
import java.time.Instant;

public class ServiceTokenProvider {

    private static final long REFRESH_SKEW_SECONDS = 30;

    private final ServiceTokenClient client;
    private final ServiceAuthProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public ServiceTokenProvider(ServiceTokenClient client, ServiceAuthProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    ServiceTokenProvider(ServiceTokenClient client, ServiceAuthProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.refreshAt())) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            now = clock.instant();
            if (current != null && now.isBefore(current.refreshAt())) {
                return current.value();
            }
            ServiceTokenResponse response = client.requestToken(properties);
            validateResponse(response);
            long usableLifetime = Math.max(1, response.expiresIn() - REFRESH_SKEW_SECONDS);
            cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(usableLifetime));
            return cachedToken.value();
        }
    }

    private void validateResponse(ServiceTokenResponse response) {
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || !"Bearer".equalsIgnoreCase(response.tokenType()) || response.expiresIn() <= 0
                || response.scope() == null || response.scope().isBlank()) {
            throw new IllegalStateException("Auth service returned an invalid service token response");
        }
        java.util.Set<String> grantedScopes = new java.util.HashSet<>(
                java.util.Arrays.asList(response.scope().trim().split("\\s+")));
        if (properties.getScope() == null || !grantedScopes.containsAll(
                java.util.Arrays.asList(properties.getScope().trim().split("\\s+")))) {
            throw new IllegalStateException("Auth service did not grant the requested scopes");
        }
    }

    private record CachedToken(String value, Instant refreshAt) {}
}
