package com.project.order.service.impl;

import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.Order;
import com.project.order.model.OutboxEvent;
import com.project.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "relayOutboxEvents", lockAtLeastFor = "5s", lockAtMostFor = "30s")
    public void relayEvents() {
        List<Order> orders = orderRepository.findOrdersWithPendingEvents("PENDING");
        if (orders.isEmpty()) {
            return;
        }

        log.debug("Found {} orders containing pending outbox events to relay", orders.size());

        for (Order order : orders) {
            boolean orderModified = false;
            
            for (OutboxEvent event : order.getOutboxEvents()) {
                if (!"PENDING".equals(event.getStatus())) {
                    continue;
                }

                try {
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
                    orderModified = true;
                } catch (Exception e) {
                    log.error("Failed to relay outbox event id={} inside order {}: {}", 
                            event.getId(), order.getId(), e.getMessage());
                    event.setStatus("FAILED");
                    event.setProcessedAt(LocalDateTime.now());
                    orderModified = true;
                    // Will retry or sit in failed state based on requirements
                }
            }

            if (orderModified) {
                orderRepository.save(order); // Atomically updates the events array inside order document
            }
        }
    }
}
