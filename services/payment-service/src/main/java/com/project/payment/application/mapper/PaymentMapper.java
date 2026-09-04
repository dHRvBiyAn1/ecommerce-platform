package com.project.payment.application.mapper;

import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getOrderNumber(),
                payment.getUserId(),
                payment.getUserEmail(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount(),
                payment.getCurrency(),
                payment.getTransactionId(),
                payment.getGatewayResponse(),
                payment.getFailureReason(),
                payment.getRetryCount(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getCompletedAt());
    }
}
