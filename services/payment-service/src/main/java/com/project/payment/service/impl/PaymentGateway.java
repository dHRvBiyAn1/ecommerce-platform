package com.project.payment.service.impl;

import com.project.payment.model.Payment;

import java.math.BigDecimal;

/**
 * Abstraction over the underlying PSP. Implementations:
 * <ul>
 *   <li>{@code SandboxGateway} (default) — deterministic for dev/tests</li>
 *   <li>{@code StripeGateway} (when {@code STRIPE_SECRET_KEY} is configured)</li>
 * </ul>
 */
public interface PaymentGateway {

    record IntentResult(String transactionId, String clientSecret) {}

    /** Create a PaymentIntent / charge intent. The secret is returned only in memory. */
    IntentResult createIntent(Payment payment);

    /** Whether terminal payment state may only be established by a verified webhook. */
    default boolean requiresVerifiedWebhook() {
        return false;
    }

    /** Capture / confirm. Returns true on success. */
    boolean confirm(Payment payment);

    /** Issue a refund. */
    void refund(Payment payment, BigDecimal amount, String reason);
}
