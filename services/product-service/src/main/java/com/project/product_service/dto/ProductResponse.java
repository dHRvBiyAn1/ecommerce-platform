package com.project.product_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponse {
    private String id;
    private String sku;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName; // optional: populate with category name
    private BigDecimal price;
    private Integer stockQuantity;
    private List<String> imageUrls;
    private UUID sellerId;
    private boolean active;
}
