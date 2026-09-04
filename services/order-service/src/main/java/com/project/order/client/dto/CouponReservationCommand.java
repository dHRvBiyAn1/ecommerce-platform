package com.project.order.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CouponReservationCommand(
        String code, UUID userId, String orderId, BigDecimal subtotal, String currency) {
}
