package com.project.coupon.repository;

import com.project.coupon.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    int countByCouponIdAndUserId(UUID couponId, UUID userId);
}
