package com.project.notification.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.InventoryEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventHandler {

    private final NotificationService notificationService;

    @Value("${notification.seller-stock-alerts-recipient:warehouse@ecommerce.local}")
    private String warehouseRecipient;

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void handle(InventoryEvent event) {
        if (event == null || event.getType() == null) return;

        switch (event.getType()) {
            case LOW_STOCK_ALERT -> notificationService.record(null, warehouseRecipient,
                    "EMAIL", "INVENTORY",
                    "Low stock: " + event.getSku(),
                    "Stock level on " + event.getSku() + " has fallen to " + event.getNewQuantity(),
                    event.getEventId());
            case OUT_OF_STOCK -> notificationService.record(null, warehouseRecipient,
                    "EMAIL", "INVENTORY",
                    "Out of stock: " + event.getSku(),
                    "Restock " + event.getSku() + " immediately.",
                    event.getEventId());
            default -> { /* ignore other transitions */ }
        }
    }
}
