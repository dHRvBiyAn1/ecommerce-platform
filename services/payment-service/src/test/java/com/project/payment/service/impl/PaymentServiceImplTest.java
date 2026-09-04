package com.project.payment.service.impl;

import com.project.payment.exception.PaymentException;
import com.project.payment.application.mapper.PaymentMapper;
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

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, eventPublisher, redis, gateway, new PaymentMapper());
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

    private Payment completedPayment(String amount, String refundedAmount) {
        return Payment.builder()
                .id("payment-1")
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .amount(new BigDecimal(amount))
                .refundedAmount(new BigDecimal(refundedAmount))
                .build();
    }
}
