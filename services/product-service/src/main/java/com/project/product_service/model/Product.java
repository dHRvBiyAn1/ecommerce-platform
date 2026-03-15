package com.project.product_service.model;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @TextIndexed
    private String name;

    @TextIndexed
    private String description;

    private String categoryId; // reference to Category.id

    private BigDecimal price;

    private Integer stockQuantity;

    private List<String> imageUrls; // array of image URLs

    private UUID sellerId; // references user-service user ID (UUID)

    // Additional attributes can be stored as a Map<String, Object> if needed
    // private Map<String, Object> attributes;

    private boolean active = true; // for soft delete / visibility
}
