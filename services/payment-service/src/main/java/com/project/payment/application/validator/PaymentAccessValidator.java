package com.project.payment.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.generated.model.PaymentResponse;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentAccessValidator {

  public void validateAccess(PaymentResponse payment, UUID requesterId, boolean administrator) {
    if (!administrator && !Objects.equals(payment.getUserId(), requesterId)) {
      throw new ForbiddenOperationException("You do not have permission to access this payment");
    }
  }
}
