package com.project.coupon.controller;

import com.project.common.constant.Permissions;
import com.project.coupon.dto.CouponRequest;
import com.project.coupon.dto.CouponResponse;
import com.project.coupon.dto.RedeemCouponRequest;
import com.project.coupon.dto.ValidateCouponRequest;
import com.project.coupon.dto.ValidateCouponResponse;
import com.project.coupon.service.CouponService;
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

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // -------------------------- Admin CRUD --------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequest req) {
        return new ResponseEntity<>(couponService.create(req), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<CouponResponse> update(@PathVariable UUID id, @Valid @RequestBody CouponRequest req) {
        return ResponseEntity.ok(couponService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_READ + "') or hasRole('ADMIN')")
    public ResponseEntity<CouponResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_READ + "') or hasRole('ADMIN')")
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
    public ResponseEntity<ValidateCouponResponse> validate(@Valid @RequestBody ValidateCouponRequest req) {
        return ResponseEntity.ok(couponService.validate(req));
    }

    /**
     * Called by order-service after a payment is captured. Atomically
     * increments usageCount and records a redemption row.
     */
    @PostMapping("/redeem")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ValidateCouponResponse> redeem(@Valid @RequestBody RedeemCouponRequest req) {
        return ResponseEntity.ok(couponService.redeem(req));
    }
}
