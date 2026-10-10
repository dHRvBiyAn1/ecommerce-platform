package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.generated.integration.order.model.OrderResponse;
import com.project.payment.exception.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOrderValidatorTest {

    private final PaymentOrderValidator validator = new PaymentOrderValidator();

    @Test
    void acceptsOwnedPendingOrderWithValidFinancialData() {
        UUID ownerId = UUID.randomUUID();

        assertThatCode(() -> validator.validateForPayment(order(ownerId, "PENDING"), ownerId))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnotherUsersOrder() {
        assertThatThrownBy(() -> validator.validateForPayment(
                order(UUID.randomUUID(), "PENDING"), UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rejectsOrderThatIsNotAwaitingPayment() {
        UUID ownerId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validateForPayment(order(ownerId, "CONFIRMED"), ownerId))
                .isInstanceOf(PaymentException.class)
                .hasMessage("Order is not awaiting payment");
    }

    private OrderResponse order(UUID ownerId, String status) {
        return new OrderResponse().id("order-1").orderNumber("ORD-1").userId(ownerId).status(com.project.payment.generated.integration.order.model.OrderResponse.StatusEnum.fromValue(status)).totalAmount(new BigDecimal("25.00")).currency("INR");
    }
}
