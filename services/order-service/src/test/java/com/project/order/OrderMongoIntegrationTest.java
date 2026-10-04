package com.project.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.project.order.application.mapper.OrderMapperImpl;
import com.project.order.application.validator.OrderRequestValidator;
import com.project.order.client.CouponClient;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.client.dto.ProductSummary;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.model.Order;
import com.project.order.model.OrderStatus;
import com.project.order.repository.OrderRepository;
import com.project.order.service.impl.OrderServiceImpl;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataMongoTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.data.mongodb.auto-index-creation=true",
        "spring.data.mongodb.uuid-representation=standard"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderMongoIntegrationTest {

    private static final String DATABASE = "order-lifecycle-" + UUID.randomUUID();

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl(DATABASE));
    }

    @Autowired
    private OrderRepository orders;

    @BeforeEach
    void clearOrders() {
        orders.deleteAll();
    }

    @Test
    void freshServiceRecoversTheRealDuplicateKeyRaceAsOnePersistedOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        ProductClient products = mock(ProductClient.class);
        InventoryClient inventory = mock(InventoryClient.class);
        CouponClient coupons = mock(CouponClient.class);
        CountDownLatch bothPassedPreRead = new CountDownLatch(2);
        CountDownLatch releaseCreates = new CountDownLatch(1);
        when(products.getProduct("product-1")).thenAnswer(invocation -> {
            bothPassedPreRead.countDown();
            if (!releaseCreates.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Both service instances did not pass the idempotency pre-read");
            }
            return product();
        });

        try (MongoClient restartedClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build())) {
            OrderRepository restarted = new MongoRepositoryFactory(
                    new MongoTemplate(restartedClient, DATABASE)).getRepository(OrderRepository.class);
            OrderServiceImpl firstService = service(orders, products, inventory, coupons);
            OrderServiceImpl restartedService = service(restarted, products, inventory, coupons);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var first = workers.submit(() -> firstService.createOrder(
                        request(), userId, "customer@example.com", "checkout-1"));
                var replay = workers.submit(() -> restartedService.createOrder(
                        request(), userId, "customer@example.com", "checkout-1"));
                assertThat(bothPassedPreRead.await(5, TimeUnit.SECONDS)).isTrue();
                releaseCreates.countDown();

                OrderResponse firstResponse = first.get(10, TimeUnit.SECONDS);
                OrderResponse replayResponse = replay.get(10, TimeUnit.SECONDS);
                assertThat(replayResponse.id()).isEqualTo(firstResponse.id());
                assertThat(restarted.count()).isEqualTo(1);
                assertThat(restarted.findByUserIdAndIdempotencyKey(userId, "checkout-1"))
                        .get().satisfies(persisted -> {
                            assertThat(persisted.getId()).isEqualTo(firstResponse.id());
                            assertThat(persisted.getUserId()).isEqualTo(userId);
                            assertThat(persisted.getIdempotencyKey()).isEqualTo("checkout-1");
                        });
            } finally {
                releaseCreates.countDown();
                workers.shutdownNow();
            }
        }
    }

    @Test
    void absentKeysAndSameKeyForDifferentCustomersRemainIndependent() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();

        orders.insert(order("missing-1", firstUser, null));
        orders.insert(order("missing-2", firstUser, null));
        orders.insert(order("first-user", firstUser, "checkout-2"));
        orders.insert(order("second-user", secondUser, "checkout-2"));

        assertThat(orders.count()).isEqualTo(4);
    }

    private static Order order(String suffix, UUID userId, String idempotencyKey) {
        return Order.builder()
                .orderNumber("ORD-" + suffix)
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .status(OrderStatus.PENDING)
                .build();
    }

    private static OrderServiceImpl service(OrderRepository repository, ProductClient products,
                                            InventoryClient inventory, CouponClient coupons) {
        return new OrderServiceImpl(repository, products, inventory, coupons,
                new OrderMapperImpl(), new OrderRequestValidator(), new ObjectMapper().findAndRegisterModules());
    }

    private static OrderRequest request() {
        return new OrderRequest(List.of(new OrderItemRequest("product-1", 1)),
                null, null, null, null, "CARD");
    }

    private static ProductSummary product() {
        ProductSummary product = new ProductSummary();
        product.setId("product-1");
        product.setSku("SKU-1");
        product.setName("Product");
        product.setPrice(new BigDecimal("10.00"));
        product.setActive(true);
        return product;
    }
}
