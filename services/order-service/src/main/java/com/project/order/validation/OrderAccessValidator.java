package com.project.order.validation;

import static com.project.common.constant.ServiceScopes.AUTHORITY_ORDERS_READ;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderAccessValidator {

  public void validateRead(UUID orderOwnerId) {
    if (CurrentUser.isService()) {
      if (!CurrentUser.hasAuthority(AUTHORITY_ORDERS_READ)) {
        throw new ForbiddenOperationException("Service scope does not permit reading orders");
      }
      return;
    }
    if (!CurrentUser.isAdmin() && !orderOwnerId.equals(CurrentUser.requireId())) {
      throw new ForbiddenOperationException("You cannot access this order");
    }
  }
}
