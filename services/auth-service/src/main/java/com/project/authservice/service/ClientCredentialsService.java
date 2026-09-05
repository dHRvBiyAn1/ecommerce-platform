package com.project.authservice.service;

import com.project.authservice.config.ServiceClientProperties;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.exception.AuthException;
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

    private static final byte[] UNKNOWN_CLIENT_SECRET =
            "invalid-unconfigured-client-secret".getBytes(StandardCharsets.UTF_8);

    private final ServiceClientProperties properties;
    private final JwtService jwtService;

    public ServiceTokenResponse issue(ClientCredentialsRequest request) {
        if (request == null || isBlank(request.clientId()) || request.clientSecret() == null) {
            throw new AuthException("Invalid client credentials");
        }

        ServiceClientProperties.Client client = properties.clients().get(request.clientId());
        byte[] expectedSecret = client == null || client.secret() == null
                ? UNKNOWN_CLIENT_SECRET
                : client.secret().getBytes(StandardCharsets.UTF_8);
        byte[] suppliedSecret = request.clientSecret().getBytes(StandardCharsets.UTF_8);
        boolean secretMatches = MessageDigest.isEqual(expectedSecret, suppliedSecret);
        Arrays.fill(suppliedSecret, (byte) 0);

        if (client == null || isBlank(client.secret()) || !secretMatches) {
            throw new AuthException("Invalid client credentials");
        }

        Set<String> requestedScopes = parseScopes(request.scope(), client.allowedScopes());
        if (requestedScopes.isEmpty() || !client.allowedScopes().containsAll(requestedScopes)) {
            throw new AuthException("Requested scope is not allowed");
        }

        Duration tokenTtl = properties.tokenTtl();
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalStateException("Service token TTL must be positive");
        }

        String normalizedScope = String.join(" ", requestedScopes);
        String accessToken = jwtService.generateServiceToken(request.clientId(), requestedScopes, tokenTtl);
        return new ServiceTokenResponse(accessToken, "Bearer", tokenTtl.toSeconds(), normalizedScope);
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
