package com.project.product_service.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductAccessValidator {
  public void requireSellerOrAdmin(UUID ownerId, UUID actorId, boolean admin) {
    if (!admin && (ownerId == null || !ownerId.equals(actorId))) {
      throw new ForbiddenOperationException("You do not own this product");
    }
  }
}
