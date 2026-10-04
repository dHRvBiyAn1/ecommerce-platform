package com.project.order.client.dto;

import java.util.UUID;

public record CouponTransitionCommand(String code, UUID userId, String orderId) {
}
