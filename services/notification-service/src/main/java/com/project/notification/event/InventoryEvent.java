package com.project.notification.event;

import lombok.Data;

import java.time.Instant;

@Data
public class InventoryEvent {
    private String type;
    private String productId;
    private String sku;
    private String productName;
    private int quantity;
    private int remainingStock;
    private Instant timestamp;
}
