package com.project.inventory.application.mapper;

import com.project.inventory.generated.model.InventoryResponse;
import com.project.inventory.domain.model.InventoryItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMapperTest {
    private final InventoryMapper mapper = new InventoryMapper();

    @Test
    void calculatesAvailableQuantityFromPhysicalAndReservedStock() {
        InventoryItem item = new InventoryItem();
        item.setId("inventory-1");
        item.setProductId("product-1");
        item.setSku("SKU-1");
        item.setQuantity(12);
        item.setReservedQuantity(5);
        InventoryResponse response = mapper.toResponse(item);
        assertThat(response.getId()).isEqualTo("inventory-1");
        assertThat(response.getAvailableQuantity()).isEqualTo(7);
    }
}
