package com.project.order.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.OrderEvent;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(Order o)          { send(o, OrderEvent.Type.CREATED); }
    public void publishStatusChanged(Order o)     { send(o, mapStatus(o)); }
    public void publishCancelled(Order o)         { send(o, OrderEvent.Type.CANCELLED); }
    public void publishPaymentCompleted(Order o)  { send(o, OrderEvent.Type.PAYMENT_COMPLETED); }
    public void publishPaymentFailed(Order o)     { send(o, OrderEvent.Type.PAYMENT_FAILED); }

    private OrderEvent.Type mapStatus(Order o) {
        return switch (o.getStatus()) {
            case CONFIRMED -> OrderEvent.Type.CONFIRMED;
            case PROCESSING -> OrderEvent.Type.PROCESSING;
            case SHIPPED -> OrderEvent.Type.SHIPPED;
            case DELIVERED -> OrderEvent.Type.DELIVERED;
            case CANCELLED -> OrderEvent.Type.CANCELLED;
            case REFUNDED -> OrderEvent.Type.REFUNDED;
            default -> OrderEvent.Type.CREATED;
        };
    }

    private void send(Order order, OrderEvent.Type type) {
        List<OrderEvent.Item> items = order.getItems() == null ? List.of() :
                order.getItems().stream().map(this::toEventItem).collect(Collectors.toList());
        OrderEvent event = OrderEvent.orderEventBuilder()
                .type(type)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(items)
                .build();
        kafkaTemplate.send(Topics.ORDER_EVENTS, order.getId(), event)
                .whenComplete((res, ex) -> {
                    if (ex != null) log.error("Failed publishing order event {}: {}", type, ex.getMessage());
                });
    }

    private OrderEvent.Item toEventItem(OrderItem i) {
        return OrderEvent.Item.builder()
                .productId(i.getProductId()).sku(i.getSku()).quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice()).build();
    }
}
