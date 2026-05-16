package com.project.payment.service;

import com.project.payment.dto.PaymentRequest;
import com.project.payment.dto.PaymentResponse;
import com.project.payment.dto.PaymentWebhookRequest;

import java.util.List;
import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request, UUID userId, String userEmail);
    PaymentResponse getPayment(String paymentId);
    PaymentResponse getPaymentByReference(String reference);
    PaymentResponse getPaymentByOrderId(String orderId);
    List<PaymentResponse> getUserPayments(UUID userId);
    PaymentResponse processPayment(String paymentId);
    PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook);
    PaymentResponse refundPayment(String paymentId, String reason);
}
