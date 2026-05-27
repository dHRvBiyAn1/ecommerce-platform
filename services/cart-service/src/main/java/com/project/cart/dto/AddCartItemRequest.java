package com.project.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/v1/cart/items}. The unit price is supplied by the
 * caller (the catalog page already has it), but the order saga re-validates
 * against {@code product-service} at checkout, so a malicious client can't
 * pay less than the catalog price.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @NotBlank
    private String productId;

    @NotBlank
    private String sku;

    @NotBlank
    private String productName;

    private String imageUrl;

    @NotNull
    @Positive
    private BigDecimal unitPrice;

    @Min(1)
    private int quantity;

    private String currency;
}
