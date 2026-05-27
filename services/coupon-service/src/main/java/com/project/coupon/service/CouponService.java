package com.project.coupon.service;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponResponse;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.dto.ValidateCouponRequest;
import com.project.coupon.dto.ValidateCouponResponse;
import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.CouponRedemption;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.repository.CouponRedemptionRepository;
import com.project.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Coupon CRUD plus validate / redeem endpoints. Validate is read-only and
 * idempotent; redeem takes a pessimistic lock on the coupon row so two
 * concurrent checkouts can't both push usageCount past usageLimit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    // ------------------------------------------------------------------
    // Admin CRUD
    // ------------------------------------------------------------------

    @Transactional
    public CouponResponse create(CouponRequest req) {
        couponRepository.findByCodeIgnoreCase(req.getCode()).ifPresent(c -> {
            throw new DuplicateResourceException("Coupon code already exists: " + req.getCode());
        });
        if (req.getValidUntil().isBefore(req.getValidFrom())) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        Coupon c = Coupon.builder()
                .code(req.getCode().toUpperCase())
                .description(req.getDescription())
                .discountType(req.getDiscountType())
                .discountValue(req.getDiscountValue())
                .maxDiscountAmount(req.getMaxDiscountAmount())
                .minOrderAmount(req.getMinOrderAmount())
                .currency(req.getCurrency() == null ? "INR" : req.getCurrency().toUpperCase())
                .validFrom(req.getValidFrom())
                .validUntil(req.getValidUntil())
                .usageLimit(req.getUsageLimit())
                .perUserLimit(req.getPerUserLimit())
                .active(req.getActive() == null || req.getActive())
                .build();
        return toResponse(couponRepository.save(c));
    }

    @Transactional
    public CouponResponse update(UUID id, CouponRequest req) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString()));
        if (!c.getCode().equalsIgnoreCase(req.getCode())) {
            couponRepository.findByCodeIgnoreCase(req.getCode()).ifPresent(other -> {
                if (!other.getId().equals(id))
                    throw new DuplicateResourceException("Coupon code already exists: " + req.getCode());
            });
            c.setCode(req.getCode().toUpperCase());
        }
        c.setDescription(req.getDescription());
        c.setDiscountType(req.getDiscountType());
        c.setDiscountValue(req.getDiscountValue());
        c.setMaxDiscountAmount(req.getMaxDiscountAmount());
        c.setMinOrderAmount(req.getMinOrderAmount());
        c.setCurrency(req.getCurrency() == null ? c.getCurrency() : req.getCurrency().toUpperCase());
        c.setValidFrom(req.getValidFrom());
        c.setValidUntil(req.getValidUntil());
        c.setUsageLimit(req.getUsageLimit());
        c.setPerUserLimit(req.getPerUserLimit());
        if (req.getActive() != null) c.setActive(req.getActive());
        return toResponse(couponRepository.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        if (!couponRepository.existsById(id)) {
            throw new ResourceNotFoundException("Coupon", id.toString());
        }
        couponRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public CouponResponse get(UUID id) {
        return toResponse(couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString())));
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> list(Pageable pageable) {
        return couponRepository.findAll(pageable).map(this::toResponse);
    }

    // ------------------------------------------------------------------
    // Public validate
    // ------------------------------------------------------------------

    /**
     * Stateless validation; no row update. Caller passes in subtotal+userId+currency.
     * Returns invalid + reason on any check failure (so callers can show a
     * caller-facing message).
     */
    @Transactional(readOnly = true)
    public ValidateCouponResponse validate(ValidateCouponRequest req) {
        Coupon c = couponRepository.findByCodeIgnoreCase(req.getCode()).orElse(null);
        if (c == null) return ValidateCouponResponse.invalid(req.getCode(), "Coupon not found");
        return validateAgainst(c, req.getUserId(), req.getSubtotal(), req.getCurrency());
    }

    // ------------------------------------------------------------------
    // Redeem (called by order-service after a successful payment)
    // ------------------------------------------------------------------

    /**
     * Atomically increments usageCount and writes a redemption row. Validation
     * is re-run inside the transaction so a stale "valid" reply from an
     * earlier validate-call can't slip past the usageLimit cap.
     */
    @Transactional
    public ValidateCouponResponse redeem(RedeemCouponRequest req) {
        Coupon c = couponRepository.findByCodeForUpdate(req.getCode())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", req.getCode()));

        ValidateCouponResponse v = validateAgainst(c, req.getUserId(), null, c.getCurrency());
        if (!v.isValid()) return v;

        c.setUsageCount(c.getUsageCount() + 1);
        couponRepository.save(c);

        CouponRedemption r = CouponRedemption.builder()
                .couponId(c.getId())
                .couponCode(c.getCode())
                .userId(req.getUserId())
                .orderId(req.getOrderId())
                .discountAmount(req.getDiscountAmount())
                .build();
        redemptionRepository.save(r);

        log.info("Coupon {} redeemed by user {} on order {} (-{})",
                c.getCode(), req.getUserId(), req.getOrderId(), req.getDiscountAmount());
        return ValidateCouponResponse.valid(c.getCode(), req.getDiscountAmount(), c.getDescription());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ValidateCouponResponse validateAgainst(Coupon c, UUID userId, BigDecimal subtotal, String currency) {
        if (!c.isActive()) return ValidateCouponResponse.invalid(c.getCode(), "Coupon is inactive");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(c.getValidFrom()))
            return ValidateCouponResponse.invalid(c.getCode(), "Coupon is not yet active");
        if (now.isAfter(c.getValidUntil()))
            return ValidateCouponResponse.invalid(c.getCode(), "Coupon has expired");
        if (c.getUsageLimit() != null && c.getUsageCount() >= c.getUsageLimit())
            return ValidateCouponResponse.invalid(c.getCode(), "Coupon usage limit reached");
        if (currency != null && !currency.equalsIgnoreCase(c.getCurrency()))
            return ValidateCouponResponse.invalid(c.getCode(),
                    "Coupon is in " + c.getCurrency() + ", cart is in " + currency);

        if (subtotal != null) {
            if (c.getMinOrderAmount() != null && subtotal.compareTo(c.getMinOrderAmount()) < 0) {
                return ValidateCouponResponse.invalid(c.getCode(),
                        "Minimum order amount is " + c.getCurrency() + " " + c.getMinOrderAmount().toPlainString());
            }
        }

        if (c.getPerUserLimit() != null && userId != null) {
            int used = redemptionRepository.countByCouponIdAndUserId(c.getId(), userId);
            if (used >= c.getPerUserLimit()) {
                return ValidateCouponResponse.invalid(c.getCode(), "You have already used this coupon");
            }
        }

        BigDecimal discount = subtotal == null ? BigDecimal.ZERO : computeDiscount(c, subtotal);
        return ValidateCouponResponse.valid(c.getCode(), discount, c.getDescription());
    }

    private BigDecimal computeDiscount(Coupon c, BigDecimal subtotal) {
        BigDecimal d = (c.getDiscountType() == DiscountType.PERCENT)
                ? subtotal.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : c.getDiscountValue();
        if (c.getMaxDiscountAmount() != null && d.compareTo(c.getMaxDiscountAmount()) > 0) {
            d = c.getMaxDiscountAmount();
        }
        // Discount cannot exceed the subtotal.
        if (d.compareTo(subtotal) > 0) d = subtotal;
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    private CouponResponse toResponse(Coupon c) {
        return CouponResponse.builder()
                .id(c.getId()).code(c.getCode()).description(c.getDescription())
                .discountType(c.getDiscountType()).discountValue(c.getDiscountValue())
                .maxDiscountAmount(c.getMaxDiscountAmount()).minOrderAmount(c.getMinOrderAmount())
                .currency(c.getCurrency())
                .validFrom(c.getValidFrom()).validUntil(c.getValidUntil())
                .usageLimit(c.getUsageLimit()).usageCount(c.getUsageCount())
                .perUserLimit(c.getPerUserLimit()).active(c.isActive())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .build();
    }
}
