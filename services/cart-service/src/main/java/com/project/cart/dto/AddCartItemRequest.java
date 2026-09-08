package com.project.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddCartItemRequest(@NotBlank String productId, @Min(1) int quantity) {}
