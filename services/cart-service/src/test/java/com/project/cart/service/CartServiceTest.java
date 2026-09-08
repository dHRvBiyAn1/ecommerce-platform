package com.project.cart.service;

import com.project.cart.client.CouponClient;
import com.project.cart.client.ProductClient;
import com.project.cart.client.ProductClientFallbackFactory;
import com.project.cart.client.ProductSummary;
import com.project.cart.dto.AddCartItemRequest;
import com.project.cart.dto.CartResponse;
import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import com.project.common.exception.ResourceNotFoundException;
import com.project.cart.exception.ProductServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CouponClient couponClient;

    @Mock
    private ProductClient productClient;

    private final com.project.cart.application.mapper.CartMapper cartMapper =
            Mappers.getMapper(com.project.cart.application.mapper.CartMapper.class);

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, couponClient, productClient, cartMapper);
    }

    @Test
    void addItemPersistsAuthoritativeProductSnapshotAndDoesNotLeakCartItem() {
        UUID userId = UUID.randomUUID();
        ProductSummary product = new ProductSummary(
                "product-1", "REAL-SKU", "Real product", List.of("real-image"),
                new BigDecimal("125.50"), true);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(productClient.getProduct("product-1")).thenReturn(product);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(userId, new AddCartItemRequest("product-1", 2));

        ArgumentCaptor<Cart> savedCart = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(savedCart.capture());
        CartItem savedItem = savedCart.getValue().getItems().get(0);
        assertEquals("REAL-SKU", savedItem.getSku());
        assertEquals("Real product", savedItem.getProductName());
        assertEquals(new BigDecimal("125.50"), savedItem.getUnitPrice());
        assertEquals("real-image", savedItem.getImageUrl());
        assertEquals(2, savedItem.getQuantity());
        assertInstanceOf(CartResponse.Item.class, response.items().get(0));
        assertTrue(response.items().stream().noneMatch(CartItem.class::isInstance));
    }

    @Test
    void addItemRejectsInactiveProduct() {
        UUID userId = UUID.randomUUID();
        when(productClient.getProduct("inactive")).thenReturn(
                new ProductSummary("inactive", "SKU", "Hidden", List.of(), BigDecimal.TEN, false));

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.addItem(userId, new AddCartItemRequest("inactive", 1)));
    }

    @Test
    void product404IsMappedToStableNotFoundErrorByFallback() {
        ProductClient fallback = new ProductClientFallbackFactory().create(feignFailure(404));

        assertThrows(ResourceNotFoundException.class, () -> fallback.getProduct("missing"));
    }

    @Test
    void wrappedProduct404IsMappedToStableNotFoundErrorByFallback() {
        ProductClient fallback = new ProductClientFallbackFactory().create(
                new RuntimeException("circuit breaker", feignFailure(404)));

        assertThrows(ResourceNotFoundException.class, () -> fallback.getProduct("missing"));
    }

    @Test
    void productUnavailableIsMappedToStableServiceUnavailableErrorByFallback() {
        ProductClient fallback = new ProductClientFallbackFactory().create(new RuntimeException("timeout"));

        ProductServiceUnavailableException error = assertThrows(ProductServiceUnavailableException.class,
                () -> fallback.getProduct("product-1"));
        assertEquals("PRODUCT_SERVICE_UNAVAILABLE", error.getCode());
    }

    @Test
    void product5xxIsMappedToStableServiceUnavailableErrorByFallback() {
        ProductClient fallback = new ProductClientFallbackFactory().create(feignFailure(503));

        assertThrows(ProductServiceUnavailableException.class, () -> fallback.getProduct("product-1"));
    }

    @Test
    void missingProductResponseIsMappedToServiceUnavailableError() {
        UUID userId = UUID.randomUUID();
        when(productClient.getProduct("missing-response")).thenReturn(null);

        assertThrows(ProductServiceUnavailableException.class,
                () -> cartService.addItem(userId, new AddCartItemRequest("missing-response", 1)));
    }

    @Test
    void boundaryDtosAreRecordsAndCartIsAudited() throws Exception {
        assertTrue(CartResponse.class.isRecord());
        assertTrue(AddCartItemRequest.class.isRecord());
        Class<?> cartAuditingMongoTest = Class.forName("com.project.cart.CartAuditingMongoTest", false,
                Thread.currentThread().getContextClassLoader());
        assertTrue(cartAuditingMongoTest.getAnnotation(Testcontainers.class).disabledWithoutDocker());
        EnabledIfSystemProperty mongoOptIn = cartAuditingMongoTest.getAnnotation(EnabledIfSystemProperty.class);
        assertNotNull(mongoOptIn);
        assertEquals("cart.mongo.integration", mongoOptIn.named());
        assertEquals("true", mongoOptIn.matches());
        assertEquals("productId", ProductClient.class.getDeclaredMethod("getProduct", String.class)
                .getParameters()[0].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class).value());
        assertTrue(Cart.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class));
        assertTrue(Cart.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class));
        assertTrue(CartService.class.getDeclaredMethod("addItem", UUID.class, AddCartItemRequest.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
        assertEquals(LocalDateTime.class, Cart.class.getDeclaredField("updatedAt").getType());
    }

    private FeignException feignFailure(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/products/product-1",
                java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("product-service", Response.builder()
                .status(status).reason("failure").request(request).build());
    }
}
