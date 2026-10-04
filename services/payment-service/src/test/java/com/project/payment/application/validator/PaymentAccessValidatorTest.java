package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.api.dto.response.PaymentResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAccessValidatorTest {

    private final PaymentAccessValidator validator = new PaymentAccessValidator();

    @Test
    void ownerCanAccessPayment() {
        UUID ownerId = UUID.randomUUID();

        assertThatCode(() -> validator.validateAccess(paymentOwnedBy(ownerId), ownerId, false))
                .doesNotThrowAnyException();
    }

    @Test
    void administratorCanAccessAnotherUsersPayment() {
        assertThatCode(() -> validator.validateAccess(
                paymentOwnedBy(UUID.randomUUID()), UUID.randomUUID(), true))
                .doesNotThrowAnyException();
    }

    @Test
    void anotherCustomerCannotAccessPayment() {
        assertThatThrownBy(() -> validator.validateAccess(
                paymentOwnedBy(UUID.randomUUID()), UUID.randomUUID(), false))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("You do not have permission to access this payment");
    }

    private PaymentResponse paymentOwnedBy(UUID ownerId) {
        return new PaymentResponse(null, null, null, null, ownerId, null, null, null,
                null, null, null, null, null, null, 0, null, null, null, null);
    }
}
