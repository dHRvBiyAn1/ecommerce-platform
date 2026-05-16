package com.project.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.notification.event.OrderEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void handleEvent(String message) {
        log.info("Received order event: {}", message);
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            log.debug("Deserialized order event: type={}, orderId={}", event.getType(), event.getOrderId());
            notificationService.handleOrderEvent(event);
        } catch (Exception e) {
            log.error("Failed to process order event: {}. Error: {}", message, e.getMessage(), e);
        }
    }
}
