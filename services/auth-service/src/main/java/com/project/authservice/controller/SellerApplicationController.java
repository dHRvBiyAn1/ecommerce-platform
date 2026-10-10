package com.project.authservice.controller;

import com.project.authservice.entity.SellerApplicationStatus;
import com.project.authservice.generated.model.RejectApplicationRequest;
import com.project.authservice.generated.model.SellerApplicationRequest;
import com.project.authservice.generated.model.SellerApplicationResponse;
import com.project.authservice.security.AuthenticatedUserValidator;
import com.project.authservice.service.SellerApplicationService;
import com.project.common.constant.Permissions;
import com.project.common.generated.model.PageEnvelope;
import com.project.common.generated.model.ResponseEnvelope;
import com.project.common.web.Responses;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Customer-facing endpoints for the seller-application workflow. Admin endpoints live on {@link
 * AdminSellerApplicationController}.
 *
 * <p>auth-service authenticates with its own JwtAuthFilter (not the common resource-server flow),
 * so the dedicated validator handles the UUID subject.
 */
@RequiredArgsConstructor
public class SellerApplicationController {

  private final SellerApplicationService service;
  private final AuthenticatedUserValidator authenticatedUserValidator;

  /** 200 with the application body, or 204 if the user has never applied. */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ResponseEnvelope<SellerApplicationResponse>> getMine(Authentication auth) {
    UUID userId = authenticatedUserValidator.requireUserId(auth);
    return service
        .getMine(userId)
        .map(r -> ResponseEntity.ok(Responses.success(r)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ResponseEnvelope<SellerApplicationResponse>> apply(
      @Valid @RequestBody SellerApplicationRequest req, Authentication auth) {
    UUID userId = authenticatedUserValidator.requireUserId(auth);
    SellerApplicationResponse out = service.apply(userId, req);
    return ResponseEntity.status(HttpStatus.CREATED).body(Responses.created(out));
  }
}

@RequiredArgsConstructor
class AdminSellerApplicationController {

  private final SellerApplicationService service;
  private final AuthenticatedUserValidator authenticatedUserValidator;

  @GetMapping
  @PreAuthorize("hasAuthority('" + Permissions.USERS_READ + "')")
  public ResponseEntity<ResponseEnvelope<PageEnvelope<SellerApplicationResponse>>> list(
      @RequestParam(required = false) SellerApplicationStatus status,
      @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        Responses.envelope(200, "Success", Responses.page(service.list(status, pageable))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('" + Permissions.USERS_READ + "')")
  public ResponseEntity<ResponseEnvelope<SellerApplicationResponse>> get(@PathVariable UUID id) {
    return ResponseEntity.ok(Responses.success(service.get(id)));
  }

  @PutMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('" + Permissions.USERS_WRITE + "')")
  public ResponseEntity<ResponseEnvelope<SellerApplicationResponse>> approve(
      @PathVariable UUID id, Authentication auth) {
    UUID adminId = authenticatedUserValidator.requireUserId(auth);
    return ResponseEntity.ok(Responses.success(service.approve(id, adminId)));
  }

  @PutMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('" + Permissions.USERS_WRITE + "')")
  public ResponseEntity<ResponseEnvelope<SellerApplicationResponse>> reject(
      @PathVariable UUID id,
      @Valid @RequestBody RejectApplicationRequest req,
      Authentication auth) {
    UUID adminId = authenticatedUserValidator.requireUserId(auth);
    return ResponseEntity.ok(Responses.success(service.reject(id, adminId, req)));
  }
}
