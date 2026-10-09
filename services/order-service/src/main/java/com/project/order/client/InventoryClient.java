package com.project.order.client;

import com.project.order.generated.integration.inventory.model.InventoryResponse;
import com.project.order.generated.integration.inventory.model.StockReservationRequest;
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
@FeignClient(name = "inventory-service", path = "/api/v1/inventory", fallbackFactory = InventoryClientFallbackFactory.class)
public interface InventoryClient {

    @PostMapping("/{productId}/reserve")
    InventoryResponse reserve(@PathVariable("productId") String productId,
                              @RequestBody StockReservationRequest command);

    @PostMapping("/{productId}/commit")
    InventoryResponse commit(@PathVariable("productId") String productId,
                             @RequestBody StockReservationRequest command);

    @PostMapping("/{productId}/release")
    InventoryResponse release(@PathVariable("productId") String productId,
                              @RequestBody StockReservationRequest command);
}
