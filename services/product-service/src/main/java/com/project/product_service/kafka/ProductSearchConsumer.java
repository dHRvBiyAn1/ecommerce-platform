package com.project.product_service.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.ProductEvent;
import com.project.product_service.model.Product;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductDocument;
import com.project.product_service.search.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchConsumer {

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;

    @KafkaListener(topics = Topics.PRODUCT_EVENTS, groupId = "product-search-indexer")
    public void indexProductEvent(ProductEvent event) {
        log.info("Async search indexing requested for product event type={} id={}", event.getType(), event.getProductId());
        
        switch (event.getType()) {
            case CREATED, UPDATED, ACTIVATED, STOCK_CHANGED, PRICE_CHANGED -> {
                productRepository.findById(event.getProductId()).ifPresentOrElse(product -> {
                    if (product.getApprovalStatus() == ProductApprovalStatus.APPROVED && product.isActive()) {
                        ProductDocument doc = mapToDocument(product);
                        searchRepository.save(doc);
                        log.info("Product search index updated: {}", product.getId());
                    } else {
                        searchRepository.deleteById(product.getId());
                        log.info("Product search index removed: {} (not active/approved)", product.getId());
                    }
                }, () -> log.warn("Product not found in DB for indexing: {}", event.getProductId()));
            }
            case DELETED, DEACTIVATED -> {
                searchRepository.deleteById(event.getProductId());
                log.info("Product search index deleted: {}", event.getProductId());
            }
            default -> log.debug("Ignored index event type={} for id={}", event.getType(), event.getProductId());
        }
    }

    private ProductDocument mapToDocument(Product p) {
        ProductDocument doc = new ProductDocument();
        doc.setId(p.getId());
        doc.setName(p.getName());
        doc.setDescription(p.getDescription());
        doc.setCategoryId(p.getCategoryId());
        doc.setPrice(p.getPrice());
        doc.setStockQuantity(p.getStockQuantity());
        doc.setImageUrls(p.getImageUrls());
        doc.setSellerId(p.getSellerId());
        doc.setActive(p.isActive());
        doc.setAttributes(p.getAttributes());
        return doc;
    }
}
