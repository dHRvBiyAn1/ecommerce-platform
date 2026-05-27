package com.project.authservice.security;

import com.project.authservice.service.JwtService;
import com.project.authservice.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates self-issued JWTs on auth-service's own protected endpoints (/api/user,
 * /api/admin, /api/auth/change-password). Other backend services use Spring's
 * standard OAuth2 resource server filter chain (see common.security.ResourceServerSecurityConfig)
 * which fetches our JWKS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService blacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (blacklist.isBlacklisted(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Token revoked");
                return;
            }
        } catch (Exception e) {
            log.warn("Blacklist check failed; falling through: {}", e.getMessage());
        }

        try {
            if (jwtService.validateToken(token)) {
                String userId = jwtService.getUserIdFromToken(token);
                Set<String> roles = jwtService.getRolesFromToken(token);
                Set<String> permissions = jwtService.getPermissionsFromToken(token);

                List<GrantedAuthority> authorities = new ArrayList<>();
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
