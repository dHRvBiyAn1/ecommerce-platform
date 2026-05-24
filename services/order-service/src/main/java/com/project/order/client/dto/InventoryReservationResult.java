package com.project.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryReservationResult {
    private String productId;
    private int quantity;
    private int reservedQuantity;
    private int availableQuantity;
}
