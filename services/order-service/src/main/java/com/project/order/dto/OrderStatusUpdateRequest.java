package com.project.order.dto;

import com.project.order.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateRequest {
    @NotNull(message = "Status is required")
    private OrderStatus status;

    private String notes;
}
