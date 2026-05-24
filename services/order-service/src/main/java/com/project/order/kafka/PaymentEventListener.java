package com.project.order.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.PaymentEvent;
import com.project.order.model.PaymentStatus;
import com.project.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Drives order state transitions in response to {@link PaymentEvent}s emitted
 * by payment-service. This is the consumer end of the order/payment saga.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;

    @KafkaListener(
            topics = Topics.PAYMENT_EVENTS,
            groupId = "order-service-payments",
            containerFactory = "orderKafkaListenerContainerFactory")
    public void onPaymentEvent(PaymentEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received payment event with no orderId; ignoring");
            return;
        }
        log.info("Received PaymentEvent type={}, paymentId={}, orderId={}",
                event.getType(), event.getPaymentId(), event.getOrderId());

        PaymentStatus status = mapStatus(event.getType());
        if (status == null) return; // ignore PROCESSING / INITIATED transitions

        orderService.onPaymentResult(event.getOrderId(), event.getPaymentId(), status);
    }

    private static PaymentStatus mapStatus(PaymentEvent.Type type) {
        return switch (type) {
            case COMPLETED -> PaymentStatus.COMPLETED;
            case FAILED, CANCELLED -> PaymentStatus.FAILED;
            case REFUNDED -> PaymentStatus.REFUNDED;
            case PARTIALLY_REFUNDED -> PaymentStatus.PARTIALLY_REFUNDED;
            default -> null;
        };
    }
}
