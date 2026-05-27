package com.project.product_service.model;

/**
 * Marketplace approval status. New seller-created products start as
 * {@code PENDING}; an admin reviews and either {@code APPROVED}s or
 * {@code REJECTED}s. Public catalog only shows APPROVED + active products;
 * sellers always see their own (regardless of approval).
 *
 * <p>Admin-created products are auto-APPROVED.
 */
public enum ProductApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
