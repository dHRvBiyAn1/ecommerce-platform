package com.project.coupon.service;

import com.project.coupon.dto.CouponReservationRequest;
import com.project.coupon.dto.CouponTransitionRequest;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.CouponRedemption;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.entity.RedemptionStatus;
import com.project.coupon.exception.CouponReservationConflictException;
import com.project.coupon.mapper.CouponMapper;
import com.project.coupon.repository.CouponRedemptionRepository;
import com.project.coupon.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceLifecycleTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository redemptionRepository;

    private CouponService service;
    private Coupon coupon;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new CouponService(couponRepository, redemptionRepository, new CouponMapper());
        userId = UUID.randomUUID();
        coupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("SAVE10")
                .description("Ten percent off")
                .discountType(DiscountType.PERCENT)
                .discountValue(new BigDecimal("10.00"))
                .currency("INR")
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .usageLimit(5)
                .usageCount(1)
                .reservedCount(0)
                .perUserLimit(2)
                .active(true)
                .build();
    }

    @Test
    void reserveHoldsCapacityWithoutConsumingUsage() {
        when(redemptionRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countActiveByCouponIdAndUserId(coupon.getId(), userId)).thenReturn(0);
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.reserve(new CouponReservationRequest(
                "SAVE10", userId, "order-1", new BigDecimal("500.00"), "INR"));

        assertThat(result.status()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(result.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(coupon.getUsageCount()).isEqualTo(1);
        assertThat(coupon.getReservedCount()).isEqualTo(1);
    }

    @Test
    void repeatedReserveForSameOrderReturnsExistingReservation() {
        CouponRedemption existing = reservation("order-1", RedemptionStatus.RESERVED);
        when(redemptionRepository.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        var result = service.reserve(new CouponReservationRequest(
                "save10", userId, "order-1", new BigDecimal("500.00"), "inr"));

        assertThat(result.status()).isEqualTo(RedemptionStatus.RESERVED);
        verify(couponRepository, never()).findByCodeForUpdate(any());
        verify(redemptionRepository, never()).save(any());
    }

    @Test
    void orderCannotBeReservedByAnotherUser() {
        CouponRedemption existing = reservation("order-1", RedemptionStatus.RESERVED);
        when(redemptionRepository.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.reserve(new CouponReservationRequest(
                "SAVE10", UUID.randomUUID(), "order-1", new BigDecimal("500.00"), "INR")))
                .isInstanceOf(CouponReservationConflictException.class);
    }

    @Test
    void commitConsumesReservedCapacityExactlyOnce() {
        CouponRedemption existing = reservation("order-1", RedemptionStatus.RESERVED);
        coupon.setReservedCount(1);
        when(redemptionRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(existing));
        when(couponRepository.findByIdForUpdate(coupon.getId())).thenReturn(Optional.of(coupon));
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.commit(new CouponTransitionRequest("SAVE10", userId, "order-1"));

        assertThat(result.status()).isEqualTo(RedemptionStatus.COMMITTED);
        assertThat(coupon.getReservedCount()).isZero();
        assertThat(coupon.getUsageCount()).isEqualTo(2);
        assertThat(existing.getCommittedAt()).isNotNull();

        when(redemptionRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(existing));
        service.commit(new CouponTransitionRequest("SAVE10", userId, "order-1"));

        assertThat(coupon.getUsageCount()).isEqualTo(2);
    }

    @Test
    void releaseRestoresCapacityAndIsIdempotent() {
        CouponRedemption existing = reservation("order-1", RedemptionStatus.RESERVED);
        coupon.setReservedCount(1);
        when(redemptionRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(existing));
        when(couponRepository.findByIdForUpdate(coupon.getId())).thenReturn(Optional.of(coupon));
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.release(new CouponTransitionRequest("SAVE10", userId, "order-1"));

        assertThat(result.status()).isEqualTo(RedemptionStatus.RELEASED);
        assertThat(coupon.getReservedCount()).isZero();
        assertThat(coupon.getUsageCount()).isEqualTo(1);

        when(redemptionRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(existing));
        service.release(new CouponTransitionRequest("SAVE10", userId, "order-1"));

        assertThat(coupon.getReservedCount()).isZero();
    }

    @Test
    void legacyRedeemIsIdempotentForTheSameOrder() {
        when(redemptionRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countActiveByCouponIdAndUserId(coupon.getId(), userId)).thenReturn(0);
        when(redemptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.redeem(new RedeemCouponRequest(
                "SAVE10", userId, "order-1", new BigDecimal("50.00")));

        assertThat(result.valid()).isTrue();
        assertThat(coupon.getUsageCount()).isEqualTo(2);
        assertThat(coupon.getReservedCount()).isZero();

        ArgumentCaptor<CouponRedemption> saved = ArgumentCaptor.forClass(CouponRedemption.class);
        verify(redemptionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RedemptionStatus.COMMITTED);
    }

    @Test
    void legacyRedeemRechecksOrderAfterAcquiringCouponLock() {
        CouponRedemption committed = reservation("order-1", RedemptionStatus.COMMITTED);
        when(redemptionRepository.findByOrderId("order-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(committed));
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));

        var result = service.redeem(new RedeemCouponRequest(
                "SAVE10", userId, "order-1", new BigDecimal("50.00")));

        assertThat(result.valid()).isTrue();
        assertThat(coupon.getUsageCount()).isEqualTo(1);
        verify(couponRepository, never()).save(any());
        verify(redemptionRepository, never()).save(any());
    }

    private CouponRedemption reservation(String orderId, RedemptionStatus status) {
        return CouponRedemption.builder()
                .id(UUID.randomUUID())
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .userId(userId)
                .orderId(orderId)
                .discountAmount(new BigDecimal("50.00"))
                .status(status)
                .reservedAt(LocalDateTime.now())
                .build();
    }
}
