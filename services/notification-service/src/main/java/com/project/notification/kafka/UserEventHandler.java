package com.project.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.notification.event.UserEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-events", groupId = "notification-service-group")
    public void handleEvent(String message) {
        log.info("Received user event: {}", message);
        try {
            UserEvent event = objectMapper.readValue(message, UserEvent.class);
            log.debug("Deserialized user event: type={}, userId={}", event.getType(), event.getUserId());
            notificationService.handleUserEvent(event);
        } catch (Exception e) {
            log.error("Failed to process user event: {}. Error: {}", message, e.getMessage(), e);
        }
    }
}
