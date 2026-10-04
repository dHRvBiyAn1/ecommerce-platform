package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.client.dto.OrderSummary;
import com.project.payment.exception.PaymentException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class PaymentOrderValidator {

    public OrderSummary validateForPayment(OrderSummary order, UUID requesterId) {
        if (order == null) {
            throw new PaymentException("Order details are unavailable");
        }
        if (!Objects.equals(order.userId(), requesterId)) {
            throw new ForbiddenOperationException("You cannot create a payment for another user's order");
        }
        if (!"PENDING".equals(order.status())) {
            throw new PaymentException("Order is not awaiting payment");
        }
        if (order.totalAmount() == null || order.totalAmount().signum() <= 0) {
            throw new PaymentException("Order total must be positive");
        }
        if (order.currency() == null || !order.currency().matches("[A-Z]{3}")) {
            throw new PaymentException("Order currency is invalid");
        }
        return order;
    }
}
