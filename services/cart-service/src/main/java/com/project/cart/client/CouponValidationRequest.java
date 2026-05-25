package com.project.cart.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body the cart-service sends to {@code coupon-service} on apply.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidationRequest {
    private String code;
    private UUID userId;
    private BigDecimal subtotal;
    private String currency;
}
