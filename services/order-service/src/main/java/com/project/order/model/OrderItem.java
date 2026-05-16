package com.project.order.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    private String productId;
    private String sku;
    private String productName;
    private String imageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal totalPrice;
}
