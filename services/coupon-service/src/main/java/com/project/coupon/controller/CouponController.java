package com.project.coupon.controller;

import com.project.common.security.CurrentUser;
import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponReservationRequest;
import com.project.coupon.dto.CouponReservationResponse;
import com.project.coupon.dto.CouponResponse;
import com.project.coupon.dto.CouponTransitionRequest;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.dto.ValidateCouponRequest;
import com.project.coupon.dto.ValidateCouponResponse;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.project.common.constant.Permissions.COUPONS_READ;
import static com.project.common.constant.Permissions.COUPONS_WRITE;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon administration, validation, and checkout lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class CouponController {

    private final CouponService couponService;
    private final CouponRequestValidator requestValidator;

    // -------------------------- Admin CRUD --------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('" + COUPONS_WRITE + "')")
    @Operation(summary = "Create a coupon")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequest req) {
        requestValidator.validateDefinition(req);
        return new ResponseEntity<>(couponService.create(req), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + COUPONS_WRITE + "')")
    @Operation(summary = "Update a coupon")
    public ResponseEntity<CouponResponse> update(@PathVariable UUID id, @Valid @RequestBody CouponRequest req) {
        requestValidator.validateDefinition(req);
        return ResponseEntity.ok(couponService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + COUPONS_WRITE + "')")
    @Operation(summary = "Delete a coupon")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + COUPONS_READ + "') or hasRole('ADMIN')")
    @Operation(summary = "Get a coupon")
    public ResponseEntity<CouponResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + COUPONS_READ + "') or hasRole('ADMIN')")
    @Operation(summary = "List coupons")
    public ResponseEntity<Page<CouponResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(couponService.list(pageable));
    }

    // -------------------------- Validate / redeem --------------------------

    /**
     * Called by cart-service when the customer applies a coupon code. Any
     * authenticated user may call it for their own validation.
     */
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate a coupon without reserving it")
    public ResponseEntity<ValidateCouponResponse> validate(@Valid @RequestBody ValidateCouponRequest req) {
        requestValidator.validateValidation(req);
        validateActor(req.userId());
        return ResponseEntity.ok(couponService.validate(req));
    }

    @PostMapping("/reserve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reserve coupon capacity for an order")
    public ResponseEntity<CouponReservationResponse> reserve(
            @Valid @RequestBody CouponReservationRequest req) {
        requestValidator.validateReservation(req);
        validateActor(req.userId());
        return ResponseEntity.ok(couponService.reserve(req));
    }

    @PostMapping("/commit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Commit an order's coupon reservation after checkout succeeds")
    public ResponseEntity<CouponReservationResponse> commit(
            @Valid @RequestBody CouponTransitionRequest req) {
        requestValidator.validateTransition(req);
        validateActor(req.userId());
        return ResponseEntity.ok(couponService.commit(req));
    }

    @PostMapping("/release")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Release an order's coupon reservation after checkout fails")
    public ResponseEntity<CouponReservationResponse> release(
            @Valid @RequestBody CouponTransitionRequest req) {
        requestValidator.validateTransition(req);
        validateActor(req.userId());
        return ResponseEntity.ok(couponService.release(req));
    }

    /**
     * Called by order-service after a payment is captured. Atomically
     * increments usageCount and records a redemption row.
     */
    @PostMapping("/redeem")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Directly commit a coupon for backward-compatible post-payment callers")
    public ResponseEntity<ValidateCouponResponse> redeem(@Valid @RequestBody RedeemCouponRequest req) {
        requestValidator.validateRedemption(req);
        validateActor(req.userId());
        return ResponseEntity.ok(couponService.redeem(req));
    }

    private void validateActor(UUID claimedUserId) {
        requestValidator.validateActor(claimedUserId, CurrentUser.requireId(), CurrentUser.isAdmin());
    }
}
