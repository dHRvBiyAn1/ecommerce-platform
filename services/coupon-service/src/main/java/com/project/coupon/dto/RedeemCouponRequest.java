package com.project.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCouponRequest {

    @NotBlank
    private String code;

    @NotNull
    private UUID userId;

    @NotBlank
    private String orderId;

    @NotNull
    @Positive
    private BigDecimal discountAmount;
}
