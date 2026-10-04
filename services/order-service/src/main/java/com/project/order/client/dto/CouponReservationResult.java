package com.project.order.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponReservationResult(
        UUID reservationId, String code, UUID userId, String orderId,
        BigDecimal discountAmount, String status) {
}
