package com.project.gateway.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@Configuration
public class ClientHeaderStrippingFilter {

    private static final Set<String> BLOCKED_HEADER_NAMES = Set.of(
            "x-user-id", "x-user-email", "x-roles", "x-roles-claim",
            "x-internal-token", "x-system-actor"
    );

    @Bean
    public Filter clientHeaderStrippingFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest http = (HttpServletRequest) req;
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
                }, res);
            }
        };
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<Filter> headerStripperRegistration(Filter clientHeaderStrippingFilter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<Filter> bean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(clientHeaderStrippingFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
