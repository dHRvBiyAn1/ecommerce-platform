package com.project.coupon.validation;

import com.project.common.exception.ForbiddenOperationException;
import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponReservationRequest;
import com.project.coupon.dto.CouponTransitionRequest;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.dto.ValidateCouponRequest;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.exception.InvalidCouponRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Component
public class CouponRequestValidator {

    public void validateActor(UUID claimedUserId, UUID callerId, boolean administrator) {
        if (!administrator && !Objects.equals(claimedUserId, callerId)) {
            throw new ForbiddenOperationException("Coupon operation is not permitted for another user");
        }
    }

    public void validateDefinition(CouponRequest request) {
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new InvalidCouponRequestException("validUntil must be after validFrom");
        }
        if (request.discountType() == DiscountType.PERCENT
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidCouponRequestException("Percentage discount cannot exceed 100");
        }
    }

    public void validateValidation(ValidateCouponRequest request) {
        requireSafeCode(request.code());
    }

    public void validateReservation(CouponReservationRequest request) {
        requireSafeCode(request.code());
        requireSafeOrderId(request.orderId());
    }

    public void validateTransition(CouponTransitionRequest request) {
        requireSafeCode(request.code());
        requireSafeOrderId(request.orderId());
    }

    public void validateRedemption(RedeemCouponRequest request) {
        requireSafeCode(request.code());
        requireSafeOrderId(request.orderId());
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
