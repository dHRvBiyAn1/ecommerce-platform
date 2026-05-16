package com.project.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProductEvent {

    public enum Type {
        CREATED, UPDATED, DELETED, STOCK_CHANGED, PRICE_CHANGED, ACTIVATED, DEACTIVATED
    }

    public ProductEvent() {}

    public ProductEvent(Type type, String productId, String sku, UUID sellerId) {
        this.type = type;
        this.productId = productId;
        this.sku = sku;
        this.sellerId = sellerId;
    }

    public ProductEvent(Type type, String productId, String sku, UUID sellerId, LocalDateTime timestamp) {
        this.type = type;
        this.productId = productId;
        this.sku = sku;
        this.sellerId = sellerId;
        this.timestamp = timestamp;
    }

    private Type type;
    private String productId;
    private String sku;
    private UUID sellerId;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public UUID getSellerId() { return sellerId; }
    public void setSellerId(UUID sellerId) { this.sellerId = sellerId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
