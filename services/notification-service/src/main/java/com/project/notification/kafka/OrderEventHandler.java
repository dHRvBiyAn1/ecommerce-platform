package com.project.notification.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.OrderEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final NotificationService notificationService;

    @KafkaListener(topics = Topics.ORDER_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void handle(OrderEvent event) {
        if (event == null || event.getType() == null) return;

        String orderNumber = event.getOrderNumber() == null ? event.getOrderId() : event.getOrderNumber();
        switch (event.getType()) {
            case CREATED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "ORDER",
                    "Order placed: " + orderNumber,
                    "Thank you for your order. Total: " + event.getCurrency() + " " + event.getTotalAmount(),
                    event.getEventId());
            case CONFIRMED, PAYMENT_COMPLETED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "ORDER",
                    "Payment received for " + orderNumber,
                    "Your payment is confirmed and we're preparing your order.",
                    event.getEventId());
            case PAYMENT_FAILED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "PAYMENT",
                    "Payment failed for " + orderNumber,
                    "Your payment didn't go through. Please retry or use a different method.",
                    event.getEventId());
            case SHIPPED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "ORDER",
                    "Shipped: " + orderNumber,
                    "Your order is on its way.", event.getEventId());
            case DELIVERED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "ORDER",
                    "Delivered: " + orderNumber,
                    "Your order has been delivered. Enjoy!", event.getEventId());
            case CANCELLED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "ORDER",
                    "Cancelled: " + orderNumber,
                    "Your order has been cancelled.", event.getEventId());
            case REFUNDED -> notificationService.record(event.getUserId(), event.getUserEmail(),
                    "EMAIL", "PAYMENT",
                    "Refund issued for " + orderNumber,
                    "Your refund is on the way (5–10 business days).", event.getEventId());
            default -> { /* PROCESSING / PAYMENT_PENDING — no email */ }
        }
    }
}
