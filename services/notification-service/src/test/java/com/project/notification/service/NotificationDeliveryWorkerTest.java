package com.project.notification.service;

import com.mongodb.client.result.UpdateResult;
import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import com.project.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Mock private MongoTemplate mongoTemplate;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailService emailService;

    private NotificationDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new NotificationDeliveryWorker(mongoTemplate, notificationRepository, emailService);
    }

    @Test
    void unsupportedChannelIsRetriedInsteadOfAcknowledgedAsSent() {
        NotificationDelivery claim = claim(1);
        Notification notification = Notification.builder()
                .id("notification-1")
                .channel("SMS")
                .recipient("owner@example.com")
                .status(Notification.Status.PENDING)
                .build();
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        worker.deliverClaimed(claim, NOW);

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.PENDING);
        verify(emailService, never()).sendEmail(any(), any(), any(), any());
        verify(notificationRepository, never()).save(any(Notification.class));
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(NotificationDelivery.class));
        assertThat((java.util.Map<Object, Object>) update.getValue().getUpdateObject().get("$set"))
                .containsKey("nextAttemptAt");
    }

    @Test
    void leaseIsRenewedBeforeEmailCanBlock() {
        NotificationDelivery claim = claim(1);
        Notification notification = Notification.builder()
                .id("notification-1")
                .channel("EMAIL")
                .recipient("owner@example.com")
                .subject("Subject")
                .body("Body")
                .status(Notification.Status.PENDING)
                .build();
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        doNothing().when(emailService).sendEmail("owner@example.com", "Subject", "Body", "notification-1");

        worker.deliverClaimed(claim, NOW);

        InOrder calls = inOrder(mongoTemplate, emailService);
        calls.verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class));
        calls.verify(emailService).sendEmail("owner@example.com", "Subject", "Body", "notification-1");
    }

    @Test
    void expiredClaimAfterSchedulerPauseIsNotSent() {
        NotificationDelivery claim = claim(1);
        Notification notification = emailNotification();
        MutableClock mutableClock = new MutableClock(NOW.plusSeconds(121));
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenAnswer(invocation -> matchesStaleSchedulerTime(invocation.getArgument(0))
                        ? UpdateResult.acknowledged(1, 1L, null)
                        : UpdateResult.acknowledged(0, 0L, null));
        worker = new NotificationDeliveryWorker(mongoTemplate, notificationRepository, emailService,
                Duration.ofSeconds(10), mutableClock);

        worker.deliverClaimed(claim, NOW);

        verify(emailService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void claimReturnsOnlyOneDeliverySoUnsentWorkDoesNotConsumeAttempts() {
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(NotificationDelivery.class)))
                .thenReturn(claim(1), claim(1), null);

        assertThat(worker.claimDueDeliveries(NOW, java.time.Duration.ofSeconds(60)))
                .hasSize(1);
        verify(mongoTemplate).findAndModify(any(), any(), any(), eq(NotificationDelivery.class));
    }

    @Test
    void heartbeatRenewsLeaseWhileSynchronousEmailIsActive() throws Exception {
        NotificationDelivery claim = claim(1);
        Notification notification = Notification.builder()
                .id("notification-1")
                .channel("EMAIL")
                .recipient("owner@example.com")
                .subject("Subject")
                .body("Body")
                .status(Notification.Status.PENDING)
                .build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendEmail("owner@example.com", "Subject", "Body", "notification-1");
        worker = new NotificationDeliveryWorker(mongoTemplate, notificationRepository, emailService,
                Duration.ofMillis(25));

        var executor = Executors.newSingleThreadExecutor();
        try {
            var delivery = executor.submit(() -> worker.deliverClaimed(claim, NOW));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(mongoTemplate, timeout(500).atLeast(2))
                    .updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class));
            release.countDown();
            delivery.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void heartbeatOwnershipLossPreventsAcknowledgementAndKeepsTicking() throws Exception {
        NotificationDelivery claim = claim(1);
        Notification notification = emailNotification();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        UpdateResult acknowledged = UpdateResult.acknowledged(1, 1L, null);
        java.util.concurrent.atomic.AtomicInteger updates = new java.util.concurrent.atomic.AtomicInteger();
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    int update = updates.incrementAndGet();
                    if (update == 2) {
                        throw new IllegalStateException("mongo unavailable");
                    }
                    if (update == 3) {
                        return UpdateResult.acknowledged(0, 0L, null);
                    }
                    return acknowledged;
                });
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendEmail("owner@example.com", "Subject", "Body", "notification-1");
        worker = new NotificationDeliveryWorker(mongoTemplate, notificationRepository, emailService,
                Duration.ofMillis(25));

        var executor = Executors.newSingleThreadExecutor();
        try {
            var delivery = executor.submit(() -> worker.deliverClaimed(claim, NOW));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(mongoTemplate, timeout(500).atLeast(4))
                    .updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class));
            release.countDown();
            delivery.get(2, TimeUnit.SECONDS);
            verify(notificationRepository, never()).save(any(Notification.class));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void heartbeatMovesLeasePastInitialExpiryBeforeReclaim() throws Exception {
        NotificationDelivery claim = claim(1);
        Notification notification = emailNotification();
        MutableClock mutableClock = new MutableClock(NOW);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mongoTemplate.findOne(any(Query.class), eq(NotificationDelivery.class))).thenReturn(claim);
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendEmail("owner@example.com", "Subject", "Body", "notification-1");
        worker = new NotificationDeliveryWorker(mongoTemplate, notificationRepository, emailService,
                Duration.ofMillis(25), mutableClock);

        var executor = Executors.newSingleThreadExecutor();
        try {
            var delivery = executor.submit(() -> worker.deliverClaimed(claim, NOW));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            verify(mongoTemplate, timeout(500).atLeast(2))
                    .updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class));
            mutableClock.advance(Duration.ofSeconds(121));
            ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
            verify(mongoTemplate, timeout(500).atLeast(3))
                    .updateFirst(any(Query.class), updates.capture(), eq(NotificationDelivery.class));
            List<Update> captured = updates.getAllValues();
            Object renewedLease = ((java.util.Map<?, ?>) captured.get(2).getUpdateObject().get("$set"))
                    .get("leaseUntil");
            assertThat((LocalDateTime) renewedLease)
                    .isAfter(LocalDateTime.ofInstant(NOW.plusSeconds(120), java.time.ZoneOffset.UTC));
            release.countDown();
            delivery.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reconciliationRepairsAcknowledgedDeliveryWithPendingNotification() {
        NotificationDelivery delivery = NotificationDelivery.builder()
                .id("delivery-1")
                .notificationId("notification-1")
                .deliveredAt(java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC))
                .build();
        Notification notification = Notification.builder()
                .id("notification-1")
                .status(Notification.Status.PENDING)
                .build();
        when(mongoTemplate.find(any(Query.class), eq(NotificationDelivery.class))).thenReturn(java.util.List.of(delivery));
        when(notificationRepository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        worker.reconcileDeliveredNotifications();

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.SENT);
        verify(notificationRepository).save(notification);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(NotificationDelivery.class));
    }

    private Notification emailNotification() {
        return Notification.builder()
                .id("notification-1")
                .channel("EMAIL")
                .recipient("owner@example.com")
                .subject("Subject")
                .body("Body")
                .status(Notification.Status.PENDING)
                .build();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }

    private NotificationDelivery claim(int attempts) {
        return NotificationDelivery.builder()
                .id("delivery-1")
                .notificationId("notification-1")
                .attempts(attempts)
                .leaseToken("token-1")
                .leaseUntil(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(30), java.time.ZoneOffset.UTC))
                .build();
    }

    private static boolean matchesStaleSchedulerTime(Query query) {
        for (Object part : (List<?>) query.getQueryObject().get("$and")) {
            if (part instanceof java.util.Map<?, ?> condition && condition.containsKey("leaseUntil")) {
                Object leaseUntil = condition.get("leaseUntil");
                if (leaseUntil instanceof java.util.Map<?, ?> expiryCondition) {
                    return LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC)
                            .equals(expiryCondition.get("$gt"));
                }
            }
        }
        throw new AssertionError("Lease ownership predicate missing");
    }
}
