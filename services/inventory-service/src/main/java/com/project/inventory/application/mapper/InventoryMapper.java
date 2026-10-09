package com.project.inventory.application.mapper;

import com.project.inventory.domain.model.InventoryItem;
import com.project.inventory.generated.model.InventoryResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {
    public InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse()
                .id(item.getId())
                .productId(item.getProductId())
                .sku(item.getSku())
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .availableQuantity(item.getQuantity() - item.getReservedQuantity())
                .lowStockThreshold(item.getLowStockThreshold())
                .location(item.getLocation())
                .lastRestockedAt(item.getLastRestockedAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt());
    }
}
