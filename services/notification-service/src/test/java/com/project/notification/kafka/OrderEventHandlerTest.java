package com.project.notification.kafka;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.project.common.event.OrderEvent;
import com.project.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderEventHandlerTest {
  private final NotificationService notifications = mock(NotificationService.class);
  private final OrderEventHandler handler = new OrderEventHandler(notifications);

  @ParameterizedTest
  @CsvSource({
    "CREATED,ORDER,Order placed: ORD-1",
    "CONFIRMED,ORDER,Payment received for ORD-1",
    "PAYMENT_COMPLETED,ORDER,Payment received for ORD-1",
    "PAYMENT_FAILED,PAYMENT,Payment failed for ORD-1",
    "SHIPPED,ORDER,Shipped: ORD-1",
    "DELIVERED,ORDER,Delivered: ORD-1",
    "CANCELLED,ORDER,Cancelled: ORD-1",
    "REFUNDED,PAYMENT,Refund issued for ORD-1"
  })
  void recordsCustomerVisibleLifecycleEventsWithTheirDeduplicationKey(
      OrderEvent.Type type, String category, String subject) {
    OrderEvent event = event(type);
    handler.handle(event);
    verify(notifications)
        .record(
            eq(event.getUserId()),
            eq(event.getUserEmail()),
            eq("EMAIL"),
            eq(category),
            eq(subject),
            anyString(),
            eq(event.getEventId()));
  }

  @Test
  void fallsBackToOrderIdWhenOrderNumberIsMissing() {
    OrderEvent event = event(OrderEvent.Type.CREATED);
    event.setOrderNumber(null);
    handler.handle(event);
    verify(notifications)
        .record(
            eq(event.getUserId()),
            eq(event.getUserEmail()),
            eq("EMAIL"),
            eq("ORDER"),
            eq("Order placed: " + event.getOrderId()),
            contains("INR 50.00"),
            eq(event.getEventId()));
  }

  @Test
  void ignoresIncompleteAndNonCustomerVisibleEvents() {
    handler.handle(null);
    handler.handle(new OrderEvent());
    handler.handle(event(OrderEvent.Type.PROCESSING));
    handler.handle(event(OrderEvent.Type.PAYMENT_PENDING));
    verifyNoInteractions(notifications);
  }

  private OrderEvent event(OrderEvent.Type type) {
    OrderEvent event =
        OrderEvent.orderEventBuilder()
            .type(type)
            .orderId("order-1")
            .orderNumber("ORD-1")
            .userId(UUID.randomUUID())
            .userEmail("customer@example.com")
            .currency("INR")
            .totalAmount(new BigDecimal("50.00"))
            .build();
    event.setEventId("source-event-1");
    return event;
  }
}
