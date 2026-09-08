package com.project.payment.service.impl;

import com.project.payment.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sandbox / dev gateway. Picks success/failure deterministically from the payment id
 * so tests are reproducible. The Stripe gateway (when its key is configured) overrides
 * this bean.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "stripeGateway")
public class SandboxGateway implements PaymentGateway {

    @Override
    public IntentResult createIntent(Payment payment) {
        log.info("[sandbox] createIntent for {} amount {} {}",
                payment.getPaymentReference(), payment.getAmount(), payment.getCurrency());
        return new IntentResult("SBX-" + UUID.randomUUID().toString().substring(0, 12),
                "sandbox_client_secret_" + UUID.randomUUID());
    }

    @Override
    public boolean confirm(Payment payment) {
        // Deterministic: succeed unless the orderNumber contains "FAIL"
        boolean ok = payment.getOrderNumber() == null || !payment.getOrderNumber().contains("FAIL");
        log.info("[sandbox] confirm {} -> {}", payment.getPaymentReference(), ok ? "OK" : "FAIL");
        return ok;
    }

    @Override
    public void refund(Payment payment, BigDecimal amount, String reason) {
        log.info("[sandbox] refund {} amount {} reason {}", payment.getPaymentReference(), amount, reason);
    }
}
