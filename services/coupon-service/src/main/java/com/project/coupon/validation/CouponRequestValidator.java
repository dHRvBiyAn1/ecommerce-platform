package com.project.coupon.validation;

import com.project.common.exception.ForbiddenOperationException;
import com.project.coupon.exception.InvalidCouponRequestException;
import com.project.coupon.generated.model.CouponRequest;
import com.project.coupon.generated.model.CouponReservationRequest;
import com.project.coupon.generated.model.CouponTransitionRequest;
import com.project.coupon.generated.model.RedeemCouponRequest;
import com.project.coupon.generated.model.ValidateCouponRequest;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CouponRequestValidator {

  public void validateActor(UUID claimedUserId, UUID callerId, boolean administrator) {
    if (!administrator && !Objects.equals(claimedUserId, callerId)) {
      throw new ForbiddenOperationException("Coupon operation is not permitted for another user");
    }
  }

  public void validateDefinition(CouponRequest request) {
    if (!request.getValidUntil().isAfter(request.getValidFrom())) {
      throw new InvalidCouponRequestException("validUntil must be after validFrom");
    }
    if (request.getDiscountType() == CouponRequest.DiscountTypeEnum.PERCENT
        && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new InvalidCouponRequestException("Percentage discount cannot exceed 100");
    }
  }

  public void validateValidation(ValidateCouponRequest request) {
    requireSafeCode(request.getCode());
  }

  public void validateReservation(CouponReservationRequest request) {
    requireSafeCode(request.getCode());
    requireSafeOrderId(request.getOrderId());
  }

  public void validateTransition(CouponTransitionRequest request) {
    requireSafeCode(request.getCode());
    requireSafeOrderId(request.getOrderId());
  }

  public void validateRedemption(RedeemCouponRequest request) {
    requireSafeCode(request.getCode());
    requireSafeOrderId(request.getOrderId());
  }

  private void requireSafeCode(String code) {
    if (code == null || !code.matches("[A-Za-z0-9_-]{1,64}")) {
      throw new InvalidCouponRequestException("Coupon code contains unsupported characters");
    }
  }

  private void requireSafeOrderId(String orderId) {
    if (orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,64}")) {
      throw new InvalidCouponRequestException("Order ID contains unsupported characters");
    }
  }
}
