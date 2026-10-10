package com.project.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.web.Responses;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.exception.PaymentException;
import com.project.payment.generated.integration.order.model.OrderResponse;
import com.project.payment.generated.model.PaymentRequest;
import com.project.payment.generated.model.PaymentWebhookRequest;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentOperation;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentOperationRepository;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

  @Mock private PaymentRepository paymentRepository;

  @Mock private PaymentGateway gateway;

  @Mock private OrderClient orderClient;

  @Mock private PaymentOperationRepository operationRepository;

  @Mock private PaymentOutboxRepository outboxRepository;

  @Mock private WebhookReceiptRepository receiptRepository;

  private PaymentServiceImpl service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(operationRepository.insert(any(PaymentOperation.class)))
        .thenAnswer(
            invocation -> {
              PaymentOperation operation = invocation.getArgument(0);
              operation.setId(UUID.randomUUID().toString());
              return operation;
            });
    lenient()
        .when(receiptRepository.insert(any(com.project.payment.model.WebhookReceipt.class)))
        .thenAnswer(
            invocation -> {
              com.project.payment.model.WebhookReceipt receipt = invocation.getArgument(0);
              receipt.setId("receipt-1");
              return receipt;
            });
    lenient()
        .when(receiptRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new PaymentServiceImpl(
            paymentRepository,
            operationRepository,
            receiptRepository,
            outboxRepository,
            gateway,
            new PaymentMapper(),
            orderClient,
            new PaymentOrderValidator(),
            new PaymentTransitionValidator(),
            new ObjectMapper().findAndRegisterModules());
  }

  @Test
  void cumulativeRefundCannotExceedCapturedAmount() {
    Payment payment = completedPayment("100.00", "30.00");
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

    assertThatThrownBy(
            () ->
                service.refundPayment(
                    "payment-1", "customer request", new BigDecimal("80.00"), null))
        .isInstanceOf(PaymentException.class)
        .hasMessageContaining("remaining refundable amount");
    verify(gateway, never()).refund(any(), any(), any(), any());
  }

  @Test
  void refundingTheExactRemainingAmountMarksPaymentFullyRefunded() {
    Payment payment = completedPayment("100.00", "30.00");
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.refundPayment("payment-1", "customer request", new BigDecimal("70.00"), null);

    verify(gateway).refund(eq(payment), eq(new BigDecimal("70.00")), eq("customer request"), any());
    assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.REFUNDED);
  }

  @Test
  void illegalRefundStateIsRejectedBeforeGatewayInvocation() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("100.00"))
            .refundedAmount(BigDecimal.ZERO)
            .build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    PaymentTransitionValidator transitions =
        org.mockito.Mockito.mock(PaymentTransitionValidator.class);
    doThrow(new PaymentException("illegal transition"))
        .when(transitions)
        .validate(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED);
    service =
        new PaymentServiceImpl(
            paymentRepository,
            operationRepository,
            receiptRepository,
            outboxRepository,
            gateway,
            new PaymentMapper(),
            orderClient,
            new PaymentOrderValidator(),
            transitions,
            new ObjectMapper().findAndRegisterModules());

    assertThatThrownBy(() -> service.refundPayment("payment-1", "customer request", null, null))
        .isInstanceOf(PaymentException.class);

    verify(gateway, never()).refund(any(), any(), any(), any());
  }

  @Test
  void webhookRequiredProviderCannotCompletePaymentThroughProcessEndpoint() {
    Payment payment = Payment.builder().id("payment-1").status(PaymentStatus.PENDING).build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(gateway.requiresVerifiedWebhook()).thenReturn(true);

    assertThatThrownBy(() -> service.processPayment("payment-1"))
        .isInstanceOf(PaymentException.class)
        .hasMessageContaining("verified webhook");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(gateway, never()).confirm(any());
  }

  @Test
  void sandboxProviderRetainsSynchronousProcessing() {
    Payment payment = Payment.builder().id("payment-1").status(PaymentStatus.PENDING).build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.confirm(payment)).thenReturn(true);

    var response = service.processPayment("payment-1");

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.COMPLETED);
    verify(gateway).confirm(payment);
  }

  @Test
  void verifiedWebhookIsTheOnlyPathThatCompletesWebhookRequiredPayment() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.handlePaymentWebhook(
        "PAY-1", new PaymentWebhookRequest("PAY-1", "intent-1", "COMPLETED", null));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    verify(gateway, never()).confirm(any());
  }

  @Test
  void createPaymentUsesAuthoritativeOrderAmountAndCurrency() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-100")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("125.50"))
                    .currency("USD")));
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
    when(paymentRepository.insert(any(Payment.class)))
        .thenAnswer(
            invocation -> {
              Payment payment = invocation.getArgument(0);
              payment.setId("payment-1");
              return payment;
            });
    when(paymentRepository.findById("payment-1"))
        .thenAnswer(
            invocation ->
                Optional.of(
                    Payment.builder()
                        .id("payment-1")
                        .orderId("order-1")
                        .orderNumber("ORD-100")
                        .userId(userId)
                        .status(PaymentStatus.PENDING)
                        .paymentMethod("CARD")
                        .amount(new BigDecimal("125.50"))
                        .currency("USD")
                        .build()));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.createIntent(any(Payment.class)))
        .thenReturn(new PaymentGateway.IntentResult("intent-1", "client-secret"));
    PaymentRequest request =
        new PaymentRequest(
            "order-1", "FAKE-ORDER", "CARD", new BigDecimal("1.00"), "INR", "checkout");

    var response = service.createPayment(request, userId, "customer@example.com", null);

    assertThat(response.getPayment().getOrderNumber()).isEqualTo("ORD-100");
    assertThat(response.getPayment().getAmount()).isEqualByComparingTo("125.50");
    assertThat(response.getPayment().getCurrency()).isEqualTo("USD");
    assertThat(response.getPayment().getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.PENDING);
    assertThat(response.getClientSecret()).isEqualTo("client-secret");
  }

  @Test
  void blankCreateKeyIsReplacedWithGeneratedDurableIdentity() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-100")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
    when(paymentRepository.insert(any(Payment.class)))
        .thenAnswer(
            invocation -> {
              Payment payment = invocation.getArgument(0);
              payment.setId("payment-1");
              return payment;
            });
    when(paymentRepository.findById("payment-1"))
        .thenAnswer(
            invocation ->
                Optional.of(
                    Payment.builder()
                        .id("payment-1")
                        .orderId("order-1")
                        .userId(userId)
                        .status(PaymentStatus.PENDING)
                        .build()));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.createIntent(any()))
        .thenReturn(new PaymentGateway.IntentResult("intent-1", "client-secret"));

    var response =
        service.createPayment(
            new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
            userId,
            "customer@example.com",
            "   ");

    assertThat(response.getClientSecret()).isEqualTo("client-secret");
    org.mockito.ArgumentCaptor<PaymentOperation> operation =
        org.mockito.ArgumentCaptor.forClass(PaymentOperation.class);
    verify(operationRepository).insert(operation.capture());
    assertThat(operation.getValue().getIdempotencyKey()).isNotBlank().isNotEqualTo("   ");
  }

  @Test
  void cancellationIsIdempotentForMissingOrAlreadyCancelledOrders() {
    when(paymentRepository.findByOrderId("missing")).thenReturn(Optional.empty());
    Payment cancelled =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .status(PaymentStatus.CANCELLED)
            .userId(UUID.randomUUID())
            .build();
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(cancelled));

    service.cancelPaymentByOrderId("missing");
    service.cancelPaymentByOrderId("order-1");

    verify(paymentRepository, never()).save(any(Payment.class));
    verify(operationRepository, never()).insert(any(PaymentOperation.class));
  }

  @Test
  void failedVerifiedWebhookPersistsFailureReasonAndOutboxEvent() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.handlePaymentWebhook(
            "PAY-1", new PaymentWebhookRequest("PAY-1", "intent-1", "FAILED", "declined"));

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("declined");
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
  }

  @Test
  void omittedRefundAmountRefundsTheRemainingBalanceWhenNoPriorRefundIsRecorded() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("100.00"))
            .refundedAmount(null)
            .build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.refundPayment("payment-1", "customer request", null, null);

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.REFUNDED);
    assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
    verify(gateway)
        .refund(eq(payment), eq(new BigDecimal("100.00")), eq("customer request"), any());
  }

  @Test
  void partialRefundPreservesTheRemainingBalance() {
    Payment payment = completedPayment("100.00", "0.00");
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.refundPayment(
            "payment-1", "customer request", new BigDecimal("25.00"), "partial-refund");

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.PARTIALLY_REFUNDED);
    assertThat(payment.getRefundedAmount()).isEqualByComparingTo("25.00");
    verify(gateway)
        .refund(
            eq(payment), eq(new BigDecimal("25.00")), eq("customer request"), eq("partial-refund"));
  }

  @Test
  void declinedSandboxConfirmationMarksPaymentFailed() {
    Payment payment = Payment.builder().id("payment-1").status(PaymentStatus.PENDING).build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(gateway.confirm(payment)).thenReturn(false);

    var response = service.processPayment("payment-1");

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Gateway declined");
  }

  @Test
  void customerCannotRefundZeroOrNegativeAmounts() {
    Payment payment = completedPayment("100.00", "0.00");
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

    for (String amount : new String[] {"0.00", "-1.00"}) {
      assertThatThrownBy(
              () ->
                  service.refundPayment(
                      "payment-1", "customer request", new BigDecimal(amount), null))
          .isInstanceOf(PaymentException.class)
          .hasMessageContaining("exceeds the remaining refundable amount");
    }

    verify(gateway, never()).refund(any(), any(), any(), any());
  }

  @Test
  void ambiguousRefundFailureRemainsBlockedForReconciliation() {
    Payment payment = completedPayment("100.00", "0.00");
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.doThrow(new IllegalStateException("provider timeout"))
        .when(gateway)
        .refund(any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                service.refundPayment(
                    "payment-1", "customer request", new BigDecimal("25.00"), "refund-key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider timeout");

    assertThat(payment.getActiveRefundOperationId()).isNotBlank();
    org.mockito.ArgumentCaptor<PaymentOperation> operation =
        org.mockito.ArgumentCaptor.forClass(PaymentOperation.class);
    verify(operationRepository, org.mockito.Mockito.atLeastOnce()).save(operation.capture());
    assertThat(operation.getAllValues())
        .extracting(PaymentOperation::getStatus)
        .contains("RECONCILE");
    verify(gateway)
        .refund(eq(payment), eq(new BigDecimal("25.00")), eq("customer request"), eq("refund-key"));
  }

  @Test
  void pendingPaymentCanBeCancelledAndPublishesItsStateTransition() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .userId(UUID.randomUUID())
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.cancelPaymentByOrderId("order-1");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
  }

  @Test
  void processingPaymentCanBeCancelledByItsOrder() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .userId(UUID.randomUUID())
            .status(PaymentStatus.PROCESSING)
            .build();
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.cancelPaymentByOrderId("order-1");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
  }

  @Test
  void cancelledWebhookAppliesTheLegalTerminalTransitionAndUnknownStatusIsRejected() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.handlePaymentWebhook(
            "PAY-1", new PaymentWebhookRequest("PAY-1", "intent-1", "CANCELLED", null));

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.CANCELLED);
    assertThatThrownBy(
            () ->
                service.handlePaymentWebhook(
                    "PAY-1", new PaymentWebhookRequest("PAY-1", "intent-1", "PROCESSING", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Unknown webhook status: PROCESSING");
  }

  @Test
  void createPaymentReusesExistingPaymentForItsOwnerWithoutCallingGateway() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    Payment existing =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .orderNumber("ORD-1")
            .userId(userId)
            .status(PaymentStatus.PENDING)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();
    when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(existing));

    var response =
        service.createPayment(
            new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
            userId,
            "customer@example.com",
            "create-key");

    assertThat(response.getPayment().getId()).isEqualTo("payment-1");
    assertThat(response.getClientSecret()).isNull();
    verify(gateway, never()).createIntent(any());
  }

  @Test
  void createPaymentRejectsAnExistingPaymentOwnedByAnotherUser() {
    UUID requester = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(requester)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    when(paymentRepository.findByOrderId("order-1"))
        .thenReturn(
            Optional.of(
                Payment.builder()
                    .id("payment-1")
                    .orderId("order-1")
                    .userId(UUID.randomUUID())
                    .status(PaymentStatus.PENDING)
                    .build()));

    assertThatThrownBy(
            () ->
                service.createPayment(
                    new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
                    requester,
                    "customer@example.com",
                    "create-key"))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Payment already exists for this order");

    verify(gateway, never()).createIntent(any());
  }

  @Test
  void stripeFailureEventIsAcceptedAndUnsupportedStripeTransitionIsRejected() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    PaymentWebhookRequest failed =
        new PaymentWebhookRequest("PAY-1", "intent-1", "FAILED", "declined");

    var response =
        service.handleStripeWebhook("evt-failed", "payment_intent.payment_failed", "PAY-1", failed);

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("declined");
    assertThatThrownBy(
            () ->
                service.handleStripeWebhook(
                    "evt-wrong",
                    "payment_intent.payment_failed",
                    "PAY-1",
                    new PaymentWebhookRequest("PAY-1", "intent-1", "COMPLETED", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Unsupported Stripe event type or status");
  }

  @Test
  void stripeSucceededStatusIsCaseInsensitive() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.handleStripeWebhook(
            "evt-lower",
            "payment_intent.succeeded",
            "PAY-1",
            new PaymentWebhookRequest("PAY-1", "intent-1", "succeeded", null));

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.COMPLETED);
  }

  @Test
  void verifiedWebhookRejectsBlankAndMismatchedPaymentReferencesBeforeReceiptWrite() {
    assertThatThrownBy(
            () ->
                service.handleVerifiedWebhook(
                    "internal",
                    "evt-blank",
                    "COMPLETED",
                    " ",
                    new PaymentWebhookRequest("PAY-1", "intent-1", "COMPLETED", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Webhook payment reference mismatch");
    assertThatThrownBy(
            () ->
                service.handleVerifiedWebhook(
                    "internal",
                    "evt-mismatch",
                    "COMPLETED",
                    "PAY-1",
                    new PaymentWebhookRequest("PAY-2", "intent-1", "COMPLETED", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Webhook payment reference mismatch");
    assertThatThrownBy(
            () ->
                service.handleVerifiedWebhook(
                    "internal",
                    "evt-null",
                    "COMPLETED",
                    "PAY-1",
                    new PaymentWebhookRequest(null, "intent-1", "COMPLETED", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Webhook payment reference mismatch");

    verify(receiptRepository, never()).insert(any(com.project.payment.model.WebhookReceipt.class));
  }

  @Test
  void createPaymentRaceReturnsThePersistedPaymentWinner() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    Payment winner =
        Payment.builder()
            .id("payment-winner")
            .orderId("order-1")
            .orderNumber("ORD-1")
            .createOperationId("other-operation")
            .userId(userId)
            .status(PaymentStatus.PENDING)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();
    when(paymentRepository.findByOrderId("order-1"))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(paymentRepository.insert(any(Payment.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("race"));

    var response =
        service.createPayment(
            new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
            userId,
            "customer@example.com",
            "create-key");

    assertThat(response.getPayment().getId()).isEqualTo("payment-winner");
    assertThat(response.getClientSecret()).isNull();
    verify(gateway, never()).createIntent(any());
  }

  @Test
  void createReplayRecoversTransactionAndEmitsItsDurableInitiatedEvent() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    PaymentOperation operation =
        PaymentOperation.builder()
            .id("operation-1")
            .operation("CREATE")
            .userId(userId)
            .idempotencyKey("create-key")
            .orderId("order-1")
            .paymentId("payment-1")
            .status("GATEWAY_STARTED")
            .build();
    when(operationRepository.insert(any(PaymentOperation.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("replay"));
    when(operationRepository.findByOperationAndUserIdAndIdempotencyKey(
            "CREATE", userId, "create-key"))
        .thenReturn(Optional.of(operation));
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .userId(userId)
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.createPayment(
            new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
            userId,
            "customer@example.com",
            "create-key");

    assertThat(response.getPayment().getId()).isEqualTo("payment-1");
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
    verify(gateway, never()).createIntent(any());
  }

  @Test
  void createReplayWithPersistedSnapshotOnlyCompletesItsOutboxPhase() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    PaymentOperation operation =
        PaymentOperation.builder()
            .id("operation-snapshotted")
            .operation("CREATE")
            .userId(userId)
            .idempotencyKey("snapshot-key")
            .orderId("order-1")
            .paymentId("payment-1")
            .status("APPLIED")
            .outboxId("outbox-1")
            .eventType("INITIATED")
            .payload("{\"type\":\"INITIATED\"}")
            .transitionSequence(1L)
            .build();
    when(operationRepository.insert(any(PaymentOperation.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("replay"));
    when(operationRepository.findByOperationAndUserIdAndIdempotencyKey(
            "CREATE", userId, "snapshot-key"))
        .thenReturn(Optional.of(operation));
    when(paymentRepository.findById("payment-1"))
        .thenReturn(
            Optional.of(
                Payment.builder()
                    .id("payment-1")
                    .userId(userId)
                    .status(PaymentStatus.PENDING)
                    .build()));

    service.createPayment(
        new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
        userId,
        "customer@example.com",
        "snapshot-key");

    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
    verify(gateway, never()).createIntent(any());
  }

  @Test
  void createReplayRecoversPersistedGatewayFailureWithoutRetryingTheProvider() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(userId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("100.00"))
                    .currency("USD")));
    PaymentOperation operation =
        PaymentOperation.builder()
            .id("operation-failed")
            .operation("CREATE")
            .userId(userId)
            .idempotencyKey("failed-key")
            .orderId("order-1")
            .paymentId("payment-1")
            .status("GATEWAY_STARTED")
            .build();
    when(operationRepository.insert(any(PaymentOperation.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("replay"));
    when(operationRepository.findByOperationAndUserIdAndIdempotencyKey(
            "CREATE", userId, "failed-key"))
        .thenReturn(Optional.of(operation));
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .orderId("order-1")
            .userId(userId)
            .status(PaymentStatus.FAILED)
            .failureReason("Gateway error")
            .build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.createPayment(
            new PaymentRequest("order-1", null, "CARD", null, null, "checkout"),
            userId,
            "customer@example.com",
            "failed-key");

    assertThat(response.getPayment().getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.FAILED);
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
    verify(gateway, never()).createIntent(any());
  }

  @Test
  void duplicateWebhookReceiptResumesItsTransitionAndRejectsConflictingReuse() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .paymentReference("PAY-1")
            .transactionId("intent-1")
            .status(PaymentStatus.PENDING)
            .build();
    when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(receiptRepository.insert(any(com.project.payment.model.WebhookReceipt.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
    com.project.payment.model.WebhookReceipt receipt =
        com.project.payment.model.WebhookReceipt.builder()
            .provider("internal")
            .eventId("receipt-event")
            .eventType("COMPLETED")
            .paymentReference("PAY-1")
            .build();
    when(receiptRepository.findByProviderAndEventId("internal", "receipt-event"))
        .thenReturn(Optional.of(receipt));
    PaymentWebhookRequest webhook =
        new PaymentWebhookRequest("PAY-1", "intent-1", "COMPLETED", null);

    service.handleVerifiedWebhook("internal", "receipt-event", "COMPLETED", "PAY-1", webhook);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThatThrownBy(
            () ->
                service.handleVerifiedWebhook(
                    "internal",
                    "receipt-event",
                    "FAILED",
                    "PAY-1",
                    new PaymentWebhookRequest("PAY-1", "intent-1", "FAILED", "declined")))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Webhook event ID conflicts with its stored type or payment reference");
  }

  @Test
  void completedRefundReplayReconstructsTheTerminalOutboxEvent() {
    UUID userId = UUID.randomUUID();
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .userId(userId)
            .status(PaymentStatus.PARTIALLY_REFUNDED)
            .amount(new BigDecimal("100.00"))
            .refundedAmount(new BigDecimal("25.00"))
            .lastRefundOperationId("refund-operation")
            .build();
    PaymentOperation operation =
        PaymentOperation.builder()
            .id("refund-operation")
            .operation("REFUND")
            .userId(userId)
            .idempotencyKey("refund-key")
            .paymentId("payment-1")
            .status("COMPLETED")
            .amount(new BigDecimal("25.00"))
            .build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(operationRepository.insert(any(PaymentOperation.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("replay"));
    when(operationRepository.findByOperationAndUserIdAndIdempotencyKey(
            "REFUND", userId, "refund-key"))
        .thenReturn(Optional.of(operation));

    var response =
        service.refundPayment(
            "payment-1", "customer request", new BigDecimal("25.00"), "refund-key");

    assertThat(response.getStatus())
        .isEqualTo(com.project.payment.generated.model.PaymentStatus.PARTIALLY_REFUNDED);
    verify(outboxRepository).insert(any(com.project.payment.model.PaymentOutboxEvent.class));
    verify(gateway, never()).refund(any(), any(), any(), any());
  }

  @Test
  void verifiedWebhookRequiresEventIdAndRejectsStripeStatusTypeMismatches() {
    PaymentWebhookRequest succeeded =
        new PaymentWebhookRequest("PAY-1", "intent-1", "COMPLETED", null);
    assertThatThrownBy(
            () -> service.handleVerifiedWebhook("internal", " ", "COMPLETED", "PAY-1", succeeded))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Webhook event ID is required");

    assertThatThrownBy(
            () ->
                service.handleStripeWebhook(
                    "evt-wrong-status",
                    "payment_intent.succeeded",
                    "PAY-1",
                    new PaymentWebhookRequest("PAY-1", "intent-1", "FAILED", null)))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Unsupported Stripe event type or status");
    assertThatThrownBy(
            () ->
                service.handleStripeWebhook(
                    "evt-wrong-type", "payment_intent.payment_failed", "PAY-1", succeeded))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Unsupported Stripe event type or status");
  }

  @Test
  void missingOrderDetailsAndAlreadyCompletedProcessingAreRejected() {
    UUID userId = UUID.randomUUID();
    when(orderClient.getOrder("missing-order")).thenReturn(null);

    assertThatThrownBy(
            () ->
                service.createPayment(
                    new PaymentRequest("missing-order", null, "CARD", null, null, "checkout"),
                    userId,
                    "customer@example.com",
                    null))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Order details are unavailable");

    Payment completed = Payment.builder().id("payment-1").status(PaymentStatus.COMPLETED).build();
    when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(completed));
    assertThatThrownBy(() -> service.processPayment("payment-1"))
        .isInstanceOf(PaymentException.class)
        .hasMessage("Payment cannot be processed; status=COMPLETED");
  }

  private Payment completedPayment(String amount, String refundedAmount) {
    return Payment.builder()
        .id("payment-1")
        .status(PaymentStatus.PARTIALLY_REFUNDED)
        .amount(new BigDecimal(amount))
        .refundedAmount(new BigDecimal(refundedAmount))
        .build();
  }
}
