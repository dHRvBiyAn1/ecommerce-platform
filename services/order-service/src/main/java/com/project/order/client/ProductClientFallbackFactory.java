package com.project.order.client;

import com.project.order.client.dto.ProductSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for {@link ProductClient}. Handles catalog outages
 * and circuit-breaker open states.
 *
 * <p>We must throw an exception on catalog inquiries during outages to prevent billing items
 * at zero prices or snapshotting corrupted/empty catalog configurations into orders.
 */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return new ProductClient() {
            @Override
            public ProductSummary getProduct(String id) {
                log.error("Resilience4j Circuit Breaker / Timeout triggered for product check of ID={}. Cause: {}", id, cause.getMessage());
                // Throw an exception so order creation aborts immediately
                throw new IllegalStateException("Product catalog is currently offline. Order checkout cannot proceed.");
            }
        };
    }
}
