package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.generated.integration.order.model.OrderResponse;
import com.project.payment.exception.PaymentException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class PaymentOrderValidator {

    public OrderResponse validateForPayment(OrderResponse order, UUID requesterId) {
        if (order == null) {
            throw new PaymentException("Order details are unavailable");
        }
        if (!Objects.equals(order.getUserId(), requesterId)) {
            throw new ForbiddenOperationException("You cannot create a payment for another user's order");
        }
        if (!com.project.payment.generated.integration.order.model.OrderResponse.StatusEnum.PENDING.equals(order.getStatus())) {
            throw new PaymentException("Order is not awaiting payment");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw new PaymentException("Order total must be positive");
        }
        if (order.getCurrency() == null || !order.getCurrency().matches("[A-Z]{3}")) {
            throw new PaymentException("Order currency is invalid");
        }
        return order;
    }
}
