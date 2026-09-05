package com.project.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maps JWT claims into Spring Security {@link GrantedAuthority}s.
 *
 * <p>Auth-service issues two custom claims:
 * <ul>
 *   <li>{@code roles}: list of role names <strong>already prefixed</strong> with {@code ROLE_}
 *       (e.g., {@code ROLE_ADMIN}). We use these as-is.</li>
 *   <li>{@code permissions}: list of fine-grained permissions
 *       (e.g., {@code orders:read}). Mapped to authorities verbatim.</li>
 * </ul>
 *
 * <p>This is the canonical converter every backend service must use. It fixes the
 * {@code ROLE_ROLE_X} double-prefix bug from the previous implementation.
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.getSubject());
    }

    /**
     * Convenience for callers that only want the authority list.
     */
    public static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        Object scopeClaim = jwt.getClaims().get("scope");
        if (scopeClaim instanceof String scopes) {
            scopes.lines()
                    .flatMap(line -> java.util.Arrays.stream(line.trim().split("\\s+")))
                    .filter(scope -> !scope.isBlank())
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .forEach(authorities::add);
        } else if (scopeClaim instanceof Collection<?> scopes) {
            scopes.stream().map(Object::toString)
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .forEach(authorities::add);
        }
        return authorities;
    }
}
