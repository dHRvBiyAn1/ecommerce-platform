package com.project.order.service.impl;

import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.Order;
import com.project.order.model.OutboxEvent;
import com.project.order.repository.OrderRepository;
import com.project.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 1000)
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                Order order = orderRepository.findById(event.getAggregateId()).orElse(null);
                if (order == null) {
                    log.error("Order not found for outbox event: aggregateId={}, eventId={}", 
                            event.getAggregateId(), event.getId());
                    event.setStatus("FAILED");
                    event.setProcessedAt(LocalDateTime.now());
                    outboxEventRepository.save(event);
                    continue;
                }

                switch (event.getEventType()) {
                    case "CREATED" -> eventPublisher.publishCreated(order);
                    case "STATUS_CHANGED" -> eventPublisher.publishStatusChanged(order);
                    case "CANCELLED" -> eventPublisher.publishCancelled(order);
                    case "PAYMENT_COMPLETED" -> eventPublisher.publishPaymentCompleted(order);
                    case "PAYMENT_FAILED" -> eventPublisher.publishPaymentFailed(order);
                    default -> log.warn("Unknown event type: {}", event.getEventType());
                }

                event.setStatus("PROCESSED");
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to relay outbox event id={}: {}", event.getId(), e.getMessage());
                // Will retry on next execution
            }
        }
    }
}
