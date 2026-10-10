package com.project.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.project.payment.model.Payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SandboxGatewayTest {
  private final SandboxGateway gateway = new SandboxGateway();

  @Test
  void returnsDistinctTransientIntentCredentialsWithoutPersistingThem() {
    Payment payment =
        Payment.builder()
            .paymentReference("PAY-1")
            .amount(new BigDecimal("50.00"))
            .currency("INR")
            .build();
    var first = gateway.createIntent(payment);
    var second = gateway.createIntent(payment);
    assertThat(first.transactionId()).startsWith("SBX-").isNotEqualTo(second.transactionId());
    assertThat(first.clientSecret())
        .startsWith("sandbox_client_secret_")
        .isNotEqualTo(second.clientSecret());
    assertThat(payment.getTransactionId()).isNull();
    assertThat(payment.getGatewayResponse()).isNull();
    assertThat(gateway.requiresVerifiedWebhook()).isFalse();
    assertThatCode(() -> gateway.refund(payment, new BigDecimal("10.00"), "return", "refund-1"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({",true", "ORD-1,true", "ORD-FAIL,false"})
  void confirmationHasDeterministicSuccessAndFailureScenarios(
      String orderNumber, boolean expected) {
    assertThat(gateway.confirm(Payment.builder().orderNumber(orderNumber).build()))
        .isEqualTo(expected);
  }
}
