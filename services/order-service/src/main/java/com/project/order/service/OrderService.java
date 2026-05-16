package com.project.order.service;

import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail);

    OrderResponse getOrder(String orderId);

    OrderResponse getOrderByNumber(String orderNumber);

    Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable);

    Page<OrderResponse> getAllOrders(Pageable pageable);

    OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request);

    OrderResponse cancelOrder(String orderId, UUID userId);

    void processOrderPayment(String orderId, String paymentId, PaymentStatus paymentStatus);

    Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable);
}
