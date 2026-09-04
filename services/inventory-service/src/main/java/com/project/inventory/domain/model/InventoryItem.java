package com.project.inventory.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
    @Id private String id;
    @Indexed(unique = true) private String productId;
    @Indexed private String sku;
    private int quantity;
    private int reservedQuantity;
    private Map<String, StockReservation> reservations = new HashMap<>();
    private int lowStockThreshold = 10;
    private String location;
    private LocalDateTime lastRestockedAt;
    @CreatedDate private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
