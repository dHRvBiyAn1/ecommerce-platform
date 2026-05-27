package com.project.authservice.service;

import com.project.common.constant.Topics;
import com.project.common.event.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async("kafkaPublisherExecutor")
    public void publish(UserEvent event) {
        try {
            kafkaTemplate.send(Topics.USER_EVENTS,
                            event.getUserId() != null ? event.getUserId().toString() : null, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish user event {}: {}", event.getType(), ex.getMessage());
                        } else {
                            log.debug("Published user event {} -> partition {} offset {}",
                                    event.getType(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Synchronous failure publishing user event {}: {}", event.getType(), e.getMessage(), e);
        }
    }
}
