package com.project.payment.controller;

import com.project.common.constant.Permissions;
import com.project.common.dto.ApiResponse;
import com.project.common.security.CurrentUser;
import com.project.common.security.HmacSignatureVerifier;
import com.project.payment.dto.PaymentRequest;
import com.project.payment.dto.PaymentResponse;
import com.project.payment.dto.PaymentWebhookRequest;
import com.project.payment.dto.RefundRequest;
import com.project.payment.exception.PaymentException;
import com.project.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${payment.webhook.secret:}")
    private String webhookSecret;
    @Value("${payment.webhook.tolerance-seconds:300}")
    private long webhookToleranceSeconds;

    // ---- Customer-initiated payment flows ----

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_PROCESS + "') or hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        UUID userId = CurrentUser.requireId();
        String email = CurrentUser.email().orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.created(paymentService.createPayment(request, userId, email, idempotencyKey)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getUserPayments(CurrentUser.requireId())));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String paymentId) {
        PaymentResponse p = paymentService.getPayment(paymentId);
        if (!CurrentUser.isAdmin() && !p.getUserId().equals(CurrentUser.requireId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ApiResponse.success(p));
    }

    @GetMapping("/reference/{reference}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByReference(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByReference(reference)));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByOrderId(orderId)));
    }

    // ---- Process / refund — admins or system only ----

    @PostMapping("/{paymentId}/process")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_PROCESS + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(@PathVariable String paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.processPayment(paymentId)));
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_REFUND + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.refundPayment(paymentId, request.reason(), request.amount(), idempotencyKey)));
    }

    // ---- Webhook (in-house, HMAC-signed) ----

    /**
     * Verifies an in-house webhook (e.g., test/demo flow). Production flows for Stripe
     * use {@code /webhook/stripe} which calls Stripe SDK signature verification. The
     * raw body is signed with PAYMENT_WEBHOOK_SECRET and delivered as
     * {@code X-Webhook-Signature: t=<unix>,v1=<hex-hmac>}.
     */
    @PostMapping(value = "/webhook")
    public ResponseEntity<Void> handleWebhook(
            HttpServletRequest request,
            @RequestBody String rawBody) throws IOException {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook received but PAYMENT_WEBHOOK_SECRET is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        String signature = HmacSignatureVerifier.extractSignatureHeader(request);
        if (signature == null) {
            log.warn("Webhook missing signature header from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!HmacSignatureVerifier.verify(webhookSecret, signature, rawBody, webhookToleranceSeconds)) {
            log.warn("Webhook signature verification failed from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Parse and dispatch
        PaymentWebhookRequest payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, PaymentWebhookRequest.class);
        } catch (Exception e) {
            throw new PaymentException("Invalid webhook payload");
        }
        paymentService.handlePaymentWebhook(payload.getPaymentReference(), payload);
        return ResponseEntity.ok().build();
    }
}
