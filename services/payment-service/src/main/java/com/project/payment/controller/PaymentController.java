package com.project.payment.controller;

import com.project.common.constant.Permissions;
import com.project.common.generated.model.ResponseEnvelope;
import com.project.common.security.CurrentUser;
import com.project.common.security.HmacSignatureVerifier;
import com.project.common.web.Responses;
import com.project.payment.application.validator.PaymentAccessValidator;
import com.project.payment.exception.PaymentException;
import com.project.payment.generated.api.PaymentsApi;
import com.project.payment.generated.model.PaymentInitiationResponse;
import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.generated.model.PaymentWebhookRequest;
import com.project.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

  private final PaymentService paymentService;
  private final PaymentAccessValidator accessValidator;

  @Value("${payment.webhook.secret:}")
  private String webhookSecret;

  @Value("${payment.webhook.tolerance-seconds:300}")
  private long webhookToleranceSeconds;

  @Value("${stripe.webhook-secret:}")
  private String stripeWebhookSecret;

  // ---- Customer-initiated payment flows ----

  @Override
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Initiate a customer payment",
      security = @SecurityRequirement(name = "bearerAuth"),
      description =
          "Validates current customer owns order. Returns clientSecret only for first successful "
              + "initiation; idempotency replays return payment state without clientSecret.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Payment initiated"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "Order is not owned by current customer"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Idempotency key conflicts with another order")
  })
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePaymentInitiation>
      initiatePayment(
          com.project.payment.generated.model.PaymentRequest request, String idempotencyKey) {
    UUID userId = CurrentUser.requireId();
    String email = CurrentUser.email().orElse(null);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            initiationEnvelope(
                Responses.created(
                    paymentService.createPayment(request, userId, email, idempotencyKey))));
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePaymentList>
      listMyPayments() {
    List<com.project.payment.generated.model.PaymentResponse> payments =
        paymentService.getUserPayments(CurrentUser.requireId());
    ResponseEnvelope<List<com.project.payment.generated.model.PaymentResponse>> response =
        Responses.success(payments);
    return ResponseEntity.ok(
        new com.project.payment.generated.model.ApiResponsePaymentList()
            .status(response.getStatus())
            .message(response.getMessage())
            .traceId(response.getTraceId())
            .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC))
            .data(response.getData()));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePayment> getPaymentById(
      String paymentId) {
    return accessible(paymentService.getPayment(paymentId));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePayment>
      getPaymentByReference(String reference) {
    return accessible(paymentService.getPaymentByReference(reference));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_READ + "')")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePayment> getPaymentByOrderId(
      String orderId) {
    return accessible(paymentService.getPaymentByOrderId(orderId));
  }

  // ---- Permission-scoped processing and refunds ----

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_PROCESS + "')")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePayment> processPayment(
      String paymentId) {
    return ResponseEntity.ok(
        paymentEnvelope(Responses.success(paymentService.processPayment(paymentId))));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.PAYMENTS_REFUND + "')")
  public ResponseEntity<com.project.payment.generated.model.ApiResponsePayment> refundPayment(
      String paymentId,
      com.project.payment.generated.model.RefundRequest request,
      String idempotencyKey) {
    ResponseEnvelope<PaymentResponse> response =
        Responses.success(
            paymentService.refundPayment(
                paymentId, request.getReason(), request.getAmount(), idempotencyKey));
    return ResponseEntity.ok(paymentEnvelope(response));
  }

  // ---- Webhook (in-house, HMAC-signed) ----

  /**
   * Verifies an in-house webhook (e.g., test/demo flow). Production flows for Stripe use {@code
   * /webhook/stripe} which calls Stripe SDK signature verification. The raw body is signed with
   * PAYMENT_WEBHOOK_SECRET and delivered as {@code X-Webhook-Signature: t=<unix>,v1=<hex-hmac>}.
   */
  @PostMapping(value = "/api/v1/payments/webhook")
  @Operation(
      summary = "Receive verified internal payment webhook",
      description =
          "Public endpoint. HMAC verification is required; duplicate verified events resume one durable transition and outbox publication.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Verified event accepted"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Signature missing or invalid"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "503",
        description = "Webhook secret is unavailable")
  })
  public ResponseEntity<Void> handleWebhook(HttpServletRequest request, @RequestBody String rawBody)
      throws IOException {

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
      payload =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(rawBody, PaymentWebhookRequest.class);
    } catch (Exception e) {
      throw new PaymentException("Invalid webhook payload");
    }
    String eventId = UUID.nameUUIDFromBytes(rawBody.getBytes(StandardCharsets.UTF_8)).toString();
    paymentService.handleVerifiedWebhook(
        "internal", eventId, payload.getStatus(), payload.getPaymentReference(), payload);
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/api/v1/payments/webhook/stripe")
  @Operation(
      summary = "Receive verified Stripe payment webhook",
      description =
          "Public endpoint. Stripe signature verification is required; duplicate verified events resume one durable transition and outbox publication.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Verified event accepted"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Signature or payload is invalid"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "503",
        description = "Webhook secret is unavailable")
  })
  public ResponseEntity<Void> handleStripeWebhook(
      @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader,
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

    if (event.getId() == null
        || event.getId().isBlank()
        || !("payment_intent.succeeded".equals(event.getType())
            || "payment_intent.payment_failed".equals(event.getType()))) {
      return ResponseEntity.badRequest().build();
    }
    Object data = event.getDataObjectDeserializer().getObject().orElse(null);
    if (!(data instanceof PaymentIntent intent)) {
      return ResponseEntity.badRequest().build();
    }
    String paymentReference = intent.getMetadata().get("paymentReference");
    if (paymentReference == null || paymentReference.isBlank() || intent.getId() == null) {
      return ResponseEntity.badRequest().build();
    }
    String status = "payment_intent.succeeded".equals(event.getType()) ? "COMPLETED" : "FAILED";
    String failureReason =
        "FAILED".equals(status)
            ? intent.getLastPaymentError() != null
                ? intent.getLastPaymentError().getMessage()
                : "Payment failed"
            : null;
    PaymentWebhookRequest payload =
        new PaymentWebhookRequest(paymentReference, intent.getId(), status, failureReason);
    paymentService.handleStripeWebhook(event.getId(), event.getType(), paymentReference, payload);

    return ResponseEntity.ok().build();
  }

  private ResponseEntity<com.project.payment.generated.model.ApiResponsePayment> accessible(
      PaymentResponse payment) {
    accessValidator.validateAccess(payment, CurrentUser.requireId(), CurrentUser.isAdmin());
    return ResponseEntity.ok(paymentEnvelope(Responses.success(payment)));
  }

  private com.project.payment.generated.model.ApiResponsePayment paymentEnvelope(
      ResponseEnvelope<PaymentResponse> response) {
    return new com.project.payment.generated.model.ApiResponsePayment()
        .status(response.getStatus())
        .message(response.getMessage())
        .traceId(response.getTraceId())
        .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC))
        .data(response.getData());
  }

  private com.project.payment.generated.model.ApiResponsePaymentInitiation initiationEnvelope(
      ResponseEnvelope<PaymentInitiationResponse> response) {
    return new com.project.payment.generated.model.ApiResponsePaymentInitiation()
        .status(response.getStatus())
        .message(response.getMessage())
        .traceId(response.getTraceId())
        .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC))
        .data(response.getData());
  }
}
