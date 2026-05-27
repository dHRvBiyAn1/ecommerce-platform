package com.project.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single cart per user. {@code userId} is unique-indexed; a user has at most
 * one active cart at a time. Add/update operations mutate {@link #items} in
 * place; the optimistic-locking {@code @Version} field stops two concurrent
 * tabs from clobbering each other's changes.
 *
 * <p>An applied coupon stores its code and the resolved discount snapshot
 * (so we don't have to call coupon-service on every cart-read). The discount
 * is re-validated when the cart is checked out.
 */
@Document(collection = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    private String id;

    @Indexed(unique = true)
    private UUID userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /** Currency code; lifted from items at first add. ISO-4217 (INR for now). */
    private String currency;

    /** Last applied coupon (nullable). */
    private String appliedCouponCode;

    /** Discount captured when the coupon was applied (nullable). */
    private BigDecimal appliedDiscountAmount;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
