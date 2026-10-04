package com.project.product_service;

import com.project.product_service.model.Product;
import com.project.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
@EnabledIfEnvironmentVariable(named = "PRODUCT_MONGO_INTEGRATION", matches = "true")
@Import(ProductMongoAuditingTest.AuditingConfiguration.class)
class ProductMongoAuditingTest {

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private ProductRepository productRepository;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        MONGO.start();
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @AfterAll
    static void stopMongo() {
        if (MONGO.isRunning()) {
            MONGO.stop();
        }
    }

    @Test
    void repositorySavePopulatesAuditFieldsAndAdvancesUpdatedAt() throws InterruptedException {
        Product product = new Product();
        product.setSku("AUDIT-1");
        product.setName("Audited product");
        product.setCategoryId("category-1");
        product.setPrice(BigDecimal.ONE);
        product.setStockQuantity(1);

        Product saved = productRepository.save(product);

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        var firstUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(5);
        saved.setStockQuantity(2);
        Product updated = productRepository.save(saved);

        assertNotNull(updated.getCreatedAt());
        assertNotNull(updated.getUpdatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(firstUpdatedAt));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMongoAuditing
    static class AuditingConfiguration {
    }
}
