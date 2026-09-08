package com.project.order.dto;

import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    String id,
    String orderNumber,
    UUID userId,
    String userEmail,
    OrderStatus status,
    List<OrderItemResponse> items,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal shippingCost,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    String currency,
    ShippingAddressResponse shippingAddress,
    BillingAddressResponse billingAddress,
    String paymentId,
    String paymentMethod,
    PaymentStatus paymentStatus,
    String couponCode,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime paidAt,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    LocalDateTime cancelledAt
) {
    public record OrderItemResponse(
            String productId,
            String sku,
            String productName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal totalPrice
    ) {}

    public record ShippingAddressResponse(
            String fullName,
            String phone,
            String street,
            String city,
            String state,
            String zipCode,
            String country
    ) {}

    public record BillingAddressResponse(
            String fullName,
            String phone,
            String street,
            String city,
            String state,
            String zipCode,
            String country
    ) {}
}
