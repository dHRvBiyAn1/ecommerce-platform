package com.project.inventory.application.validator;

import com.project.common.exception.ValidationException;
import com.project.inventory.api.dto.request.InventoryRequest;
import com.project.inventory.domain.model.InventoryItem;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class InventoryValidator {
    private static final int MAX_ORDER_ID_LENGTH = 64;
    private static final Pattern SAFE_ORDER_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    public void validateReservation(int quantity, String orderId) {
        validatePositiveQuantity(quantity);
        if (orderId == null || orderId.isBlank() || orderId.length() > MAX_ORDER_ID_LENGTH
                || !SAFE_ORDER_ID.matcher(orderId).matches()) {
            throw new ValidationException("Order ID must contain only letters, numbers, underscores, or hyphens");
        }
    }

    public void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be positive");
        }
    }

    public void validateUpdate(InventoryItem item, InventoryRequest request) {
        if (request.quantity() < item.getReservedQuantity()) {
            throw new ValidationException("Quantity cannot be lower than the reserved quantity");
        }
    }
}
