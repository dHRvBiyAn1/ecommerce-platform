package com.project.coupon.dto;

import com.project.coupon.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id, String code, String description, DiscountType discountType,
        BigDecimal discountValue, BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
        String currency, LocalDateTime validFrom, LocalDateTime validUntil,
        Integer usageLimit, int usageCount, int reservedCount, Integer perUserLimit,
        boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
