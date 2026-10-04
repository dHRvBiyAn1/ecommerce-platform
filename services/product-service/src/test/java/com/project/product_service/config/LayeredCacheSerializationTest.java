package com.project.product_service.config;

import com.project.product_service.dto.CategoryResponse;
import com.project.product_service.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayeredCacheSerializationTest {
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 10, 4, 12, 0);

    @Test
    void coldCategoryCacheRestoresDtoElementsFromRedisJson() {
        var category = new CategoryResponse("category-1", "Home", "Home goods", null,
                "image.jpg", true, timestamp, timestamp);
        var cache = cacheWithRedisValue("categories", "SimpleKey []", List.of(category));

        assertThat(cache.get("SimpleKey []").get()).isEqualTo(List.of(category));
    }

    @Test
    void coldProductCacheRestoresRecordFieldsFromRedisJson() {
        var product = new ProductResponse("product-1", "SKU-1", "Lamp", "Desk lamp",
                "category-1", "Home", new BigDecimal("1299.50"), 10, List.of("image.jpg"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"), true, "APPROVED", null,
                Map.of("colour", "white", "dimensions", Map.of("height", 25)), timestamp, timestamp);
        var cache = cacheWithRedisValue("products", "product-1", product);

        assertThat(cache.get("product-1", ProductResponse.class)).isEqualTo(product);
    }

    @Test
    void malformedRedisCategoryDataIsACacheMiss() {
        var cache = cacheWithRedisValue("categories", "SimpleKey []",
                List.of(Map.of("id", "category-1", "createdAt", "invalid-date")));

        assertThat(cache.get("SimpleKey []")).isNull();
    }

    private org.springframework.cache.Cache cacheWithRedisValue(String name, String key, Object value) {
        var configuredTemplate = new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        @SuppressWarnings("unchecked")
        var serializer = (RedisSerializer<Object>) configuredTemplate.getValueSerializer();
        Object redisValue = serializer.deserialize(serializer.serialize(value));
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get(name + ":" + key)).thenReturn(redisValue);
        return new LayeredCacheManager(template).getCache(name);
    }
}
