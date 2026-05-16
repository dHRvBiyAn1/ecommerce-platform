package com.project.notification.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PaymentEvent {
    private String type;
    private String paymentId;
    private String orderId;
    private String userId;
    private String email;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
}
