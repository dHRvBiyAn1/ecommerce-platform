package com.project.payment.controller;

import com.project.common.constant.Permissions;
import com.project.common.dto.ApiResponse;
import com.project.common.security.CurrentUser;
import com.project.common.security.HmacSignatureVerifier;
import com.project.payment.application.validator.PaymentAccessValidator;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.api.dto.request.RefundRequest;
import com.project.payment.api.dto.response.PaymentResponse;
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

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAccessValidator accessValidator;

    @Value("${payment.webhook.secret:}")
    private String webhookSecret;
    @Value("${payment.webhook.tolerance-seconds:300}")
    private long webhookToleranceSeconds;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

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
        return accessible(paymentService.getPayment(paymentId));
    }

    @GetMapping("/reference/{reference}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByReference(@PathVariable String reference) {
        return accessible(paymentService.getPaymentByReference(reference));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable String orderId) {
        return accessible(paymentService.getPaymentByOrderId(orderId));
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
        paymentService.handlePaymentWebhook(payload.paymentReference(), payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/webhook/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String rawBody) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.error("Stripe webhook received but stripe.webhook-secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(rawBody, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.warn("Stripe webhook payload invalid");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if ("payment_intent.succeeded".equals(event.getType()) || "payment_intent.payment_failed".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (intent != null) {
                String paymentReference = intent.getMetadata().get("paymentReference");
                String status = "payment_intent.succeeded".equals(event.getType()) ? "COMPLETED" : "FAILED";
                String failureReason = "FAILED".equals(status)
                        ? intent.getLastPaymentError() != null
                            ? intent.getLastPaymentError().getMessage()
                            : "Payment failed"
                        : null;
                PaymentWebhookRequest payload = new PaymentWebhookRequest(
                        paymentReference, intent.getId(), status, failureReason);

                if (payload.paymentReference() != null) {
                    paymentService.handlePaymentWebhook(payload.paymentReference(), payload);
                }
            }
        }
        
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<ApiResponse<PaymentResponse>> accessible(PaymentResponse payment) {
        accessValidator.validateAccess(payment, CurrentUser.requireId(), CurrentUser.isAdmin());
        return ResponseEntity.ok(ApiResponse.success(payment));
    }
}
