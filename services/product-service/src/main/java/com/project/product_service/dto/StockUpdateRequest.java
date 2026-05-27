package com.project.product_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateRequest(
    @NotNull @PositiveOrZero Integer stockQuantity
) {}
