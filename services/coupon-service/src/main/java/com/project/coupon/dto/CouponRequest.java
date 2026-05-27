package com.project.coupon.dto;

import com.project.coupon.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must be uppercase alphanumeric, dashes, or underscores")
    private String code;

    private String description;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal discountValue;

    @Positive
    private BigDecimal maxDiscountAmount;

    @Positive
    private BigDecimal minOrderAmount;

    @Size(min = 3, max = 3)
    private String currency = "INR";

    @NotNull
    private LocalDateTime validFrom;

    @NotNull
    @Future
    private LocalDateTime validUntil;

    /** Global usage cap. */
    @Positive
    private Integer usageLimit;

    /** Per-user cap. */
    @Positive
    private Integer perUserLimit;

    private Boolean active;
}
