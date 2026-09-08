package com.project.cart.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cart projection returned to the client. Includes the computed subtotal /
 * total so the frontend doesn't have to re-derive them.
 */
public record CartResponse(
        String id,
        UUID userId,
        List<Item> items,
        String currency,
        String appliedCouponCode,
        BigDecimal appliedDiscountAmount,
        BigDecimal subtotal,
        BigDecimal total,
        int itemCount,
        LocalDateTime updatedAt) {

    public record Item(String productId, String sku, String productName, String imageUrl,
                       BigDecimal unitPrice, int quantity) {}
}
