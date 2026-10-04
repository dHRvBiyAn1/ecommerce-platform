package com.project.product_service;

import com.project.common.exception.ResourceNotFoundException;
import com.project.product_service.application.mapper.ProductMapper;
import com.project.product_service.application.validator.CategoryIntegrityValidator;
import com.project.product_service.application.validator.ProductAccessValidator;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.model.Product;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.ProductEventPublisher;
import com.project.product_service.service.ProductService;
import com.project.product_service.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Testcontainers
@DataMongoTest
@Import(ProductMongoIntegrationTest.CacheConfiguration.class)
class ProductMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private ProductRepository products;

    @Autowired
    private com.project.product_service.repository.CategoryRepository categories;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    private Cache productsCache;
    private UUID sellerId;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @BeforeEach
    void setUp() {
        products.deleteAll();
        template.indexOps(Product.class).ensureIndex(new TextIndexDefinitionBuilder()
                .onField("name").onField("description").build());
        sellerId = UUID.randomUUID();
        productsCache = cacheManager.getCache("products");
    }

    @Test
    void productLifecycleKeepsMongoPublicSearchAndCacheStateConsistent() {
        Product pending = new Product();
        pending.setSku("LIFECYCLE-" + UUID.randomUUID());
        pending.setName("Lifecycle search product");
        pending.setDescription("Mongo lifecycle evidence");
        pending.setCategoryId("category-1");
        pending.setPrice(BigDecimal.TEN);
        pending.setStockQuantity(2);
        pending.setSellerId(sellerId);
        Product saved = products.save(pending);
        String productId = saved.getId();

        Product persisted = products.findById(productId).orElseThrow();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(products.findByActiveTrueAndApprovalStatus(ProductApprovalStatus.APPROVED, PageRequest.of(0, 20)))
                .extracting(Product::getId).doesNotContain(productId);
        assertThat(products.searchByText("lifecycle", PageRequest.of(0, 20))).isEmpty();
        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(ResourceNotFoundException.class);

        ProductResponse approved = productService.setApprovalStatus(productId, ProductApprovalStatus.APPROVED,
                UUID.randomUUID(), null);

        assertThat(approved.approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.createdAt()).isNotNull();
        assertThat(approved.updatedAt()).isNotNull();
        assertThat(products.findByActiveTrueAndApprovalStatus(ProductApprovalStatus.APPROVED, PageRequest.of(0, 20)))
                .extracting(Product::getId).contains(productId);
        assertThat(products.searchByText("lifecycle", PageRequest.of(0, 20)))
                .extracting(Product::getId).contains(productId);
        assertThat(productService.getProduct(productId).stockQuantity()).isEqualTo(2);
        assertThat(productsCache.get(productId)).isNotNull();

        productService.updateStock(productId, 7, sellerId, false);

        assertThat(productsCache.get(productId)).isNull();
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(7);
        assertThat(products.searchByText("lifecycle", PageRequest.of(0, 20)))
                .singleElement().extracting(Product::getStockQuantity).isEqualTo(7);
        assertThat(productService.getProduct(productId).stockQuantity()).isEqualTo(7);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheConfiguration {
        @Bean
        ProductServiceImpl productService(ProductRepository products, CategoryRepository categories) {
            return new ProductServiceImpl(products, mock(ProductEventPublisher.class),
                    mock(ProductSearchRepository.class), Mappers.getMapper(ProductMapper.class),
                    new ProductAccessValidator(), new CategoryIntegrityValidator(categories));
        }

        @Bean
        CacheManager cacheManager() {
            var cacheManager = new org.springframework.cache.support.SimpleCacheManager();
            cacheManager.setCaches(java.util.List.of(new org.springframework.cache.concurrent.ConcurrentMapCache("products")));
            return cacheManager;
        }
    }
}
