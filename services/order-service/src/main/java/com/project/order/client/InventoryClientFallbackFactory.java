package com.project.order.client;

import com.project.order.client.dto.InventoryReservationResult;
import com.project.order.client.dto.StockReservationCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for {@link InventoryClient}. Handles transient failures
 * and circuit-breaker open states.
 *
 * <p>For reserve operations, we must throw an exception to abort order checkout
 * and prevent overselling when inventory-service is offline.
 * For release operations, we log the failure clearly so that manual/outbox compensation audits
 * can resolve discrepancies.
 */
@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {
            @Override
            public InventoryReservationResult reserve(String productId, StockReservationCommand command) {
                log.error("Resilience4j Circuit Breaker / Timeout triggered for stock reservation of product={}, quantity={} in order={}. Cause: {}",
                        productId, command.getQuantity(), command.getOrderId(), cause.getMessage());
                // Throw an exception so OrderServiceImpl catches it and aborts the checkout cleanly
                throw new IllegalStateException("Inventory service is currently unavailable. Checkout aborted to prevent overselling.");
            }

            @Override
            public InventoryReservationResult release(String productId, StockReservationCommand command) {
                log.error("Resilience4j Circuit Breaker / Timeout triggered for stock release of product={}, quantity={} in order={}. Compensation failed! Cause: {}",
                        productId, command.getQuantity(), command.getOrderId(), cause.getMessage());
                // Log and return a dummy failure state so that OrderServiceImpl logs the compensation error
                throw new IllegalStateException("Compensation release failed due to inventory service outage.");
            }
        };
    }
}
