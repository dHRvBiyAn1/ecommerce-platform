package com.project.payment.dto;

import com.project.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String id;
    private String paymentReference;
    private String orderId;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private PaymentStatus status;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private String gatewayResponse;
    private String failureReason;
    private int retryCount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
