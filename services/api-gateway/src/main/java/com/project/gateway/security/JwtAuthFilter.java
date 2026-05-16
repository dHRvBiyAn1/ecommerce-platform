package com.project.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/auth/", "/.well-known/", "/oauth2/", "/login/oauth2/"
    );

    @Value("${gateway.jwt.jwks-url}")
    private String jwksUrl;

    private final ObjectMapper objectMapper;
    private final AtomicReference<PublicKey> publicKeyRef = new AtomicReference<>();
    private volatile Instant lastFetchTime = Instant.EPOCH;

    public JwtAuthFilter() {
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        fetchJwks();
    }

    private void fetchJwks() {
        try {
            log.info("Fetching JWKS from {}", jwksUrl);
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(jwksUrl, String.class);
            if (response != null) {
                parseAndCacheFirstKey(response);
                log.info("JWKS public key cached successfully");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch JWKS from {}: {}", jwksUrl, e.getMessage());
        }
        lastFetchTime = Instant.now();
    }

    private void parseAndCacheFirstKey(String jwksResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jwksResponse);
        JsonNode keys = root.get("keys");
        if (keys == null || !keys.isArray() || keys.isEmpty()) {
            throw new IllegalArgumentException("No RSA keys found in JWKS response");
        }
        JsonNode firstKey = keys.get(0);
        String kty = firstKey.get("kty").asText();
        if (!"RSA".equalsIgnoreCase(kty)) {
            throw new IllegalArgumentException("Only RSA keys supported, got: " + kty);
        }
        String n = firstKey.get("n").asText();
        String e = firstKey.get("e").asText();
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        publicKeyRef.set(factory.generatePublic(spec));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isPublicPath(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        String token = authHeader.substring(7);
        try {
            refreshIfNeeded();
            PublicKey publicKey = publicKeyRef.get();
            if (publicKey == null) {
                log.warn("No cached JWKS public key, attempting emergency fetch");
                fetchJwks();
                publicKey = publicKeyRef.get();
                if (publicKey == null) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    return;
                }
            }

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            Object rolesObj = claims.get("roles");
            String roles = "";
            if (rolesObj instanceof List<?> rolesList) {
                roles = String.join(",", rolesList.stream()
                        .map(Object::toString)
                        .toArray(String[]::new));
            }

            request.setAttribute("X-User-Id", userId != null ? userId : "");
            request.setAttribute("X-User-Email", email != null ? email : "");
            request.setAttribute("X-Roles", roles);

        } catch (Exception e) {
            log.debug("JWT validation failed for {}: {}", path, e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path, String method) {
        if (PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (HttpMethod.GET.name().equals(method)
                && (path.startsWith("/api/v1/products/") || path.startsWith("/api/v1/categories/"))) {
            return true;
        }
        return false;
    }

    private void refreshIfNeeded() {
        if (Duration.between(lastFetchTime, Instant.now()).toMinutes() >= 5) {
            fetchJwks();
        }
    }
}
