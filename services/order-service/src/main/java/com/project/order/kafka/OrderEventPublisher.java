package com.project.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.constant.Topics;
import com.project.common.event.OrderEvent;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CompletionStage<Void> publish(OutboxEvent outboxEvent) {
        final OrderEvent event;
        try {
            event = objectMapper.readValue(outboxEvent.getPayload(), OrderEvent.class);
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return kafkaTemplate.send(Topics.ORDER_EVENTS, event.getOrderId(), event)
                .thenApply(result -> null);
    }

    public String snapshot(Order order, String eventType) {
        List<OrderEvent.Item> items = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(this::eventItem)
                .toList();
        OrderEvent event = OrderEvent.orderEventBuilder()
                .type(eventType(eventType, order.getStatus()))
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(items)
                .build();
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to snapshot legacy order event", exception);
        }
    }

    private OrderEvent.Item eventItem(OrderItem item) {
        return OrderEvent.Item.builder()
                .productId(item.getProductId())
                .sku(item.getSku())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build();
    }

    private OrderEvent.Type eventType(String eventType, OrderStatus status) {
        return switch (eventType) {
            case "CANCELLED" -> OrderEvent.Type.CANCELLED;
            case "PAYMENT_COMPLETED" -> OrderEvent.Type.PAYMENT_COMPLETED;
            case "PAYMENT_FAILED" -> OrderEvent.Type.PAYMENT_FAILED;
            case "STATUS_CHANGED" -> switch (status) {
                case CONFIRMED -> OrderEvent.Type.CONFIRMED;
                case PROCESSING -> OrderEvent.Type.PROCESSING;
                case SHIPPED -> OrderEvent.Type.SHIPPED;
                case DELIVERED -> OrderEvent.Type.DELIVERED;
                case CANCELLED -> OrderEvent.Type.CANCELLED;
                case REFUNDED -> OrderEvent.Type.REFUNDED;
                default -> OrderEvent.Type.CREATED;
            };
            default -> OrderEvent.Type.CREATED;
        };
    }
}
