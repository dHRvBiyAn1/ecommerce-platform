package com.project.product_service.controller;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.product_service.application.mapper.ProductMapper;
import com.project.product_service.application.validator.CategoryIntegrityValidator;
import com.project.product_service.application.validator.ProductAccessValidator;
import com.project.product_service.config.LayeredCache;
import com.project.product_service.config.SecurityConfig;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.model.Product;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.ProductEventPublisher;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductService;
import com.project.product_service.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProductController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerSecurityTest {

    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    @Test
    void anonymousCallCannotReadAnotherSellersPrivateCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/products/seller/{sellerId}", SELLER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void foreignSellerCannotChangeStockThroughTheRealServiceOwnershipCheck() {
        ProductRepository products = mock(ProductRepository.class);
        Product product = product("product-1", true, ProductApprovalStatus.APPROVED);
        product.setSellerId(SELLER_ID);
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        assertThrows(ForbiddenOperationException.class,
                () -> service(products, mock(ProductSearchRepository.class)).updateStock(
                        "product-1", 7, UUID.randomUUID(), false));
    }

    @Test
    void publicSearchDoesNotRouteThroughTheStaleIndexAndKeepsTheAuthoritativeMongoPage() {
        ProductRepository products = mock(ProductRepository.class);
        ProductSearchRepository search = mock(ProductSearchRepository.class);
        PageRequest pageable = PageRequest.of(0, 2);
        when(search.search("desk", pageable))
                .thenThrow(new AssertionError("public search must not route through the stale index"));
        when(products.searchByText("desk", pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                product("first", true, ProductApprovalStatus.APPROVED),
                product("second", true, ProductApprovalStatus.APPROVED)), pageable, 5));

        Page<ProductResponse> result = service(products, search).searchProducts("desk", pageable);

        assertEquals(List.of("first", "second"), result.getContent().stream().map(ProductResponse::id).toList());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void publicGetRejectsAProductWithoutAnApprovedStatus() {
        ProductRepository products = mock(ProductRepository.class);
        Product legacyProduct = product("legacy", true, null);
        when(products.findById("legacy")).thenReturn(Optional.of(legacyProduct));

        assertThrows(com.project.common.exception.ResourceNotFoundException.class,
                () -> service(products, mock(ProductSearchRepository.class)).getProduct("legacy"));
    }

    @Test
    void publicGetRejectsInactiveAndPendingProducts() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById("inactive")).thenReturn(Optional.of(product("inactive", false, ProductApprovalStatus.APPROVED)));
        when(products.findById("pending")).thenReturn(Optional.of(product("pending", true, ProductApprovalStatus.PENDING)));
        ProductServiceImpl service = service(products, mock(ProductSearchRepository.class));

        assertThrows(com.project.common.exception.ResourceNotFoundException.class, () -> service.getProduct("inactive"));
        assertThrows(com.project.common.exception.ResourceNotFoundException.class, () -> service.getProduct("pending"));
    }

    @Test
    void publicListKeepsTheAuthoritativeTotalWhenVisibleProductsSpanPages() {
        ProductRepository products = mock(ProductRepository.class);
        PageRequest pageable = PageRequest.of(0, 2);
        when(products.findByActiveTrueAndApprovalStatus(ProductApprovalStatus.APPROVED, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        product("first", true, ProductApprovalStatus.APPROVED),
                        product("second", true, ProductApprovalStatus.APPROVED)), pageable, 5));

        Page<ProductResponse> result = service(products, mock(ProductSearchRepository.class))
                .getAllActiveProducts(pageable);

        assertEquals(List.of("first", "second"), result.getContent().stream().map(ProductResponse::id).toList());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void mongoSearchFallbackKeepsTheAuthoritativeTotalWhenVisibleProductsSpanPages() {
        ProductRepository products = mock(ProductRepository.class);
        ProductSearchRepository search = mock(ProductSearchRepository.class);
        PageRequest pageable = PageRequest.of(0, 2);
        when(search.search("desk", pageable)).thenThrow(new IllegalStateException("Elasticsearch unavailable"));
        when(products.searchByText("desk", pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                product("first", true, ProductApprovalStatus.APPROVED),
                product("second", true, ProductApprovalStatus.APPROVED)), pageable, 5));

        Page<ProductResponse> result = service(products, search).searchProducts("desk", pageable);

        assertEquals(List.of("first", "second"), result.getContent().stream().map(ProductResponse::id).toList());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void priceFilterKeepsTheAuthoritativeTotalWhenVisibleProductsSpanPages() {
        ProductRepository products = mock(ProductRepository.class);
        PageRequest pageable = PageRequest.of(0, 2);
        when(products.findByPriceBetweenAndActiveTrue(BigDecimal.ZERO, BigDecimal.TEN, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        product("first", true, ProductApprovalStatus.APPROVED),
                        product("second", true, ProductApprovalStatus.APPROVED)), pageable, 5));

        Page<ProductResponse> result = service(products, mock(ProductSearchRepository.class))
                .getProductsByPriceRange(BigDecimal.ZERO, BigDecimal.TEN, pageable);

        assertEquals(List.of("first", "second"), result.getContent().stream().map(ProductResponse::id).toList());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void clearingTheProductCacheRemovesTheL2ValueBeforeTheNextPublicRead() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        Map<String, Object> l2 = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            l2.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), any(), any(Long.class), any());
        when(values.get(anyString())).thenAnswer(invocation -> l2.get(invocation.getArgument(0)));
        when(redis.keys("products:*")).thenAnswer(invocation -> Set.copyOf(l2.keySet()));
        doAnswer(invocation -> {
            ((java.util.Collection<String>) invocation.getArgument(0)).forEach(l2::remove);
            return null;
        }).when(redis).delete(any(java.util.Collection.class));
        LayeredCache cache = new LayeredCache("products", com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build(), redis, 60);
        cache.put("product-1", "visible");

        cache.clear();

        assertNull(cache.get("product-1"));
    }

    private static ProductServiceImpl service(ProductRepository products, ProductSearchRepository search) {
        return new ProductServiceImpl(products, mock(ProductEventPublisher.class), search,
                Mappers.getMapper(ProductMapper.class), new ProductAccessValidator(),
                new CategoryIntegrityValidator(mock(com.project.product_service.repository.CategoryRepository.class)));
    }

    private static Product product(String id, boolean active, ProductApprovalStatus approvalStatus) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("Desk " + id);
        product.setPrice(BigDecimal.TEN);
        product.setActive(active);
        product.setApprovalStatus(approvalStatus);
        return product;
    }
}
