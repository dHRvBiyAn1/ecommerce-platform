package com.project.product_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LayeredCacheManager implements CacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    public LayeredCacheManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::createLayeredCache);
    }

    private Cache createLayeredCache(String name) {
        // L1 Cache (Local Caffeine): Expires locally in 5 minutes to prevent stale drift
        com.github.benmanes.caffeine.cache.Cache<Object, Object> l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        
        // L2 Cache TTL (Redis): Expires in 24 hours
        long redisTtlSeconds = 86400L;
        
        return new LayeredCache(name, l1Cache, redisTemplate, redisTtlSeconds);
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }
}
