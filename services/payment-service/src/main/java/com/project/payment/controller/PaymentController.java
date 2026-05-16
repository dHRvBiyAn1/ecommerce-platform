package com.project.payment.controller;

import com.project.payment.dto.PaymentRequest;
import com.project.payment.dto.PaymentResponse;
import com.project.payment.dto.PaymentWebhookRequest;
import com.project.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Email") String userEmail) {
        log.info("REST request to create payment for orderId={}", request.getOrderId());
        PaymentResponse response = paymentService.createPayment(request, userId, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getUserPayments(
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("REST request to get payments for userId={}", userId);
        List<PaymentResponse> responses = paymentService.getUserPayments(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        log.info("REST request to get payment by id={}", paymentId);
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<PaymentResponse> getPaymentByReference(@PathVariable String reference) {
        log.info("REST request to get payment by reference={}", reference);
        PaymentResponse response = paymentService.getPaymentByReference(reference);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable String orderId) {
        log.info("REST request to get payment by orderId={}", orderId);
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/process")
    public ResponseEntity<PaymentResponse> processPayment(@PathVariable String paymentId) {
        log.info("REST request to process payment id={}", paymentId);
        PaymentResponse response = paymentService.processPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<PaymentResponse> handleWebhook(
            @Valid @RequestBody PaymentWebhookRequest webhook) {
        log.info("REST request to handle payment webhook for reference={}",
                webhook.getPaymentReference());
        PaymentResponse response = paymentService.handlePaymentWebhook(
                webhook.getPaymentReference(), webhook);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false, defaultValue = "") String reason) {
        log.info("REST request to refund payment id={}, reason={}", paymentId, reason);
        PaymentResponse response = paymentService.refundPayment(paymentId, reason);
        return ResponseEntity.ok(response);
    }
}
