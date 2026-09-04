package com.project.coupon.dto;

import com.project.coupon.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must be uppercase alphanumeric, dashes, or underscores")
        String code,
        @Size(max = 1000) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        @Positive BigDecimal maxDiscountAmount,
        @Positive BigDecimal minOrderAmount,
        @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @NotNull LocalDateTime validFrom,
        @NotNull @Future LocalDateTime validUntil,
        @Positive Integer usageLimit,
        @Positive Integer perUserLimit,
        Boolean active) {
}
