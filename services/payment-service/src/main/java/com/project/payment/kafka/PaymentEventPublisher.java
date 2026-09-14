package com.project.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.constant.Topics;
import com.project.common.event.PaymentEvent;
import com.project.payment.model.PaymentOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CompletionStage<Void> publish(PaymentOutboxEvent outboxEvent) {
        final PaymentEvent event;
        try {
            event = objectMapper.readValue(outboxEvent.getPayload(), PaymentEvent.class);
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.getOrderId(), event).thenApply(result -> null);
    }
}
