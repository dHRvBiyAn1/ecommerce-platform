package com.project.product_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String categoryId,
        String categoryName,
        BigDecimal price,
        Integer stockQuantity,
        List<String> imageUrls,
        UUID sellerId,
        boolean active,
        String approvalStatus,
        String rejectionReason,
        Map<String, Object> attributes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
