package com.project.inventory.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockReservationRequest(
        @Positive(message = "Quantity must be positive") int quantity,
        @NotBlank(message = "Order ID is required")
        @Size(max = 64, message = "Order ID must not exceed 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Order ID contains unsupported characters")
        String orderId
) {
}
