package com.project.payment.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(PaymentEvent event) {
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.getOrderId(), event)
                .whenComplete((res, ex) -> {
                    if (ex != null) log.error("Failed to publish payment event {}: {}", event.getType(), ex.getMessage());
                    else log.debug("Published payment event {} -> offset {}",
                            event.getType(), res.getRecordMetadata().offset());
                });
    }
}
