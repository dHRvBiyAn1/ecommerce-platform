package com.project.notification.service;

import com.project.common.exception.ResourceNotFoundException;
import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.model.Notification;
import com.project.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final EmailService emailService;
    private final NotificationMapper notificationMapper;
    private final NotificationAccessValidator accessValidator;

    public NotificationResponse record(UUID userId, String recipient, String channel, String category,
                                       String subject, String body, String sourceEventId) {
        // Idempotent on sourceEventId: skip if we already processed this event
        if (sourceEventId != null) {
            var existing = repository.findBySourceEventId(sourceEventId);
            if (existing.isPresent()) {
                log.debug("Skipping notification: sourceEventId {} already processed", sourceEventId);
                return notificationMapper.toResponse(existing.get());
            }
        }

        Notification n = Notification.builder()
                .userId(userId)
                .recipient(recipient)
                .channel(channel)
                .category(category)
                .subject(subject)
                .body(body)
                .status(Notification.Status.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .sourceEventId(sourceEventId)
                .build();
        n = repository.save(n);
        try {
            if ("EMAIL".equals(channel) && recipient != null) {
                emailService.sendEmail(recipient, subject, body);
                n.setStatus(Notification.Status.SENT);
                n.setSentAt(LocalDateTime.now());
            } else {
                // Future: SMS / push / in-app gateways
                n.setStatus(Notification.Status.SENT);
                n.setSentAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            n.setStatus(Notification.Status.FAILED);
            n.setFailureReason(e.getMessage());
            log.warn("Notification send failed for userId={}, recipient={}: {}",
                    userId, recipient, e.getMessage());
        }
        return notificationMapper.toResponse(repository.save(n));
    }

    public Page<NotificationResponse> listForUser(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(notificationMapper::toResponse);
    }

    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndStatus(userId, Notification.Status.SENT);
    }

    public NotificationResponse markRead(String id, UUID requesterId, boolean administrator) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        accessValidator.validateAccess(n, requesterId, administrator);
        n.setStatus(Notification.Status.READ);
        n.setReadAt(LocalDateTime.now());
        return notificationMapper.toResponse(repository.save(n));
    }
}
