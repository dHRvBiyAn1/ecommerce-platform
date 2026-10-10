package com.project.product_service.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.project.product_service.generated.model.CategoryResponse;
import com.project.product_service.generated.model.ProductResponse;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

public class LayeredCacheManager implements CacheManager {

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper cacheValueMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());
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
    com.github.benmanes.caffeine.cache.Cache<Object, Object> l1Cache =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10000).build();

    // L2 Cache TTL (Redis): Expires in 24 hours
    long redisTtlSeconds = 86400L;

    // Redis stores plain JSON; restore only the DTO type owned by each cache.
    Function<Object, Object> valueDecoder =
        switch (name) {
          case "products" -> value -> cacheValueMapper.convertValue(value, ProductResponse.class);
          case "categories" ->
              value ->
                  cacheValueMapper.convertValue(
                      value, new TypeReference<List<CategoryResponse>>() {});
          default -> Function.identity();
        };
    return new LayeredCache(name, l1Cache, redisTemplate, redisTtlSeconds, valueDecoder);
  }

  @Override
  public Collection<String> getCacheNames() {
    return List.copyOf(caches.keySet());
  }
}
