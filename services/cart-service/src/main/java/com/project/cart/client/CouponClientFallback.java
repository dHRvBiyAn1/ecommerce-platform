package com.project.cart.client;

import com.project.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for {@link CouponClient} when coupon-service is offline,
 * timing out, or circuit breaker is open.
 *
 * <p>Fails closed so cart-service never grants a discount when validation is unavailable.
 */
@Slf4j
@Component
public class CouponClientFallback implements CouponClient {

    @Override
    public CouponValidationResponse validate(CouponValidationRequest request) {
        log.error("Coupon-service call failed. Resilience4j fallback triggered for coupon code={}", request.getCode());
        throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "COUPON_UNAVAILABLE",
                "Coupon validation is currently unavailable. Try again shortly.");
    }
}
