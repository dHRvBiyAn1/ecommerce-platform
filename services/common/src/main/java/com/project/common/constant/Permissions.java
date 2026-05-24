package com.project.common.constant;

/**
 * Permission strings used for fine-grained {@code @PreAuthorize("hasAuthority(...)")}
 * checks. The DataInitializer in auth-service must keep these in sync.
 */
public final class Permissions {

    public static final String USERS_READ = "admin:users:read";
    public static final String USERS_WRITE = "admin:users:write";
    public static final String ROLES_READ = "admin:roles:read";
    public static final String ROLES_WRITE = "admin:roles:write";

    public static final String PRODUCTS_CREATE = "products:create";
    public static final String PRODUCTS_READ = "products:read";
    public static final String PRODUCTS_UPDATE = "products:update";
    public static final String PRODUCTS_DELETE = "products:delete";

    public static final String ORDERS_CREATE = "orders:create";
    public static final String ORDERS_READ = "orders:read";
    public static final String ORDERS_UPDATE = "orders:update";
    public static final String ORDERS_CANCEL = "orders:cancel";
    public static final String ORDERS_REFUND = "orders:refund";

    public static final String INVENTORY_READ = "inventory:read";
    public static final String INVENTORY_WRITE = "inventory:write";
    public static final String INVENTORY_RESERVE = "inventory:reserve";

    public static final String PAYMENTS_READ = "payments:read";
    public static final String PAYMENTS_REFUND = "payments:refund";
    public static final String PAYMENTS_PROCESS = "payments:process";

    public static final String NOTIFICATIONS_READ = "notifications:read";
    public static final String NOTIFICATIONS_SEND = "notifications:send";

    public static final String COUPONS_READ = "coupons:read";
    public static final String COUPONS_WRITE = "coupons:write";

    public static final String CMS_WRITE = "cms:write";
    public static final String ANALYTICS_READ = "analytics:read";
    public static final String AUDIT_READ = "audit:read";

    private Permissions() {}
}
