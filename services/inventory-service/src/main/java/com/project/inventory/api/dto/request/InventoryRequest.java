package com.project.inventory.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryRequest(
        @NotBlank(message = "Product ID is required")
        @Size(max = 64, message = "Product ID must not exceed 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Product ID contains unsupported characters")
        String productId,
        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must not exceed 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "SKU contains unsupported characters")
        String sku,
        @PositiveOrZero(message = "Quantity must be zero or positive")
        int quantity,
        @PositiveOrZero(message = "Low stock threshold must be zero or positive")
        int lowStockThreshold,
        @Size(max = 128, message = "Location must not exceed 128 characters")
        String location
) {
}
