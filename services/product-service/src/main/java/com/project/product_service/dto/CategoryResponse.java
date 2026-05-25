package com.project.product_service.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private String parentCategoryId; // Optional for subcategories
    private String imageUrl; // Optional
}
