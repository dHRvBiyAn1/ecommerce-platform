package com.project.product_service.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Data
public class ProductRequest {
    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String categoryId;

    @NotNull
    @Positive
    private BigDecimal price;

    @NotNull
    @PositiveOrZero
    private Integer stockQuantity;

    private List<String> imageUrls;

    private UUID sellerId; // set from authenticated user context
}
