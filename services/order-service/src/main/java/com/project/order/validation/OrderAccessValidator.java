package com.project.order.validation;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.project.common.constant.ServiceScopes.AUTHORITY_ORDERS_READ;

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
