package com.project.payment.application.mapper;

import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.generated.model.PaymentStatus;
import com.project.payment.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentMapper {
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .orderId(payment.getOrderId())
                .orderNumber(payment.getOrderNumber())
                .userId(payment.getUserId())
                .userEmail(payment.getUserEmail())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .transactionId(payment.getTransactionId())
                .gatewayResponse(payment.getGatewayResponse())
                .failureReason(payment.getFailureReason())
                .retryCount(payment.getRetryCount())
                .description(payment.getDescription())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .completedAt(payment.getCompletedAt())
                .status(payment.getStatus() == null ? null : PaymentStatus.valueOf(payment.getStatus().name()))
                .refundedAmount(payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount());
    }
}
