package com.project.notification.application.mapper;

import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.model.Notification;
import com.project.common.dto.PageResponse;
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
                NotificationResponse.Status.valueOf(notification.getStatus().name()),
                notification.getFailureReason(),
                notification.getRetryCount(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getSentAt(),
                notification.getReadAt());
    }

    public com.project.notification.generated.model.NotificationResponse toApi(NotificationResponse response) {
        return new com.project.notification.generated.model.NotificationResponse()
                .id(response.id())
                .userId(response.userId())
                .recipient(response.recipient())
                .channel(response.channel())
                .category(response.category())
                .subject(response.subject())
                .body(response.body())
                .status(response.status() == null ? null
                        : com.project.notification.generated.model.NotificationResponse.StatusEnum
                                .valueOf(response.status().name()))
                .failureReason(response.failureReason())
                .retryCount(response.retryCount())
                .createdAt(response.createdAt())
                .updatedAt(response.updatedAt())
                .sentAt(response.sentAt())
                .readAt(response.readAt());
    }

    public com.project.notification.generated.model.PageResponseNotificationResponse toApi(
            PageResponse<NotificationResponse> page) {
        return new com.project.notification.generated.model.PageResponseNotificationResponse()
                .content(page.getContent().stream().map(this::toApi).toList())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast());
    }
}
