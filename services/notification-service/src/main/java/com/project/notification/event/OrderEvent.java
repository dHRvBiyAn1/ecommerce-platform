package com.project.notification.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderEvent {
    private String type;
    private String orderId;
    private String userId;
    private String email;
    private BigDecimal totalAmount;
    private String status;
    private Instant timestamp;
}
