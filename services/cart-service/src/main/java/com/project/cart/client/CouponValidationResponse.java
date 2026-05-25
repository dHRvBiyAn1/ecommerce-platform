package com.project.cart.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * coupon-service's reply to a validation call. Mirrors the shape produced by
 * coupon-service so the cart-service can deserialize directly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidationResponse {
    private boolean valid;
    private String code;
    /** Reason for rejection, populated only when {@code valid=false}. */
    private String reason;
    private BigDecimal discountAmount;
    private String description;
}
