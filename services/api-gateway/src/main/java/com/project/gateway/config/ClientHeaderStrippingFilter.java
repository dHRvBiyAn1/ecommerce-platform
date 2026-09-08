package com.project.gateway.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Strips client-supplied identity headers before they reach backend services so a
 * malicious client cannot spoof an admin identity by adding {@code X-User-Id} to
 * their request. Only the gateway is allowed to forward identity, and as of the
 * resource-server migration we no longer do that — backends derive identity from
 * the JWT directly. This filter is belt-and-suspenders.
 */
public class ClientHeaderStrippingFilter extends OncePerRequestFilter {
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    private static final Set<String> BLOCKED_HEADER_NAMES = Set.of(
            "x-user-id", "x-user-email", "x-roles", "x-roles-claim",
            "x-internal-token", "x-system-actor"
    );

    protected void doFilterInternal(HttpServletRequest http, HttpServletResponse response,
                                    jakarta.servlet.FilterChain chain) throws IOException, ServletException {
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override
                    public String getHeader(String name) {
                        if (name != null && BLOCKED_HEADER_NAMES.contains(name.toLowerCase(Locale.ROOT))) return null;
                        return super.getHeader(name);
                    }
                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if (name != null && BLOCKED_HEADER_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                            return Collections.emptyEnumeration();
                        }
                        return super.getHeaders(name);
                    }
                    @Override
                    public Enumeration<String> getHeaderNames() {
                        return Collections.enumeration(
                                Collections.list(super.getHeaderNames()).stream()
                                        .filter(n -> !BLOCKED_HEADER_NAMES.contains(n.toLowerCase(Locale.ROOT)))
                                        .toList());
                    }
                }, response);
    }

}
