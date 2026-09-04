package com.project.payment.service.impl;

import com.project.payment.exception.PaymentException;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
import com.project.common.dto.ApiResponse;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PaymentGateway gateway;
    @Mock private OrderClient orderClient;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository, eventPublisher, redis, gateway, new PaymentMapper(), orderClient,
                new PaymentOrderValidator());
    }

    @Test
    void cumulativeRefundCannotExceedCapturedAmount() {
        Payment payment = completedPayment("100.00", "30.00");
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refundPayment(
                "payment-1", "customer request", new BigDecimal("80.00"), null))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("remaining refundable amount");
        verify(gateway, never()).refund(any(), any(), any());
    }

    @Test
    void refundingTheExactRemainingAmountMarksPaymentFullyRefunded() {
        Payment payment = completedPayment("100.00", "30.00");
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.refundPayment(
                "payment-1", "customer request", new BigDecimal("70.00"), null);

        verify(gateway).refund(payment, new BigDecimal("70.00"), "customer request");
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void failedRefundReleasesIdempotencyKeySoTheRequestCanBeRetried() {
        Payment payment = completedPayment("100.00", "0.00");
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "payment:refund:payment-1:refund-key", "PENDING", java.time.Duration.ofMinutes(10)))
                .thenReturn(true);
        when(paymentRepository.findById("payment-1")).thenReturn(Optional.of(payment));
        org.mockito.Mockito.doThrow(new IllegalStateException("gateway unavailable"))
                .when(gateway).refund(payment, new BigDecimal("25.00"), "customer request");

        assertThatThrownBy(() -> service.refundPayment(
                "payment-1", "customer request", new BigDecimal("25.00"), "refund-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gateway unavailable");

        verify(redis).delete("payment:refund:payment-1:refund-key");
    }

    @Test
    void createPaymentUsesAuthoritativeOrderAmountAndCurrency() {
        UUID userId = UUID.randomUUID();
        when(orderClient.getOrder("order-1")).thenReturn(ApiResponse.success(new OrderSummary(
                "order-1", "ORD-100", userId, "PENDING", new BigDecimal("125.50"), "USD")));
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId("payment-1");
            return payment;
        });
        when(gateway.createIntent(any(Payment.class))).thenReturn("intent-1");
        PaymentRequest request = new PaymentRequest(
                "order-1", "FAKE-ORDER", "CARD", new BigDecimal("1.00"), "INR", "checkout");

        var response = service.createPayment(request, userId, "customer@example.com", null);

        assertThat(response.orderNumber()).isEqualTo("ORD-100");
        assertThat(response.amount()).isEqualByComparingTo("125.50");
        assertThat(response.currency()).isEqualTo("USD");
    }

    @Test
    void failedPaymentCreationReleasesIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("payment:create:" + userId + ":create-key")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                "payment:create:" + userId + ":create-key", "PENDING", java.time.Duration.ofMinutes(10)))
                .thenReturn(true);
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(orderClient.getOrder("order-1")).thenThrow(new IllegalStateException("order service unavailable"));
        PaymentRequest request = new PaymentRequest("order-1", null, "CARD", null, null, null);

        assertThatThrownBy(() -> service.createPayment(
                request, userId, "customer@example.com", "create-key"))
                .isInstanceOf(IllegalStateException.class);

        verify(redis).delete("payment:create:" + userId + ":create-key");
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
