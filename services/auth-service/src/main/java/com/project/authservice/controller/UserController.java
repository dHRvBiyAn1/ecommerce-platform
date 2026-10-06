package com.project.authservice.controller;

import com.project.authservice.generated.api.UserProfileApi;
import com.project.authservice.generated.model.ApiResponseSellerApplicationResponse;
import com.project.authservice.generated.model.ApiResponseUserProfileDto;
import com.project.authservice.generated.model.SellerApplicationRequest;
import com.project.authservice.generated.model.UserUpdateRequest;
import com.project.authservice.mapper.AuthApiMapper;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.SellerApplicationService;
import com.project.authservice.service.UserProfileService;
import com.project.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController implements UserProfileApi {

    private final UserProfileService userProfileService;
    private final SellerApplicationService sellerApplicationService;
    private final AuthenticatedUserValidator authenticatedUserValidator;
    private final AuthApiMapper apiMapper;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseUserProfileDto> getProfile() {
        UUID userId = authenticatedUserValidator.requireUserId(SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(apiMapper.toApiUserProfile(
                ApiResponse.success(userProfileService.getProfile(userId))));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseUserProfileDto> updateProfile(UserUpdateRequest userUpdateRequest) {
        UUID userId = authenticatedUserValidator.requireUserId(SecurityContextHolder.getContext().getAuthentication());
        var profile = userProfileService.updateProfile(userId, apiMapper.toDomain(userUpdateRequest));
        return ResponseEntity.ok(apiMapper.toApiUserProfile(ApiResponse.success(profile)));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseSellerApplicationResponse> getMine() {
        UUID userId = authenticatedUserValidator.requireUserId(SecurityContextHolder.getContext().getAuthentication());
        return sellerApplicationService.getMine(userId)
                .map(response -> ResponseEntity.ok(apiMapper.toApiSellerApplication(ApiResponse.success(response))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseSellerApplicationResponse> apply(SellerApplicationRequest sellerApplicationRequest) {
        UUID userId = authenticatedUserValidator.requireUserId(SecurityContextHolder.getContext().getAuthentication());
        var response = sellerApplicationService.apply(userId, apiMapper.toDomain(sellerApplicationRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiMapper.toApiSellerApplication(ApiResponse.created(response)));
    }
}
