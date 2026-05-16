package com.project.order.kafka;

import com.project.order.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${order.kafka.topic:order-events}")
    private String topic;

    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing order event to topic {}: {}", topic, event.getOrderNumber());
        kafkaTemplate.send(topic, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Order event published successfully: {} at offset {}",
                                event.getOrderNumber(), result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to publish order event: {}", event.getOrderNumber(), ex);
                    }
                });
    }
}
