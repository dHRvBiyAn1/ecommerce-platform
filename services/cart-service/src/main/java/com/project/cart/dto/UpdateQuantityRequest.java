package com.project.cart.dto;

import jakarta.validation.constraints.Min;
public record UpdateQuantityRequest(
    /** Use 0 to remove the item entirely. */
    @Min(0) int quantity) {}
