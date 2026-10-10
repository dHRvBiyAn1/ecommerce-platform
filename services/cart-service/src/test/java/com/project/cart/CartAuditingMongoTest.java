package com.project.cart;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataMongoTest
@EnabledIfSystemProperty(named = "cart.mongo.integration", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class CartAuditingMongoTest {

  @Container static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

  @Autowired private CartRepository cartRepository;

  @DynamicPropertySource
  static void mongoProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
  }

  @Test
  void mongoAuditingPopulatesAndAdvancesCartTimestamps() throws InterruptedException {
    Cart cart = Cart.builder().userId(UUID.randomUUID()).items(new ArrayList<>()).build();
    Cart saved = cartRepository.save(cart);
    assertNotNull(saved.getCreatedAt());
    assertNotNull(saved.getUpdatedAt());

    LocalDateTime initialUpdatedAt = saved.getUpdatedAt();
    Thread.sleep(5);
    saved
        .getItems()
        .add(
            CartItem.builder()
                .productId("product-1")
                .unitPrice(java.math.BigDecimal.TEN)
                .quantity(1)
                .build());
    Cart updated = cartRepository.save(saved);

    assertTrue(updated.getUpdatedAt().isAfter(initialUpdatedAt));
  }
}
