package com.project.order.client;

import com.project.order.client.dto.CouponValidationRequest;
import com.project.order.client.dto.CouponValidationResponse;
import com.project.order.client.dto.CouponReservationCommand;
import com.project.order.client.dto.CouponReservationResult;
import com.project.order.client.dto.CouponTransitionCommand;
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

            @Override
            public CouponReservationResult reserve(CouponReservationCommand request) {
                throw unavailable("reserve", cause);
            }

            @Override
            public CouponReservationResult commit(CouponTransitionCommand request) {
                throw unavailable("commit", cause);
            }

            @Override
            public CouponReservationResult release(CouponTransitionCommand request) {
                throw unavailable("release", cause);
            }
        };
    }

    private IllegalStateException unavailable(String operation, Throwable cause) {
        log.error("Fallback triggered for coupon {}: {}", operation, cause.getMessage());
        return new IllegalStateException("Coupon service is currently unavailable", cause);
    }
}
