package com.project.authservice.controller;

import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.dto.request.UserUpdateRequest;
import com.project.common.dto.ApiResponse;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthenticatedUserValidator authenticatedUserValidator;

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileDto>> getProfile(Authentication authentication) {
        UUID userId = authenticatedUserValidator.requireUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getProfile(userId)));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateProfile(
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        UUID userId = authenticatedUserValidator.requireUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateProfile(userId, request)));
    }
}
