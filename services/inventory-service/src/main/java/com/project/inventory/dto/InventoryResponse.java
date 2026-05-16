package com.project.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String id;
    private String productId;
    private String sku;
    private int quantity;
    private int reservedQuantity;
    private int availableQuantity;
    private int lowStockThreshold;
    private String location;
    private LocalDateTime lastRestockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
