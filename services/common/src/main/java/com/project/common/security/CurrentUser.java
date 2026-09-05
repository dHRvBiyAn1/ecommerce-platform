package com.project.common.security;

import com.project.common.exception.ForbiddenOperationException;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Helpers to extract the current user from the Spring Security context.
 *
 * <p>Replaces the previous {@code @RequestHeader("X-User-Id")} pattern which trusted
 * arbitrary upstream headers. Now the JWT is the only source of truth.
 */
@UtilityClass
public class CurrentUser {

    public static Optional<UUID> id() {
        if (isService()) return Optional.empty();
        return jwt().map(Jwt::getSubject).flatMap(CurrentUser::parseUuid);
    }

    public static UUID requireId() {
        return id().orElseThrow(() -> new ForbiddenOperationException("Authentication required"));
    }

    public static Optional<String> email() {
        return jwt().map(j -> j.getClaimAsString("email"));
    }

    public static String requireEmail() {
        return email().orElseThrow(() -> new ForbiddenOperationException("Authentication required"));
    }

    public static boolean hasRole(String role) {
        return authorities().anyMatch(a -> a.equals(role));
    }

    public static boolean hasAuthority(String authority) {
        return authorities().anyMatch(a -> a.equals(authority));
    }

    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    public static boolean isService() {
        return jwt().map(token -> "service".equals(token.getClaimAsString("token_type")))
                .orElse(false);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static java.util.stream.Stream<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return java.util.stream.Stream.empty();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority);
    }

    private static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken t) {
            return Optional.of(t.getToken());
        }
        return Optional.empty();
    }
}
