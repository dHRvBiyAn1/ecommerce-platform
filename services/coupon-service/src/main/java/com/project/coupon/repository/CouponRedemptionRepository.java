package com.project.coupon.repository;

import com.project.coupon.entity.CouponRedemption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    Optional<CouponRedemption> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CouponRedemption r WHERE r.orderId = :orderId")
    Optional<CouponRedemption> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Query("SELECT COUNT(r) FROM CouponRedemption r " +
            "WHERE r.couponId = :couponId AND r.userId = :userId " +
            "AND r.status IN (com.project.coupon.entity.RedemptionStatus.RESERVED, " +
            "com.project.coupon.entity.RedemptionStatus.COMMITTED)")
    int countActiveByCouponIdAndUserId(@Param("couponId") UUID couponId, @Param("userId") UUID userId);
}
