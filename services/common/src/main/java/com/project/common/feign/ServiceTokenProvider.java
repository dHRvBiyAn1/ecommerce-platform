package com.project.common.feign;

import com.project.common.generated.token.ServiceTokenResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class ServiceTokenProvider {

  private static final long REFRESH_SKEW_SECONDS = 30;

  private final ServiceTokenClient client;
  private final ServiceAuthProperties properties;
  private final Clock clock;
  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();

  public ServiceTokenProvider(ServiceTokenClient client, ServiceAuthProperties properties) {
    this(client, properties, Clock.systemUTC());
  }

  ServiceTokenProvider(ServiceTokenClient client, ServiceAuthProperties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  public String getAccessToken() {
    CachedToken current = cachedToken.get();
    Instant now = clock.instant();
    if (current != null && now.isBefore(current.refreshAt())) {
      return current.value();
    }
    refreshLock.lock();
    try {
      current = cachedToken.get();
      now = clock.instant();
      if (current != null && now.isBefore(current.refreshAt())) {
        return current.value();
      }
      ServiceTokenResponse response = client.requestToken(properties);
      validateResponse(response);
      long usableLifetime = Math.max(1, response.expiresIn() - REFRESH_SKEW_SECONDS);
      CachedToken updated =
          new CachedToken(response.accessToken(), now.plusSeconds(usableLifetime));
      cachedToken.set(updated);
      return updated.value();
    } finally {
      refreshLock.unlock();
    }
  }

  private void validateResponse(ServiceTokenResponse response) {
    switch (response) {
      case null ->
          throw new IllegalStateException(
              "Auth service returned an invalid service token response");
      case ServiceTokenResponse(
          String accessToken,
          String tokenType,
          long expiresIn,
          String scope) -> {
        if (accessToken == null
            || accessToken.isBlank()
            || !"Bearer".equalsIgnoreCase(tokenType)
            || expiresIn <= 0
            || scope == null
            || scope.isBlank()) {
          throw new IllegalStateException(
              "Auth service returned an invalid service token response");
        }
        java.util.Set<String> grantedScopes =
            new java.util.HashSet<>(java.util.Arrays.asList(scope.trim().split("\\s+")));
        if (properties.getScope() == null
            || !grantedScopes.containsAll(
                java.util.Arrays.asList(properties.getScope().trim().split("\\s+")))) {
          throw new IllegalStateException("Auth service did not grant the requested scopes");
        }
      }
    }
  }

  private record CachedToken(String value, Instant refreshAt) {}
}
