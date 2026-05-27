package com.project.inventory.config;

import com.project.common.sampledata.SampleIds;
import com.project.inventory.model.InventoryItem;
import com.project.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds an inventory row per sample product. The productId/SKU pair matches
 * what product-service seeds, so the order saga's {@code InventoryClient.reserve}
 * will find a record to operate on. Quantities are deliberately varied so
 * the storefront shows a mix of "in stock", "low stock" and "sold out".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (inventoryRepository.count() > 0) {
            log.info("Inventory already populated ({}); skipping", inventoryRepository.count());
            return;
        }

        int i = 0;
        for (var p : SampleIds.PRODUCTS) {
            int qty = quantityFor(i++);
            InventoryItem item = new InventoryItem();
            item.setProductId(p.id());
            item.setSku(p.sku());
            item.setQuantity(qty);
            item.setReservedQuantity(0);
            item.setLowStockThreshold(5);
            item.setLocation("WH-IN-MUM-01");
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            if (qty > 0) item.setLastRestockedAt(LocalDateTime.now());
            inventoryRepository.save(item);
        }
        log.info("Seeded {} inventory items", SampleIds.PRODUCTS.size());
    }

    /**
     * Mix: ~70% healthy stock, ~20% low (1-4 units), ~10% sold out.
     */
    private static int quantityFor(int index) {
        if (index % 11 == 0) return 0;             // sold out
        if (index % 7 == 0)  return 1 + (index % 4); // low stock
        return 30 + (index % 12);                  // healthy
    }
}
