package com.project.authservice.service;

import com.project.authservice.config.ServiceClientProperties;
import com.project.authservice.generated.model.ServiceTokenResponse;
import com.project.authservice.exception.AuthException;
import com.project.authservice.exception.InvalidScopeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ClientCredentialsService {

    private static final Duration MIN_TOKEN_TTL = Duration.ofSeconds(1);
    private static final Duration MAX_TOKEN_TTL = Duration.ofMinutes(15);
    private static final byte[] UNKNOWN_CLIENT_SECRET =
            "invalid-unconfigured-client-secret".getBytes(StandardCharsets.UTF_8);

    private final ServiceClientProperties properties;
    private final JwtService jwtService;

    public ServiceTokenResponse issue(String clientId, String clientSecret, String scope) {
        if (isBlank(clientId) || clientSecret == null) {
            throw new AuthException("Invalid client credentials");
        }

        ServiceClientProperties.Client client = properties.clients().get(clientId);
        byte[] expectedSecret = client == null || client.secret() == null
                ? UNKNOWN_CLIENT_SECRET
                : client.secret().getBytes(StandardCharsets.UTF_8);
        byte[] suppliedSecret = clientSecret.getBytes(StandardCharsets.UTF_8);
        boolean secretMatches = MessageDigest.isEqual(expectedSecret, suppliedSecret);
        Arrays.fill(suppliedSecret, (byte) 0);

        if (client == null || isBlank(client.secret()) || !secretMatches) {
            throw new AuthException("Invalid client credentials");
        }

        Set<String> requestedScopes = parseScopes(scope, client.allowedScopes());
        if (requestedScopes.isEmpty() || !client.allowedScopes().containsAll(requestedScopes)) {
            throw new InvalidScopeException("Requested scope is not allowed");
        }

        Duration tokenTtl = properties.tokenTtl();
        if (tokenTtl == null || tokenTtl.compareTo(MIN_TOKEN_TTL) < 0 || tokenTtl.compareTo(MAX_TOKEN_TTL) > 0) {
            throw new IllegalStateException("Service token TTL must be between 1 second and 15 minutes");
        }

        String normalizedScope = String.join(" ", requestedScopes);
        String accessToken = jwtService.generateServiceToken(clientId, requestedScopes, tokenTtl);
        return new ServiceTokenResponse().accessToken(accessToken)
                .tokenType(ServiceTokenResponse.TokenTypeEnum.BEARER).expiresIn(tokenTtl.toSeconds()).scope(normalizedScope);
    }

    private static Set<String> parseScopes(String requestedScope, Set<String> allowedScopes) {
        if (isBlank(requestedScope)) {
            return new TreeSet<>(allowedScopes);
        }
        Set<String> scopes = new TreeSet<>();
        Arrays.stream(requestedScope.trim().split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .forEach(scopes::add);
        return scopes;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
