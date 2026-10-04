package com.project.product_service.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductAccessValidator {
    public void requireSellerOrAdmin(UUID ownerId, UUID actorId, boolean admin) {
        if (!admin && (ownerId == null || !ownerId.equals(actorId))) {
            throw new ForbiddenOperationException("You do not own this product");
        }
    }
}
