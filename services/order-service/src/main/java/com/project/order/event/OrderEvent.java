package com.project.order.event;

import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private String eventType;
    private String orderId;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime timestamp;
}
