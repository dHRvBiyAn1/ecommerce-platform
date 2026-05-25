package com.project.coupon.entity;

public enum DiscountType {
    /** Percent off the subtotal (capped by {@code maxDiscountAmount} if set). */
    PERCENT,
    /** Flat amount off in {@code currency}. */
    FIXED
}
