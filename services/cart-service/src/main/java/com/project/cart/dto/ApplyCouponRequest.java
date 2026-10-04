package com.project.cart.dto;

import jakarta.validation.constraints.NotBlank;
public record ApplyCouponRequest(@NotBlank String code) {}
