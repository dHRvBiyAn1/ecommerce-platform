package com.project.order.dto;

import com.project.order.model.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private String id;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currency;
    private ShippingAddress shippingAddress;
    private BillingAddress billingAddress;
    private String paymentId;
    private String paymentMethod;
    private PaymentStatus paymentStatus;
    private String couponCode;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
}
