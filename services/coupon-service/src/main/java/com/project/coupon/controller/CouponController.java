package com.project.coupon.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.common.exception.ForbiddenOperationException;
import com.project.common.security.CurrentUser;
import com.project.coupon.mapper.CouponApiMapper;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import com.project.coupon.generated.api.CouponsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CouponController implements CouponsApi {

    private final CouponService couponService;
    private final CouponRequestValidator requestValidator;
    private final CouponApiMapper apiMapper;

    @Override
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<com.project.coupon.generated.model.CouponResponse> createCoupon(
            com.project.coupon.generated.model.CouponRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateDefinition(domainRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(apiMapper.toApi(couponService.create(domainRequest)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<com.project.coupon.generated.model.CouponResponse> updateCoupon(
            UUID id, com.project.coupon.generated.model.CouponRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateDefinition(domainRequest);
        return ResponseEntity.ok(apiMapper.toApi(couponService.update(id, domainRequest)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('" + Permissions.COUPONS_WRITE + "')")
    public ResponseEntity<Void> deleteCoupon(UUID id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_READ + "') or hasRole('ADMIN')")
    public ResponseEntity<com.project.coupon.generated.model.CouponResponse> getCoupon(UUID id) {
        return ResponseEntity.ok(apiMapper.toApi(couponService.get(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.COUPONS_READ + "') or hasRole('ADMIN')")
    public ResponseEntity<com.project.coupon.generated.model.PageCouponResponse> listCoupons(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(couponService.list(pageable)));
    }

    @Override
    @PreAuthorize("isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
            + ServiceScopes.AUTHORITY_COUPONS_READ + "'))")
    public ResponseEntity<com.project.coupon.generated.model.ValidateCouponResponse> validateCoupon(
            com.project.coupon.generated.model.ValidateCouponRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateValidation(domainRequest);
        validateActor(domainRequest.userId(), ServiceScopes.AUTHORITY_COUPONS_READ);
        return ResponseEntity.ok(apiMapper.toApi(couponService.validate(domainRequest)));
    }

    @Override
    @PreAuthorize("isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
            + ServiceScopes.AUTHORITY_COUPONS_WRITE + "'))")
    public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse> reserveCoupon(
            com.project.coupon.generated.model.CouponReservationRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateReservation(domainRequest);
        validateActor(domainRequest.userId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
        return ResponseEntity.ok(apiMapper.toApi(couponService.reserve(domainRequest)));
    }

    @Override
    @PreAuthorize("isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
            + ServiceScopes.AUTHORITY_COUPONS_WRITE + "'))")
    public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse> commitCouponReservation(
            com.project.coupon.generated.model.CouponTransitionRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateTransition(domainRequest);
        validateActor(domainRequest.userId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
        return ResponseEntity.ok(apiMapper.toApi(couponService.commit(domainRequest)));
    }

    @Override
    @PreAuthorize("isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
            + ServiceScopes.AUTHORITY_COUPONS_WRITE + "'))")
    public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse> releaseCouponReservation(
            com.project.coupon.generated.model.CouponTransitionRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateTransition(domainRequest);
        validateActor(domainRequest.userId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
        return ResponseEntity.ok(apiMapper.toApi(couponService.release(domainRequest)));
    }

    @Override
    @PreAuthorize("isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
            + ServiceScopes.AUTHORITY_COUPONS_WRITE + "'))")
    public ResponseEntity<com.project.coupon.generated.model.ValidateCouponResponse> redeemCoupon(
            com.project.coupon.generated.model.RedeemCouponRequest request) {
        var domainRequest = apiMapper.toDomain(request);
        requestValidator.validateRedemption(domainRequest);
        validateActor(domainRequest.userId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
        return ResponseEntity.ok(apiMapper.toApi(couponService.redeem(domainRequest)));
    }

    private void validateActor(UUID claimedUserId, String requiredScope) {
        if (CurrentUser.isService()) {
            if (!CurrentUser.hasAuthority(requiredScope)) {
                throw new ForbiddenOperationException("Service scope does not permit this coupon operation");
            }
            return;
        }
        requestValidator.validateActor(claimedUserId, CurrentUser.requireId(), CurrentUser.isAdmin());
    }

}
