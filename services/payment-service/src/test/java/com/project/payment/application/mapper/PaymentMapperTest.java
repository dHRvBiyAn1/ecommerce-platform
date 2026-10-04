package com.project.payment.application.mapper;

import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void mapsNullLegacyRefundAmountAsZero() {
        Payment payment = Payment.builder()
                .id("payment-1")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .refundedAmount(null)
                .build();

        var response = mapper.toResponse(payment);

        assertThat(response.id()).isEqualTo("payment-1");
        assertThat(response.refundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
