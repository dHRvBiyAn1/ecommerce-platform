package com.project.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Subset of the product-service ProductResponse — only the fields order-service
 * needs at order creation. We tolerate unknown fields so a richer product
 * response from a future product-service version doesn't break us.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSummary {
    private String id;
    private String sku;
    private String name;
    private BigDecimal price;
    private Integer stockQuantity;
    private UUID sellerId;
    private boolean active;
}
