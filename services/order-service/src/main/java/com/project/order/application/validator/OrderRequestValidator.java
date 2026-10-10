package com.project.order.application.validator;

import com.project.order.exception.OrderValidationException;
import com.project.order.generated.model.BillingAddressRequest;
import com.project.order.generated.model.OrderItemRequest;
import com.project.order.generated.model.OrderRequest;
import com.project.order.generated.model.ShippingAddressRequest;
import com.project.order.model.OrderStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderRequestValidator {

  public void validateCreate(OrderRequest request, UUID userId) {
    if (request == null || userId == null) {
      throw new OrderValidationException("Order request and user are required");
    }
    if (request.getItems() == null || request.getItems().isEmpty()) {
      throw new OrderValidationException("Order must contain at least one item");
    }
    for (OrderItemRequest item : request.getItems()) {
      if (item == null || item.getProductId() == null || item.getProductId().isBlank()) {
        throw new OrderValidationException("Product ID is required");
      }
      if (item.getQuantity() == null || item.getQuantity() < 1) {
        throw new OrderValidationException("Quantity must be at least 1");
      }
    }
    validateShippingAddress(request.getShippingAddress());
    validateBillingAddress(request.getBillingAddress());
    if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
      throw new OrderValidationException("Payment method is required");
    }
  }

  public void validateStatusTransition(OrderStatus current, OrderStatus next) {
    boolean allowed =
        switch (current) {
          case CONFIRMED -> next == OrderStatus.PROCESSING;
          case PROCESSING -> next == OrderStatus.SHIPPED;
          case SHIPPED -> next == OrderStatus.DELIVERED;
          default -> false;
        };
    if (!allowed) {
      throw new OrderValidationException("Cannot transition order from " + current + " to " + next);
    }
  }

  private void validateShippingAddress(ShippingAddressRequest address) {
    if (address == null) {
      return;
    }
    if (isBlank(address.getFullName())
        || isBlank(address.getPhone())
        || isBlank(address.getStreet())
        || isBlank(address.getCity())
        || isBlank(address.getZipCode())
        || isBlank(address.getCountry())) {
      throw new OrderValidationException("Shipping address is invalid");
    }
  }

  private void validateBillingAddress(BillingAddressRequest address) {
    if (address == null) {
      return;
    }
    if (isBlank(address.getFullName())
        || isBlank(address.getPhone())
        || isBlank(address.getStreet())
        || isBlank(address.getCity())
        || isBlank(address.getZipCode())
        || isBlank(address.getCountry())) {
      throw new OrderValidationException("Billing address is invalid");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
