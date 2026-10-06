package com.project.payment.application.mapper;

import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentMapper {

    public com.project.payment.api.dto.request.PaymentRequest toDomain(
            com.project.payment.generated.model.PaymentRequest request) {
        return new com.project.payment.api.dto.request.PaymentRequest(
                request.getOrderId(), request.getOrderNumber(), request.getPaymentMethod(),
                request.getAmount(), request.getCurrency(), request.getDescription());
    }

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

    public com.project.payment.generated.model.PaymentResponse toApi(PaymentResponse response) {
        return new com.project.payment.generated.model.PaymentResponse()
                .id(response.id())
                .paymentReference(response.paymentReference())
                .orderId(response.orderId())
                .orderNumber(response.orderNumber())
                .userId(response.userId())
                .userEmail(response.userEmail())
                .status(response.status() == null ? null
                        : com.project.payment.generated.model.PaymentStatus.valueOf(response.status().name()))
                .paymentMethod(response.paymentMethod())
                .amount(response.amount())
                .refundedAmount(response.refundedAmount())
                .currency(response.currency())
                .transactionId(response.transactionId())
                .gatewayResponse(response.gatewayResponse())
                .failureReason(response.failureReason())
                .retryCount(response.retryCount())
                .description(response.description())
                .createdAt(response.createdAt())
                .updatedAt(response.updatedAt())
                .completedAt(response.completedAt());
    }

    public com.project.payment.api.dto.request.RefundRequest toDomain(
            com.project.payment.generated.model.RefundRequest request) {
        return new com.project.payment.api.dto.request.RefundRequest(request.getReason(), request.getAmount());
    }

    public com.project.payment.generated.model.PaymentInitiationResponse toApi(
            PaymentInitiationResponse response) {
        return new com.project.payment.generated.model.PaymentInitiationResponse(toApi(response.payment()))
                .clientSecret(response.clientSecret());
    }
}
