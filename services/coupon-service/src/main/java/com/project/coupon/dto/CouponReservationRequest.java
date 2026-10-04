package com.project.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponReservationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull UUID userId,
        @NotBlank @Size(max = 64) String orderId,
        @NotNull @Positive BigDecimal subtotal,
        @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Za-z]{3}$") String currency) {
}
