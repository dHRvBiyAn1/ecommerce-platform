package com.project.payment.service.impl;

import com.project.payment.exception.PaymentException;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
import com.project.common.dto.ApiResponse;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentOperation;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.repository.PaymentOperationRepository;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
        lenient().when(operationRepository.insert(any(PaymentOperation.class))).thenAnswer(invocation -> {
            PaymentOperation operation = invocation.getArgument(0);
            operation.setId(UUID.randomUUID().toString());
            return operation;
        });
        lenient().when(receiptRepository.insert(any(com.project.payment.model.WebhookReceipt.class))).thenAnswer(invocation -> {
            com.project.payment.model.WebhookReceipt receipt = invocation.getArgument(0);
            receipt.setId("receipt-1");
            return receipt;
        });
        lenient().when(receiptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PaymentServiceImpl(
                paymentRepository, operationRepository, receiptRepository, outboxRepository, gateway,
                new PaymentMapper(), orderClient, new PaymentOrderValidator(),
                new PaymentTransitionValidator(), new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void cumulativeRefundCannotExceedCapturedAmount() {
        Payment payment = completedPayment("100.00", "30.00");
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPayment(
                "payment-1", "customer request", new BigDecimal("80.00"), null))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("remaining refundable amount");
        verify(gateway, never()).refund(any(), any(), any(), any());
    }

    @Test
    void refundingTheExactRemainingAmountMarksPaymentFullyRefunded() {
        Payment payment = completedPayment("100.00", "30.00");
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.refundPayment(
                "payment-1", "customer request", new BigDecimal("70.00"), null);

        verify(gateway).refund(eq(payment), eq(new BigDecimal("70.00")), eq("customer request"), any());
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void illegalRefundStateIsRejectedBeforeGatewayInvocation() {
        Payment payment = Payment.builder()
                .id("payment-1")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .refundedAmount(BigDecimal.ZERO)
                .build();
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        PaymentTransitionValidator transitions = org.mockito.Mockito.mock(PaymentTransitionValidator.class);
        doThrow(new PaymentException("illegal transition"))
                .when(transitions).validate(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED);
        service = new PaymentServiceImpl(
                paymentRepository, operationRepository, receiptRepository, outboxRepository, gateway,
                new PaymentMapper(), orderClient, new PaymentOrderValidator(), transitions,
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> service.refundPayment("payment-1", "customer request", null, null))
                .isInstanceOf(PaymentException.class);

        verify(gateway, never()).refund(any(), any(), any(), any());
    }

    @Test
    void webhookRequiredProviderCannotCompletePaymentThroughProcessEndpoint() {
        Payment payment = Payment.builder()
                .id("payment-1")
                .status(PaymentStatus.PENDING)
                .build();
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
        Payment payment = Payment.builder()
                .id("payment-1")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.confirm(payment)).thenReturn(true);

        var response = service.processPayment("payment-1");

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(gateway).confirm(payment);
    }

    @Test
    void verifiedWebhookIsTheOnlyPathThatCompletesWebhookRequiredPayment() {
        Payment payment = Payment.builder()
                .id("payment-1")
                .paymentReference("PAY-1")
                .transactionId("intent-1")
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.handlePaymentWebhook("PAY-1", new PaymentWebhookRequest(
                "PAY-1", "intent-1", "COMPLETED", null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(gateway, never()).confirm(any());
    }

    @Test
    void createPaymentUsesAuthoritativeOrderAmountAndCurrency() {
        UUID userId = UUID.randomUUID();
        when(orderClient.getOrder("order-1")).thenReturn(ApiResponse.success(new OrderSummary(
                "order-1", "ORD-100", userId, "PENDING", new BigDecimal("125.50"), "USD")));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(paymentRepository.insert(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId("payment-1");
            return payment;
        });
        when(paymentRepository.findById("payment-1")).thenAnswer(invocation -> Optional.of(
                Payment.builder().id("payment-1").orderId("order-1").orderNumber("ORD-100")
                        .userId(userId).status(PaymentStatus.PENDING).paymentMethod("CARD")
                        .amount(new BigDecimal("125.50")).currency("USD").build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.createIntent(any(Payment.class)))
                .thenReturn(new PaymentGateway.IntentResult("intent-1", "client-secret"));
        PaymentRequest request = new PaymentRequest(
                "order-1", "FAKE-ORDER", "CARD", new BigDecimal("1.00"), "INR", "checkout");

        var response = service.createPayment(request, userId, "customer@example.com", null);

        assertThat(response.payment().orderNumber()).isEqualTo("ORD-100");
        assertThat(response.payment().amount()).isEqualByComparingTo("125.50");
        assertThat(response.payment().currency()).isEqualTo("USD");
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.clientSecret()).isEqualTo("client-secret");
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
