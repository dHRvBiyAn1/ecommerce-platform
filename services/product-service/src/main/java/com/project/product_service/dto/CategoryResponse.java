package com.project.product_service.dto;

import java.time.LocalDateTime;

public record CategoryResponse(
        String id,
        String name,
        String description,
        String parentCategoryId,
        String imageUrl,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
