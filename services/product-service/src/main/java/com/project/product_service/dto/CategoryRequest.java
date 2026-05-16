package com.project.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    private String name;
    private String description;
    private String parentCategoryId; // Optional for subcategories
    private String imageUrl; // Optional
}
