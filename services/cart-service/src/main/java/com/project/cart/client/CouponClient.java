package com.project.cart.client;

import com.project.common.feign.FeignAuthForwardingConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for coupon-service. The request carries the user's JWT
 * forwarded via {@link FeignAuthForwardingConfig} so coupon-service can
 * enforce per-user redemption limits.
 */
@FeignClient(name = "coupon-service", configuration = FeignAuthForwardingConfig.class, fallback = CouponClientFallback.class)
public interface CouponClient {

    @PostMapping("/api/v1/coupons/validate")
    CouponValidationResponse validate(@RequestBody CouponValidationRequest request);
}
