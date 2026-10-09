package com.project.notification.controller;

import com.project.common.web.Responses;
import com.project.common.security.CurrentUser;
import com.project.notification.generated.model.NotificationResponse;
import com.project.notification.service.NotificationService;
import com.project.notification.generated.api.NotificationsApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Authenticated user's notification history")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController implements NotificationsApi {

    private final NotificationService service;

    @Override
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List the current user's notifications")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<com.project.notification.generated.model.ApiResponsePageResponseNotificationResponse> callList(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> results = service.listForUser(CurrentUser.requireId(), pageable);
        var page = new com.project.notification.generated.model.PageResponseNotificationResponse()
                .content(results.getContent())
                .page(results.getNumber())
                .size(results.getSize())
                .totalElements(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .first(results.isFirst())
                .last(results.isLast());
        var response = Responses.success(page);
        return ResponseEntity.ok(new com.project.notification.generated.model.ApiResponsePageResponseNotificationResponse()
                .status(response.getStatus()).message(response.getMessage()).traceId(response.getTraceId())
                .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), java.time.ZoneOffset.UTC))
                .data(page));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count unread notifications for the current user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<com.project.notification.generated.model.ApiResponseLong> unreadCount() {
        var response = Responses.success(service.unreadCount(CurrentUser.requireId()));
        return ResponseEntity.ok(new com.project.notification.generated.model.ApiResponseLong()
                .status(response.getStatus()).message(response.getMessage()).traceId(response.getTraceId())
                .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), java.time.ZoneOffset.UTC))
                .data(response.getData()));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark an owned notification as read")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<com.project.notification.generated.model.ApiResponseNotificationResponse> markRead(String id) {
        var data = service.markRead(id, CurrentUser.requireId(), CurrentUser.isAdmin());
        var response = Responses.success(
                data);
        return ResponseEntity.ok(new com.project.notification.generated.model.ApiResponseNotificationResponse()
                .status(response.getStatus()).message(response.getMessage()).traceId(response.getTraceId())
                .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), java.time.ZoneOffset.UTC))
                .data(data));
    }
}
