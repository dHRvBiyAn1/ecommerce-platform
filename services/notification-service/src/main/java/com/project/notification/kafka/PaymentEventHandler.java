package com.project.notification.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.PaymentEvent;
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

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void handle(PaymentEvent event) {
        if (event == null || event.getType() == null) return;

        switch (event.getType()) {
            case COMPLETED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "PAYMENT",
                    "Payment receipt for order " + event.getOrderId(),
                    "We received " + event.getCurrency() + " " + event.getAmount(),
                    event.getEventId());
            case REFUNDED, PARTIALLY_REFUNDED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "PAYMENT",
                    "Refund issued for order " + event.getOrderId(),
                    "Your refund is being processed. Amount: " + event.getCurrency() + " " + event.getAmount(),
                    event.getEventId());
            default -> { /* ignore INITIATED/PROCESSING */ }
        }
    }
}
