package com.project.order.service;

import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail, String idempotencyKey);

    OrderResponse getOrder(String orderId);

    OrderResponse getOrderByNumber(String orderNumber);

    Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable);

    Page<OrderResponse> getAllOrders(Pageable pageable);

    Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable);

    OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request);

    OrderResponse cancelOrder(String orderId, UUID userId, boolean isAdmin);

    /** Called by the payment-events consumer when payment status changes. */
    void onPaymentResult(String orderId, String paymentId, com.project.order.model.PaymentStatus paymentStatus);
}
