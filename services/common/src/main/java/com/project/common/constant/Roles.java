package com.project.common.constant;

/**
 * Centralized role names. Roles in JWT tokens follow the canonical Spring Security
 * convention with the {@code ROLE_} prefix. Downstream services rely on this constant
 * to avoid mismatched authority strings.
 */
public final class Roles {

    public static final String CUSTOMER = "ROLE_CUSTOMER";
    public static final String SELLER = "ROLE_SELLER";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String SUPPORT = "ROLE_SUPPORT";
    public static final String SYSTEM = "ROLE_SYSTEM"; // service-to-service token

    private Roles() {}
}
