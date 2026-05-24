package com.project.payment.service.impl;

import com.project.payment.exception.PaymentException;
import com.project.payment.model.Payment;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Real Stripe integration. Activated when {@code stripe.secret-key} is set
 * (env var {@code STRIPE_SECRET_KEY}). The amounts are converted to the
 * smallest currency unit (paise for INR, cents for USD) per Stripe's convention.
 *
 * <p>3DS / SCA happens client-side: the client confirms the PaymentIntent in the
 * browser; the webhook delivers final state to {@code /webhook/stripe}.
 */
@Slf4j
@Component(value = "stripeGateway")
@ConditionalOnProperty(value = "stripe.secret-key")
public class StripeGateway implements PaymentGateway {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        Stripe.setMaxNetworkRetries(2);
        log.info("Stripe gateway initialized (key prefix={})",
                secretKey.length() > 7 ? secretKey.substring(0, 7) : "?");
    }

    @Override
    public String createIntent(Payment payment) {
        long minorUnits = toMinorUnits(payment.getAmount(), payment.getCurrency());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderId", payment.getOrderId());
        metadata.put("paymentReference", payment.getPaymentReference());
        if (payment.getUserId() != null) metadata.put("userId", payment.getUserId().toString());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(minorUnits)
                .setCurrency(payment.getCurrency().toLowerCase())
                .setDescription(payment.getDescription())
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();
        try {
            RequestOptions opts = RequestOptions.builder()
                    .setIdempotencyKey("create-intent:" + payment.getPaymentReference())
                    .build();
            PaymentIntent intent = PaymentIntent.create(params, opts);
            return intent.getId();
        } catch (StripeException e) {
            throw new PaymentException("Stripe createIntent failed: " + e.getMessage());
        }
    }

    @Override
    public boolean confirm(Payment payment) {
        // Confirmation happens client-side via Stripe.js → final status arrives by webhook.
        // We treat this server call as a no-op success.
        return true;
    }

    @Override
    public void refund(Payment payment, BigDecimal amount, String reason) {
        long minorUnits = toMinorUnits(amount, payment.getCurrency());
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getTransactionId())
                    .setAmount(minorUnits)
                    .setReason(mapReason(reason))
                    .build();
            RequestOptions opts = RequestOptions.builder()
                    .setIdempotencyKey("refund:" + payment.getPaymentReference() + ":" + amount.toPlainString())
                    .build();
            Refund.create(params, opts);
        } catch (StripeException e) {
            throw new PaymentException("Stripe refund failed: " + e.getMessage());
        }
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        // Most currencies have 2 decimals; JPY/KRW have 0. Cover INR/USD/EUR/GBP/CAD/AUD here.
        int scale = "JPY".equalsIgnoreCase(currency) || "KRW".equalsIgnoreCase(currency) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_UP)
                .movePointRight(scale)
                .longValueExact();
    }

    private RefundCreateParams.Reason mapReason(String reason) {
        if (reason == null) return RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        return switch (reason.toLowerCase()) {
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            default -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }
}
