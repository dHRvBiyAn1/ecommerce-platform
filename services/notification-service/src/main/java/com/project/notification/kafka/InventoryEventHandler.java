package com.project.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.notification.event.InventoryEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-events", groupId = "notification-service-group")
    public void handleEvent(String message) {
        log.info("Received inventory event: {}", message);
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            log.debug("Deserialized inventory event: type={}, productId={}", event.getType(), event.getProductId());
            notificationService.handleInventoryEvent(event);
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}. Error: {}", message, e.getMessage(), e);
        }
    }
}
