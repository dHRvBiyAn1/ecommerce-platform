package com.project.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String ROLES_HEADER = "X-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String userEmail = request.getHeader(USER_EMAIL_HEADER);
        String rolesHeader = request.getHeader(ROLES_HEADER);

        if (userId != null && !userId.isEmpty()) {
            List<SimpleGrantedAuthority> authorities = List.of();
            if (rolesHeader != null && !rolesHeader.isEmpty()) {
                authorities = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, userEmail, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {} with roles: {}", userId, rolesHeader);
        }

        filterChain.doFilter(request, response);
    }
}
