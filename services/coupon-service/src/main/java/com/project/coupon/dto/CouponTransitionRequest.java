package com.project.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CouponTransitionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID userId,
        @NotBlank @Size(max = 64) String orderId) {
}
