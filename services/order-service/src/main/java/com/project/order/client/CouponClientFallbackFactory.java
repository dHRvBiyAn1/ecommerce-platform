package com.project.order.client;

import com.project.order.client.dto.CouponValidationRequest;
import com.project.order.client.dto.CouponValidationResponse;
import com.project.order.client.dto.RedeemCouponRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponClientFallbackFactory implements FallbackFactory<CouponClient> {
    @Override
    public CouponClient create(Throwable cause) {
        return new CouponClient() {
            @Override
            public CouponValidationResponse validate(CouponValidationRequest request) {
                log.error("Fallback triggered for coupon validate: {}", cause.getMessage());
                return CouponValidationResponse.builder()
                        .valid(false)
                        .reason("Coupon service is currently unavailable")
                        .build();
            }

            @Override
            public CouponValidationResponse redeem(RedeemCouponRequest request) {
                log.error("Fallback triggered for coupon redeem: {}", cause.getMessage());
                return CouponValidationResponse.builder()
                        .valid(false)
                        .reason("Coupon service is currently unavailable")
                        .build();
            }
        };
    }
}
