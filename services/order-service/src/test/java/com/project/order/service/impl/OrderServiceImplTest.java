package com.project.order.service.impl;

import com.project.order.client.CouponClient;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
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

import static org.assertj.core.api.Assertions.assertThat;
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
        service = new OrderServiceImpl(orderRepository, productClient, inventoryClient, couponClient, redis);
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
