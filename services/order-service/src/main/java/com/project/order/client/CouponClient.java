package com.project.order.client;

import com.project.order.generated.integration.coupon.model.ValidateCouponRequest;
import com.project.order.generated.integration.coupon.model.ValidateCouponResponse;
import com.project.order.generated.integration.coupon.model.CouponReservationRequest;
import com.project.order.generated.integration.coupon.model.CouponReservationResponse;
import com.project.order.generated.integration.coupon.model.CouponTransitionRequest;
import com.project.order.generated.integration.coupon.model.RedeemCouponRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "coupon-service", fallbackFactory = CouponClientFallbackFactory.class)
public interface CouponClient {
    @PostMapping("/api/v1/coupons/validate")
    ValidateCouponResponse validate(@RequestBody ValidateCouponRequest request);

    @PostMapping("/api/v1/coupons/redeem")
    ValidateCouponResponse redeem(@RequestBody RedeemCouponRequest request);

    @PostMapping("/api/v1/coupons/reserve")
    CouponReservationResponse reserve(@RequestBody CouponReservationRequest request);

    @PostMapping("/api/v1/coupons/commit")
    CouponReservationResponse commit(@RequestBody CouponTransitionRequest request);

    @PostMapping("/api/v1/coupons/release")
    CouponReservationResponse release(@RequestBody CouponTransitionRequest request);
}
