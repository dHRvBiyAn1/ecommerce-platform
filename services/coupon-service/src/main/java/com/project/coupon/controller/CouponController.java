package com.project.coupon.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.common.exception.ForbiddenOperationException;
import com.project.common.security.CurrentUser;
import com.project.coupon.generated.api.CouponsApi;
import com.project.coupon.mapper.CouponApiMapper;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

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
    requestValidator.validateDefinition(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(request));
  }

  @Override
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('" + Permissions.COUPONS_WRITE + "')")
  public ResponseEntity<com.project.coupon.generated.model.CouponResponse> updateCoupon(
      UUID id, com.project.coupon.generated.model.CouponRequest request) {
    requestValidator.validateDefinition(request);
    return ResponseEntity.ok(couponService.update(id, request));
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
    return ResponseEntity.ok(couponService.get(id));
  }

  @Override
  @PreAuthorize("hasAuthority('" + Permissions.COUPONS_READ + "') or hasRole('ADMIN')")
  public ResponseEntity<com.project.coupon.generated.model.PageCouponResponse> listCoupons(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(apiMapper.toApi(couponService.list(pageable)));
  }

  @Override
  @PreAuthorize(
      "isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
          + ServiceScopes.AUTHORITY_COUPONS_READ
          + "'))")
  public ResponseEntity<com.project.coupon.generated.model.ValidateCouponResponse> validateCoupon(
      com.project.coupon.generated.model.ValidateCouponRequest request) {
    requestValidator.validateValidation(request);
    validateActor(request.getUserId(), ServiceScopes.AUTHORITY_COUPONS_READ);
    return ResponseEntity.ok(couponService.validate(request));
  }

  @Override
  @PreAuthorize(
      "isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
          + ServiceScopes.AUTHORITY_COUPONS_WRITE
          + "'))")
  public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse> reserveCoupon(
      com.project.coupon.generated.model.CouponReservationRequest request) {
    requestValidator.validateReservation(request);
    validateActor(request.getUserId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
    return ResponseEntity.ok(couponService.reserve(request));
  }

  @Override
  @PreAuthorize(
      "isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
          + ServiceScopes.AUTHORITY_COUPONS_WRITE
          + "'))")
  public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse>
      commitCouponReservation(com.project.coupon.generated.model.CouponTransitionRequest request) {
    requestValidator.validateTransition(request);
    validateActor(request.getUserId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
    return ResponseEntity.ok(couponService.commit(request));
  }

  @Override
  @PreAuthorize(
      "isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
          + ServiceScopes.AUTHORITY_COUPONS_WRITE
          + "'))")
  public ResponseEntity<com.project.coupon.generated.model.CouponReservationResponse>
      releaseCouponReservation(com.project.coupon.generated.model.CouponTransitionRequest request) {
    requestValidator.validateTransition(request);
    validateActor(request.getUserId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
    return ResponseEntity.ok(couponService.release(request));
  }

  @Override
  @PreAuthorize(
      "isAuthenticated() and (!T(com.project.common.security.CurrentUser).isService() or hasAuthority('"
          + ServiceScopes.AUTHORITY_COUPONS_WRITE
          + "'))")
  public ResponseEntity<com.project.coupon.generated.model.ValidateCouponResponse> redeemCoupon(
      com.project.coupon.generated.model.RedeemCouponRequest request) {
    requestValidator.validateRedemption(request);
    validateActor(request.getUserId(), ServiceScopes.AUTHORITY_COUPONS_WRITE);
    return ResponseEntity.ok(couponService.redeem(request));
  }

  private void validateActor(UUID claimedUserId, String requiredScope) {
    if (CurrentUser.isService()) {
      if (!CurrentUser.hasAuthority(requiredScope)) {
        throw new ForbiddenOperationException(
            "Service scope does not permit this coupon operation");
      }
      return;
    }
    requestValidator.validateActor(claimedUserId, CurrentUser.requireId(), CurrentUser.isAdmin());
  }
}
