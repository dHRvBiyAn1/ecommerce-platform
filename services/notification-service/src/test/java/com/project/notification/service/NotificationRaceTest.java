package com.project.notification.service;

import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import com.project.notification.repository.NotificationDeliveryRepository;
import com.project.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRaceTest {

    @Mock private NotificationRepository repository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private EmailService emailService;

    @Test
    void duplicateSourceEventRereadsWinnerAndCreatesOneDelivery() {
        UUID userId = UUID.randomUUID();
        Notification winner = Notification.builder()
                .id("winner")
                .userId(userId)
                .sourceEventId("event-1")
                .status(Notification.Status.PENDING)
                .build();
        AtomicBoolean firstSave = new AtomicBoolean(true);
        when(repository.findBySourceEventId("event-1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.insert(any(Notification.class))).thenAnswer(invocation -> {
            if (firstSave.getAndSet(false)) {
                throw new DuplicateKeyException("sourceEventId");
            }
            return invocation.getArgument(0);
        });
        when(deliveryRepository.findByNotificationId("winner")).thenReturn(Optional.empty());
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationService service = new NotificationService(
                repository, deliveryRepository, emailService,
                new NotificationMapper(), new NotificationAccessValidator());

        NotificationResponse response = service.record(
                userId, "user@example.com", "EMAIL", "ORDER", "Subject", "Body", "event-1");

        assertThat(response.id()).isEqualTo("winner");
        assertThat(response.status()).isEqualTo(NotificationResponse.Status.PENDING);
        verify(deliveryRepository).save(any(NotificationDelivery.class));
        verify(repository, times(1)).insert(any(Notification.class));
        verify(repository, times(2)).findBySourceEventId("event-1");
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void concurrentDuplicateSourceEventsPersistOneNotificationAndDelivery() throws Exception {
        UUID userId = UUID.randomUUID();
        AtomicReference<Notification> storedNotification = new AtomicReference<>();
        AtomicReference<NotificationDelivery> storedDelivery = new AtomicReference<>();
        when(repository.findBySourceEventId("event-2"))
                .thenAnswer(invocation -> Optional.ofNullable(storedNotification.get()));
        when(repository.insert(any(Notification.class))).thenAnswer(invocation -> {
            synchronized (storedNotification) {
                Notification notification = invocation.getArgument(0);
                if (storedNotification.get() != null && notification.getId() == null) {
                    throw new DuplicateKeyException("sourceEventId");
                }
                if (notification.getId() == null) {
                    notification.setId("notification-2");
                }
                storedNotification.set(notification);
                return notification;
            }
        });
        when(repository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.findByNotificationId("notification-2"))
                .thenAnswer(invocation -> Optional.ofNullable(storedDelivery.get()));
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> {
            synchronized (storedDelivery) {
                if (storedDelivery.get() != null) {
                    throw new DuplicateKeyException("notificationId");
                }
                NotificationDelivery delivery = invocation.getArgument(0);
                delivery.setId("delivery-2");
                storedDelivery.set(delivery);
                return delivery;
            }
        });

        NotificationService service = new NotificationService(
                repository, deliveryRepository, emailService,
                new NotificationMapper(), new NotificationAccessValidator());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<NotificationResponse> first = executor.submit(() -> recordAfter(start, service, userId));
            Future<NotificationResponse> second = executor.submit(() -> recordAfter(start, service, userId));
            start.countDown();

            assertThat(first.get().id()).isEqualTo("notification-2");
            assertThat(second.get().id()).isEqualTo("notification-2");
        } finally {
            executor.shutdownNow();
        }

        assertThat(storedNotification.get()).isNotNull();
        assertThat(storedDelivery.get()).isNotNull();
        verify(repository, atLeast(2)).findBySourceEventId("event-2");
        verify(deliveryRepository, atLeast(1)).findByNotificationId("notification-2");
    }

    private NotificationResponse recordAfter(
            CountDownLatch start, NotificationService service, UUID userId) throws InterruptedException {
        start.await();
        return service.record(userId, "user@example.com", "EMAIL", "ORDER",
                "Subject", "Body", "event-2");
    }

    @Test
    void responseRecordDoesNotExposeNotificationDocumentTypes() {
        assertThat(NotificationResponse.class.isRecord()).isTrue();
        for (RecordComponent component : NotificationResponse.class.getRecordComponents()) {
            assertThat(component.getType()).isNotEqualTo(Notification.class);
            assertThat(component.getType().getName()).doesNotContain("notification.model");
        }
    }
}
