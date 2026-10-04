package com.project.gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import com.project.gateway.filter.ForwardedIpTrustFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP rate limiting backed by a distributed Redis-based Bucket4j proxy.
 * Eliminates local memory leaks (OOM) and split-brain rate-limiting behavior
 * by maintaining state globally across gateway instances.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {
    public static final int RATE_LIMIT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

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

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    private final MeterRegistry meterRegistry;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "gateway.rate-limit.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisClient redisClient() {
        String uri = "redis://" + (redisPassword.isEmpty() ? "" : ":" + redisPassword + "@") + redisHost + ":" + redisPort;
        log.info("Connecting Bucket4j to Redis cluster at {}:{}", redisHost, redisPort);
        return RedisClient.create(uri);
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.redis.enabled", havingValue = "true", matchIfMissing = true)
    public ProxyManager<byte[]> lettuceProxyManager(RedisClient redisClient) {
        StatefulRedisConnection<byte[], byte[]> connection = redisClient.connect(
                RedisCodec.of(new ByteArrayCodec(), new ByteArrayCodec())
        );
        ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                .withExpirationAfterWriteStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)));
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(clientSideConfig)
                .build();
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> rateLimitFilterRegistration(ProxyManager<byte[]> proxyManager) {
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(new RateLimitFilter(proxyManager));
        reg.addUrlPatterns("/*");
        reg.setOrder(RATE_LIMIT_FILTER_ORDER);
        return reg;
    }

    private class RateLimitFilter extends OncePerRequestFilter {
        private final ProxyManager<byte[]> proxyManager;

        private RateLimitFilter(ProxyManager<byte[]> proxyManager) {
            this.proxyManager = proxyManager;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
                if (seedEnabled) {
                    chain.doFilter(req, res);
                    return;
                }
                String ip = clientIp(req);
                String path = req.getRequestURI();
                boolean authPath = path != null && path.startsWith("/api/auth/");
                String key = "rl:" + ip + (authPath ? ":auth" : ":default");

                BucketConfiguration config = getConfiguration(authPath);
                
                if (proxyManager.builder().build(key.getBytes(), config).tryConsume(1)) {
                    chain.doFilter(req, res);
                } else {
                    log.warn("Rate limit hit for distributed key={} path={}", key, path);
                    meterRegistry.counter("gateway.rate_limit.blocked", "ip", ip, "path", path).increment();
                    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\": \"Too many requests\", \"message\": \"Rate limit exceeded.\"}");
                }
            }
        }

    private BucketConfiguration getConfiguration(boolean authPath) {
        Bandwidth limit = authPath
                ? Bandwidth.builder()
                    .capacity(authCapacity)
                    .refillIntervally(authRefill, Duration.ofSeconds(authPeriodSec))
                    .build()
                : Bandwidth.builder()
                    .capacity(defaultCapacity)
                    .refillIntervally(defaultRefill, Duration.ofSeconds(defaultPeriodSec))
                    .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    private static String clientIp(HttpServletRequest req) {
        Object trustedIp = req.getAttribute(ForwardedIpTrustFilter.CLIENT_IP_ATTRIBUTE);
        return trustedIp instanceof String ip && !ip.isBlank() ? ip : req.getRemoteAddr();
    }
}
