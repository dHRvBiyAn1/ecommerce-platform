package com.project.product_service.service;

import com.project.common.constant.Topics;
import com.project.common.event.ProductEvent;
import com.project.product_service.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes typed {@link ProductEvent}s defined in the common module. The previous
 * inline ProductEvent record was removed; events now share schema with the rest of
 * the platform.
 */
@Slf4j
@Service
public class ProductEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(Product product)      { publish(ProductEvent.Type.CREATED, product); }
    public void publishUpdated(Product product)      { publish(ProductEvent.Type.UPDATED, product); }
    public void publishDeleted(Product product)      { publish(ProductEvent.Type.DELETED, product); }
    public void publishStockChanged(Product product) { publish(ProductEvent.Type.STOCK_CHANGED, product); }
    public void publishPriceChanged(Product product) { publish(ProductEvent.Type.PRICE_CHANGED, product); }
    public void publishActivated(Product product)    { publish(ProductEvent.Type.ACTIVATED, product); }
    public void publishDeactivated(Product product)  { publish(ProductEvent.Type.DEACTIVATED, product); }

    private void publish(ProductEvent.Type type, Product p) {
        ProductEvent event = ProductEvent.productEventBuilder()
                .type(type)
                .productId(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .sellerId(p.getSellerId())
                .build();
        kafkaTemplate.send(Topics.PRODUCT_EVENTS, p.getId(), event)
                .whenComplete((res, ex) -> {
                    if (ex != null) log.error("Failed publishing product event {}: {}", type, ex.getMessage());
                });
    }
}
