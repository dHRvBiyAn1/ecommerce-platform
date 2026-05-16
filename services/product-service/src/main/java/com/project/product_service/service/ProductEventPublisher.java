package com.project.product_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);
    private static final String TOPIC = "product-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publishEvent(Type type, String productId, String sku, UUID sellerId) {
        ProductEvent event = new ProductEvent(type, productId, sku, sellerId, Instant.now());
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, productId, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send product event: {} for product: {}", type, productId, ex);
                        } else {
                            log.info("Product event sent: {} for product: {}, partition: {}, offset: {}",
                                    type, productId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize product event: {} for product: {}", type, productId, e);
        }
    }

    public enum Type {
        CREATED, UPDATED, DELETED, STOCK_CHANGED, PRICE_CHANGED, ACTIVATED, DEACTIVATED
    }

    public record ProductEvent(Type type, String productId, String sku, UUID sellerId, Instant timestamp) {}
}
