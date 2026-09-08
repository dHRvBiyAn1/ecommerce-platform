package com.project.order.service.impl;

import com.project.order.client.CouponClient;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.client.dto.CouponTransitionCommand;
import com.project.order.client.dto.CouponReservationCommand;
import com.project.order.client.dto.CouponValidationResponse;
import com.project.order.client.dto.ProductSummary;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.ShippingAddressRequest;
import com.project.order.exception.OrderValidationException;
import com.project.order.application.mapper.OrderMapper;
import com.project.order.application.mapper.OrderMapperImpl;
import com.project.order.application.validator.OrderRequestValidator;
import com.project.order.constant.OrderPricing;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import com.project.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductClient productClient;
    @Mock private InventoryClient inventoryClient;
    @Mock private CouponClient couponClient;
    @Mock private StringRedisTemplate redis;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, productClient, inventoryClient, couponClient, redis,
                new OrderMapperImpl(), new OrderRequestValidator());
    }

    @Test
    void paymentCompletionCommitsReservedStockBeforeConfirmingTheOrder() {
        Order order = pendingOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

        verify(inventoryClient).commit("product-1", new com.project.order.client.dto.StockReservationCommand(2, "order-1"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void duplicatePaymentCompletionDoesNotRepeatSideEffects() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setPaymentId("payment-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

        verify(inventoryClient, never()).commit(any(), any());
        verify(couponClient, never()).redeem(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void paymentCompletionCommitsReservedCoupon() {
        Order order = pendingOrder();
        order.setCouponCode("SAVE10");
        order.setUserId(java.util.UUID.randomUUID());
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

        verify(couponClient).commit(new CouponTransitionCommand(
                "SAVE10", order.getUserId(), "order-1"));
        verify(couponClient, never()).redeem(any());
    }

    @Test
    void failedPaymentReleasesReservedCoupon() {
        Order order = pendingOrder();
        order.setCouponCode("SAVE10");
        order.setUserId(java.util.UUID.randomUUID());
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.onPaymentResult("order-1", "payment-1", PaymentStatus.FAILED);

        verify(couponClient).release(new CouponTransitionCommand(
                "SAVE10", order.getUserId(), "order-1"));
    }

    @Test
    void orderCreationReservesCouponForThePersistedOrder() {
        UUID userId = UUID.randomUUID();
        stubCheckoutDependencies(userId);

        service.createOrder(orderRequest(), userId, "customer@example.com", null);

        verify(couponClient).reserve(new CouponReservationCommand(
                "SAVE10", userId, "order-1", new BigDecimal("100.00"), "INR"));
    }

    @Test
    void requestAndResponseBoundariesAreImmutableRecords() {
        assertThat(OrderRequest.class.isRecord()).isTrue();
        assertThat(OrderItemRequest.class.isRecord()).isTrue();
        assertThat(ShippingAddressRequest.class.isRecord()).isTrue();
        assertThat(OrderResponse.class.isRecord()).isTrue();
    }

    @Test
    void mapperMapsAnOrderWithoutExposingItsOutboxEvents() {
        Order order = pendingOrder();
        order.setOrderNumber("ORD-1");
        order.setUserId(UUID.randomUUID());
        order.setOutboxEvents(List.of());

        OrderResponse response = new OrderMapperImpl().toResponse(order);

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo("product-1");
    }

    @Test
    void validatorRejectsInvalidAddressBeforePersistence() {
        OrderRequest request = new OrderRequest(
                List.of(new OrderItemRequest("product-1", 1)),
                null,
                new ShippingAddressRequest("", "phone", "street", "city", "state", "zip", "IN"),
                null,
                null,
                "CARD");

        assertThatThrownBy(() -> new OrderRequestValidator().validateCreate(request, UUID.randomUUID()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Shipping address");
    }

    @Test
    void validatorRejectsInvalidItemQuantityBeforePersistence() {
        OrderRequest request = new OrderRequest(
                List.of(new OrderItemRequest("product-1", 0)),
                null, null, null, null, "CARD");

        assertThatThrownBy(() -> new OrderRequestValidator().validateCreate(request, UUID.randomUUID()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessage("Quantity must be at least 1");
    }

    @Test
    void orderUsesCentralPricingConstants() {
        assertThat(OrderPricing.DEFAULT_CURRENCY).isEqualTo("INR");
        assertThat(OrderPricing.TAX_RATE).isEqualByComparingTo("0.18");
        assertThat(OrderPricing.SHIPPING_COST).isEqualByComparingTo("49.00");
        assertThat(OrderPricing.FREE_SHIPPING_THRESHOLD).isEqualByComparingTo("499.00");
    }

    @Test
    void orderTimestampsAreMongoAudited() throws NoSuchFieldException {
        assertThat(Order.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
        assertThat(Order.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
    }

    @Test
    void inventoryFailureReleasesCouponReservation() {
        UUID userId = UUID.randomUUID();
        AtomicReference<Order> persisted = stubCheckoutDependencies(userId);
        when(inventoryClient.reserve("product-1",
                new com.project.order.client.dto.StockReservationCommand(2, "order-1")))
                .thenThrow(new IllegalStateException("inventory unavailable"));
        when(orderRepository.findById("order-1"))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));

        assertThatThrownBy(() -> service.createOrder(
                orderRequest(), userId, "customer@example.com", null))
                .isInstanceOf(OrderValidationException.class)
                .hasMessage("Unable to reserve checkout resources");

        verify(couponClient).release(new CouponTransitionCommand("SAVE10", userId, "order-1"));
    }

    private AtomicReference<Order> stubCheckoutDependencies(UUID userId) {
        ProductSummary product = new ProductSummary();
        product.setId("product-1");
        product.setSku("SKU-1");
        product.setName("Product");
        product.setPrice(new BigDecimal("50.00"));
        product.setActive(true);
        when(productClient.getProduct("product-1")).thenReturn(product);
        when(couponClient.validate(any())).thenReturn(CouponValidationResponse.builder()
                .valid(true)
                .discountAmount(new BigDecimal("10.00"))
                .build());
        AtomicReference<Order> savedOrder = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-1");
            savedOrder.set(order);
            return order;
        });
        return savedOrder;
    }

    private OrderRequest orderRequest() {
        return new OrderRequest(
                List.of(new OrderItemRequest("product-1", 2)),
                "SAVE10", null, null, null, "CARD");
    }

    private Order pendingOrder() {
        OrderItem item = OrderItem.builder().productId("product-1").quantity(2).build();
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setItems(List.of(item));
        return order;
    }
}
