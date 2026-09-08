package com.project.order.application.validator;

import com.project.order.dto.BillingAddressRequest;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.ShippingAddressRequest;
import com.project.order.exception.OrderValidationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderRequestValidator {

    public void validateCreate(OrderRequest request, UUID userId) {
        if (request == null || userId == null) {
            throw new OrderValidationException("Order request and user are required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new OrderValidationException("Order must contain at least one item");
        }
        for (OrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.productId().isBlank()) {
                throw new OrderValidationException("Product ID is required");
            }
            if (item.quantity() < 1) {
                throw new OrderValidationException("Quantity must be at least 1");
            }
        }
        validateShippingAddress(request.shippingAddress());
        validateBillingAddress(request.billingAddress());
        if (request.paymentMethod() == null || request.paymentMethod().isBlank()) {
            throw new OrderValidationException("Payment method is required");
        }
    }

    private void validateShippingAddress(ShippingAddressRequest address) {
        if (address == null) {
            return;
        }
        if (isBlank(address.fullName()) || isBlank(address.phone()) || isBlank(address.street())
                || isBlank(address.city()) || isBlank(address.zipCode()) || isBlank(address.country())) {
            throw new OrderValidationException("Shipping address is invalid");
        }
    }

    private void validateBillingAddress(BillingAddressRequest address) {
        if (address == null) {
            return;
        }
        if (isBlank(address.fullName()) || isBlank(address.phone()) || isBlank(address.street())
                || isBlank(address.city()) || isBlank(address.zipCode()) || isBlank(address.country())) {
            throw new OrderValidationException("Billing address is invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
