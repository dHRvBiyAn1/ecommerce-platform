package com.project.payment.kafka;

import com.project.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publish(PaymentEvent event) {
        log.info("Publishing payment event to topic '{}': type={}, paymentId={}, orderId={}",
                TOPIC, event.getType(), event.getPaymentId(), event.getOrderId());

        CompletableFuture<SendResult<String, PaymentEvent>> future = kafkaTemplate.send(TOPIC, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Payment event published successfully: offset={}", result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish payment event: {}", ex.getMessage(), ex);
            }
        });
    }
}
