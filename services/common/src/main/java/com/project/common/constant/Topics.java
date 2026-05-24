package com.project.common.constant;

/**
 * Centralized Kafka topic names. Every producer/consumer MUST reference these constants
 * instead of hardcoding strings to keep the event bus consistent.
 */
public final class Topics {

    public static final String USER_EVENTS = "user-events";
    public static final String PRODUCT_EVENTS = "product-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";
    public static final String CART_EVENTS = "cart-events";
    public static final String REVIEW_EVENTS = "review-events";
    public static final String COUPON_EVENTS = "coupon-events";
    public static final String SHIPPING_EVENTS = "shipping-events";
    public static final String AUDIT_EVENTS = "audit-events";

    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {}
}
