package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.seller.RejectApplicationRequest;
import com.project.authservice.dto.seller.SellerApplicationRequest;
import com.project.authservice.dto.seller.SellerApplicationResponse;
import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer-facing endpoints for the seller-application workflow.
 * Admin endpoints live on {@link AdminSellerApplicationController}.
 *
 * <p>auth-service authenticates with its own JwtAuthFilter (not the common
 * resource-server flow), so we extract userId from {@code authentication.getName()}
 * directly rather than going through {@code CurrentUser}, matching the pattern
 * in {@link UserController}.
 */
@RestController
@RequestMapping("/api/user/seller-application")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService service;

    /** 200 with the application body, or 204 if the user has never applied. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> getMine(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return service.getMine(userId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> apply(
            @Valid @RequestBody SellerApplicationRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        SellerApplicationResponse out = service.apply(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(out));
    }
}

@RestController
@RequestMapping("/api/admin/seller-applications")
@RequiredArgsConstructor
class AdminSellerApplicationController {

    private final SellerApplicationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<Page<SellerApplicationResponse>>> list(
            @RequestParam(required = false) SellerApplicationStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('admin:users:write')")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> approve(
            @PathVariable UUID id, Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(service.approve(id, adminId)));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('admin:users:write')")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectApplicationRequest req,
            Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(service.reject(id, adminId, req)));
    }
}
