package com.project.common.constant;

/**
 * Stable machine-readable error codes shared by all platform services.
 */
public enum ErrorCode {
    ACCESS_DENIED,
    BAD_CREDENTIALS,
    DUPLICATE_RESOURCE,
    FORBIDDEN,
    INSUFFICIENT_STOCK,
    INTERNAL_ERROR,
    METHOD_NOT_ALLOWED,
    NOT_FOUND,
    OAUTH2_PROVIDER_NOT_CONFIGURED,
    RESOURCE_NOT_FOUND,
    TYPE_MISMATCH,
    UNAUTHENTICATED,
    VALIDATION_FAILED;

    public String value() {
        return name();
    }
}
