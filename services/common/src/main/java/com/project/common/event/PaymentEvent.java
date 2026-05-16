package com.project.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentEvent {

    public enum Type {
        INITIATED, PROCESSING, COMPLETED, FAILED, REFUNDED, PARTIALLY_REFUNDED
    }

    public PaymentEvent() {}

    public PaymentEvent(Type type, String paymentId, String orderId, BigDecimal amount) {
        this.type = type;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public PaymentEvent(Type type, String paymentId, String orderId, BigDecimal amount, String currency) {
        this.type = type;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
    }

    public PaymentEvent(Type type, String paymentId, String orderId, BigDecimal amount, String currency, LocalDateTime timestamp) {
        this.type = type;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    private Type type;
    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency = "USD";
    private LocalDateTime timestamp = LocalDateTime.now();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
