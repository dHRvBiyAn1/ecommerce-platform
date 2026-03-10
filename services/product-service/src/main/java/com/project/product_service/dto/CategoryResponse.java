package com.project.product_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private String id;
    private String name;
    private String description;
    private String parentCategoryId; // Optional for subcategories
    private String imageUrl; // Optional
}
