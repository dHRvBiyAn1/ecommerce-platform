package com.project.product_service.model;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;
    private String parentCategoryId;
    private String imageUrl;
}