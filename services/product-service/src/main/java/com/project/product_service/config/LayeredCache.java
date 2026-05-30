package com.project.product_service.config;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class LayeredCache implements Cache {

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<Object, Object> l1Cache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;

    public LayeredCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> l1Cache,
                        RedisTemplate<String, Object> redisTemplate, long ttlSeconds) {
        this.name = name;
        this.l1Cache = l1Cache;
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return l1Cache;
    }

    @Override
    public ValueWrapper get(Object key) {
        if (key == null) return null;
        
        // 1. Read L1 (Caffeine)
        Object value = l1Cache.getIfPresent(key);
        if (value != null) {
            return new SimpleValueWrapper(value);
        }

        // 2. Read L2 (Redis)
        String redisKey = buildRedisKey(key);
        try {
            value = redisTemplate.opsForValue().get(redisKey);
            if (value != null) {
                l1Cache.put(key, value); // Load into L1 cache for subsequent reads
                return new SimpleValueWrapper(value);
            }
        } catch (Exception e) {
            // Fail-silent on Redis connectivity errors so DB fallback still works
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        return wrapper != null ? type.cast(wrapper.get()) : null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }

        // Key-level synchronization to prevent Cache Stampede
        synchronized (this) {
            wrapper = get(key);
            if (wrapper != null) {
                return (T) wrapper.get();
            }
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (key == null || value == null) return;
        
        l1Cache.put(key, value);
        
        String redisKey = buildRedisKey(key);
        try {
            redisTemplate.opsForValue().set(redisKey, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Fail-silent on Redis connectivity errors
        }
    }

    @Override
    public void evict(Object key) {
        if (key == null) return;
        
        l1Cache.invalidate(key);
        
        String redisKey = buildRedisKey(key);
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            // Fail-silent on Redis connectivity errors
        }
    }

    @Override
    public void clear() {
        l1Cache.invalidateAll();
    }

    private String buildRedisKey(Object key) {
        return name + ":" + key.toString();
    }
}
