package com.project.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async
    public void publishEvent(EventType type, UUID userId, String email) {
        UserEvent event = new UserEvent(type, userId, email, Instant.now());
        kafkaTemplate.send("user-events", event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish user event: {}", ex.getMessage());
            } else {
                log.debug("Published user event: {}", event);
            }
        });
    }

    public enum EventType {
        CREATED, LOGGED_IN, LOGGED_OUT
    }

    public record UserEvent(EventType type, UUID userId, String email, Instant timestamp) {}
}
