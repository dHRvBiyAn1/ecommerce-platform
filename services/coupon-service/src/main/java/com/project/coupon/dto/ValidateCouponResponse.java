package com.project.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateCouponResponse {
    private boolean valid;
    private String code;
    private String reason;
    private BigDecimal discountAmount;
    private String description;

    public static ValidateCouponResponse invalid(String code, String reason) {
        return ValidateCouponResponse.builder().valid(false).code(code).reason(reason).build();
    }

    public static ValidateCouponResponse valid(String code, BigDecimal discount, String description) {
        return ValidateCouponResponse.builder()
                .valid(true).code(code).discountAmount(discount).description(description).build();
    }
}
