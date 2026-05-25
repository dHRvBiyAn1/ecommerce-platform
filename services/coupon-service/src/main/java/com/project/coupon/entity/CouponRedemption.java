package com.project.coupon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per (coupon, user, order). Used to enforce {@code perUserLimit} and
 * to audit refund-time reversals (a future feature).
 */
@Entity
@Table(name = "coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRedemption {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "coupon_id", nullable = false, columnDefinition = "uuid")
    private UUID couponId;

    @Column(name = "coupon_code", nullable = false, length = 64)
    private String couponCode;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @CreationTimestamp
    @Column(name = "redeemed_at", updatable = false)
    private LocalDateTime redeemedAt;

    @jakarta.persistence.PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }
}
