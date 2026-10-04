package com.project.coupon.mapper;

import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponReservationResponse;
import com.project.coupon.dto.CouponResponse;
import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.CouponRedemption;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CouponMapper {

    public Coupon toEntity(CouponRequest request) {
        return Coupon.builder()
                .code(normalizeCode(request.code()))
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscountAmount(request.maxDiscountAmount())
                .minOrderAmount(request.minOrderAmount())
                .currency(normalizeCurrency(request.currency(), "INR"))
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .usageLimit(request.usageLimit())
                .perUserLimit(request.perUserLimit())
                .active(request.active() == null || request.active())
                .build();
    }

    public void update(Coupon coupon, CouponRequest request) {
        coupon.setCode(normalizeCode(request.code()));
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setCurrency(normalizeCurrency(request.currency(), coupon.getCurrency()));
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setPerUserLimit(request.perUserLimit());
        if (request.active() != null) coupon.setActive(request.active());
    }

    public CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(), coupon.getCode(), coupon.getDescription(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMaxDiscountAmount(), coupon.getMinOrderAmount(),
                coupon.getCurrency(), coupon.getValidFrom(), coupon.getValidUntil(), coupon.getUsageLimit(),
                coupon.getUsageCount(), coupon.getReservedCount(), coupon.getPerUserLimit(), coupon.isActive(),
                coupon.getCreatedAt(), coupon.getUpdatedAt());
    }

    public CouponReservationResponse toReservationResponse(CouponRedemption redemption) {
        return new CouponReservationResponse(
                redemption.getId(), redemption.getCouponCode(), redemption.getUserId(), redemption.getOrderId(),
                redemption.getDiscountAmount(), redemption.getStatus());
    }

    public String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency, String fallback) {
        return currency == null ? fallback : currency.toUpperCase(Locale.ROOT);
    }
}
