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

    /** Create a PaymentIntent / charge intent. Returns a gateway transaction id. */
    String createIntent(Payment payment);

    /** Capture / confirm. Returns true on success. */
    boolean confirm(Payment payment);

    /** Issue a refund. */
    void refund(Payment payment, BigDecimal amount, String reason);
}
