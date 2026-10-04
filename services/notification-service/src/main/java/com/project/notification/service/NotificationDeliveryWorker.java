package com.project.notification.service;

import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import com.project.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import com.mongodb.client.result.UpdateResult;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class NotificationDeliveryWorker {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(60);
    // Three 25-second JavaMail timeouts plus a 45-second safety margin.
    private static final Duration DELIVERY_LEASE = Duration.ofSeconds(120);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private final MongoTemplate mongoTemplate;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final Duration heartbeatInterval;
    private final Clock clock;

    @Autowired
    public NotificationDeliveryWorker(MongoTemplate mongoTemplate, NotificationRepository notificationRepository,
                                      EmailService emailService) {
        this(mongoTemplate, notificationRepository, emailService, HEARTBEAT_INTERVAL, Clock.systemUTC());
    }

    NotificationDeliveryWorker(MongoTemplate mongoTemplate, NotificationRepository notificationRepository,
                               EmailService emailService, Duration heartbeatInterval) {
        this(mongoTemplate, notificationRepository, emailService, heartbeatInterval, Clock.systemUTC());
    }

    NotificationDeliveryWorker(MongoTemplate mongoTemplate, NotificationRepository notificationRepository,
                               EmailService emailService, Duration heartbeatInterval, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.heartbeatInterval = heartbeatInterval;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.poll-ms:1000}")
    public void processDueDeliveries() {
        reconcileDeliveredNotifications();
        Instant now = clock.instant();
        claimDueDeliveries(now, DELIVERY_LEASE)
                .stream()
                .findFirst()
                .ifPresent(claim -> deliverClaimed(claim, now));
    }

    public List<NotificationDelivery> claimDueDeliveries(Instant now, Duration lease) {
        LocalDateTime current = local(now);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("deliveredAt").is(null),
                Criteria.where("attempts").lt(MAX_ATTEMPTS),
                new Criteria().orOperator(
                        Criteria.where("nextAttemptAt").is(null),
                        Criteria.where("nextAttemptAt").lte(current)),
                new Criteria().orOperator(
                        Criteria.where("leaseUntil").is(null),
                        Criteria.where("leaseUntil").lte(current))))
                .with(Sort.by(Sort.Direction.ASC, "nextAttemptAt", "createdAt"));

        NotificationDelivery delivery = mongoTemplate.findAndModify(
                query,
                new Update().inc("attempts", 1)
                        .set("leaseUntil", local(now.plus(lease)))
                        .set("leaseToken", UUID.randomUUID().toString()),
                FindAndModifyOptions.options().returnNew(true),
                NotificationDelivery.class);
        return delivery == null ? List.of() : List.of(delivery);
    }

    public void deliverClaimed(NotificationDelivery claim, Instant now) {
        Query ownership = ownership(claim, clock.instant());
        NotificationDelivery owned = mongoTemplate.findOne(ownership, NotificationDelivery.class);
        if (owned == null) {
            return;
        }

        Notification notification = notificationRepository.findById(owned.getNotificationId()).orElse(null);
        if (notification == null) {
            return;
        }

        try {
            if (!"EMAIL".equals(notification.getChannel()) || notification.getRecipient() == null
                    || notification.getRecipient().isBlank()) {
                throw new IllegalStateException("Unsupported notification delivery");
            }
            Instant sendAt = clock.instant();
            UpdateResult renewal = mongoTemplate.updateFirst(ownership(claim, sendAt),
                    new Update().set("leaseUntil", local(sendAt.plus(DELIVERY_LEASE))),
                    NotificationDelivery.class);
            if (renewal.getModifiedCount() != 1) {
                return;
            }
            if (!sendWithHeartbeat(claim, notification)) {
                return;
            }
            LocalDateTime completedAt = local(clock.instant());
            Query activeOwnership = ownership(claim, clock.instant());
            if (mongoTemplate.updateFirst(activeOwnership, new Update()
                    .set("deliveredAt", completedAt)
                    .set("leaseUntil", null)
                    .set("leaseToken", null)
                    .set("nextAttemptAt", null), NotificationDelivery.class).getModifiedCount() == 1) {
                notification.setStatus(Notification.Status.SENT);
                notification.setSentAt(completedAt);
                notificationRepository.save(notification);
            }
        } catch (Exception exception) {
            LocalDateTime current = local(clock.instant());
            boolean terminal = owned.getAttempts() >= MAX_ATTEMPTS;
            Update failure = new Update().set("leaseUntil", null).set("leaseToken", null);
            if (terminal) {
                failure.set("nextAttemptAt", null);
            } else {
                failure.set("nextAttemptAt", current.plus(FIRST_RETRY_DELAY.multipliedBy(owned.getAttempts())));
            }
            if (mongoTemplate.updateFirst(ownership(claim, clock.instant()), failure, NotificationDelivery.class).getModifiedCount() == 1
                    && terminal) {
                notification.setStatus(Notification.Status.FAILED);
                notification.setFailureReason(exception.getMessage());
                notificationRepository.save(notification);
            }
            log.warn("Notification delivery failed id={} attempt={}: {}",
                    owned.getNotificationId(), owned.getAttempts(), exception.getMessage());
        }
    }

    public void reconcileDeliveredNotifications() {
        Query delivered = new Query(new Criteria().andOperator(
                Criteria.where("deliveredAt").exists(true).ne(null),
                Criteria.where("reconciledAt").is(null)));
        for (NotificationDelivery delivery : mongoTemplate.find(delivered, NotificationDelivery.class)) {
            notificationRepository.findById(delivery.getNotificationId()).ifPresent(notification -> {
                if (notification.getStatus() == Notification.Status.PENDING
                        || notification.getStatus() == Notification.Status.FAILED) {
                    notification.setStatus(Notification.Status.SENT);
                    notification.setSentAt(delivery.getDeliveredAt());
                    notification.setFailureReason(null);
                    notificationRepository.save(notification);
                }
                mongoTemplate.updateFirst(new Query(new Criteria().andOperator(
                                Criteria.where("_id").is(delivery.getId()),
                                Criteria.where("deliveredAt").exists(true).ne(null),
                                Criteria.where("reconciledAt").is(null))),
                        new Update().set("reconciledAt", local(clock.instant())), NotificationDelivery.class);
            });
        }
    }

    private boolean sendWithHeartbeat(NotificationDelivery claim, Notification notification) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean ownershipLost = new AtomicBoolean();
        ScheduledFuture<?> heartbeat = executor.scheduleAtFixedRate(
                () -> renewLease(claim, ownershipLost), heartbeatInterval.toMillis(), heartbeatInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        try {
            emailService.sendEmail(notification.getRecipient(), notification.getSubject(),
                    notification.getBody(), notification.getId());
        } finally {
            heartbeat.cancel(false);
            executor.shutdownNow();
        }
        return !ownershipLost.get();
    }

    private void renewLease(NotificationDelivery claim, AtomicBoolean ownershipLost) {
        Instant now = clock.instant();
        try {
            UpdateResult result = mongoTemplate.updateFirst(ownership(claim, now),
                    new Update().set("leaseUntil", local(now.plus(DELIVERY_LEASE))),
                    NotificationDelivery.class);
            if (result.getModifiedCount() != 1) {
                ownershipLost.set(true);
                log.warn("Notification delivery lease lost id={}", claim.getNotificationId());
            }
        } catch (Exception exception) {
            ownershipLost.set(true);
            log.warn("Notification delivery heartbeat failed id={}: {}",
                    claim.getNotificationId(), exception.getMessage());
        }
    }

    private Query ownership(NotificationDelivery claim, Instant now) {
        return new Query(new Criteria().andOperator(
                Criteria.where("_id").is(claim.getId()),
                Criteria.where("leaseToken").is(claim.getLeaseToken()),
                Criteria.where("leaseUntil").gt(local(now)),
                Criteria.where("deliveredAt").is(null)));
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
