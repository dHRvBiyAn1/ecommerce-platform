package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "inventoryEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryEvent extends BaseEvent {

    public enum Type {
        STOCK_RESERVED, STOCK_COMMITTED, STOCK_RELEASED, STOCK_ADDED, STOCK_DEPLETED,
        LOW_STOCK_ALERT, OUT_OF_STOCK, RESTOCKED, INVENTORY_CREATED,
        INVENTORY_UPDATED, INVENTORY_DELETED
    }

    private Type type;
    private String productId;
    private String sku;
    private String warehouseId;
    private int quantityChange;
    private int newQuantity;
    private int reservedQuantity;
    private int availableQuantity;
    private String orderId;
    private String productName;
}
