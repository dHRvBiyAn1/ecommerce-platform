package com.project.cart.dto;

import com.project.cart.model.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cart projection returned to the client. Includes the computed subtotal /
 * total so the frontend doesn't have to re-derive them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {
    private String id;
    private UUID userId;
    private List<CartItem> items;
    private String currency;
    private String appliedCouponCode;
    private BigDecimal appliedDiscountAmount;
    private BigDecimal subtotal;
    private BigDecimal total;
    private int itemCount;
    private LocalDateTime updatedAt;
}
