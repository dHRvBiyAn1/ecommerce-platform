package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.api.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class PaymentAccessValidator {

    public void validateAccess(PaymentResponse payment, UUID requesterId, boolean administrator) {
        if (!administrator && !Objects.equals(payment.userId(), requesterId)) {
            throw new ForbiddenOperationException("You do not have permission to access this payment");
        }
    }
}
