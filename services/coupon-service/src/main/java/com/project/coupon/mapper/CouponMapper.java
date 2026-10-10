package com.project.coupon.mapper;

import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.CouponRedemption;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.generated.model.CouponRequest;
import com.project.coupon.generated.model.CouponReservationResponse;
import com.project.coupon.generated.model.CouponResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

  public Coupon toEntity(CouponRequest request) {
    return Coupon.builder()
        .code(normalizeCode(request.getCode()))
        .description(request.getDescription())
        .discountType(DiscountType.valueOf(request.getDiscountType().name()))
        .discountValue(request.getDiscountValue())
        .maxDiscountAmount(request.getMaxDiscountAmount())
        .minOrderAmount(request.getMinOrderAmount())
        .currency(normalizeCurrency(request.getCurrency(), "INR"))
        .validFrom(request.getValidFrom())
        .validUntil(request.getValidUntil())
        .usageLimit(request.getUsageLimit())
        .perUserLimit(request.getPerUserLimit())
        .active(request.getActive() == null || request.getActive())
        .build();
  }

  public void update(Coupon coupon, CouponRequest request) {
    coupon.setCode(normalizeCode(request.getCode()));
    coupon.setDescription(request.getDescription());
    coupon.setDiscountType(DiscountType.valueOf(request.getDiscountType().name()));
    coupon.setDiscountValue(request.getDiscountValue());
    coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
    coupon.setMinOrderAmount(request.getMinOrderAmount());
    coupon.setCurrency(normalizeCurrency(request.getCurrency(), coupon.getCurrency()));
    coupon.setValidFrom(request.getValidFrom());
    coupon.setValidUntil(request.getValidUntil());
    coupon.setUsageLimit(request.getUsageLimit());
    coupon.setPerUserLimit(request.getPerUserLimit());
    if (request.getActive() != null) coupon.setActive(request.getActive());
  }

  public CouponResponse toResponse(Coupon coupon) {
    return new CouponResponse()
        .id(coupon.getId())
        .code(coupon.getCode())
        .description(coupon.getDescription())
        .discountType(CouponResponse.DiscountTypeEnum.valueOf(coupon.getDiscountType().name()))
        .discountValue(coupon.getDiscountValue())
        .maxDiscountAmount(coupon.getMaxDiscountAmount())
        .minOrderAmount(coupon.getMinOrderAmount())
        .currency(coupon.getCurrency())
        .validFrom(coupon.getValidFrom())
        .validUntil(coupon.getValidUntil())
        .usageLimit(coupon.getUsageLimit())
        .usageCount(coupon.getUsageCount())
        .reservedCount(coupon.getReservedCount())
        .perUserLimit(coupon.getPerUserLimit())
        .active(coupon.isActive())
        .createdAt(coupon.getCreatedAt())
        .updatedAt(coupon.getUpdatedAt());
  }

  public CouponReservationResponse toReservationResponse(CouponRedemption redemption) {
    return new CouponReservationResponse()
        .reservationId(redemption.getId())
        .code(redemption.getCouponCode())
        .userId(redemption.getUserId())
        .orderId(redemption.getOrderId())
        .discountAmount(redemption.getDiscountAmount())
        .status(CouponReservationResponse.StatusEnum.valueOf(redemption.getStatus().name()));
  }

  public String normalizeCode(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCurrency(String currency, String fallback) {
    return currency == null ? fallback : currency.toUpperCase(Locale.ROOT);
  }
}
