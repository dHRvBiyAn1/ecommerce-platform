package com.project.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.notification.event.PaymentEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group")
    public void handleEvent(String message) {
        log.info("Received payment event: {}", message);
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            log.debug("Deserialized payment event: type={}, paymentId={}", event.getType(), event.getPaymentId());
            notificationService.handlePaymentEvent(event);
        } catch (Exception e) {
            log.error("Failed to process payment event: {}. Error: {}", message, e.getMessage(), e);
        }
    }
}
