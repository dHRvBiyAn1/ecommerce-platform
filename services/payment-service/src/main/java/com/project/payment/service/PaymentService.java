package com.project.payment.service;

import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.api.dto.response.PaymentInitiationResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentInitiationResponse createPayment(PaymentRequest request, UUID userId, String userEmail, String idempotencyKey);

    PaymentResponse getPayment(String paymentId);

    PaymentResponse getPaymentByReference(String reference);

    PaymentResponse getPaymentByOrderId(String orderId);

    List<PaymentResponse> getUserPayments(UUID userId);

    PaymentResponse processPayment(String paymentId);

    PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook);

    PaymentResponse refundPayment(String paymentId, String reason, BigDecimal amount, String idempotencyKey);

    void cancelPaymentByOrderId(String orderId);
}
