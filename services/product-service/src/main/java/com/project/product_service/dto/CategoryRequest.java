package com.project.product_service.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryRequest {
    private String name;
    private String description;
    private String parentCategoryId; // Optional for subcategories
    private String imageUrl; // Optional
}
