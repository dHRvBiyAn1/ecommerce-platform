package com.project.notification.service;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.model.Notification;
import com.project.notification.repository.NotificationDeliveryRepository;
import com.project.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private EmailService emailService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                repository, deliveryRepository, emailService,
                new NotificationMapper(), new NotificationAccessValidator());
    }

    @Test
    void ownerCanMarkNotificationRead() {
        UUID ownerId = UUID.randomUUID();
        Notification notification = notification(ownerId);
        when(repository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.markRead("notification-1", ownerId, false);

        assertThat(response.status()).isEqualTo(NotificationResponse.Status.READ);
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void anotherCustomerCannotMarkNotificationRead() {
        Notification notification = notification(UUID.randomUUID());
        when(repository.findById("notification-1")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead("notification-1", UUID.randomUUID(), false))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void administratorCanMarkAnotherUsersNotificationRead() {
        Notification notification = notification(UUID.randomUUID());
        when(repository.findById("notification-1")).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.markRead("notification-1", UUID.randomUUID(), true);

        assertThat(response.status()).isEqualTo(NotificationResponse.Status.READ);
    }

    @Test
    void missingNotificationUsesSharedNotFoundContract() {
        UUID callerId = UUID.randomUUID();
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("missing", callerId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Notification notification(UUID ownerId) {
        return Notification.builder()
                .id("notification-1")
                .userId(ownerId)
                .status(Notification.Status.SENT)
                .build();
    }
}
