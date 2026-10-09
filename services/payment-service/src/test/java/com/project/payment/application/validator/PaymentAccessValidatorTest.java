package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.generated.model.PaymentResponse;
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
        return new PaymentResponse().id(null).paymentReference(null).orderId(null).orderNumber(null).userId(ownerId).userEmail(null).status(null).paymentMethod(null).amount(null).refundedAmount(null).currency(null).transactionId(null).gatewayResponse(null).failureReason(null).retryCount(0).description(null).createdAt(null).updatedAt(null).completedAt(null);
    }
}
