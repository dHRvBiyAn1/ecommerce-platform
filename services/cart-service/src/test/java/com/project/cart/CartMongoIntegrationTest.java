package com.project.cart;

import com.project.cart.client.CouponClient;
import com.project.cart.client.CouponValidationResponse;
import com.project.cart.client.ProductClient;
import com.project.cart.client.ProductSummary;
import com.project.cart.dto.AddCartItemRequest;
import com.project.cart.dto.ApplyCouponRequest;
import com.project.cart.dto.UpdateQuantityRequest;
import com.project.cart.model.Cart;
import com.project.cart.repository.CartRepository;
import com.project.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused",
        "service.auth.enabled=true",
        "service.auth.token-uri=http://localhost/unused/token",
        "service.auth.client-id=cart-service",
        "service.auth.client-secret=test-secret",
        "service.auth.scope=coupons.read"
})
class CartMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CouponClient couponClient;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @BeforeEach
    void clearCarts() {
        cartRepository.deleteAll();
    }

    @Test
    void mongoReloadPreservesProductSnapshotAndCouponInvalidationAfterQuantityChange() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(productClient.getProduct("product-1")).thenReturn(new ProductSummary(
                "product-1", "SKU-1", "Snapshot product", List.of("image-1"),
                new BigDecimal("125.50"), true));
        when(couponClient.validate(any())).thenReturn(CouponValidationResponse.builder()
                .valid(true)
                .code("SAVE10")
                .discountAmount(new BigDecimal("10.00"))
                .build());

        cartService.addItem(userId, new AddCartItemRequest("product-1", 2));
        cartService.applyCoupon(userId, new ApplyCouponRequest("SAVE10"));

        Cart couponReloaded = cartRepository.findByUserId(userId).orElseThrow();
        assertThat(couponReloaded.getAppliedCouponCode()).isEqualTo("SAVE10");
        assertThat(couponReloaded.getAppliedDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(couponReloaded.getCreatedAt()).isNotNull();
        assertThat(couponReloaded.getUpdatedAt()).isNotNull();

        LocalDateTime historicalUpdatedAt = couponReloaded.getUpdatedAt().minusSeconds(5);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(couponReloaded.getId())),
                new Update().set("updatedAt", historicalUpdatedAt), Cart.class);
        Cart baseline = cartRepository.findByUserId(userId).orElseThrow();
        assertThat(baseline.getAppliedCouponCode()).isEqualTo("SAVE10");
        assertThat(baseline.getAppliedDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(baseline.getUpdatedAt()).isEqualTo(historicalUpdatedAt);

        cartService.updateQuantity(userId, "product-1", new UpdateQuantityRequest(3));

        Cart reloaded = cartRepository.findByUserId(userId).orElseThrow();

        assertThat(reloaded.getCreatedAt()).isEqualTo(baseline.getCreatedAt());
        assertThat(reloaded.getUpdatedAt()).isAfter(baseline.getUpdatedAt());
        assertThat(reloaded.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo("product-1");
            assertThat(item.getSku()).isEqualTo("SKU-1");
            assertThat(item.getProductName()).isEqualTo("Snapshot product");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("125.50");
            assertThat(item.getQuantity()).isEqualTo(3);
        });
        assertThat(reloaded.getAppliedCouponCode()).isNull();
        assertThat(reloaded.getAppliedDiscountAmount()).isNull();
        assertThat(cartService.getMyCart(userId).subtotal()).isEqualByComparingTo("376.50");
    }
}
