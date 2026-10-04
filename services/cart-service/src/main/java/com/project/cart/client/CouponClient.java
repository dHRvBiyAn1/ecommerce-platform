package com.project.cart.client;

import com.project.common.feign.FeignAuthForwardingConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for coupon-service. The request carries cart-service's machine
 * credential via {@link FeignAuthForwardingConfig}; the request body retains
 * the customer identity that coupon-service validates.
 */
@FeignClient(name = "coupon-service", configuration = FeignAuthForwardingConfig.class, fallback = CouponClientFallback.class)
public interface CouponClient {

    @PostMapping("/api/v1/coupons/validate")
    CouponValidationResponse validate(@RequestBody CouponValidationRequest request);
}
