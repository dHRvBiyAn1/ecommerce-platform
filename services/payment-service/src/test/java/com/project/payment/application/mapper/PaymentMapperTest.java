package com.project.payment.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentMapperTest {

  private final PaymentMapper mapper = new com.project.payment.generated.mapper.PaymentMapperImpl();

  @Test
  void mapsNullLegacyRefundAmountAsZero() {
    Payment payment =
        Payment.builder()
            .id("payment-1")
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("50.00"))
            .refundedAmount(null)
            .build();

    var response = mapper.toResponse(payment);

    assertThat(response.getId()).isEqualTo("payment-1");
    assertThat(response.getRefundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
