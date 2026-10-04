package com.project.payment.service.impl;

import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.PaymentOutboxEvent;
import com.project.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private static final int MAX_OPTIMISTIC_ATTEMPTS = 3;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${payment.outbox.delay-ms:1000}")
    public void relayEvents() {
        LocalDateTime now = LocalDateTime.now();
        for (PaymentOutboxEvent candidate
                : outboxRepository.findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(now)) {
            Claim claim = claim(candidate.getId(), now);
            if (claim == null) continue;
            try {
                eventPublisher.publish(claim.event()).whenComplete((ignored, error) ->
                        complete(claim.event().getId(), claim.token(), error));
            } catch (RuntimeException exception) {
                complete(claim.event().getId(), claim.token(), exception);
            }
        }
    }

    private Claim claim(String eventId, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            PaymentOutboxEvent event = outboxRepository.findById(eventId).orElse(null);
            if (event == null || event.getPublishedAt() != null
                    || (event.getNextAttemptAt() != null && event.getNextAttemptAt().isAfter(now))
                    || (event.getLeaseUntil() != null && event.getLeaseUntil().isAfter(now))
                    || hasEarlierUnpublishedTransition(event)) {
                return null;
            }
            String token = UUID.randomUUID().toString();
            event.setLeaseToken(token);
            event.setLeaseUntil(now.plus(LEASE_DURATION));
            event.setAttempts(event.getAttempts() + 1);
            try {
                return new Claim(outboxRepository.save(event), token);
            } catch (OptimisticLockingFailureException exception) {
                log.debug("Retrying payment outbox claim after optimistic conflict for {}", eventId);
            }
        }
        return null;
    }

    private boolean hasEarlierUnpublishedTransition(PaymentOutboxEvent event) {
        return outboxRepository.findByPaymentIdAndPublishedAtIsNull(event.getPaymentId()).stream()
                .anyMatch(other -> !other.getId().equals(event.getId()) && precedes(other, event));
    }

    private boolean precedes(PaymentOutboxEvent first, PaymentOutboxEvent second) {
        if (first.getTransitionSequence() != null && second.getTransitionSequence() != null) {
            return first.getTransitionSequence() < second.getTransitionSequence();
        }
        int createdAt = Comparator.nullsFirst(LocalDateTime::compareTo)
                .compare(first.getCreatedAt(), second.getCreatedAt());
        return createdAt < 0 || (createdAt == 0 && Comparator.nullsFirst(String::compareTo)
                .compare(first.getId(), second.getId()) < 0);
    }

    private void complete(String eventId, String token, Throwable error) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            PaymentOutboxEvent event = outboxRepository.findById(eventId).orElse(null);
            if (event == null || event.getPublishedAt() != null || !token.equals(event.getLeaseToken())) return;
            LocalDateTime now = LocalDateTime.now();
            event.setLeaseToken(null);
            event.setLeaseUntil(null);
            if (error == null) {
                event.setPublishedAt(now);
                event.setNextAttemptAt(null);
            } else {
                log.error("Failed to relay payment outbox event {}: {}", eventId, error.getMessage());
                event.setNextAttemptAt(now.plus(RETRY_DELAY.multipliedBy(Math.max(1, event.getAttempts()))));
            }
            try {
                outboxRepository.save(event);
                return;
            } catch (OptimisticLockingFailureException exception) {
                log.debug("Retrying payment outbox completion after optimistic conflict for {}", eventId);
            }
        }
    }

    private record Claim(PaymentOutboxEvent event, String token) {}
}
