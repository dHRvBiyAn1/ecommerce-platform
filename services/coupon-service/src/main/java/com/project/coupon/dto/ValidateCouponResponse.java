package com.project.coupon.dto;

import java.math.BigDecimal;

public record ValidateCouponResponse(
        boolean valid, String code, String reason, BigDecimal discountAmount, String description) {

    public static ValidateCouponResponse invalid(String code, String reason) {
        return new ValidateCouponResponse(false, code, reason, null, null);
    }

    public static ValidateCouponResponse valid(String code, BigDecimal discount, String description) {
        return new ValidateCouponResponse(true, code, null, discount, description);
    }
}
