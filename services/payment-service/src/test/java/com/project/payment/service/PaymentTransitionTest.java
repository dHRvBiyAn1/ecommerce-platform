package com.project.payment.service;

import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.exception.PaymentException;
import com.project.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTransitionTest {

    private final PaymentTransitionValidator validator = new PaymentTransitionValidator();

    @Test
    void allowsEveryLegalPaymentTransition() {
        Map<PaymentStatus, EnumSet<PaymentStatus>> legal = Map.of(
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

        legal.forEach((current, nextStatuses) -> nextStatuses.forEach(next ->
                assertThatCode(() -> validator.validate(current, next)).doesNotThrowAnyException()));
    }

    @Test
    void rejectsIllegalPaymentTransition() {
        for (PaymentStatus current : PaymentStatus.values()) {
            for (PaymentStatus next : PaymentStatus.values()) {
                boolean legal = switch (current) {
                    case PENDING -> next == PaymentStatus.PROCESSING || next == PaymentStatus.COMPLETED
                            || next == PaymentStatus.FAILED || next == PaymentStatus.CANCELLED;
                    case PROCESSING -> next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED
                            || next == PaymentStatus.CANCELLED;
                    case COMPLETED -> next == PaymentStatus.PARTIALLY_REFUNDED || next == PaymentStatus.REFUNDED;
                    case PARTIALLY_REFUNDED -> next == PaymentStatus.PARTIALLY_REFUNDED
                            || next == PaymentStatus.REFUNDED;
                    case FAILED, REFUNDED, CANCELLED -> false;
                };
                if (!legal) {
                    assertThatThrownBy(() -> validator.validate(current, next))
                            .isInstanceOf(PaymentException.class);
                }
            }
        }
    }
}
