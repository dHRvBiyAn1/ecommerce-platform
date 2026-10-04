package com.project.coupon.dto;

import com.project.coupon.entity.RedemptionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponReservationResponse(
        UUID reservationId, String code, UUID userId, String orderId,
        BigDecimal discountAmount, RedemptionStatus status) {
}
