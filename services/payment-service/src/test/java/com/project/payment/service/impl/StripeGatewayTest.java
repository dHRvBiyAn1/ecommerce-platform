package com.project.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StripeGatewayTest {

  @Test
  void stripeRequiresVerifiedWebhookForCompletion() {
    assertThat(new StripeGateway().requiresVerifiedWebhook()).isTrue();
  }

  @Test
  void sandboxKeepsSynchronousProcessingSemantics() {
    assertThat(new SandboxGateway().requiresVerifiedWebhook()).isFalse();
  }
}
