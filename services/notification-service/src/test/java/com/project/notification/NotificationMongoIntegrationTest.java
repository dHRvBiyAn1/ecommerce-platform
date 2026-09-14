package com.project.notification.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.config.NotificationIndexInitializer;
import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import com.project.notification.repository.NotificationDeliveryRepository;
import com.project.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@Testcontainers
class NotificationMongoIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private MongoClient client;
    private MongoTemplate mongoTemplate;
    private NotificationRepository notifications;
    private NotificationDeliveryRepository deliveries;
    private EmailService emailService;
    private NotificationDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongoTemplate = new MongoTemplate(client, "notification-delivery-" + UUID.randomUUID());
        new NotificationIndexInitializer(mongoTemplate).initialize();
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongoTemplate);
        notifications = factory.getRepository(NotificationRepository.class);
        deliveries = factory.getRepository(NotificationDeliveryRepository.class);
        emailService = Mockito.mock(EmailService.class);
        worker = new NotificationDeliveryWorker(mongoTemplate, notifications, emailService,
                Duration.ofSeconds(10), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void competingWorkersAtomicallyClaimOneDeliveryUntilLeaseExpires() throws Exception {
        NotificationDelivery delivery = pendingEmail();
        NotificationDeliveryWorker competitor = new NotificationDeliveryWorker(mongoTemplate, notifications, emailService,
                Duration.ofSeconds(10), Clock.fixed(NOW, ZoneOffset.UTC));
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<List<NotificationDelivery>>> claims = List.of(
                    () -> worker.claimDueDeliveries(NOW, Duration.ofSeconds(30)),
                    () -> competitor.claimDueDeliveries(NOW, Duration.ofSeconds(30)));

            List<NotificationDelivery> claimed = executor.invokeAll(claims).stream()
                    .flatMap(future -> {
                        try {
                            return future.get().stream();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }).toList();

            assertThat(claimed).hasSize(1);
            assertThat(claimed.get(0).getId()).isEqualTo(delivery.getId());
            assertThat(claimed.get(0).getAttempts()).isEqualTo(1);
            assertThat(claimed.get(0).getLeaseUntil()).isEqualTo(local(NOW.plusSeconds(30)));
            assertThat(worker.claimDueDeliveries(NOW.plusSeconds(29), Duration.ofSeconds(30))).isEmpty();
            assertThat(worker.claimDueDeliveries(NOW.plusSeconds(30), Duration.ofSeconds(30)))
                    .singleElement().satisfies(reclaimed -> {
                        assertThat(reclaimed.getAttempts()).isEqualTo(2);
                        assertThat(reclaimed.getLeaseUntil()).isEqualTo(local(NOW.plusSeconds(60)));
                    });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleWorkerCannotSendOrAcknowledgeReclaimedDelivery() {
        NotificationDelivery delivery = pendingEmail();
        NotificationDelivery staleClaim = worker.claimDueDeliveries(NOW, Duration.ofSeconds(30)).get(0);
        NotificationDelivery activeClaim = worker.claimDueDeliveries(
                NOW.plusSeconds(30), Duration.ofSeconds(30)).get(0);

        worker.deliverClaimed(staleClaim, NOW.plusSeconds(31));

        NotificationDelivery unchanged = deliveries.findById(delivery.getId()).orElseThrow();
        Mockito.verify(emailService, Mockito.never()).sendEmail(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        assertThat(unchanged.getDeliveredAt()).isNull();
        assertThat(unchanged.getLeaseUntil()).isEqualTo(activeClaim.getLeaseUntil());
        assertThat(notifications.findById(activeClaim.getNotificationId()).orElseThrow().getStatus())
                .isEqualTo(Notification.Status.PENDING);
    }

    @Test
    void activeWorkerRenewsLeaseBeforeBlockingMailPreventsCausalReclaim() throws Exception {
        pendingEmail();
        NotificationDelivery claim = worker.claimDueDeliveries(NOW, Duration.ofSeconds(30)).get(0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendEmail(Mockito.eq("owner@example.com"), Mockito.eq("Subject"),
                Mockito.eq("Body"), Mockito.any());

        var executor = Executors.newSingleThreadExecutor();
        try {
            var delivery = executor.submit(() -> worker.deliverClaimed(claim, NOW));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.claimDueDeliveries(NOW.plusSeconds(31), Duration.ofSeconds(30))).isEmpty();
            release.countDown();
            delivery.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void schedulerRepairsDeliveryAcknowledgedBeforeNotificationStatus() {
        NotificationDelivery delivery = pendingEmail();
        delivery.setDeliveredAt(local(NOW));
        deliveries.save(delivery);

        worker.reconcileDeliveredNotifications();

        Notification repaired = notifications.findById(delivery.getNotificationId()).orElseThrow();
        assertThat(repaired.getStatus()).isEqualTo(Notification.Status.SENT);
        assertThat(repaired.getSentAt()).isEqualTo(local(NOW));
        assertThat(deliveries.findById(delivery.getId()).orElseThrow().getReconciledAt()).isEqualTo(local(NOW));
    }

    @Test
    void transientMailFailureBacksOffThenAcknowledgesOnlySuccessfulRetry() {
        NotificationDelivery delivery = pendingEmail();
        doThrow(new IllegalStateException("smtp unavailable"))
                .doNothing()
                .when(emailService).sendEmail(Mockito.eq("owner@example.com"), Mockito.eq("Subject"),
                        Mockito.eq("Body"), Mockito.any());

        NotificationDelivery firstClaim = worker.claimDueDeliveries(NOW, Duration.ofSeconds(30)).get(0);
        worker.deliverClaimed(firstClaim, NOW);

        NotificationDelivery retry = deliveries.findById(delivery.getId()).orElseThrow();
        assertThat(retry.getDeliveredAt()).isNull();
        assertThat(retry.getLeaseUntil()).isNull();
        assertThat(retry.getNextAttemptAt()).isEqualTo(local(NOW.plusSeconds(60)));
        assertThat(notifications.findById(retry.getNotificationId()).orElseThrow().getStatus())
                .isEqualTo(Notification.Status.PENDING);
        assertThat(worker.claimDueDeliveries(NOW.plusSeconds(59), Duration.ofSeconds(30))).isEmpty();

        NotificationDelivery secondClaim = worker.claimDueDeliveries(
                NOW.plusSeconds(60), Duration.ofSeconds(30)).get(0);
        worker.deliverClaimed(secondClaim, NOW.plusSeconds(60));

        NotificationDelivery acknowledged = deliveries.findById(delivery.getId()).orElseThrow();
        assertThat(acknowledged.getDeliveredAt()).isEqualTo(local(NOW.plusSeconds(60)));
        assertThat(acknowledged.getLeaseUntil()).isNull();
        assertThat(notifications.findById(acknowledged.getNotificationId()).orElseThrow().getStatus())
                .isEqualTo(Notification.Status.SENT);
    }

    @Test
    void repeatedMailFailureStopsAfterThreeAttempts() {
        NotificationDelivery delivery = pendingEmail();
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(emailService).sendEmail(Mockito.eq("owner@example.com"), Mockito.eq("Subject"),
                        Mockito.eq("Body"), Mockito.any());

        failClaimAt(NOW);
        failClaimAt(NOW.plusSeconds(60));
        failClaimAt(NOW.plusSeconds(180));

        NotificationDelivery terminal = deliveries.findById(delivery.getId()).orElseThrow();
        assertThat(terminal.getAttempts()).isEqualTo(3);
        assertThat(terminal.getDeliveredAt()).isNull();
        assertThat(terminal.getLeaseUntil()).isNull();
        assertThat(terminal.getNextAttemptAt()).isNull();
        assertThat(notifications.findById(terminal.getNotificationId()).orElseThrow().getStatus())
                .isEqualTo(Notification.Status.FAILED);
        assertThat(worker.claimDueDeliveries(NOW.plus(Duration.ofDays(1)), Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    void listForUserReturnsOnlyOwnedNotifications() {
        UUID owner = UUID.randomUUID();
        notifications.insert(notification(owner, "owner@example.com"));
        notifications.insert(notification(UUID.randomUUID(), "other@example.com"));
        NotificationService service = new NotificationService(
                notifications, deliveries, emailService,
                new NotificationMapper(), new NotificationAccessValidator());

        var page = service.listForUser(owner, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement()
                .satisfies(item -> assertThat(item.userId()).isEqualTo(owner));
    }

    private NotificationDelivery pendingEmail() {
        Notification notification = notifications.insert(notification(UUID.randomUUID(), "owner@example.com"));
        return deliveries.insert(NotificationDelivery.builder()
                .notificationId(notification.getId())
                .attempts(0)
                .build());
    }

    private Notification notification(UUID userId, String recipient) {
        return Notification.builder()
                .userId(userId)
                .recipient(recipient)
                .channel("EMAIL")
                .category("ORDER")
                .subject("Subject")
                .body("Body")
                .status(Notification.Status.PENDING)
                .createdAt(local(NOW))
                .build();
    }

    private void failClaimAt(Instant now) {
        NotificationDelivery claim = worker.claimDueDeliveries(now, Duration.ofSeconds(30)).get(0);
        worker.deliverClaimed(claim, now);
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
