package com.project.payment.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.OrderEvent;
import com.project.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = Topics.ORDER_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void handle(OrderEvent event) {
        if (event == null || event.getType() == null) return;

        log.info("Received order event {} for order {}", event.getType(), event.getOrderId());
        if (event.getType() == OrderEvent.Type.CANCELLED) {
            try {
                paymentService.cancelPaymentByOrderId(event.getOrderId());
            } catch (Exception e) {
                log.error("Failed to cancel payment for order {}: {}", event.getOrderId(), e.getMessage());
            }
        }
    }
}
