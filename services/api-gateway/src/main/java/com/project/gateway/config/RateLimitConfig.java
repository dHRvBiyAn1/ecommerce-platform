package com.project.gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP rate limiting backed by Bucket4j. Auth-related routes get a tighter
 * bucket; everything else uses the default. For multi-replica gateways replace
 * the in-memory map with a Redis-backed Bucket4j proxy.
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    @Value("${gateway.rate-limit.default.capacity:100}")
    private long defaultCapacity;
    @Value("${gateway.rate-limit.default.refill-tokens:100}")
    private long defaultRefill;
    @Value("${gateway.rate-limit.default.refill-period-seconds:60}")
    private long defaultPeriodSec;

    @Value("${gateway.rate-limit.auth.capacity:20}")
    private long authCapacity;
    @Value("${gateway.rate-limit.auth.refill-tokens:20}")
    private long authRefill;
    @Value("${gateway.rate-limit.auth.refill-period-seconds:60}")
    private long authPeriodSec;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String ip = clientIp(req);
                String path = req.getRequestURI();
                boolean authPath = path != null && path.startsWith("/api/auth/");
                Bucket bucket = bucketFor(ip + (authPath ? ":auth" : ":default"), authPath);
                if (bucket.tryConsume(1)) {
                    chain.doFilter(req, res);
                } else {
                    log.warn("Rate limit hit for ip={} path={}", ip, path);
                    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    res.getWriter().write("Too many requests");
                }
            }
        });
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // after header stripping, before routing
        return reg;
    }

    private Bucket bucketFor(String key, boolean authPath) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth band = authPath
                    ? Bandwidth.builder()
                        .capacity(authCapacity)
                        .refillIntervally(authRefill, Duration.ofSeconds(authPeriodSec))
                        .build()
                    : Bandwidth.builder()
                        .capacity(defaultCapacity)
                        .refillIntervally(defaultRefill, Duration.ofSeconds(defaultPeriodSec))
                        .build();
            return Bucket.builder().addLimit(band).build();
        });
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
