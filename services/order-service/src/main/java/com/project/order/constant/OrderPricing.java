package com.project.order.constant;

import java.math.BigDecimal;

public final class OrderPricing {
    public static final String DEFAULT_CURRENCY = "INR";
    public static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    public static final BigDecimal SHIPPING_COST = new BigDecimal("49.00");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499.00");

    private OrderPricing() {
    }
}
