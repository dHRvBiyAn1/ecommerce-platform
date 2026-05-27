package com.project.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Tiny idempotency-key gate backed by Redis. Use for HTTP requests that should
 * not be re-applied (payments, refunds, order creation under retries).
 *
 * <p>Usage:
 * <pre>{@code
 *   if (!idempotency.tryAcquire("payment:" + key, Duration.ofMinutes(10))) {
 *       throw new DuplicateRequestException();
 *   }
 *   // perform side-effect
 * }</pre>
 */
public class IdempotencyService {

    private static final String PREFIX = "idemp:";
    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String key) {
        redis.delete(PREFIX + key);
    }
}
