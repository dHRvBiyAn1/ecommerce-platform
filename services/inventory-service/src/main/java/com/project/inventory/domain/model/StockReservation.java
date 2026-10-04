package com.project.inventory.domain.model;

import java.time.LocalDateTime;

public record StockReservation(int quantity, ReservationStatus status, LocalDateTime updatedAt) {
}
