package com.project.order.service.impl;

import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.Order;
import com.project.order.model.OutboxEvent;
import com.project.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private static final int MAX_OPTIMISTIC_ATTEMPTS = 3;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "relayOutboxEvents", lockAtLeastFor = "5s", lockAtMostFor = "30s")
    public void relayEvents() {
        List<Order> orders = orderRepository.findOrdersWithPendingEvents("PENDING");
        LocalDateTime now = LocalDateTime.now();
        for (Order candidateOrder : orders) {
            Order order = ensureDeliveryIds(candidateOrder.getId());
            if (order == null) {
                continue;
            }
            if (order.getOutboxEvents() == null) {
                continue;
            }
            for (OutboxEvent candidate : new ArrayList<>(order.getOutboxEvents())) {
                if (candidate.getPublishedAt() != null || !"PENDING".equals(candidate.getStatus())) {
                    continue;
                }
                Claim claim = claim(order.getId(), candidate.getDeliveryId(), now);
                if (claim == null) {
                    // The earliest pending event is an aggregate-order barrier.
                    break;
                }
                try {
                    CompletionStage<Void> publication = eventPublisher.publish(claim.event());
                    publication.whenComplete((ignored, error) ->
                            completePublication(claim.orderId(), claim.deliveryId(), claim.token(), error));
                } catch (RuntimeException exception) {
                    completePublication(claim.orderId(), claim.deliveryId(), claim.token(), exception);
                }
                // Keep one publication in flight per aggregate version.
                break;
            }
        }
    }

    private Claim claim(String orderId, String deliveryId, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Order current = orderRepository.findById(orderId).orElse(null);
            OutboxEvent event = findEvent(current, deliveryId);
            if (event == null || !isEligible(event, now)) {
                return null;
            }
            if (event.getPayload() == null || event.getPayload().isBlank()) {
                if (!"CREATED".equals(event.getEventType())) {
                    event.setStatus("MANUAL");
                    event.setProcessedAt(now);
                    event.setNextAttemptAt(null);
                    try {
                        orderRepository.save(current);
                        return null;
                    } catch (OptimisticLockingFailureException exception) {
                        log.debug("Retrying legacy outbox terminal transition for event {}", event.getId());
                        continue;
                    }
                }
                event.setPayload(eventPublisher.snapshot(current, event.getEventType()));
            }
            String token = UUID.randomUUID().toString();
            event.setLeaseToken(token);
            event.setLeaseUntil(now.plus(LEASE_DURATION));
            event.setAttempts(event.getAttempts() + 1);
            try {
                Order claimed = orderRepository.save(current);
                return new Claim(orderId, deliveryId, token, findEvent(claimed, deliveryId));
            } catch (OptimisticLockingFailureException exception) {
                log.debug("Retrying outbox claim after optimistic conflict for delivery {}", deliveryId);
            }
        }
        log.warn("Could not claim outbox delivery {} after optimistic retries", deliveryId);
        return null;
    }

    private void completePublication(String orderId, String deliveryId, String token, Throwable error) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Order current = orderRepository.findById(orderId).orElse(null);
            OutboxEvent event = findEvent(current, deliveryId);
            if (event == null || event.getPublishedAt() != null || !token.equals(event.getLeaseToken())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            event.setLeaseUntil(null);
            event.setLeaseToken(null);
            if (error == null) {
                event.setStatus("PROCESSED");
                event.setPublishedAt(now);
                event.setProcessedAt(now);
                event.setNextAttemptAt(null);
            } else {
                log.error("Failed to relay outbox event id={} inside order {}: {}",
                        event.getId(), orderId, error.getMessage());
                event.setStatus("PENDING");
                event.setNextAttemptAt(now.plus(RETRY_DELAY.multipliedBy(Math.max(1, event.getAttempts()))));
            }
            try {
                orderRepository.save(current);
                return;
            } catch (OptimisticLockingFailureException exception) {
                log.debug("Retrying outbox completion after optimistic conflict for delivery {}", deliveryId);
            }
        }
        log.warn("Could not persist completion for outbox delivery {} after optimistic retries", deliveryId);
    }

    private Order ensureDeliveryIds(String orderId) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Order current = orderRepository.findById(orderId).orElse(null);
            if (current == null || current.getOutboxEvents() == null) {
                return current;
            }
            Set<String> seen = new HashSet<>();
            boolean needsIdentity = current.getOutboxEvents().stream()
                    .anyMatch(event -> event.getDeliveryId() == null
                            || event.getDeliveryId().isBlank()
                            || !seen.add(event.getDeliveryId()));
            if (!needsIdentity) {
                return current;
            }
            assignDeliveryIds(current);
            try {
                return orderRepository.save(current);
            } catch (OptimisticLockingFailureException exception) {
                log.debug("Retrying outbox delivery identity migration for order {}", orderId);
            }
        }
        log.warn("Could not persist outbox delivery identities for order {} after optimistic retries", orderId);
        return null;
    }

    private void assignDeliveryIds(Order order) {
        Set<String> used = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (OutboxEvent event : order.getOutboxEvents()) {
            if (event.getDeliveryId() != null && !event.getDeliveryId().isBlank()
                    && used.add(event.getDeliveryId())) {
                continue;
            }
            String fingerprint = order.getId() + ":" + event.getId() + ":" + event.getEventType()
                    + ":" + event.getCreatedAt() + ":" + event.getAggregateId();
            String deliveryId;
            do {
                int occurrence = occurrences.merge(fingerprint, 1, Integer::sum);
                deliveryId = UUID.nameUUIDFromBytes(
                        (fingerprint + ":" + occurrence).getBytes(StandardCharsets.UTF_8)).toString();
            } while (used.contains(deliveryId));
            event.setDeliveryId(deliveryId);
            used.add(deliveryId);
        }
    }

    private boolean isEligible(OutboxEvent event, LocalDateTime now) {
        return "PENDING".equals(event.getStatus())
                && event.getPublishedAt() == null
                && (event.getNextAttemptAt() == null || !event.getNextAttemptAt().isAfter(now))
                && (event.getLeaseUntil() == null || !event.getLeaseUntil().isAfter(now));
    }

    private OutboxEvent findEvent(Order order, String deliveryId) {
        if (order == null || order.getOutboxEvents() == null) {
            return null;
        }
        return order.getOutboxEvents().stream()
                .filter(event -> deliveryId.equals(event.getDeliveryId()))
                .findFirst()
                .orElse(null);
    }

    private record Claim(String orderId, String deliveryId, String token, OutboxEvent event) {
    }
}
