package com.project.inventory.api.dto.response;

import java.time.LocalDateTime;

public record InventoryResponse(
        String id,
        String productId,
        String sku,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        int lowStockThreshold,
        String location,
        LocalDateTime lastRestockedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
