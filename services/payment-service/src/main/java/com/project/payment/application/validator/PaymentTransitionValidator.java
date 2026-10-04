package com.project.payment.application.validator;

import com.project.payment.exception.PaymentException;
import com.project.payment.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

@Component
public class PaymentTransitionValidator {

    private static final Map<PaymentStatus, EnumSet<PaymentStatus>> LEGAL_TRANSITIONS = Map.of(
            PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.COMPLETED,
                    PaymentStatus.FAILED, PaymentStatus.CANCELLED),
            PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED,
                    PaymentStatus.CANCELLED),
            PaymentStatus.COMPLETED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED),
            PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED,
                    PaymentStatus.REFUNDED),
            PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.CANCELLED, EnumSet.noneOf(PaymentStatus.class));

    public void validate(PaymentStatus current, PaymentStatus next) {
        if (current == null || next == null || !LEGAL_TRANSITIONS.get(current).contains(next)) {
            throw new PaymentException("Illegal payment transition: " + current + " -> " + next);
        }
    }
}
