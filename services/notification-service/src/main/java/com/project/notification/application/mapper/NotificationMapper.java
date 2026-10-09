package com.project.notification.application.mapper;

import com.project.notification.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public com.project.notification.generated.model.NotificationResponse toResponse(Notification notification) {
        return new com.project.notification.generated.model.NotificationResponse()
                .id(notification.getId())
                .userId(notification.getUserId())
                .recipient(notification.getRecipient())
                .channel(notification.getChannel())
                .category(notification.getCategory())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus() == null ? null
                        : com.project.notification.generated.model.NotificationResponse.StatusEnum
                                .valueOf(notification.getStatus().name()))
                .failureReason(notification.getFailureReason())
                .retryCount(notification.getRetryCount())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt());
    }
}
