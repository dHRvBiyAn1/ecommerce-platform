package com.project.order.client;

import com.project.order.client.dto.CouponValidationRequest;
import com.project.order.client.dto.CouponValidationResponse;
import com.project.order.client.dto.CouponReservationCommand;
import com.project.order.client.dto.CouponReservationResult;
import com.project.order.client.dto.CouponTransitionCommand;
import com.project.order.client.dto.RedeemCouponRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "coupon-service", fallbackFactory = CouponClientFallbackFactory.class)
public interface CouponClient {
    @PostMapping("/api/v1/coupons/validate")
    CouponValidationResponse validate(@RequestBody CouponValidationRequest request);

    @PostMapping("/api/v1/coupons/redeem")
    CouponValidationResponse redeem(@RequestBody RedeemCouponRequest request);

    @PostMapping("/api/v1/coupons/reserve")
    CouponReservationResult reserve(@RequestBody CouponReservationCommand request);

    @PostMapping("/api/v1/coupons/commit")
    CouponReservationResult commit(@RequestBody CouponTransitionCommand request);

    @PostMapping("/api/v1/coupons/release")
    CouponReservationResult release(@RequestBody CouponTransitionCommand request);
}
