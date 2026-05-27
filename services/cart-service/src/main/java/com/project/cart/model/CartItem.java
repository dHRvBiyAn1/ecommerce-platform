package com.project.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line item in a {@link Cart}. Stored embedded in the cart document.
 *
 * <p>The unit price is captured at the time the item is added so that the cart
 * total stays stable even if the seller bumps the catalog price afterwards.
 * Price is re-validated against {@code product-service} during checkout (the
 * order saga is the authoritative source).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    private String productId;
    private String sku;
    private String productName;
    private String imageUrl;
    private BigDecimal unitPrice;
    private int quantity;
}
