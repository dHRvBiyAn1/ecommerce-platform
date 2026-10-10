package com.project.product_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.project.product_service.generated.model.CategoryResponse;
import com.project.product_service.generated.model.ProductApprovalStatus;
import com.project.product_service.generated.model.ProductResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;

class LayeredCacheSerializationTest {
  private final LocalDateTime timestamp = LocalDateTime.of(2026, 10, 4, 12, 0);

  @Test
  void coldCategoryCacheRestoresDtoElementsFromRedisJson() {
    var category =
        new CategoryResponse()
            .id("category-1")
            .name("Home")
            .description("Home goods")
            .imageUrl("image.jpg")
            .active(true)
            .createdAt(timestamp)
            .updatedAt(timestamp);
    var cache = cacheWithRedisValue("categories", "SimpleKey []", List.of(category));

    assertThat(cache.get("SimpleKey []").get()).isEqualTo(List.of(category));
  }

  @Test
  void coldProductCacheRestoresRecordFieldsFromRedisJson() {
    var product =
        new ProductResponse()
            .id("product-1")
            .sku("SKU-1")
            .name("Lamp")
            .description("Desk lamp")
            .categoryId("category-1")
            .categoryName("Home")
            .price(new BigDecimal("1299.50"))
            .stockQuantity(10)
            .imageUrls(List.of("image.jpg"))
            .sellerId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .active(true)
            .approvalStatus(ProductApprovalStatus.APPROVED)
            .rejectionReason(null)
            .attributes(Map.of("colour", "white", "dimensions", Map.of("height", 25)))
            .createdAt(timestamp)
            .updatedAt(timestamp);
    var cache = cacheWithRedisValue("products", "product-1", product);

    assertThat(cache.get("product-1", ProductResponse.class)).isEqualTo(product);
  }

  @Test
  void coldProductCacheKeepsMissingLegacyCollectionsNull() {
    var cache =
        cacheWithRedisValue("products", "product-1", Map.of("id", "product-1", "name", "Lamp"));

    ProductResponse cached = cache.get("product-1", ProductResponse.class);

    assertThat(cached.getImageUrls()).isNull();
    assertThat(cached.getAttributes()).isNull();
  }

  @Test
  void malformedRedisCategoryDataIsACacheMiss() {
    var cache =
        cacheWithRedisValue(
            "categories",
            "SimpleKey []",
            List.of(Map.of("id", "category-1", "createdAt", "invalid-date")));

    assertThat(cache.get("SimpleKey []")).isNull();
  }

  private org.springframework.cache.Cache cacheWithRedisValue(
      String name, String key, Object value) {
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
