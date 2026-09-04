package com.project.notification.application.mapper;

import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getRecipient(),
                notification.getChannel(),
                notification.getCategory(),
                notification.getSubject(),
                notification.getBody(),
                notification.getStatus(),
                notification.getFailureReason(),
                notification.getRetryCount(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getSentAt(),
                notification.getReadAt());
    }
}
