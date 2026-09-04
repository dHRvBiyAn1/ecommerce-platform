package com.project.inventory.application.mapper;

import com.project.inventory.api.dto.response.InventoryResponse;
import com.project.inventory.domain.model.InventoryItem;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {
    public InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(
                item.getId(), item.getProductId(), item.getSku(), item.getQuantity(), item.getReservedQuantity(),
                item.getQuantity() - item.getReservedQuantity(), item.getLowStockThreshold(), item.getLocation(),
                item.getLastRestockedAt(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
