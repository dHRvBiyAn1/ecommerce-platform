package com.project.order.client;

import com.project.order.client.dto.InventoryReservationResult;
import com.project.order.client.dto.StockReservationCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Calls inventory-service to reserve and release stock atomically.
 *
 * <p>Reserve happens on order creation (inside the saga); release happens on
 * cancel or payment failure as compensation.
 */
@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @PostMapping("/{productId}/reserve")
    InventoryReservationResult reserve(@PathVariable("productId") String productId,
                                       @RequestBody StockReservationCommand command);

    @PostMapping("/{productId}/release")
    InventoryReservationResult release(@PathVariable("productId") String productId,
                                       @RequestBody StockReservationCommand command);
}
