package com.project.inventory.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationRequest {
    @Positive(message = "Quantity must be positive")
    private int quantity;

    private String orderId;
}
