package com.project.product_service.config;

import com.project.common.sampledata.SampleIds;
import com.project.product_service.model.Category;
import com.project.product_service.model.Product;
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductDocument;
import com.project.product_service.search.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Idempotent sample-data seeder for product-service.
 *
 * <p>Seeds 8 categories and 30 products using stable IDs from
 * {@link SampleIds}. Skipped when {@code seed.enabled=false} or when
 * either collection already has more than a handful of rows (so re-running
 * with existing real data is safe).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;

        seedCategories();
        seedProducts();
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            log.info("Categories already populated ({}); skipping", categoryRepository.count());
            return;
        }
        for (var c : SampleIds.CATEGORIES) {
            Category cat = new Category();
            cat.setId(c.id());
            cat.setName(c.name());
            cat.setDescription(c.description());
            cat.setImageUrl(null);
            categoryRepository.save(cat);
        }
        log.info("Seeded {} categories", SampleIds.CATEGORIES.size());
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            log.info("Products already populated ({}); skipping", productRepository.count());
            return;
        }
        int defaultStock = 25;
        for (var p : SampleIds.PRODUCTS) {
            Product product = new Product();
            product.setId(p.id());
            product.setSku(p.sku());
            product.setName(p.name());
            product.setDescription(p.description());
            product.setCategoryId(p.categoryId());
            product.setPrice(BigDecimal.valueOf(p.priceRupees()));
            product.setStockQuantity(defaultStock);
            product.setImageUrls(null);
            product.setSellerId(p.sellerId());
            product.setActive(true);
            // Seeded products start APPROVED so the storefront has visible items
            // immediately. Real seller-listed products go through PENDING moderation.
            product.setApprovalStatus(
                    com.project.product_service.model.ProductApprovalStatus.APPROVED);
            product.setReviewedAt(java.time.LocalDateTime.now());
            product = productRepository.save(product);

            // Best-effort ES sync; OK if ES is down at boot.
            try {
                ProductDocument doc = new ProductDocument();
                doc.setId(product.getId());
                doc.setName(product.getName());
                doc.setDescription(product.getDescription());
                doc.setCategoryId(product.getCategoryId());
                doc.setPrice(product.getPrice());
                doc.setStockQuantity(product.getStockQuantity());
                doc.setImageUrls(null);
                doc.setSellerId(product.getSellerId());
                doc.setActive(true);
                productSearchRepository.save(doc);
            } catch (Exception e) {
                log.warn("ES sync failed for {} (will reconcile later): {}", product.getId(), e.getMessage());
            }
        }
        log.info("Seeded {} products", SampleIds.PRODUCTS.size());
    }
}
