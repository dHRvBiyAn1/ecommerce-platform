package com.project.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class OrderEvent {

    public enum Type {
        CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED,
        PAYMENT_PENDING, PAYMENT_COMPLETED, PAYMENT_FAILED
    }

    public OrderEvent() {}

    public OrderEvent(Type type, String orderId, UUID userId, BigDecimal totalAmount) {
        this.type = type;
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }

    public OrderEvent(Type type, String orderId, UUID userId, BigDecimal totalAmount, LocalDateTime timestamp) {
        this.type = type;
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
    }

    private Type type;
    private String orderId;
    private UUID userId;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
