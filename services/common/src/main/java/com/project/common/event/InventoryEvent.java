package com.project.common.event;

import java.time.LocalDateTime;

public class InventoryEvent {

    public enum Type {
        STOCK_RESERVED, STOCK_RELEASED, STOCK_DEPLETED, STOCK_RESTOCKED,
        LOW_STOCK_ALERT, OUT_OF_STOCK
    }

    public InventoryEvent() {}

    public InventoryEvent(Type type, String productId, String sku, int quantity, int remainingStock) {
        this.type = type;
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
        this.remainingStock = remainingStock;
    }

    public InventoryEvent(Type type, String productId, String sku, int quantity, int remainingStock, LocalDateTime timestamp) {
        this.type = type;
        this.productId = productId;
        this.sku = sku;
        this.quantity = quantity;
        this.remainingStock = remainingStock;
        this.timestamp = timestamp;
    }

    private Type type;
    private String productId;
    private String sku;
    private int quantity;
    private int remainingStock;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getRemainingStock() { return remainingStock; }
    public void setRemainingStock(int remainingStock) { this.remainingStock = remainingStock; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
