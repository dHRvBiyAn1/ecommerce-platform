package com.project.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRequest {
    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "SKU is required")
    private String sku;

    @PositiveOrZero(message = "Quantity must be zero or positive")
    private int quantity;

    @PositiveOrZero(message = "Low stock threshold must be zero or positive")
    private int lowStockThreshold;

    private String location;
}
