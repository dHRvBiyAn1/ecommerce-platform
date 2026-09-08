package com.project.coupon.service;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponReservationRequest;
import com.project.coupon.dto.CouponReservationResponse;
import com.project.coupon.dto.CouponResponse;
import com.project.coupon.dto.CouponTransitionRequest;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.dto.ValidateCouponRequest;
import com.project.coupon.dto.ValidateCouponResponse;
import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.CouponRedemption;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.entity.RedemptionStatus;
import com.project.coupon.exception.CouponReservationConflictException;
import com.project.coupon.exception.CouponUnavailableException;
import com.project.coupon.mapper.CouponMapper;
import com.project.coupon.repository.CouponRedemptionRepository;
import com.project.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CouponMapper couponMapper;

    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = couponMapper.normalizeCode(request.code());
        couponRepository.findByCode(code).ifPresent(coupon -> {
            throw new DuplicateResourceException("Coupon code already exists: " + code);
        });
        try {
            return couponMapper.toResponse(couponRepository.saveAndFlush(couponMapper.toEntity(request)));
        } catch (DataIntegrityViolationException exception) {
            throw translateCouponCodeViolation(exception, code);
        }
    }

    @Transactional
    public CouponResponse update(UUID id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString()));
        String code = couponMapper.normalizeCode(request.code());
        if (!coupon.getCode().equals(code)) {
            couponRepository.findByCode(code).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new DuplicateResourceException("Coupon code already exists: " + request.code());
                }
            });
        }
        couponMapper.update(coupon, request);
        try {
            return couponMapper.toResponse(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException exception) {
            throw translateCouponCodeViolation(exception, code);
        }
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
        return couponMapper.toResponse(couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString())));
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> list(Pageable pageable) {
        return couponRepository.findAll(pageable).map(couponMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ValidateCouponResponse validate(ValidateCouponRequest request) {
        Coupon coupon = couponRepository.findByCode(couponMapper.normalizeCode(request.code())).orElse(null);
        if (coupon == null) {
            return ValidateCouponResponse.invalid(request.code(), "Coupon not found");
        }
        return validateAgainst(coupon, request.userId(), request.subtotal(), request.currency());
    }

    @Transactional
    public CouponReservationResponse reserve(CouponReservationRequest request) {
        String code = couponMapper.normalizeCode(request.code());
        CouponRedemption existing = redemptionRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            requireOwnership(existing, code, request.userId());
            if (existing.getStatus() == RedemptionStatus.RELEASED) {
                throw new CouponReservationConflictException("Released coupon reservation cannot be reused");
            }
            return couponMapper.toReservationResponse(existing);
        }

        Coupon coupon = couponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", code));

        existing = redemptionRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            requireOwnership(existing, code, request.userId());
            if (existing.getStatus() == RedemptionStatus.RELEASED) {
                throw new CouponReservationConflictException("Released coupon reservation cannot be reused");
            }
            return couponMapper.toReservationResponse(existing);
        }

        ValidateCouponResponse validation = validateAgainst(
                coupon, request.userId(), request.subtotal(), request.currency());
        if (!validation.valid()) {
            throw new CouponUnavailableException(validation.reason());
        }

        coupon.setReservedCount(coupon.getReservedCount() + 1);
        couponRepository.save(coupon);

        CouponRedemption reservation = CouponRedemption.builder()
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .userId(request.userId())
                .orderId(request.orderId())
                .discountAmount(validation.discountAmount())
                .status(RedemptionStatus.RESERVED)
                .build();
        CouponRedemption saved = redemptionRepository.save(reservation);
        log.info("Coupon {} reserved for order {}", coupon.getCode(), request.orderId());
        return couponMapper.toReservationResponse(saved);
    }

    @Transactional
    public CouponReservationResponse commit(CouponTransitionRequest request) {
        CouponRedemption redemption = findReservationForUpdate(request.orderId());
        requireOwnership(redemption, couponMapper.normalizeCode(request.code()), request.userId());
        if (redemption.getStatus() == RedemptionStatus.COMMITTED) {
            return couponMapper.toReservationResponse(redemption);
        }
        if (redemption.getStatus() == RedemptionStatus.RELEASED) {
            throw new CouponReservationConflictException("Released coupon reservation cannot be committed");
        }

        Coupon coupon = findCouponForUpdate(redemption.getCouponId());
        if (coupon.getReservedCount() <= 0) {
            throw new CouponReservationConflictException("Coupon reservation capacity is inconsistent");
        }
        coupon.setReservedCount(coupon.getReservedCount() - 1);
        coupon.setUsageCount(coupon.getUsageCount() + 1);
        couponRepository.save(coupon);

        redemption.setStatus(RedemptionStatus.COMMITTED);
        redemption.setCommittedAt(LocalDateTime.now());
        CouponRedemption saved = redemptionRepository.save(redemption);
        log.info("Coupon {} committed for order {}", redemption.getCouponCode(), request.orderId());
        return couponMapper.toReservationResponse(saved);
    }

    @Transactional
    public CouponReservationResponse release(CouponTransitionRequest request) {
        CouponRedemption redemption = findReservationForUpdate(request.orderId());
        requireOwnership(redemption, couponMapper.normalizeCode(request.code()), request.userId());
        if (redemption.getStatus() == RedemptionStatus.RELEASED) {
            return couponMapper.toReservationResponse(redemption);
        }
        if (redemption.getStatus() == RedemptionStatus.COMMITTED) {
            throw new CouponReservationConflictException("Committed coupon redemption cannot be released");
        }

        Coupon coupon = findCouponForUpdate(redemption.getCouponId());
        if (coupon.getReservedCount() <= 0) {
            throw new CouponReservationConflictException("Coupon reservation capacity is inconsistent");
        }
        coupon.setReservedCount(coupon.getReservedCount() - 1);
        couponRepository.save(coupon);

        redemption.setStatus(RedemptionStatus.RELEASED);
        redemption.setReleasedAt(LocalDateTime.now());
        CouponRedemption saved = redemptionRepository.save(redemption);
        log.info("Coupon {} released for order {}", redemption.getCouponCode(), request.orderId());
        return couponMapper.toReservationResponse(saved);
    }

    /**
     * Backward-compatible direct redemption for callers that only apply the
     * coupon after payment has succeeded.
     */
    @Transactional
    public ValidateCouponResponse redeem(RedeemCouponRequest request) {
        String code = couponMapper.normalizeCode(request.code());
        CouponRedemption existing = redemptionRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            requireOwnership(existing, code, request.userId());
            if (existing.getStatus() == RedemptionStatus.RELEASED) {
                throw new CouponReservationConflictException("Released coupon reservation cannot be redeemed");
            }
            if (existing.getStatus() == RedemptionStatus.RESERVED) {
                commit(new CouponTransitionRequest(request.code(), request.userId(), request.orderId()));
            }
            return ValidateCouponResponse.valid(
                    existing.getCouponCode(), existing.getDiscountAmount(), null);
        }

        Coupon coupon = couponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", code));
        existing = redemptionRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            requireOwnership(existing, code, request.userId());
            if (existing.getStatus() == RedemptionStatus.RELEASED) {
                throw new CouponReservationConflictException("Released coupon reservation cannot be redeemed");
            }
            if (existing.getStatus() == RedemptionStatus.RESERVED) {
                if (coupon.getReservedCount() <= 0) {
                    throw new CouponReservationConflictException("Coupon reservation capacity is inconsistent");
                }
                coupon.setReservedCount(coupon.getReservedCount() - 1);
                coupon.setUsageCount(coupon.getUsageCount() + 1);
                couponRepository.save(coupon);
                existing.setStatus(RedemptionStatus.COMMITTED);
                existing.setCommittedAt(LocalDateTime.now());
                redemptionRepository.save(existing);
            }
            return ValidateCouponResponse.valid(
                    existing.getCouponCode(), existing.getDiscountAmount(), coupon.getDescription());
        }
        ValidateCouponResponse validation = validateAgainst(coupon, request.userId(), null, coupon.getCurrency());
        if (!validation.valid()) {
            return validation;
        }

        coupon.setUsageCount(coupon.getUsageCount() + 1);
        couponRepository.save(coupon);
        CouponRedemption redemption = CouponRedemption.builder()
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .userId(request.userId())
                .orderId(request.orderId())
                .discountAmount(request.discountAmount())
                .status(RedemptionStatus.COMMITTED)
                .committedAt(LocalDateTime.now())
                .build();
        redemptionRepository.save(redemption);
        return ValidateCouponResponse.valid(coupon.getCode(), request.discountAmount(), coupon.getDescription());
    }

    private CouponRedemption findReservationForUpdate(String orderId) {
        return redemptionRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon reservation", orderId));
    }

    private Coupon findCouponForUpdate(UUID couponId) {
        return couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", couponId.toString()));
    }

    private void requireOwnership(CouponRedemption redemption, String code, UUID userId) {
        if (!redemption.getUserId().equals(userId)
                || !redemption.getCouponCode().equalsIgnoreCase(code)) {
            throw new CouponReservationConflictException(
                    "Order is already associated with another coupon reservation");
        }
    }

    private RuntimeException translateCouponCodeViolation(
            DataIntegrityViolationException exception, String code) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && ("coupons_code_key".equals(violation.getConstraintName())
                    || "uq_coupons_normalized_code".equals(violation.getConstraintName()))) {
                return new DuplicateResourceException("Coupon code already exists: " + code);
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private ValidateCouponResponse validateAgainst(
            Coupon coupon, UUID userId, BigDecimal subtotal, String currency) {
        if (!coupon.isActive()) return invalid(coupon, "Coupon is inactive");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom())) return invalid(coupon, "Coupon is not yet active");
        if (now.isAfter(coupon.getValidUntil())) return invalid(coupon, "Coupon has expired");
        if (coupon.getUsageLimit() != null
                && coupon.getUsageCount() + coupon.getReservedCount() >= coupon.getUsageLimit()) {
            return invalid(coupon, "Coupon usage limit reached");
        }
        if (currency != null && !currency.equalsIgnoreCase(coupon.getCurrency())) {
            return invalid(coupon, "Coupon is in " + coupon.getCurrency() + ", cart is in " + currency);
        }
        if (subtotal != null && coupon.getMinOrderAmount() != null
                && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return invalid(coupon, "Minimum order amount is " + coupon.getCurrency() + " "
                    + coupon.getMinOrderAmount().toPlainString());
        }
        if (coupon.getPerUserLimit() != null && userId != null
                && redemptionRepository.countActiveByCouponIdAndUserId(coupon.getId(), userId)
                >= coupon.getPerUserLimit()) {
            return invalid(coupon, "You have already used this coupon");
        }
        BigDecimal discount = subtotal == null ? BigDecimal.ZERO : computeDiscount(coupon, subtotal);
        return ValidateCouponResponse.valid(coupon.getCode(), discount, coupon.getDescription());
    }

    private ValidateCouponResponse invalid(Coupon coupon, String reason) {
        return ValidateCouponResponse.invalid(coupon.getCode(), reason);
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount = coupon.getDiscountType() == DiscountType.PERCENT
                ? subtotal.multiply(coupon.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue();
        if (coupon.getMaxDiscountAmount() != null
                && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }
        if (discount.compareTo(subtotal) > 0) discount = subtotal;
        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
