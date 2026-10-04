package com.project.payment.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
