package com.project.order.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Document(collection = "orders")
@CompoundIndex(
        name = "user_idempotency_key_unique",
        def = "{'userId': 1, 'idempotencyKey': 1}",
        unique = true,
        partialFilter = "{'idempotencyKey': {'$type': 'string'}}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    private String id;
    @Version
    private Long version;
    private String orderNumber;
    private UUID userId;
    private String idempotencyKey;
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
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private SagaState sagaState;
    private List<OutboxEvent> outboxEvents;
}
