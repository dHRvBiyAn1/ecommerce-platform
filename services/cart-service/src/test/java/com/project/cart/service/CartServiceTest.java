package com.project.cart.service;

import com.project.cart.client.CouponClient;
import com.project.cart.client.ProductClient;
import com.project.cart.client.ProductClientFallbackFactory;
import com.project.cart.generated.integration.product.model.ProductResponse;
import com.project.cart.generated.model.AddCartItemRequest;
import com.project.cart.generated.model.CartResponse;
import com.project.cart.generated.model.UpdateQuantityRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
        cartService = new CartService(cartRepository, couponClient, productClient, cartMapper,
                new CartCouponPersistenceService(cartRepository));
    }

    @Test
    void addItemPersistsAuthoritativeProductSnapshotAndDoesNotLeakCartItem() {
        UUID userId = UUID.randomUUID();
        ProductResponse product = product("product-1", "REAL-SKU", "Real product",
                List.of("first-image", "second-image"), new BigDecimal("125.50"), true);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(productClient.getProduct("product-1")).thenReturn(product);
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(userId,
                new AddCartItemRequest().productId("product-1").quantity(2));

        ArgumentCaptor<Cart> savedCart = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(savedCart.capture());
        CartItem savedItem = savedCart.getValue().getItems().get(0);
        assertEquals("REAL-SKU", savedItem.getSku());
        assertEquals("Real product", savedItem.getProductName());
        assertEquals(new BigDecimal("125.50"), savedItem.getUnitPrice());
        assertEquals("first-image", savedItem.getImageUrl());
        assertEquals(2, savedItem.getQuantity());
        assertInstanceOf(com.project.cart.generated.model.CartItem.class, response.getItems().get(0));
    }

    @Test
    void addItemKeepsImageNullWhenProductHasNoImages() {
        UUID userId = UUID.randomUUID();
        when(productClient.getProduct("no-image")).thenReturn(
                product("no-image", "SKU", "No image", List.of(), BigDecimal.TEN, true));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(userId,
                new AddCartItemRequest().productId("no-image").quantity(1));

        assertNull(response.getItems().get(0).getImageUrl());
    }

    @Test
    void addingExistingItemIncrementsQuantityRefreshesSnapshotAndInvalidatesCoupon() {
        UUID userId = UUID.randomUUID();
        CartItem existingItem = CartItem.builder()
                .productId("product-1")
                .sku("OLD-SKU")
                .productName("Old name")
                .imageUrl("old-image")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2)
                .build();
        Cart cart = Cart.builder()
                .userId(userId)
                .currency("INR")
                .items(new ArrayList<>(List.of(existingItem)))
                .appliedCouponCode("SAVE10")
                .appliedDiscountAmount(new BigDecimal("10.00"))
                .build();
        ProductResponse refreshedProduct = product("product-1", "NEW-SKU", "Updated name",
                List.of("new-first-image", "new-second-image"), new BigDecimal("125.00"), true);
        when(productClient.getProduct("product-1")).thenReturn(refreshedProduct);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(userId,
                new AddCartItemRequest().productId("product-1").quantity(3));

        ArgumentCaptor<Cart> savedCart = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, times(1)).save(savedCart.capture());
        CartItem savedItem = savedCart.getValue().getItems().getFirst();
        assertEquals(5, savedItem.getQuantity());
        assertEquals("NEW-SKU", savedItem.getSku());
        assertEquals("Updated name", savedItem.getProductName());
        assertEquals("new-first-image", savedItem.getImageUrl());
        assertEquals(new BigDecimal("125.00"), savedItem.getUnitPrice());
        assertNull(savedCart.getValue().getAppliedCouponCode());
        assertNull(savedCart.getValue().getAppliedDiscountAmount());
        assertNull(response.getAppliedCouponCode());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getAppliedDiscountAmount());
    }

    @Test
    void addItemRejectsInactiveProduct() {
        UUID userId = UUID.randomUUID();
        when(productClient.getProduct("inactive")).thenReturn(
                product("inactive", "SKU", "Hidden", List.of(), BigDecimal.TEN, false));

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.addItem(userId,
                        new AddCartItemRequest().productId("inactive").quantity(1)));
    }

    @Test
    void directProduct404BecomesStableNotFoundWithoutLoadingOrSavingCart() {
        UUID userId = UUID.randomUUID();
        when(productClient.getProduct("missing")).thenThrow(feignFailure(404));

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> cartService.addItem(userId, new AddCartItemRequest().productId("missing").quantity(1)));

        assertEquals("RESOURCE_NOT_FOUND", error.getCode());
        assertEquals("Product not found: missing", error.getMessage());
        verify(cartRepository, org.mockito.Mockito.never()).findByUserId(any());
        verify(cartRepository, org.mockito.Mockito.never()).save(any(Cart.class));
    }

    @Test
    void unexpectedProductClientFailureBecomesUnavailableAndPreservesCause() {
        UUID userId = UUID.randomUUID();
        IllegalStateException cause = new IllegalStateException("connection reset");
        when(productClient.getProduct("product-1")).thenThrow(cause);

        ProductServiceUnavailableException error = assertThrows(ProductServiceUnavailableException.class,
                () -> cartService.addItem(userId,
                        new AddCartItemRequest().productId("product-1").quantity(1)));

        assertEquals("PRODUCT_SERVICE_UNAVAILABLE", error.getCode());
        assertEquals("Product service is temporarily unavailable", error.getMessage());
        assertEquals(cause, error.getCause());
        verify(cartRepository, org.mockito.Mockito.never()).findByUserId(any());
        verify(cartRepository, org.mockito.Mockito.never()).save(any(Cart.class));
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
                () -> cartService.addItem(userId,
                        new AddCartItemRequest().productId("missing-response").quantity(1)));
    }

    @Test
    void getMyCartReturnsAnUnsavedEmptyCartWhenNoCartExists() {
        UUID userId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        CartResponse response = cartService.getMyCart(userId);

        assertEquals(userId, response.getUserId());
        assertTrue(response.getItems().isEmpty());
        assertEquals(0, response.getItemCount());
        assertEquals(new BigDecimal("0.00"), response.getSubtotal());
        assertEquals(new BigDecimal("0.00"), response.getTotal());
        verify(cartRepository, org.mockito.Mockito.never()).save(any(Cart.class));
    }

    @Test
    void updateQuantityChangesExistingLineOrRemovesItWhenQuantityIsZero() {
        UUID userId = UUID.randomUUID();
        CartItem item = pricedItem("product-1", 2, "10.00");
        Cart cart = cartWithCoupon(userId, item);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse updated = cartService.updateQuantity(userId, "product-1",
                new UpdateQuantityRequest().quantity(4));

        assertEquals(4, item.getQuantity());
        assertEquals(4, updated.getItemCount());
        assertEquals(new BigDecimal("40.00"), updated.getSubtotal());
        assertNull(cart.getAppliedCouponCode());
        assertNull(cart.getAppliedDiscountAmount());

        CartResponse removed = cartService.updateQuantity(userId, "product-1",
                new UpdateQuantityRequest().quantity(0));

        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, removed.getItemCount());
        assertEquals(new BigDecimal("0.00"), removed.getTotal());
        verify(cartRepository, times(2)).save(cart);
    }

    @Test
    void updateQuantityRequiresAnExistingCartAndLine() {
        UUID userId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.updateQuantity(userId, "product-1",
                new UpdateQuantityRequest().quantity(1)));

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        assertThrows(ResourceNotFoundException.class, () -> cartService.updateQuantity(userId, "product-1",
                new UpdateQuantityRequest().quantity(1)));
        verify(cartRepository, org.mockito.Mockito.never()).save(any(Cart.class));
    }

    @Test
    void removeItemIsIdempotentAndInvalidatesCouponOnlyWhenItRemovesALine() {
        UUID userId = UUID.randomUUID();
        CartItem item = pricedItem("product-1", 2, "10.00");
        Cart cart = cartWithCoupon(userId, item);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse unchanged = cartService.removeItem(userId, "missing");

        assertEquals(2, unchanged.getItemCount());
        assertEquals("SAVE10", unchanged.getAppliedCouponCode());
        verify(cartRepository, org.mockito.Mockito.never()).save(any(Cart.class));

        CartResponse removed = cartService.removeItem(userId, "product-1");

        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, removed.getItemCount());
        assertNull(cart.getAppliedCouponCode());
        assertNull(cart.getAppliedDiscountAmount());
        verify(cartRepository).save(cart);
    }

    @Test
    void clearAndRemoveCouponPersistTheirExpectedCartState() {
        UUID userId = UUID.randomUUID();
        Cart cart = cartWithCoupon(userId, pricedItem("product-1", 2, "10.00"));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse cleared = cartService.clear(userId);

        assertTrue(cart.getItems().isEmpty());
        assertNull(cleared.getAppliedCouponCode());
        assertEquals(new BigDecimal("0.00"), cleared.getAppliedDiscountAmount());
        assertEquals(0, cleared.getItemCount());

        cart.setItems(new ArrayList<>(List.of(pricedItem("product-1", 1, "10.00"))));
        cart.setAppliedCouponCode("SAVE10");
        cart.setAppliedDiscountAmount(new BigDecimal("2.00"));
        CartResponse couponRemoved = cartService.removeCoupon(userId);

        assertEquals(1, couponRemoved.getItemCount());
        assertNull(cart.getAppliedCouponCode());
        assertNull(cart.getAppliedDiscountAmount());
        verify(cartRepository, times(2)).save(cart);
    }

    @Test
    void boundaryModelsAreGeneratedAndCartIsAudited() throws Exception {
        assertTrue(CartResponse.class.getPackageName().endsWith("generated.model"));
        assertTrue(AddCartItemRequest.class.getPackageName().endsWith("generated.model"));
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

    private static ProductResponse product(String id, String sku, String name, List<String> images,
                                            BigDecimal price, boolean active) {
        return new ProductResponse().id(id).sku(sku).name(name).imageUrls(images).price(price).active(active);
    }

    private static CartItem pricedItem(String productId, int quantity, String price) {
        return CartItem.builder().productId(productId).quantity(quantity).unitPrice(new BigDecimal(price)).build();
    }

    private static Cart cartWithCoupon(UUID userId, CartItem item) {
        return Cart.builder().userId(userId).currency("INR").items(new ArrayList<>(List.of(item)))
                .appliedCouponCode("SAVE10").appliedDiscountAmount(new BigDecimal("2.00")).build();
    }
}
