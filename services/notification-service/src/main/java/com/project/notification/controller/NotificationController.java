package com.project.notification.controller;

import com.project.common.dto.ApiResponse;
import com.project.common.dto.PageResponse;
import com.project.common.security.CurrentUser;
import com.project.notification.api.dto.response.NotificationResponse;
import com.project.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Authenticated user's notification history")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List the current user's notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> page = service.listForUser(CurrentUser.requireId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/unread/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count unread notifications for the current user")
    public ResponseEntity<ApiResponse<Long>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(service.unreadCount(CurrentUser.requireId())));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark an owned notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                service.markRead(id, CurrentUser.requireId(), CurrentUser.isAdmin())));
    }
}
