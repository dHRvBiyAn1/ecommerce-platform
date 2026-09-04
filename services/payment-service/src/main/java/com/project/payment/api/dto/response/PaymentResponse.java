package com.project.payment.api.dto.response;

import com.project.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        String id,
        String paymentReference,
        String orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        PaymentStatus status,
        String paymentMethod,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String currency,
        String transactionId,
        String gatewayResponse,
        String failureReason,
        int retryCount,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {}
