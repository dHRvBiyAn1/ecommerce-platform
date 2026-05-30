package com.project.cart.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for {@link CouponClient} when coupon-service is offline,
 * timing out, or circuit breaker is open.
 *
 * <p>Fail-open strategy: Logs the incident and returns a clean, invalid validation response
 * so the user's cart operation doesn't crash, allowing them to proceed without coupon savings.
 */
@Slf4j
@Component
public class CouponClientFallback implements CouponClient {

    @Override
    public CouponValidationResponse validate(CouponValidationRequest request) {
        log.error("Coupon-service call failed. Resilience4j fallback triggered for coupon code={}", request.getCode());
        return CouponValidationResponse.builder()
                .valid(false)
                .code(request.getCode())
                .reason("Coupon validation engine is currently offline. Please try again later.")
                .discountAmount(BigDecimal.ZERO)
                .description("Service offline fallback.")
                .build();
    }
}
