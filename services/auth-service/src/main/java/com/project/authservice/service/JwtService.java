package com.project.authservice.service;

import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.security.JwtKey;
import com.project.authservice.security.KeyManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issues and verifies JWTs.
 *
 * <p>Issued tokens carry:
 * <ul>
 *   <li>{@code sub}: user id (UUID string)</li>
 *   <li>{@code email}</li>
 *   <li>{@code roles}: list of {@code ROLE_*} strings</li>
 *   <li>{@code permissions}: list of fine-grained authorities</li>
 *   <li>{@code iss}: {@code auth-service}</li>
 *   <li>{@code kid} header: id of the key used to sign</li>
 * </ul>
 *
 * <p>Verification uses {@link KeyManager#publicKeyFor(String)} so a JWT signed
 * with the previous (rotating-out) key still verifies until expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final KeyManager keyManager;

    @Value("${jwt.access-token-expiration:900000}")
    private long jwtExpiration;

    @Value("${jwt.issuer:auth-service}")
    private String issuer;

    public String generateToken(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());

        JwtKey key = keyManager.getCurrentKey();
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .header().keyId(key.getKid()).type("JWT").and()
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpiration))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .signWith(key.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRolesFromToken(String token) {
        List<String> roles = parseClaims(token).get("roles", List.class);
        return roles != null ? Set.copyOf(roles) : Set.of();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsFromToken(String token) {
        List<String> permissions = parseClaims(token).get("permissions", List.class);
        return permissions != null ? Set.copyOf(permissions) : Set.of();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .keyLocator(new LocatorAdapter<>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        String kid = header.getKeyId();
                        Key found = keyManager.publicKeyFor(kid);
                        if (found == null) {
                            throw new io.jsonwebtoken.security.SignatureException(
                                    "Unknown key id in JWT header: " + kid);
                        }
                        return found;
                    }
                })
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
