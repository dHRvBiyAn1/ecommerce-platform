package com.project.notification.api.dto.response;

import com.project.notification.model.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        String id,
        UUID userId,
        String recipient,
        String channel,
        String category,
        String subject,
        String body,
        Notification.Status status,
        String failureReason,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {}
