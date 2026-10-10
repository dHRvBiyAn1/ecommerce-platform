package com.project.order.client;

import com.project.order.generated.integration.coupon.model.CouponReservationRequest;
import com.project.order.generated.integration.coupon.model.CouponReservationResponse;
import com.project.order.generated.integration.coupon.model.CouponTransitionRequest;
import com.project.order.generated.integration.coupon.model.RedeemCouponRequest;
import com.project.order.generated.integration.coupon.model.ValidateCouponRequest;
import com.project.order.generated.integration.coupon.model.ValidateCouponResponse;
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
      public ValidateCouponResponse validate(ValidateCouponRequest request) {
        log.error("Fallback triggered for coupon validate: {}", cause.getMessage());
        return new ValidateCouponResponse()
            .valid(false)
            .reason("Coupon service is currently unavailable");
      }

      @Override
      public ValidateCouponResponse redeem(RedeemCouponRequest request) {
        log.error("Fallback triggered for coupon redeem: {}", cause.getMessage());
        return new ValidateCouponResponse()
            .valid(false)
            .reason("Coupon service is currently unavailable");
      }

      @Override
      public CouponReservationResponse reserve(CouponReservationRequest request) {
        throw unavailable("reserve", cause);
      }

      @Override
      public CouponReservationResponse commit(CouponTransitionRequest request) {
        throw unavailable("commit", cause);
      }

      @Override
      public CouponReservationResponse release(CouponTransitionRequest request) {
        throw unavailable("release", cause);
      }
    };
  }

  private IllegalStateException unavailable(String operation, Throwable cause) {
    log.error("Fallback triggered for coupon {}: {}", operation, cause.getMessage());
    return new IllegalStateException("Coupon service is currently unavailable", cause);
  }
}
