package com.project.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryEvent {
    private String eventId;
    private String eventType;
    private String productId;
    private String sku;
    private int quantityChange;
    private int newQuantity;
    private int reservedQuantity;
    private String orderId;
    private String location;
    private LocalDateTime timestamp;
}
