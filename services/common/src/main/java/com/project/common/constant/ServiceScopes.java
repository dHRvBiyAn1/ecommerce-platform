package com.project.common.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceScopes {
    public static final String COUPONS_READ = "coupons.read";
    public static final String COUPONS_WRITE = "coupons.write";
    public static final String INVENTORY_WRITE = "inventory.write";
    public static final String ORDERS_READ = "orders.read";

    public static final String AUTHORITY_COUPONS_READ = "SCOPE_" + COUPONS_READ;
    public static final String AUTHORITY_COUPONS_WRITE = "SCOPE_" + COUPONS_WRITE;
    public static final String AUTHORITY_INVENTORY_WRITE = "SCOPE_" + INVENTORY_WRITE;
    public static final String AUTHORITY_ORDERS_READ = "SCOPE_" + ORDERS_READ;
}
